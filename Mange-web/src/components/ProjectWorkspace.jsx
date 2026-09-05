import { useEffect, useMemo, useRef, useState } from 'react'
import {
  ArrowLeft,
  BookOpen,
  CheckCircle2,
  ChevronRight,
  Clapperboard,
  Download,
  FileText,
  Film,
  Image as ImageIcon,
  LoaderCircle,
  MapPinned,
  Pencil,
  Play,
  Save,
  Sparkles,
  WandSparkles,
  X,
} from 'lucide-react'
import {
  generateProjectScript,
  getProjectWorkspace,
  importProjectScript,
  saveProjectScript,
  updateProjectWorkflowStage,
} from '../api/projectApi'
import { useLanguage } from '../i18n/LanguageContext'
import './project-workspace.css'

const WORKFLOW_STAGES = Object.freeze([
  { value: 'OVERVIEW', labelKey: 'workflow.overview', icon: MapPinned },
  { value: 'SCRIPT', labelKey: 'workflow.script', icon: BookOpen },
  { value: 'STORYBOARD', labelKey: 'workflow.storyboard', icon: Film },
  { value: 'ASSETS', labelKey: 'workflow.assets', icon: ImageIcon },
  { value: 'GENERATION', labelKey: 'workflow.generation', icon: WandSparkles },
  { value: 'EXPORT', labelKey: 'workflow.export', icon: Download },
])

