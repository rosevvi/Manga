import { useEffect, useState } from 'react'
import { ArrowLeft, BookOpen, CheckCircle2, ChevronRight, Clapperboard, FileText, Film, Image as ImageIcon, LoaderCircle, Plus, RefreshCw, Save, Trash2, WandSparkles } from 'lucide-react'
import { getProjectChapter, getProjectTasks, getProjectWorkspace, importProjectChapter, importProjectScript, saveProjectChapter, deleteProjectChapter } from '../api/projectApi'
import { useLanguage } from '../i18n/LanguageContext'
import './project-workspace.css'

const STAGES = [
  ['OVERVIEW', 'workflow.overview', MapPinIcon], ['SCRIPT', 'workflow.script', BookOpen], ['STORYBOARD', 'workflow.storyboard', Film],
  ['ASSETS', 'workflow.assets', ImageIcon], ['GENERATION', 'workflow.generation', WandSparkles], ['EXPORT', 'workflow.export', FileText],
]

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

  const loadWorkspace = async () => {
    setLoading(true); setError('')
    try { const [nextWorkspace, nextTasks] = await Promise.all([getProjectWorkspace(accessToken, projectId), getProjectTasks(accessToken, projectId)]); setWorkspace(nextWorkspace); setTasks(nextTasks) } catch (e) { setError(e.message) } finally { setLoading(false) }
  }
  useEffect(() => { loadWorkspace() }, [accessToken, projectId])
  useEffect(() => {
    if (!workspace?.tasks?.some((task) => task.status === 'QUEUED' || task.status === 'RUNNING')) return undefined
    const timer = window.setInterval(loadWorkspace, 3000)
    return () => window.clearInterval(timer)
  }, [workspace?.tasks, accessToken, projectId])
  useEffect(() => {
    if (!initialChapterId) return
    setActiveStage('SCRIPT_CHAPTER')
    getProjectChapter(accessToken, projectId, initialChapterId).then((chapter) => {
      setSelectedChapter(chapter); setChapterTitle(chapter.title || ''); setChapterText(chapter.rawContent || '')
    }).catch((e) => setError(e.message))
  }, [accessToken, projectId, initialChapterId])

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
    {activeStage === 'OVERVIEW' && <Overview workspace={workspace} onScript={() => navigateStage('SCRIPT')} onStoryboard={() => onOpenStoryboard(project)} translate={translate} />}
    {activeStage === 'SCRIPT' && <ScriptList chapters={chapters} tasks={tasks} importing={importing} importFull={importFull} importOne={importOne} chapterTitle={chapterTitle} setChapterTitle={setChapterTitle} chapterText={chapterText} setChapterText={setChapterText} onChapter={(chapter) => onNavigateChapter?.(chapter.id)} translate={translate} />}
    {activeStage === 'SCRIPT_CHAPTER' && selectedChapter && <ChapterEditor chapter={selectedChapter} title={chapterTitle} setTitle={setChapterTitle} text={chapterText} setText={setChapterText} saving={importing} onSave={async () => { setImporting(true); try { await saveProjectChapter(accessToken, projectId, selectedChapter.id, { title: chapterTitle, synopsis: selectedChapter.synopsis, rawContent: chapterText, sourceType: selectedChapter.sourceType, scenes: selectedChapter.scenes || [] }); await loadWorkspace() } catch (e) { setError(e.message) } finally { setImporting(false) } }} onDelete={async () => { await deleteProjectChapter(accessToken, projectId, selectedChapter.id); onNavigateStage?.('SCRIPT') }} translate={translate} />}
    {activeStage === 'STORYBOARD' && <Placeholder icon={Film} title={translate('workflow.storyboard')} text={translate('workflow.storyboardReady')} action={() => onOpenStoryboard(project)} label={translate('storyboards.create')} />}
    {activeStage === 'ASSETS' && <Placeholder icon={ImageIcon} title={translate('workflow.assets')} text={translate('workflow.assetsPlanned')} />}
    {activeStage === 'GENERATION' && <Placeholder icon={WandSparkles} title={translate('workflow.generation')} text={translate('workflow.generationPlanned')} />}
    {activeStage === 'EXPORT' && <Placeholder icon={FileText} title={translate('workflow.export')} text={translate('workflow.exportPlanned')} />}
  </section>
}

