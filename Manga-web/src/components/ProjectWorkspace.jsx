import { useCallback, useEffect, useRef, useState } from 'react'
import { ArrowLeft, BookOpen, ChevronRight, Clapperboard, FileText, Film, Image as ImageIcon, LoaderCircle, Plus, Save, Trash2, WandSparkles } from 'lucide-react'
import { getProjectChapter, getProjectWorkspace, importProjectChapter, importProjectScript, saveProjectChapter, deleteProjectChapter } from '../api/projectApi'
import { createManualRun } from '../api/creativeRunApi'
import { useLanguage } from '../i18n/LanguageContext'
import MediaImage from './MediaImage'
import ProjectCreativeTaskHistory from './ProjectCreativeTaskHistory'
import GenerationJobsPanel from './GenerationJobsPanel'
import { CREATIVE_PROJECT_CHANGED } from './creativeProjectEvents'
import { openCreativeTask } from './creativeTaskEvents'
import useUnsavedChanges from './useUnsavedChanges'
import { REVISION_CONFLICT_CODE } from '../constants/changeSet'
import { CREATIVE_OPERATION, CREATIVE_PERMISSION, CREATIVE_RESOURCE } from '../constants/creative'
import './project-workspace.css'
import './creative-workbench.css'

const STAGES = [
  ['OVERVIEW', '项目概览', Clapperboard], ['SCRIPT', '剧本', BookOpen], ['STORYBOARD', '分镜', Film],
  ['ASSETS', '角色与场景', ImageIcon], ['GENERATION', '图片与视频', WandSparkles], ['EXPORT', '合成与导出', FileText],
]