/** 展示项目创作阶段，并承载首期剧本工作区。 */
function ProjectWorkspace({ accessToken, projectId, initialStage = 'OVERVIEW', onBack, onOpenStoryboard, onNavigateStage }) {
  const { translate } = useLanguage()
  const [workspace, setWorkspace] = useState(null)
  const [activeStage, setActiveStage] = useState(initialStage)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadWorkspace = async () => {
    setLoading(true)
    setError('')
    try {
      const result = await getProjectWorkspace(accessToken, projectId)
      setWorkspace(result)
      setActiveStage(initialStage === 'OVERVIEW' ? 'OVERVIEW' : initialStage || result.workflowStage || 'SCRIPT')
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadWorkspace()
  }, [accessToken, projectId])

  const project = workspace?.project
  const stageIndex = useMemo(
    () => Math.max(0, WORKFLOW_STAGES.findIndex((stage) => stage.value === activeStage)),
    [activeStage],
  )

  const selectStage = async (stage) => {
    setActiveStage(stage.value)
    onNavigateStage?.(stage.value)
    if (stage.value === 'OVERVIEW') return
    try {
      await updateProjectWorkflowStage(accessToken, projectId, stage.value)
      setWorkspace((current) => current ? { ...current, workflowStage: stage.value } : current)
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  if (loading) {
    return <div className="workspace-state"><LoaderCircle className="spin" size={20} />{translate('common.loading')}</div>
  }
  if (!workspace || !project) {
    return <div className="workspace-alert">{error || translate('projects.detailUnavailable')}</div>
  }

  return (
    <section className="workspace-module project-workspace" aria-labelledby="project-workspace-title">
      <button className="workspace-back" type="button" onClick={onBack}><ArrowLeft size={14} />{translate('projects.backToProjects')}</button>
      <header className="project-workspace-header">
        <div className="project-workspace-cover">
          {project.coverUrl ? <img src={project.coverUrl} alt="" /> : <Clapperboard size={30} />}
        </div>
        <div className="project-workspace-heading">
          <span className="workspace-eyebrow">{translate('projects.workspaceEyebrow')}</span>
          <h1 id="project-workspace-title">{project.name}</h1>
          <p>{project.description || translate('projects.descriptionUnset')}</p>
          <div className="project-workspace-meta">
            <span>{project.genre || translate('projects.genreUnset')}</span>
            <span>{project.aspectRatio}</span>
            <span>{translate(`projectStatus.${project.status}`)}</span>
          </div>
        </div>
        <button className="project-workspace-edit" type="button" onClick={onBack}><Pencil size={14} />{translate('projects.editTitle')}</button>
      </header>

      <nav className="workflow-stepper" aria-label={translate('workflow.navigation')}>
        {WORKFLOW_STAGES.map((stage, index) => {
          const Icon = stage.icon
          const completed = index < stageIndex
          const active = stage.value === activeStage
          return (
            <button
              className={active ? 'workflow-step active' : completed ? 'workflow-step completed' : 'workflow-step'}
              key={stage.value}
              type="button"
              onClick={() => selectStage(stage)}
            >
              <span className="workflow-step-icon">{completed ? <CheckCircle2 size={15} /> : <Icon size={15} />}</span>
              <span>{translate(stage.labelKey)}</span>
              {index < WORKFLOW_STAGES.length - 1 && <ChevronRight className="workflow-step-arrow" size={14} />}
            </button>
          )
        })}
      </nav>

      {error && <div className="workspace-alert">{error}</div>}
      {activeStage === 'OVERVIEW' && (
        <ProjectOverview workspace={workspace} onStage={selectStage} onOpenStoryboard={() => onOpenStoryboard(project)} />
      )}
      {activeStage === 'SCRIPT' && (
        <ProjectScriptWorkspace
          accessToken={accessToken}
          projectId={projectId}
          script={workspace.script}
          onSaved={loadWorkspace}
        />
      )}
      {activeStage === 'STORYBOARD' && (
        <WorkflowPlaceholder icon={Film} title={translate('workflow.storyboard')} description={translate('workflow.storyboardReady')} action={translate('storyboards.create')} onClick={() => onOpenStoryboard(project)} />
      )}
      {activeStage === 'ASSETS' && <WorkflowPlaceholder icon={ImageIcon} title={translate('workflow.assets')} description={translate('workflow.assetsPlanned')} />}
      {activeStage === 'GENERATION' && <WorkflowPlaceholder icon={Sparkles} title={translate('workflow.generation')} description={translate('workflow.generationPlanned')} />}
      {activeStage === 'EXPORT' && <WorkflowPlaceholder icon={Download} title={translate('workflow.export')} description={translate('workflow.exportPlanned')} />}
    </section>
  )
}

function ProjectOverview({ workspace, onStage, onOpenStoryboard }) {
  const { translate } = useLanguage()
  const script = workspace.script
  return (
    <div className="project-overview-grid">
      <article className="project-overview-card project-overview-card-wide">
        <div className="project-card-heading"><span className="project-card-icon"><BookOpen size={18} /></span><div><span className="workspace-eyebrow">{translate('workflow.script')}</span><h2>{script?.title || translate('script.emptyTitle')}</h2></div></div>
        <p>{script?.synopsis || translate('script.emptyDescription')}</p>
        <div className="project-stat-row"><span>{workspace.scriptEpisodeCount} {translate('script.episodes')}</span><span>{workspace.scriptSceneCount} {translate('script.scenes')}</span></div>
        <button type="button" className="workspace-primary" onClick={() => onStage(WORKFLOW_STAGES[1])}><BookOpen size={15} />{translate(script ? 'script.open' : 'script.start')}</button>
      </article>
      <article className="project-overview-card">
        <div className="project-card-heading"><span className="project-card-icon cyan"><Film size={18} /></span><div><span className="workspace-eyebrow">{translate('workflow.storyboard')}</span><h2>{workspace.storyboardShotCount} {translate('script.shots')}</h2></div></div>
        <p>{workspace.storyboardShotCount ? translate('workflow.storyboardReady') : translate('workflow.storyboardEmpty')}</p>
        <button type="button" className="workspace-secondary" onClick={onOpenStoryboard}><Film size={15} />{translate('storyboards.create')}</button>
      </article>
      <article className="project-overview-card project-overview-card-wide project-overview-next">
        <div><span className="workspace-eyebrow">{translate('workflow.nextStep')}</span><h2>{translate('workflow.nextStepTitle')}</h2><p>{translate('workflow.nextStepDescription')}</p></div>
        <button type="button" onClick={() => onStage(WORKFLOW_STAGES[1])}><Play size={15} />{translate('workflow.continue')}</button>
      </article>
    </div>
  )
}

function ProjectScriptWorkspace({ accessToken, projectId, script, onSaved }) {
  const { translate } = useLanguage()
  const [title, setTitle] = useState(script?.title || '')
  const [synopsis, setSynopsis] = useState(script?.synopsis || '')
  const [rawContent, setRawContent] = useState(script?.rawContent || '')
  const [structureText, setStructureText] = useState(JSON.stringify(script?.episodes || [], null, 2))
  const [generationPrompt, setGenerationPrompt] = useState('')
  const [preview, setPreview] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [fileName, setFileName] = useState('')
  const abortRef = useRef(null)

  useEffect(() => {
    setTitle(script?.title || '')
    setSynopsis(script?.synopsis || '')
    setRawContent(script?.rawContent || '')
    setStructureText(JSON.stringify(script?.episodes || [], null, 2))
  }, [script])

  const save = async (episodes = null, content = rawContent) => {
    let parsedEpisodes = episodes
    if (parsedEpisodes === null) {
      try {
        parsedEpisodes = JSON.parse(structureText || '[]')
      } catch {
        setError(translate('script.invalidStructure'))
        return
      }
    }
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await saveProjectScript(accessToken, projectId, {
        title: title.trim() || '未命名剧本',
        synopsis,
        rawContent: content,
        sourceType: script?.sourceType || 'MANUAL',
        episodes: parsedEpisodes,
      })
      setNotice(translate('script.saved'))
      await onSaved()
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setBusy(false)
    }
  }

  const importFile = async (event) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    setFileName(file.name)
    try {
      const content = await file.text()
      setRawContent(content)
      await importProjectScript(accessToken, projectId, content, file.name.toLowerCase().endsWith('.md') ? 'MARKDOWN' : 'TXT')
      setNotice(translate('script.imported'))
      await onSaved()
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  const generate = async () => {
    if (!generationPrompt.trim()) {
      setError(translate('script.promptRequired'))
      return
    }
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    setBusy(true)
    setError('')
    setNotice('')
    setPreview('')
    try {
      await generateProjectScript(accessToken, projectId, generationPrompt, (event) => {
        if (event.event === 'delta') setPreview((current) => current + event.data)
        if (event.event === 'complete') {
          const generated = JSON.parse(event.data)
          setTitle(generated.title || '')
          setSynopsis(generated.synopsis || '')
          setRawContent(generated.rawContent || event.data)
          setStructureText(JSON.stringify(generated.episodes || [], null, 2))
          setNotice(translate('script.generated'))
        }
        if (event.event === 'error') setError(event.data)
      }, controller.signal)
    } catch (requestError) {
      if (requestError.name !== 'AbortError') setError(requestError.message)
    } finally {
      setBusy(false)
      abortRef.current = null
    }
  }

  return (
    <div className="script-workspace">
      <div className="script-workspace-heading"><div><span className="workspace-eyebrow">{translate('workflow.script')}</span><h2>{translate('script.workspaceTitle')}</h2><p>{translate('script.workspaceDescription')}</p></div><label className="workspace-secondary script-import-button"><FileText size={15} />{fileName || translate('script.import')}<input type="file" accept=".txt,.md,text/plain,text/markdown" onChange={importFile} /></label></div>
      <div className="script-editor-grid">
        <div className="script-editor-panel">
          <label className="workspace-field"><span>{translate('script.title')}</span><input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={120} /></label>
          <label className="workspace-field"><span>{translate('script.synopsis')}</span><textarea rows="3" value={synopsis} onChange={(event) => setSynopsis(event.target.value)} /></label>
          <label className="workspace-field"><span>{translate('script.rawContent')}</span><textarea className="script-raw-input" rows="15" value={rawContent} onChange={(event) => setRawContent(event.target.value)} placeholder={translate('script.rawPlaceholder')} /></label>
          <div className="script-panel-footer"><span>{rawContent.length} {translate('script.characters')}</span><button className="workspace-primary" type="button" disabled={busy || !rawContent.trim()} onClick={() => save()}><Save size={15} />{translate(busy ? 'common.saving' : 'common.save')}</button></div>
        </div>
        <div className="script-editor-panel">
          <div className="script-panel-title"><div><span className="workspace-eyebrow">JSON</span><h3>{translate('script.structureTitle')}</h3></div><span>{translate('script.structureHint')}</span></div>
          <textarea className="script-structure-input" rows="27" value={structureText} onChange={(event) => setStructureText(event.target.value)} />
          <div className="script-panel-footer"><span>{translate('script.episodes')}: {countEpisodes(structureText)} · {translate('script.scenes')}: {countScenes(structureText)}</span><button className="workspace-secondary" type="button" disabled={busy} onClick={() => save()}><Save size={15} />{translate('script.saveStructure')}</button></div>
        </div>
      </div>
      <section className="script-ai-panel">
        <div className="script-ai-heading"><span className="project-card-icon"><Sparkles size={18} /></span><div><span className="workspace-eyebrow">{translate('script.aiEyebrow')}</span><h2>{translate('script.aiTitle')}</h2><p>{translate('script.aiDescription')}</p></div></div>
        <textarea rows="3" value={generationPrompt} onChange={(event) => setGenerationPrompt(event.target.value)} placeholder={translate('script.aiPlaceholder')} />
        {preview && <pre className="script-generation-preview">{preview}</pre>}
        {(error || notice) && <p className={error ? 'script-feedback error' : 'script-feedback'}>{error || notice}</p>}
        <div className="script-ai-actions"><button className="workspace-primary" type="button" disabled={busy} onClick={generate}>{busy ? <LoaderCircle className="spin" size={15} /> : <Sparkles size={15} />}{translate(busy ? 'script.generating' : 'script.generate')}</button>{busy && <button className="workspace-secondary" type="button" onClick={() => abortRef.current?.abort()}><X size={15} />{translate('script.stop')}</button>}</div>
      </section>
    </div>
  )
}

function WorkflowPlaceholder({ icon: Icon, title, description, action, onClick }) {
  return <article className="workflow-placeholder"><span className="project-card-icon"><Icon size={22} /></span><span className="workspace-eyebrow">WORKFLOW</span><h2>{title}</h2><p>{description}</p>{action && <button className="workspace-primary" type="button" onClick={onClick}>{action}<ChevronRight size={15} /></button>}</article>
}

function countEpisodes(value) {
  try { return JSON.parse(value || '[]').length } catch { return 0 }
}

function countScenes(value) {
  try { return JSON.parse(value || '[]').reduce((total, episode) => total + (episode.scenes?.length || 0), 0) } catch { return 0 }
}

export default ProjectWorkspace