function Overview({ workspace, onScript, onStoryboard, translate }) { return <div className="project-overview-grid"><article className="project-overview-card project-overview-card-wide"><div className="project-card-heading"><span className="project-card-icon"><BookOpen size={18} /></span><div><span className="workspace-eyebrow">{translate('workflow.script')}</span><h2>{workspace.script?.title || translate('script.emptyTitle')}</h2></div></div><p>{workspace.script ? `${workspace.script.chapterCount} ${translate('script.chapters')}` : translate('script.emptyDescription')}</p><button className="workspace-primary" type="button" onClick={onScript}><BookOpen size={15} />{translate('script.open')}</button></article><article className="project-overview-card"><div className="project-card-heading"><span className="project-card-icon cyan"><Film size={18} /></span><div><span className="workspace-eyebrow">{translate('workflow.storyboard')}</span><h2>{workspace.storyboardShotCount} {translate('script.shots')}</h2></div></div><p>{translate('workflow.storyboardReady')}</p><button className="workspace-secondary" type="button" onClick={onStoryboard}><Film size={15} />{translate('storyboards.create')}</button></article></div> }

function ScriptList({ chapters, tasks, importing, importFull, importOne, chapterTitle, setChapterTitle, chapterText, setChapterText, onChapter, translate }) { return <div className="script-workspace"><div className="script-workspace-heading"><div><span className="workspace-eyebrow">{translate('workflow.script')}</span><h2>{translate('script.chapterList')}</h2><p>{translate('script.chapterListDescription')}</p></div><label className="workspace-secondary script-import-button"><FileText size={15} />{translate('script.importFull')}<input type="file" accept=".txt,.md,text/plain,text/markdown" onChange={importFull} /></label></div>{tasks.filter((task) => task.status === 'QUEUED' || task.status === 'RUNNING').map((task) => <div className="script-task-banner" key={task.id}><LoaderCircle className="spin" size={15} /><span>{task.title} · {task.completedUnits}/{task.totalUnits || '?'} {translate('script.chapters')}</span></div>)}<div className="script-chapter-list">{chapters.length === 0 && <div className="workspace-empty"><BookOpen size={28} /><p>{translate('script.noChapters')}</p></div>}{chapters.map((chapter) => <button className="script-chapter-card" key={chapter.id} type="button" onClick={() => onChapter(chapter)}><span className="script-chapter-number">{chapter.chapterNumber}</span><span><strong>{chapter.title}</strong><small>{chapter.sceneCount} {translate('script.scenes')} · {chapter.rawContentLength} {translate('script.characters')}</small></span><span className={`script-chapter-status ${String(chapter.parseStatus || '').toLowerCase()}`}>{chapter.parseStatus}</span><ChevronRight size={15} /></button>)}</div><section className="script-import-panel"><div className="script-panel-title"><Plus size={17} /><h3>{translate('script.importOne')}</h3></div><input className="script-inline-input" value={chapterTitle} onChange={(event) => setChapterTitle(event.target.value)} placeholder={translate('script.chapterTitle')} /><textarea className="script-raw-input" rows="6" value={chapterText} onChange={(event) => setChapterText(event.target.value)} placeholder={translate('script.rawPlaceholder')} /><div className="script-ai-actions"><span>{importing ? translate('script.processing') : translate('script.singleImportHint')}</span><button className="workspace-primary" type="button" disabled={importing} onClick={importOne}>{importing ? <LoaderCircle className="spin" size={15} /> : <Save size={15} />}{translate('script.saveChapter')}</button></div></section></div> }

function ChapterEditor({ chapter, title, setTitle, text, setText, saving, onSave, onDelete, translate }) { return <div className="script-workspace"><div className="script-workspace-heading"><div><span className="workspace-eyebrow">{translate('script.chapterDetail')}</span><h2>{title}</h2></div><button className="workspace-secondary" type="button" onClick={onDelete}><Trash2 size={15} />{translate('common.delete')}</button></div><label className="workspace-field"><span>{translate('script.chapterTitle')}</span><input className="script-inline-input" value={title} onChange={(event) => setTitle(event.target.value)} /></label><label className="workspace-field"><span>{translate('script.rawContent')}</span><textarea className="script-raw-input" rows="24" value={text} onChange={(event) => setText(event.target.value)} /></label><div className="script-ai-actions"><span>{chapter.parseStatus}</span><button className="workspace-primary" type="button" disabled={saving} onClick={onSave}>{saving ? <LoaderCircle className="spin" size={15} /> : <Save size={15} />}{translate('common.save')}</button></div></div> }

function Placeholder({ icon: Icon, title, text, action, label }) { return <article className="workflow-placeholder"><span className="project-card-icon"><Icon size={22} /></span><span className="workspace-eyebrow">WORKFLOW</span><h2>{title}</h2><p>{text}</p>{action && <button className="workspace-primary" type="button" onClick={action}>{label}<ChevronRight size={15} /></button>}</article> }

export default ProjectWorkspace
