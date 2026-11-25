import { useCallback, useEffect, useRef, useState } from 'react'
import { BookOpen, FileText, Film, LayoutGrid, LoaderCircle, Plus, Save, Trash2, WandSparkles } from 'lucide-react'
import { getProjectChapter, getProjectWorkspace, importProjectChapter, importProjectScript, saveProjectChapter, deleteProjectChapter } from '../api/projectApi'
import { createManualRun } from '../api/creativeRunApi'
import { useLanguage } from '../i18n/LanguageContext'
import GenerationJobsPanel from './GenerationJobsPanel'
import ProjectStudioHeader from './ProjectStudioHeader'
import ScriptChapterWorkspace from './ScriptChapterWorkspace'
import { CREATIVE_PROJECT_CHANGED } from './creativeProjectEvents'
import { openCreativeTask } from './creativeTaskEvents'
import useUnsavedChanges, { useDiscardChangesConfirmation } from './useUnsavedChanges'
import { CHAPTER_ASSET_PREVIEW } from './scriptChapterAssetPreview'
import { useWorkspaceConfirm } from './WorkspaceConfirmDialog'
import { REVISION_CONFLICT_CODE } from '../constants/changeSet'
import { CREATIVE_OPERATION, CREATIVE_PERMISSION, CREATIVE_RESOURCE } from '../constants/creative'
import './project-workspace.css'
import './creative-workbench.css'

const SCRIPT_VIEW = Object.freeze({ OVERVIEW: 'overview', SOURCE: 'source' })