/** 项目概览与章节编辑共用紧凑框架，所有 AI 执行、审批与结果均进入任务中心。 */
export default function ProjectWorkspace({ accessToken, projectId, initialStage = 'OVERVIEW', initialChapterId, onBack, onOpenStoryboard, onNavigateStage, onNavigateChapter }) {
  const { translate } = useLanguage()
  const [workspace, setWorkspace] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [chapter, setChapter] = useState(null)
  const [title, setTitle] = useState('')
  const [text, setText] = useState('')
  const [conflict, setConflict] = useState(false)
  const [activeTask, setActiveTask] = useState(false)
  const [taskVersion, setTaskVersion] = useState(0)
  const [showGeneration, setShowGeneration] = useState(false)
  const [instruction, setInstruction] = useState('')
  const [showImport, setShowImport] = useState(false)
  const scope = useRef(0)
  const current = useRef(null)
  current.current = { chapter, title, text }
  const stage = initialChapterId ? 'SCRIPT_CHAPTER' : initialStage
  const dirty = chapter ? title !== (chapter.title || '') || text !== (chapter.rawContent || '') : Boolean(title || text)
  useUnsavedChanges(dirty)

  const loadWorkspace = useCallback(async () => {
    const version = ++scope.current
    try {
      const result = await getProjectWorkspace(accessToken, projectId)
      if (version === scope.current) { setWorkspace(result); setError('') }
    } catch (failure) { if (version === scope.current) setError(failure.message) }
    finally { if (version === scope.current) setLoading(false) }
  }, [accessToken, projectId])

  useEffect(() => { void loadWorkspace(); return () => { scope.current += 1 } }, [loadWorkspace])

  /** URL 选章独立读取正文，旧章节请求不会覆盖新选章。 */
  useEffect(() => {
    let stopped = false
    setChapter(null); setTitle(''); setText(''); setConflict(false)
    if (!initialChapterId) return undefined
    getProjectChapter(accessToken, projectId, initialChapterId).then((result) => {
      if (!stopped) { setChapter(result); setTitle(result.title || ''); setText(result.rawContent || '') }
    }).catch((failure) => { if (!stopped) setError(failure.message) })
    return () => { stopped = true }
  }, [accessToken, projectId, initialChapterId])

  /** 审批或采用后刷新业务快照，保留未保存草稿并提示版本冲突。 */
  useEffect(() => {
    const refresh = async (event) => {
      if (String(event.detail?.projectId) !== String(projectId)) return
      void loadWorkspace()
      const before = current.current
      if (!before.chapter) return
      try {
        const latest = await getProjectChapter(accessToken, projectId, before.chapter.id)
        const editor = current.current
        if (editor.chapter?.id !== latest.id) return
        const edited = editor.title !== (editor.chapter.title || '') || editor.text !== (editor.chapter.rawContent || '')
        if (edited) { if (latest.revision !== editor.chapter.revision) setConflict(true); return }
        setChapter(latest); setTitle(latest.title || ''); setText(latest.rawContent || '')
      } catch (failure) { setError(failure.message) }
    }
    window.addEventListener(CREATIVE_PROJECT_CHANGED, refresh)
    return () => window.removeEventListener(CREATIVE_PROJECT_CHANGED, refresh)
  }, [accessToken, projectId, loadWorkspace])

  /** 导入期间串行补偿查询，后台切章导入完成后目录自动恢复。 */
  useEffect(() => {
    if (!workspace?.tasks?.some((task) => ['QUEUED', 'RUNNING'].includes(task.status))) return undefined
    let stopped = false
    let timer
    const poll = async () => { await loadWorkspace(); if (!stopped) timer = window.setTimeout(poll, 3000) }
    timer = window.setTimeout(poll, 3000)
    return () => { stopped = true; window.clearTimeout(timer) }
  }, [workspace?.tasks, loadWorkspace])

  const execute = async (action) => {
    if (busy) return
    setBusy(true); setError('')
    try { await action() }
    catch (failure) { if (failure.code === REVISION_CONFLICT_CODE) setConflict(true); setError(failure.message) }
    finally { setBusy(false) }
  }

  /** 保存读取时的 revision，冲突时保留用户输入。 */
  const save = () => execute(async () => {
    if (!title.trim() || !text.trim()) throw new Error('请填写章节标题和正文')
    if (chapter) {
      const result = await saveProjectChapter(accessToken, projectId, chapter.id, {
        title, synopsis: chapter.synopsis, rawContent: text, sourceType: chapter.sourceType,
        scenes: chapter.scenes || [], expectedRevision: chapter.revision,
      })
      setChapter(result); setTitle(result.title || ''); setText(result.rawContent || ''); setConflict(false)
    } else {
      await importProjectChapter(accessToken, projectId, { title, rawContent: text, sourceType: 'MANUAL' })
      setTitle(''); setText(''); setShowImport(false)
    }
    await loadWorkspace()
  })

  const importFull = (event) => {
    const file = event.target.files?.[0]; event.target.value = ''
    if (!file) return
    void execute(async () => {
      await importProjectScript(accessToken, projectId, file, file.name.toLowerCase().endsWith('.md') ? 'MARKDOWN' : 'TXT')
      await loadWorkspace()
    })
  }

  /** 手动推进不依赖会话，操作目标与权限明确冻结到新 Run。 */
  const startTask = (operationType) => execute(async () => {
    let target
    let prompt
    if (operationType === CREATIVE_OPERATION.PARSE_SCRIPT) {
      target = { resourceType: CREATIVE_RESOURCE.SCRIPT, resourceId: workspace.script.id }
      prompt = translate('script.analysisInstruction')
    } else if (operationType === CREATIVE_OPERATION.GENERATE_CHAPTER_STORYBOARD) {
      target = { resourceType: CREATIVE_RESOURCE.CHAPTER, resourceId: chapter.id }
      prompt = `为章节《${chapter.title}》生成分镜`
    } else {
      target = { resourceType: CREATIVE_RESOURCE.PROJECT, resourceId: projectId }
      prompt = instruction.trim()
    }
    const run = await createManualRun(accessToken, { requestId: globalThis.crypto.randomUUID(), projectId, operationType,
      target, instruction: prompt, permissionPolicy: operationType === CREATIVE_OPERATION.PARSE_SCRIPT
        ? CREATIVE_PERMISSION.READ_ONLY : CREATIVE_PERMISSION.PROPOSE_CHANGES })
    setTaskVersion((value) => value + 1)
    openCreativeTask(run.runId, projectId)
    setShowGeneration(false)
  })

  if (loading) return <div className="workspace-state"><LoaderCircle size={18} className="spin" />正在加载创作工作台…</div>
  if (!workspace) return <div role="alert" className="workspace-alert">{error || '项目暂不可用'}</div>
  const project = workspace.project
  const chapters = workspace.script?.chapters || []
  const editorStage = stage === 'SCRIPT' || stage === 'SCRIPT_CHAPTER'
  const openStage = (value) => value === 'STORYBOARD' || value === 'GENERATION' ? onOpenStoryboard(project) : onNavigateStage?.(value)
  return <section className="workspace-module project-workspace">
    <button className="workspace-back" type="button" onClick={onBack}><ArrowLeft size={14} />全部项目</button>
    <header className="project-workspace-header">
      <div className="project-workspace-cover"><MediaImage src={project.coverPreviewUrl || project.coverUrl} fallback={<Clapperboard size={23} />} /></div>
      <div className="project-workspace-heading"><h1>{project.name}</h1><p>{project.description || '从剧本、分镜到最终成片'}</p></div>
      <button type="button" className="workspace-secondary" onClick={() => openCreativeTask(undefined, projectId)}>AI 任务中心</button>
    </header>
    <nav className="workflow-stepper">{STAGES.map(([value, label, Icon]) => <button type="button" key={value}
      className={stage === value || value === 'SCRIPT' && stage === 'SCRIPT_CHAPTER' ? 'workflow-step active' : 'workflow-step'} onClick={() => openStage(value)}>
      <Icon size={15} />{label}<ChevronRight size={12} /></button>)}</nav>
    {error && <div className="workspace-alert" role="alert">{error}</div>}
    <ProjectCreativeTaskHistory accessToken={accessToken} projectId={projectId} reloadKey={taskVersion} onActiveChanged={setActiveTask} />
    {stage === 'OVERVIEW' && <>
      <div className="overview-stats"><article><small>剧本章节</small><strong>{chapters.length}</strong></article><article><small>镜头数量</small><strong>{workspace.storyboardShotCount || 0}</strong></article>
        <article><small>剧本状态</small><strong style={{ fontSize: 18 }}>{workspace.script ? '已创建' : '待创作'}</strong></article><article><small>创作任务</small><strong style={{ fontSize: 18 }}>{activeTask ? '进行中' : '就绪'}</strong></article></div>
      <div className="overview-columns"><section className="overview-section"><h2>剧本与章节</h2>
        {chapters.slice(0, 6).map((item) => <button type="button" className="overview-chapter" key={item.id} onClick={() => onNavigateChapter(item.id)}><BookOpen size={16} /><strong>{item.title}</strong><small>{item.sceneCount || 0} 场景</small><ChevronRight size={14} /></button>)}
        {!chapters.length && <p>从导入剧本或创作第一章开始。</p>}
        <div className="workbench-action-row" style={{ marginTop: 18 }}><button type="button" className="workspace-primary" onClick={() => openStage('SCRIPT')}><BookOpen size={14} />进入剧本</button>
          <button type="button" className="workspace-secondary" onClick={() => onOpenStoryboard(project)}><Film size={14} />进入分镜</button></div></section>
        <section className="overview-section"><h2>项目创作流程</h2><p>整理章节，生成分镜提案，审批后进入镜头工作区，为每个镜头选择图片和视频模型。</p><p>聊天与手动操作都可以推进创作。所有待审批内容、执行记录和生成产物可在任务中心找回。</p><button type="button" className="workspace-secondary" onClick={() => openCreativeTask(undefined, projectId)}>查看创作记录</button></section></div>
      <section className="overview-section" style={{ marginTop: 16 }}><h2>最近生成产物</h2><GenerationJobsPanel accessToken={accessToken} projectId={projectId} compact /></section>
    </>}
    {editorStage && <div className="script-editor-layout">
      <aside className="chapter-directory"><header><strong>章节目录</strong><span>{chapters.length} 章</span></header>
        {chapters.map((item) => <button type="button" key={item.id} className={String(item.id) === String(initialChapterId) ? 'chapter-item active' : 'chapter-item'} onClick={() => onNavigateChapter(item.id)}>
          <BookOpen size={15} /><span><strong>{item.title}</strong><small>{item.sceneCount || 0} 场景 · {item.rawContentLength || 0} 字</small></span></button>)}
        <button type="button" className="task-new" onClick={() => { setShowImport(true); if (initialChapterId) onNavigateStage('SCRIPT') }}><Plus size={14} />新增章节</button>
      </aside>
      <main className="script-editor-main"><header><div><h2>{chapter ? chapter.title : '剧本创作'}</h2><small>{chapter ? `第 ${chapter.chapterNumber} 章 · ${dirty ? '有未保存修改' : '已保存'}` : '选择章节编辑，或导入一部新的剧本'}</small></div>
        <div className="workbench-action-row">
          <label className="workspace-secondary chapter-import-label"><FileText size={14} />导入剧本<input type="file" disabled={busy} accept=".txt,.md,text/plain,text/markdown" onChange={importFull} /></label>
          <button type="button" className="workspace-secondary" disabled={busy || activeTask || !workspace.script?.id} onClick={() => startTask(CREATIVE_OPERATION.PARSE_SCRIPT)}><WandSparkles size={14} />解析剧本</button>
          {chapter && <><button type="button" className="workspace-secondary" disabled={busy || activeTask || dirty} title={dirty ? '请先保存章节，再生成分镜' : ''} onClick={() => startTask(CREATIVE_OPERATION.GENERATE_CHAPTER_STORYBOARD)}><Film size={14} />生成分镜</button>
            <button type="button" className="workspace-primary" disabled={busy || !dirty} onClick={save}><Save size={14} />保存</button></>}
          {!chapter && <button type="button" className="workspace-primary" onClick={() => setShowGeneration((value) => !value)}><WandSparkles size={14} />AI 创作章节</button>}
        </div></header>
        {conflict && <div className="workspace-alert" role="alert">章节已发生变化，当前编辑仍保留。<button type="button" className="workspace-secondary" onClick={() => {
          if (!window.confirm('重新加载将放弃当前修改，确定继续吗？')) return
          void execute(async () => { const latest = await getProjectChapter(accessToken, projectId, chapter.id); setChapter(latest); setTitle(latest.title || ''); setText(latest.rawContent || ''); setConflict(false) })
        }}>重新加载最新章节</button></div>}
        {showGeneration && <section className="script-import-panel"><h3>AI 创作新章节</h3><textarea rows={4} className="script-raw-input" style={{ minHeight: 120 }} maxLength={12000}
          value={instruction} onChange={(event) => setInstruction(event.target.value)} placeholder="描述本章剧情目标、角色和风格，生成后可先预览再批准。" />
          <div className="workbench-action-row"><button type="button" className="workspace-primary" disabled={busy || activeTask || !instruction.trim()} onClick={() => startTask(CREATIVE_OPERATION.GENERATE_SCRIPT_CHAPTER)}>生成章节提案</button>
            <button type="button" className="workspace-secondary" onClick={() => setShowGeneration(false)}>收起</button></div></section>}
        {chapter || showImport ? <>
          <label className="workspace-field"><span>章节标题</span><input className="script-inline-input" maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
          <label className="workspace-field"><span>正文</span><textarea className="script-raw-input" rows={22} value={text} onChange={(event) => setText(event.target.value)} placeholder="在这里写下故事…" /></label>
          <div className="workbench-action-row"><small className="workbench-muted">{text.length} 字</small>
            <button type="button" className="workspace-primary" disabled={busy || !title.trim() || !text.trim()} onClick={save}><Save size={14} />{chapter ? '保存修改' : '保存新章节'}</button>
            {chapter && <button type="button" className="workspace-secondary" disabled={busy} onClick={() => {
              if (!window.confirm('确定删除这个章节吗？')) return
              void execute(async () => { await deleteProjectChapter(accessToken, projectId, chapter.id); setChapter(null); setTitle(''); setText(''); await loadWorkspace(); onNavigateStage('SCRIPT') })
            }}><Trash2 size={14} />删除章节</button>}</div>
        </> : <div className="workbench-empty"><BookOpen size={35} /><h2>{chapters.length ? '选择一个章节，继续创作' : '你的故事，从这里开始'}</h2><p>导入 TXT / Markdown 剧本，或手动写下第一章。</p>
          <button type="button" className="workspace-secondary" onClick={() => setShowImport(true)}><Plus size={14} />手动新增章节</button></div>}
      </main>
    </div>}
    {!['OVERVIEW', 'SCRIPT', 'SCRIPT_CHAPTER'].includes(stage) && <section className="workbench-empty"><Clapperboard size={30} /><h2>{stage === 'ASSETS' ? '角色与场景' : '合成与导出'}</h2><p>此项业务能力尚未开放，先完成剧本和分镜创作。</p></section>}
  </section>
}
