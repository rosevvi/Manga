import { useCallback, useEffect, useRef, useState } from 'react'
import { ArrowLeft, BookOpen, CheckCircle2, ChevronRight, Clapperboard, FileText, Film, Image as ImageIcon, LoaderCircle, Plus, RefreshCw, Save, Trash2, WandSparkles } from 'lucide-react'
import { getProjectChapter, getProjectTasks, getProjectWorkspace, importProjectChapter, importProjectScript, saveProjectChapter, deleteProjectChapter } from '../api/projectApi'
import { createManualRun, getCreativeRun, streamCreativeRunEvents } from '../api/creativeRunApi'
import { useLanguage } from '../i18n/LanguageContext'
import './project-workspace.css'
import ManualCreativeTaskPanel from './ManualCreativeTaskPanel'
import ProjectCreativeTaskHistory from './ProjectCreativeTaskHistory'
import { CREATIVE_PROJECT_CHANGED } from './creativeProjectEvents'
import { REVISION_CONFLICT_CODE } from '../constants/changeSet'
import { CREATIVE_OPERATION, CREATIVE_PERMISSION, CREATIVE_RESOURCE } from '../constants/creative'

const STAGES = [
  ['OVERVIEW', 'workflow.overview', MapPinIcon], ['SCRIPT', 'workflow.script', BookOpen], ['STORYBOARD', 'workflow.storyboard', Film],
  ['ASSETS', 'workflow.assets', ImageIcon], ['GENERATION', 'workflow.generation', WandSparkles], ['EXPORT', 'workflow.export', FileText],
]

const TERMINAL_RUN_STATUSES = new Set(['COMPLETED', 'FAILED', 'CANCELLED'])
const SCRIPT_ANALYSIS_STORAGE_PREFIX = 'manga.script.analysis.'