/** 项目概览、章节概要和原文编辑共用创作工作台。 */
export default function ProjectWorkspace({ accessToken, projectId, initialStage = 'OVERVIEW', initialChapterId, onBack, onNavigateStage, onNavigateChapter }) {
  const { translate } = useLanguage()
  const confirm = useWorkspaceConfirm()
  const confirmDiscard = useDiscardChangesConfirmation()
  const [workspace, setWorkspace] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [chapter, setChapter] = useState(null)
  const [title, setTitle] = useState('')
  const [text, setText] = useState('')
  const [conflict, setConflict] = useState(false)
  const [showGeneration, setShowGeneration] = useState(false)
  const [instruction, setInstruction] = useState('')
  const [showImport, setShowImport] = useState(false)
  const [mobileView, setMobileView] = useState('content')
  const [scriptView, setScriptView] = useState(SCRIPT_VIEW.OVERVIEW)
  const [chapterAssetDrafts, setChapterAssetDrafts] = useState({})
  const scope = useRef(0)
  const current = useRef(null)
  current.current = { chapter, title, text }
  const stage = initialChapterId ? 'SCRIPT_CHAPTER' : initialStage
  const editorStage = stage === 'SCRIPT' || stage === 'SCRIPT_CHAPTER'
  const chapters = workspace?.script?.chapters || []
  const selectedChapterId = editorStage && !showImport ? initialChapterId ?? chapters[0]?.id : null
  const selectedChapter = String(chapter?.id) === String(selectedChapterId) ? chapter
    : chapters.find((item) => String(item.id) === String(selectedChapterId))
  const showChapterOverview = Boolean(selectedChapter && scriptView === SCRIPT_VIEW.OVERVIEW)
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
    if (!selectedChapterId) return undefined
    setScriptView(SCRIPT_VIEW.OVERVIEW)
    setMobileView('content')
    getProjectChapter(accessToken, projectId, selectedChapterId).then((result) => {
      if (!stopped) { setChapter(result); setTitle(result.title || ''); setText(result.rawContent || '') }
    }).catch((failure) => { if (!stopped) setError(failure.message) })
    return () => { stopped = true }
  }, [accessToken, projectId, selectedChapterId])

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

  /** 解析、生成和审批任务统一从任务中心查看。 */
  const startTask = (operationType) => execute(async () => {
    let target
    let prompt
    const parsing = operationType === CREATIVE_OPERATION.PARSE_SCRIPT
    if (parsing) {
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
      target, instruction: prompt, permissionPolicy: parsing ? CREATIVE_PERMISSION.READ_ONLY : CREATIVE_PERMISSION.PROPOSE_CHANGES })
    openCreativeTask(run.runId, projectId)
    if (!parsing) setShowGeneration(false)
  })

  const project = workspace?.project
  const shotCount = workspace?.storyboardShotCount || 0

  const openNewChapter = async () => {
    if (initialChapterId && !(await onNavigateStage('SCRIPT'))) return
    if (!initialChapterId && dirty && !(await confirmDiscard())) return
    setShowImport(true)
    setScriptView(SCRIPT_VIEW.SOURCE)
    setMobileView('content')
  }

  const openChapterOverview = async (item) => {
    if (String(item.id) !== String(selectedChapterId) && !(await onNavigateChapter(item.id))) return
    setShowImport(false)
    setScriptView(SCRIPT_VIEW.OVERVIEW)
    setMobileView('content')
  }

  return <section className="workspace-module project-studio">
    <ProjectStudioHeader project={project} projectId={projectId} stage={editorStage ? 'SCRIPT' : 'OVERVIEW'} loading={loading}
      summary={[`${chapters.length} 章`, editorStage ? selectedChapter?.title : `${shotCount} 个镜头`].filter(Boolean).join(' · ')} onBack={onBack}
      onNavigateStage={(value) => { if (value !== stage) onNavigateStage(value) }}>
      {editorStage ? <>
        <button type="button" className="workspace-secondary" disabled={busy || !workspace?.script?.id}
          onClick={() => startTask(CREATIVE_OPERATION.PARSE_SCRIPT)}><WandSparkles size={15} />解析剧本</button>
        {selectedChapter ? <button type="button" className="workspace-primary" disabled={busy || dirty || !chapter}
          title={dirty ? '请先保存章节，再生成分镜' : ''} onClick={() => startTask(CREATIVE_OPERATION.GENERATE_CHAPTER_STORYBOARD)}><Film size={15} />生成分镜</button>
          : <button type="button" className="workspace-primary" disabled={busy || !project} onClick={() => {
            setShowGeneration((value) => scriptView === SCRIPT_VIEW.SOURCE ? !value : true)
            setScriptView(SCRIPT_VIEW.SOURCE); setMobileView('content')
          }}><WandSparkles size={15} />AI 创作章节</button>}
      </> : <>
        <button type="button" className="workspace-secondary" onClick={() => onNavigateStage('STORYBOARD')}><Film size={15} />进入分镜</button>
        <button type="button" className="workspace-primary" onClick={() => onNavigateStage('SCRIPT')}><BookOpen size={15} />进入剧本</button>
      </>}
    </ProjectStudioHeader>
    {error && <div className="workspace-alert" role="alert">{error}</div>}
    <div className="workbench-tabs studio-mobile-tabs" role="group" aria-label={editorStage ? '剧本工作区' : '概览工作区'}>
      {[
        ['directory', '章节目录'],
        ['content', showChapterOverview ? '章节概览' : editorStage ? '原文' : '项目详情'],
        ...(showChapterOverview ? [['assets', '章节资产']] : []),
      ].map(([value, label]) => <button type="button" key={value}
        className={mobileView === value ? 'active' : ''} aria-pressed={mobileView === value}
        onClick={() => setMobileView(value)}>{label}</button>)}
    </div>
    <div className={`project-studio-layout project-content-layout${showChapterOverview ? ' script-chapter-layout' : ''}`} data-view={mobileView}>
      <aside className="studio-directory" aria-label="章节目录">
        <header className="studio-directory-heading"><strong>章节目录</strong><span>{chapters.length} 章</span></header>
        <div className="studio-directory-scroll">
          {workspace && !chapters.length && <p className="studio-directory-empty">暂无章节</p>}
          {chapters.map((item) => <button type="button" key={item.id} className="studio-chapter-row"
            aria-current={String(item.id) === String(selectedChapterId) || undefined} onClick={() => openChapterOverview(item)}>
            <BookOpen size={15} aria-hidden="true" /><strong title={item.title}>{item.title}</strong><small>{item.sceneCount || 0} 场景 · {item.rawContentLength || 0} 字</small>
          </button>)}
        </div>
        {editorStage && <button type="button" className="workspace-secondary studio-directory-create" disabled={!project || busy} onClick={openNewChapter}><Plus size={15} />新增章节</button>}
      </aside>
      {showChapterOverview ? <ScriptChapterWorkspace key={selectedChapterId} chapter={selectedChapter}
        project={project} assets={chapterAssetDrafts[selectedChapterId] || CHAPTER_ASSET_PREVIEW}
        onAssetsChange={(update) => setChapterAssetDrafts((current) => ({
          ...current,
          [selectedChapterId]: update(current[selectedChapterId] || CHAPTER_ASSET_PREVIEW),
        }))}
        onShowAssets={() => setMobileView('assets')}
        onOpenSource={() => { setScriptView(SCRIPT_VIEW.SOURCE); setMobileView('content') }} />
        : editorStage ? <section className="project-content-panel script-editor-main script-source" aria-label="剧本原文">
        <header><div><h2>{chapter ? chapter.title : '剧本创作'}</h2>{chapter && <small>{`第 ${chapter.chapterNumber} 章 · ${dirty ? '有未保存修改' : '已保存'}`}</small>}</div>
        <div className="workbench-action-row">
            {selectedChapter && <button type="button" className="workspace-secondary" onClick={() => {
              setScriptView(SCRIPT_VIEW.OVERVIEW); setMobileView('content')
            }}><LayoutGrid size={14} />查看概览</button>}
          <label className="workspace-secondary chapter-import-label"><FileText size={14} />导入剧本<input type="file" disabled={busy || !project} accept=".txt,.md,text/plain,text/markdown" onChange={importFull} /></label>
          {chapter && <button type="button" className="workspace-primary" disabled={busy || !dirty} onClick={save}><Save size={14} />保存</button>}
        </div></header>
        {conflict && <div className="workspace-alert" role="alert">章节已发生变化，当前编辑仍保留。<button type="button" className="workspace-secondary" onClick={async () => {
          if (!(await confirmDiscard(translate('confirm.reloadChapter')))) return
          void execute(async () => { const latest = await getProjectChapter(accessToken, projectId, chapter.id); setChapter(latest); setTitle(latest.title || ''); setText(latest.rawContent || ''); setConflict(false) })
        }}>重新加载最新章节</button></div>}
        {showGeneration && <section className="script-import-panel"><h3>AI 创作新章节</h3><textarea rows={4} className="script-raw-input" style={{ minHeight: 120 }} maxLength={12000}
          value={instruction} onChange={(event) => setInstruction(event.target.value)} placeholder="描述本章剧情目标、角色和风格，生成后可先预览再批准。" />
          <div className="workbench-action-row"><button type="button" className="workspace-primary" disabled={busy || !instruction.trim()} onClick={() => startTask(CREATIVE_OPERATION.GENERATE_SCRIPT_CHAPTER)}>生成章节提案</button>
            <button type="button" className="workspace-secondary" onClick={() => setShowGeneration(false)}>收起</button></div></section>}
        {chapter || showImport ? <>
          <label className="workspace-field"><span>章节标题</span><input className="script-inline-input" maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
          <label className="workspace-field"><span>正文</span><textarea className="script-raw-input" rows={12} value={text} onChange={(event) => setText(event.target.value)} placeholder="在这里写下故事…" /></label>
          <div className="workbench-action-row"><small className="workbench-muted">{text.length} 字</small>
            <button type="button" className="workspace-primary" disabled={busy || !title.trim() || !text.trim()} onClick={save}><Save size={14} />{chapter ? '保存修改' : '保存新章节'}</button>
            {chapter && <button type="button" className="workspace-secondary" disabled={busy} onClick={async () => {
              if (!(await confirm({
                title: translate('confirm.deleteChapterTitle'), message: translate('confirm.deleteChapterMessage', { title: chapter.title }),
                confirmLabel: translate('common.delete'), danger: true,
              }))) return
              void execute(async () => { await deleteProjectChapter(accessToken, projectId, chapter.id); setChapter(null); setTitle(''); setText(''); await loadWorkspace(); onNavigateStage('SCRIPT') })
            }}><Trash2 size={14} />删除章节</button>}</div>
        </> : <div className="workbench-empty">
            {loading || selectedChapterId && !error ? <div className="workspace-state"><LoaderCircle size={18} className="spin" />正在加载剧本…</div> : project && <>
            <BookOpen size={35} /><h2>{chapters.length ? '选择一个章节，继续创作' : '你的故事，从这里开始'}</h2><p>导入 TXT / Markdown 剧本，或手动写下第一章。</p>
            <button type="button" className="workspace-secondary" disabled={busy} onClick={openNewChapter}><Plus size={14} />手动新增章节</button>
          </>}
        </div>}
      </section> : <section className="project-content-panel" aria-label="项目详情">
        {loading && <div className="workspace-state"><LoaderCircle size={18} className="spin" />正在加载项目概览…</div>}
        {workspace && <>
          {project.description && <p className="overview-description">{project.description}</p>}
          <dl className="overview-stats">
            <div><dt>剧本章节</dt><dd>{chapters.length}</dd></div>
            <div><dt>镜头数量</dt><dd>{shotCount}</dd></div>
            <div><dt>剧本状态</dt><dd>{workspace.script ? '已创建' : '待创作'}</dd></div>
          </dl>
          <section className="overview-artifacts">
            <h2>最近生成产物</h2>
            <GenerationJobsPanel accessToken={accessToken} projectId={projectId} compact />
          </section>
        </>}
      </section>}
    </div>
  </section>
}