/** 创建浏览器兼容的手动 Run 幂等标识。 */
function nextRequestId() { return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}` }

/** 安全恢复当前项目最近一次剧本解析游标。 */
function restoreScriptAnalysis(projectId) {
  try { return JSON.parse(sessionStorage.getItem(`${SCRIPT_ANALYSIS_STORAGE_PREFIX}${projectId}`) || 'null') }
  catch { return null }
}

function MapPinIcon({ size = 16 }) { return <span style={{ fontSize: size }}>⌖</span> }

/** 项目工作区：只加载章节摘要，章节正文通过独立详情接口按需读取。 */
function ProjectWorkspace({ accessToken, projectId, initialStage = 'OVERVIEW', initialChapterId, onBack, onOpenStoryboard, onNavigateStage, onNavigateChapter }) {
  const { translate } = useLanguage()
  const [workspace, setWorkspace] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [activeStage, setActiveStage] = useState(initialChapterId ? 'SCRIPT_CHAPTER' : initialStage)
  const [importing, setImporting] = useState(false)
  const [chapterTitle, setChapterTitle] = useState('')
  const [chapterText, setChapterText] = useState('')
  const [selectedChapter, setSelectedChapter] = useState(null)
  const [tasks, setTasks] = useState([])
  const analysisKey = `${SCRIPT_ANALYSIS_STORAGE_PREFIX}${projectId}`
  const analysisRef = useRef(restoreScriptAnalysis(projectId))
  const analysisCursorRef = useRef(analysisRef.current?.lastSequence || 0)
  const analysisAbortRef = useRef(null)
  const analysisReconnectRef = useRef(null)
  const [scriptAnalysis, setScriptAnalysis] = useState(analysisRef.current)
  const [reviewRunId, setReviewRunId] = useState(null)
  const [reviewRunStatus, setReviewRunStatus] = useState(null)
  const [hasServerActiveRun, setHasServerActiveRun] = useState(false)
  const [runListVersion, setRunListVersion] = useState(0)
  const [chapterConflict, setChapterConflict] = useState(false)
  const editor = useRef(null)
  const chapterReadVersion = useRef(0)
  const workspaceReadVersion = useRef(0)
  editor.current = { chapter: selectedChapter, title: chapterTitle, text: chapterText }
  const [generationInstruction, setGenerationInstruction] = useState('')
  const hasActiveReview = hasServerActiveRun || (Boolean(reviewRunId) && !TERMINAL_RUN_STATUSES.has(reviewRunStatus))

  /** 切换项目时恢复该项目的手动任务，避免串用审批卡片。 */
  useEffect(() => {
    setReviewRunId(null); setReviewRunStatus(null); setHasServerActiveRun(false); setGenerationInstruction('')
  }, [projectId, accessToken])

  /** 新任务立即展示，任务清单随后从服务端重新读取。 */
  const rememberReviewRun = (id) => {
    setReviewRunId(id)
    setReviewRunStatus(null)
    setRunListVersion((value) => value + 1)
  }

  const loadWorkspace = async (showLoading = true) => {
    const version = ++workspaceReadVersion.current
    if (showLoading) setLoading(true)
    setError('')
    try {
      const [nextWorkspace, nextTasks] = await Promise.all([getProjectWorkspace(accessToken, projectId), getProjectTasks(accessToken, projectId)])
      if (version !== workspaceReadVersion.current) return
      setWorkspace(nextWorkspace); setTasks(nextTasks)
    } catch (failure) { if (version === workspaceReadVersion.current) setError(failure.message) }
    finally { if (version === workspaceReadVersion.current) setLoading(false) }
  }
  useEffect(() => { loadWorkspace() }, [accessToken, projectId])
  useEffect(() => {
    if (!workspace?.tasks?.some((task) => task.status === 'QUEUED' || task.status === 'RUNNING')) return undefined
    const timer = window.setInterval(loadWorkspace, 3000)
    return () => window.clearInterval(timer)
  }, [workspace?.tasks, accessToken, projectId])
  useEffect(() => {
    if (!initialChapterId) return
    let stopped = false
    chapterReadVersion.current += 1
    setActiveStage('SCRIPT_CHAPTER')
    getProjectChapter(accessToken, projectId, initialChapterId).then((chapter) => {
      if (stopped) return
      setSelectedChapter(chapter); setChapterTitle(chapter.title || ''); setChapterText(chapter.rawContent || '')
      setChapterConflict(false)
    }).catch((e) => { if (!stopped) setError(e.message) })
    return () => { stopped = true; chapterReadVersion.current += 1 }
  }, [accessToken, projectId, initialChapterId])

  /** 审批后刷新章节；保留未保存编辑及其旧版本，用户显式重载后才替换草稿。 */
  const refreshChapter = async (force = false) => {
    const id = editor.current?.chapter?.id
    if (!id) return
    const version = ++chapterReadVersion.current
    const latest = await getProjectChapter(accessToken, projectId, id)
    if (version !== chapterReadVersion.current || editor.current?.chapter?.id !== id) return
    const current = editor.current
    const dirty = current.title !== current.chapter.title || current.text !== current.chapter.rawContent
    if (!force && dirty && latest.revision !== current.chapter.revision) { setChapterConflict(true); return }
    if (!force && dirty) return
    setSelectedChapter(latest); setChapterTitle(latest.title || ''); setChapterText(latest.rawContent || ''); setChapterConflict(false)
  }

  /** 保存编辑器读取时的版本；遇到冲突保留用户输入，不自动覆盖新内容。 */
  const saveChapterChanges = async () => {
    if (!selectedChapter || importing) return
    setImporting(true); setError('')
    try {
      const saved = await saveProjectChapter(accessToken, projectId, selectedChapter.id, {
        title: chapterTitle, synopsis: selectedChapter.synopsis, rawContent: chapterText,
        sourceType: selectedChapter.sourceType, scenes: selectedChapter.scenes || [], expectedRevision: selectedChapter.revision,
      })
      setSelectedChapter(saved); setChapterConflict(false)
      await loadWorkspace(false)
    } catch (failure) {
      if (failure.code === REVISION_CONFLICT_CODE) setChapterConflict(true)
      setError(failure.message)
    } finally { setImporting(false) }
  }

  /** 会话审批和手动审批共用业务刷新信号。 */
  useEffect(() => {
    const refresh = (event) => {
      if (String(event.detail.projectId) !== String(projectId)) return
      loadWorkspace(false)
      refreshChapter().catch((failure) => setError(failure.message))
    }
    window.addEventListener(CREATIVE_PROJECT_CHANGED, refresh)
    return () => window.removeEventListener(CREATIVE_PROJECT_CHANGED, refresh)
  }, [accessToken, projectId])

  /** 同时保存 UI 状态和断线重连所需的 Run cursor。 */
  const persistScriptAnalysis = useCallback((patch) => {
    const next = { ...(analysisRef.current || {}), ...patch }
    analysisRef.current = next
    setScriptAnalysis(next)
    sessionStorage.setItem(analysisKey, JSON.stringify(next))
  }, [analysisKey])

  /** 切换项目时隔离解析 Run、文本与 cursor，避免跨项目串流。 */
  useEffect(() => {
    analysisAbortRef.current?.abort()
    const restored = restoreScriptAnalysis(projectId)
    analysisRef.current = restored
    analysisCursorRef.current = restored?.lastSequence || 0
    setScriptAnalysis(restored)
  }, [projectId])

  /** 严格按 sequence 应用解析事件，重复回放不会重复追加文本。 */
  const applyScriptAnalysisEvent = useCallback((event) => {
    if (event.sequence <= analysisCursorRef.current) return
    analysisCursorRef.current = event.sequence
    const current = analysisRef.current || {}
    const content = event.eventType === 'CONTENT_DELTA'
      ? `${current.content || ''}${event.payload?.delta || ''}` : current.content || ''
    const tool = event.eventType === 'TOOL_STARTED' || event.eventType === 'TOOL_COMPLETED'
      ? { name: event.payload?.toolName || '', status: event.payload?.status || '' } : current.tool
    const status = event.eventType === 'RUN_COMPLETED' ? 'COMPLETED'
      : event.eventType === 'RUN_FAILED' ? 'FAILED'
        : event.eventType === 'RUN_CANCELLED' ? 'CANCELLED' : current.status
    persistScriptAnalysis({ lastSequence: event.sequence, content, tool, status })
    if (event.eventType === 'RUN_FAILED') {
      getCreativeRun(accessToken, event.runId).then((run) => {
        persistScriptAnalysis({ error: run.errorMessage || translate('script.analysisFailed') })
      }).catch(() => persistScriptAnalysis({ error: translate('script.analysisFailed') }))
    }
  }, [accessToken, persistScriptAnalysis, translate])

  /** 从已提交 cursor 连接 SSE，非终态网络中断后自动续连。 */
  const connectScriptAnalysis = useCallback(async (runId) => {
    analysisAbortRef.current?.abort()
    const controller = new AbortController()
    analysisAbortRef.current = controller
    try {
      await streamCreativeRunEvents(
        accessToken, runId, analysisCursorRef.current, applyScriptAnalysisEvent, controller.signal,
      )
    } catch (streamError) {
      if (controller.signal.aborted || TERMINAL_RUN_STATUSES.has(analysisRef.current?.status)) return
      setError(streamError.message)
      analysisReconnectRef.current = window.setTimeout(() => connectScriptAnalysis(runId), 1500)
    }
  }, [accessToken, applyScriptAnalysisEvent])

  /** 页面重开时先确认 Run 状态，再从本地 cursor 恢复未完成的解析流。 */
  useEffect(() => {
    const saved = analysisRef.current
    if (!accessToken || !saved?.runId) return undefined
    getCreativeRun(accessToken, saved.runId).then((run) => {
      persistScriptAnalysis({ status: run.status })
      if (!TERMINAL_RUN_STATUSES.has(run.status)) connectScriptAnalysis(run.runId)
    }).catch((restoreError) => setError(restoreError.message))
    return () => {
      analysisAbortRef.current?.abort()
      window.clearTimeout(analysisReconnectRef.current)
    }
  }, [accessToken, connectScriptAnalysis, persistScriptAnalysis])

  const navigateStage = (stage) => { setActiveStage(stage); onNavigateStage?.(stage) }
  const importFull = async (event) => {
    const file = event.target.files?.[0]; event.target.value = ''; if (!file) return
    setImporting(true); setError('')
    try { await importProjectScript(accessToken, projectId, file, file.name.toLowerCase().endsWith('.md') ? 'MARKDOWN' : 'TXT'); await loadWorkspace() }
    catch (e) { setError(e.message) } finally { setImporting(false) }
  }
  const importOne = async () => {
    if (!chapterTitle.trim() || !chapterText.trim()) return setError('请填写章节标题和正文')
    setImporting(true); setError('')
    try { await importProjectChapter(accessToken, projectId, { title: chapterTitle, rawContent: chapterText, sourceType: 'MANUAL' }); setChapterTitle(''); setChapterText(''); await loadWorkspace() }
    catch (e) { setError(e.message) } finally { setImporting(false) }
  }
  /** 从章节编辑页手动创建独立 Creative Run，不依赖助手会话推进工作流。 */
  const generateChapterStoryboard = async () => {
    if (!selectedChapter || hasActiveReview) return
    setImporting(true); setError('')
    try {
      const run = await createManualRun(accessToken, {
        requestId: nextRequestId(),
        projectId,
        operationType: CREATIVE_OPERATION.GENERATE_CHAPTER_STORYBOARD,
        target: { resourceType: CREATIVE_RESOURCE.CHAPTER, resourceId: selectedChapter.id },
        permissionPolicy: CREATIVE_PERMISSION.PROPOSE_CHANGES,
        instruction: `为章节《${chapterTitle}》生成分镜`,
      })
      rememberReviewRun(run.runId)
    } catch (e) { setError(e.message) } finally { setImporting(false) }
  }

  /** 手动生成新章节，与聊天入口使用相同的提案和审批链路。 */
  const generateScriptChapter = async () => {
    if (!generationInstruction.trim() || hasActiveReview) return
    setImporting(true); setError('')
    try {
      const run = await createManualRun(accessToken, {
        requestId: nextRequestId(), projectId, operationType: CREATIVE_OPERATION.GENERATE_SCRIPT_CHAPTER,
        target: { resourceType: CREATIVE_RESOURCE.PROJECT, resourceId: projectId }, permissionPolicy: CREATIVE_PERMISSION.PROPOSE_CHANGES,
        instruction: generationInstruction.trim(),
      })
      rememberReviewRun(run.runId)
    } catch (failure) { setError(failure.message) }
    finally { setImporting(false) }
  }

  /** 手动启动只读剧本解析 Agent，并立即订阅可恢复的运行事件。 */
  const parseScript = async () => {
    if (!workspace?.script?.id) return setError(translate('script.analysisUnavailable'))
    setError('')
    analysisAbortRef.current?.abort()
    analysisCursorRef.current = 0
    persistScriptAnalysis({ runId: null, lastSequence: 0, content: '', tool: null, error: '', status: 'QUEUED' })
    try {
      const run = await createManualRun(accessToken, {
        requestId: nextRequestId(),
        projectId,
        operationType: CREATIVE_OPERATION.PARSE_SCRIPT,
        target: { resourceType: CREATIVE_RESOURCE.SCRIPT, resourceId: workspace.script.id },
        permissionPolicy: CREATIVE_PERMISSION.READ_ONLY,
        instruction: translate('script.analysisInstruction'),
      })
      persistScriptAnalysis({ runId: run.runId, status: run.status })
      connectScriptAnalysis(run.runId)
    } catch (requestError) {
      persistScriptAnalysis({ status: 'FAILED', error: requestError.message })
      setError(requestError.message)
    }
  }

  if (loading) return <div className="workspace-state"><LoaderCircle className="spin" size={18} />{translate('common.loading')}</div>
  if (!workspace) return <div className="workspace-alert">{error || translate('projects.detailUnavailable')}</div>
  const project = workspace.project
  const chapters = workspace.script?.chapters || []
  const stageIndex = Math.max(0, STAGES.findIndex(([value]) => value === activeStage))

  return <section className="workspace-module project-workspace">
    <button className="workspace-back" type="button" onClick={onBack}><ArrowLeft size={14} />{translate('projects.backToProjects')}</button>
    <header className="project-workspace-header"><div className="project-workspace-cover">{project.coverUrl ? <img src={project.coverUrl} alt="" /> : <Clapperboard size={30} />}</div><div className="project-workspace-heading"><span className="workspace-eyebrow">{translate('projects.workspaceEyebrow')}</span><h1>{project.name}</h1><p>{project.description || translate('projects.descriptionUnset')}</p></div></header>
    <nav className="workflow-stepper">{STAGES.map(([value, label, Icon], index) => <button key={value} type="button" className={value === activeStage ? 'workflow-step active' : index < stageIndex ? 'workflow-step completed' : 'workflow-step'} onClick={() => navigateStage(value)}><span className="workflow-step-icon">{index < stageIndex ? <CheckCircle2 size={15} /> : <Icon size={15} />}</span>{translate(label)}{index < STAGES.length - 1 && <ChevronRight size={13} />}</button>)}</nav>
    {error && <div className="workspace-alert">{error}</div>}
    {chapterConflict && <div className="workspace-alert" role="alert">章节已被更新，已保留你的未保存编辑。
      <button type="button" onClick={() => refreshChapter(true).catch((failure) => setError(failure.message))}>重新加载最新章节（放弃当前编辑）</button>
    </div>}
    <ProjectCreativeTaskHistory accessToken={accessToken} projectId={projectId} selectedRunId={reviewRunId}
      reloadKey={runListVersion} onSelect={(id) => { setReviewRunId(id); setReviewRunStatus(null) }} onActiveChanged={setHasServerActiveRun} />
    {activeStage === 'SCRIPT' && <section className="script-import-panel">
      <h3>AI 生成新章节</h3>
      <textarea className="script-raw-input" rows={3} maxLength={12000} value={generationInstruction}
        onChange={(event) => setGenerationInstruction(event.target.value)} placeholder="描述章节目标、人物和剧情要求，生成后先预览再批准保存。" />
      <button className="workspace-primary" type="button" disabled={importing || hasActiveReview || !generationInstruction.trim()} onClick={generateScriptChapter}>生成章节提案</button>
      {hasActiveReview && <small>当前手动任务尚未结束，请先处理下方审批或停止任务。</small>}
    </section>}
    {reviewRunId && <ManualCreativeTaskPanel key={`${projectId}.${reviewRunId}`} accessToken={accessToken}
      projectId={projectId} runId={reviewRunId} onRunChanged={rememberReviewRun} onStatusChanged={setReviewRunStatus} />}
    {activeStage === 'OVERVIEW' && <Overview workspace={workspace} onScript={() => navigateStage('SCRIPT')} onStoryboard={() => onOpenStoryboard(project)} translate={translate} />}
    {activeStage === 'SCRIPT' && <ScriptList chapters={chapters} tasks={tasks} importing={importing} importFull={importFull} importOne={importOne} chapterTitle={chapterTitle} setChapterTitle={setChapterTitle} chapterText={chapterText} setChapterText={setChapterText} onChapter={(chapter) => onNavigateChapter?.(chapter.id)} onParseScript={parseScript} scriptAnalysis={scriptAnalysis} hasScript={Boolean(workspace.script?.id)} translate={translate} />}
    {activeStage === 'SCRIPT_CHAPTER' && selectedChapter && <ChapterEditor chapter={selectedChapter} title={chapterTitle} setTitle={setChapterTitle} text={chapterText} setText={setChapterText} saving={importing} generating={hasActiveReview} onGenerateStoryboard={generateChapterStoryboard} onSave={saveChapterChanges} onDelete={async () => { await deleteProjectChapter(accessToken, projectId, selectedChapter.id); onNavigateStage?.('SCRIPT') }} translate={translate} />}
    {activeStage === 'STORYBOARD' && <Placeholder icon={Film} title={translate('workflow.storyboard')} text={translate('workflow.storyboardReady')} action={() => onOpenStoryboard(project)} label={translate('storyboards.create')} />}
    {activeStage === 'ASSETS' && <Placeholder icon={ImageIcon} title={translate('workflow.assets')} text={translate('workflow.assetsPlanned')} />}
    {activeStage === 'GENERATION' && <Placeholder icon={WandSparkles} title={translate('workflow.generation')} text={translate('workflow.generationPlanned')} />}
    {activeStage === 'EXPORT' && <Placeholder icon={FileText} title={translate('workflow.export')} text={translate('workflow.exportPlanned')} />}
  </section>
}

function Overview({ workspace, onScript, onStoryboard, translate }) { return <div className="project-overview-grid"><article className="project-overview-card project-overview-card-wide"><div className="project-card-heading"><span className="project-card-icon"><BookOpen size={18} /></span><div><span className="workspace-eyebrow">{translate('workflow.script')}</span><h2>{workspace.script?.title || translate('script.emptyTitle')}</h2></div></div><p>{workspace.script ? `${workspace.script.chapterCount} ${translate('script.chapters')}` : translate('script.emptyDescription')}</p><button className="workspace-primary" type="button" onClick={onScript}><BookOpen size={15} />{translate('script.open')}</button></article><article className="project-overview-card"><div className="project-card-heading"><span className="project-card-icon cyan"><Film size={18} /></span><div><span className="workspace-eyebrow">{translate('workflow.storyboard')}</span><h2>{workspace.storyboardShotCount} {translate('script.shots')}</h2></div></div><p>{translate('workflow.storyboardReady')}</p><button className="workspace-secondary" type="button" onClick={onStoryboard}><Film size={15} />{translate('storyboards.create')}</button></article></div> }

function ScriptList({ chapters, tasks, importing, importFull, importOne, chapterTitle, setChapterTitle, chapterText, setChapterText, onChapter, onParseScript, scriptAnalysis, hasScript, translate }) { const analysisActive = scriptAnalysis?.status && !TERMINAL_RUN_STATUSES.has(scriptAnalysis.status); return <div className="script-workspace"><div className="script-workspace-heading"><div><span className="workspace-eyebrow">{translate('workflow.script')}</span><h2>{translate('script.chapterList')}</h2><p>{translate('script.chapterListDescription')}</p></div><div className="script-heading-actions"><button className="workspace-primary" type="button" disabled={!hasScript || analysisActive} onClick={onParseScript}>{analysisActive ? <LoaderCircle className="spin" size={15} /> : <WandSparkles size={15} />}{translate('script.analyze')}</button><label className="workspace-secondary script-import-button"><FileText size={15} />{translate('script.importFull')}<input type="file" accept=".txt,.md,text/plain,text/markdown" onChange={importFull} /></label></div></div>{tasks.filter((task) => task.status === 'QUEUED' || task.status === 'RUNNING').map((task) => <div className="script-task-banner" key={task.id}><LoaderCircle className="spin" size={15} /><span>{task.title} · {task.completedUnits}/{task.totalUnits || '?'} {translate('script.chapters')}</span></div>)}{scriptAnalysis && <section className="script-analysis-panel"><div className="script-panel-title"><WandSparkles size={17} /><h3>{translate('script.analysisTitle')}</h3><span className={`script-analysis-status ${String(scriptAnalysis.status || '').toLowerCase()}`}>{scriptAnalysis.status}</span></div>{scriptAnalysis.tool?.status && <small>{translate('script.analysisTool')} · {scriptAnalysis.tool.status}</small>}{scriptAnalysis.error && <small className="script-analysis-error">{scriptAnalysis.error}</small>}<pre>{scriptAnalysis.content || translate('script.analysisWaiting')}</pre></section>}<div className="script-chapter-list">{chapters.length === 0 && <div className="workspace-empty"><BookOpen size={28} /><p>{translate('script.noChapters')}</p></div>}{chapters.map((chapter) => <button className="script-chapter-card" key={chapter.id} type="button" onClick={() => onChapter(chapter)}><span className="script-chapter-number">{chapter.chapterNumber}</span><span><strong>{chapter.title}</strong><small>{chapter.sceneCount} {translate('script.scenes')} · {chapter.rawContentLength} {translate('script.characters')}</small></span><span className={`script-chapter-status ${String(chapter.parseStatus || '').toLowerCase()}`}>{chapter.parseStatus}</span><ChevronRight size={15} /></button>)}</div><section className="script-import-panel"><div className="script-panel-title"><Plus size={17} /><h3>{translate('script.importOne')}</h3></div><input className="script-inline-input" value={chapterTitle} onChange={(event) => setChapterTitle(event.target.value)} placeholder={translate('script.chapterTitle')} /><textarea className="script-raw-input" rows="6" value={chapterText} onChange={(event) => setChapterText(event.target.value)} placeholder={translate('script.rawPlaceholder')} /><div className="script-ai-actions"><span>{importing ? translate('script.processing') : translate('script.singleImportHint')}</span><button className="workspace-primary" type="button" disabled={importing} onClick={importOne}>{importing ? <LoaderCircle className="spin" size={15} /> : <Save size={15} />}{translate('script.saveChapter')}</button></div></section></div> }

function ChapterEditor({ chapter, title, setTitle, text, setText, saving, generating, onSave, onDelete, onGenerateStoryboard, translate }) { return <div className="script-workspace"><div className="script-workspace-heading"><div><span className="workspace-eyebrow">{translate('script.chapterDetail')}</span><h2>{title}</h2></div><button className="workspace-secondary" type="button" onClick={onDelete}><Trash2 size={15} />{translate('common.delete')}</button></div><label className="workspace-field"><span>{translate('script.chapterTitle')}</span><input className="script-inline-input" value={title} onChange={(event) => setTitle(event.target.value)} /></label><label className="workspace-field"><span>{translate('script.rawContent')}</span><textarea className="script-raw-input" rows="24" value={text} onChange={(event) => setText(event.target.value)} /></label><div className="script-ai-actions"><span>{chapter.parseStatus}</span><button className="workspace-secondary" type="button" disabled={saving || generating} onClick={onGenerateStoryboard}><Film size={15} />生成本章分镜</button><button className="workspace-primary" type="button" disabled={saving} onClick={onSave}>{saving ? <LoaderCircle className="spin" size={15} /> : <Save size={15} />}{translate('common.save')}</button></div></div> }

function Placeholder({ icon: Icon, title, text, action, label }) { return <article className="workflow-placeholder"><span className="project-card-icon"><Icon size={22} /></span><span className="workspace-eyebrow">WORKFLOW</span><h2>{title}</h2><p>{text}</p>{action && <button className="workspace-primary" type="button" onClick={action}>{label}<ChevronRight size={15} /></button>}</article> }

export default ProjectWorkspace
