import { useEffect, useMemo, useRef, useState } from 'react'
import { ArrowDown, ArrowLeft, ArrowUp, BookOpen, Film, ImageOff, Plus, Save, Trash2, WandSparkles } from 'lucide-react'
import { createStoryboardShot, deleteStoryboardShot, getProjectWorkspace, getStoryboardShots, reorderStoryboardShots, updateStoryboardShot } from '../api/projectApi'
import { createManualRun } from '../api/creativeRunApi'
import { PROJECT_LIMITS, STORYBOARD_SHOT_STATUS } from '../constants/project'
import { CREATIVE_OPERATION, CREATIVE_PERMISSION, CREATIVE_RESOURCE } from '../constants/creative'
import { CREATIVE_PROJECT_CHANGED } from './creativeProjectEvents'
import { openCreativeTask } from './creativeTaskEvents'
import useUnsavedChanges, { confirmCreativeNavigation } from './useUnsavedChanges'
import ImageGenerationPanel from './ImageGenerationPanel'
import GenerationJobsPanel from './GenerationJobsPanel'
import ProjectCreativeTaskHistory from './ProjectCreativeTaskHistory'
import WorkspaceSelect from './WorkspaceSelect'
import MediaImage from './MediaImage'
import './project-module.css'
import './project-workspace.css'
import './creative-workbench.css'

const EMPTY_SHOT = Object.freeze({ chapterId: null, title: '', sceneName: '', shotType: '', cameraMovement: '',
  durationSeconds: 5, content: '', dialogue: '', soundEffect: '', imageUrl: '', notes: '', status: STORYBOARD_SHOT_STATUS.DRAFT })

/** 只取可编辑字段，保留章节关联，避免全量更新时意外把已有镜头移出章节。 */
function shotForm(shot) {
  return Object.fromEntries(Object.keys(EMPTY_SHOT).map((field) => [field, shot?.[field] ?? EMPTY_SHOT[field]]))
}
function restoredSelection() {
  const query = new URLSearchParams(window.location.search)
  return { chapter: query.get('chapter') || 'all', scene: query.get('scene') || '', shot: query.get('shot') || '' }
}

/** 分镜工作台：目录、镜头编辑、媒体生成和底部镜头条共用一份服务端快照。 */
export default function StoryboardModule({ accessToken, projects, selectedProjectId, onSelectProject, onBackToProjects, onNavigateStage }) {
  const [shots, setShots] = useState([])
  const [chapters, setChapters] = useState([])
  const [selection, setSelection] = useState(restoredSelection)
  const [form, setForm] = useState(EMPTY_SHOT)
  const [baseline, setBaseline] = useState(JSON.stringify(EMPTY_SHOT))
  const [creating, setCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [mediaType, setMediaType] = useState(() => new URLSearchParams(window.location.search).get('media') === 'VIDEO' ? 'VIDEO' : 'IMAGE')
  const [mobileView, setMobileView] = useState('content')
  const readVersion = useRef(0)
  const current = useRef(null)
  const projectId = selectedProjectId ?? projects[0]?.id
  const project = projects.find((item) => String(item.id) === String(projectId))
  const dirty = JSON.stringify(form) !== baseline
  useUnsavedChanges(dirty)
  current.current = { dirty, creating }

  const visibleShots = useMemo(() => shots.filter((shot) => (selection.chapter === 'all'
    || selection.chapter === 'unassigned' && !shot.chapterId || String(shot.chapterId) === selection.chapter)
    && (!selection.scene || (shot.sceneName || '未命名场景') === selection.scene)), [shots, selection.chapter, selection.scene])
  const selectedShot = !creating ? visibleShots.find((shot) => String(shot.id) === selection.shot) || visibleShots[0] : null

  /** 返回键、刷新和分享 URL 均可恢复章节、场景、镜头。 */
  useEffect(() => {
    const pop = () => { setSelection(restoredSelection()); setCreating(false) }
    window.addEventListener('popstate', pop)
    return () => window.removeEventListener('popstate', pop)
  }, [])

  const load = async () => {
    const version = ++readVersion.current
    if (!projectId) return
    setLoading(true)
    try {
      const [items, workspace] = await Promise.all([getStoryboardShots(accessToken, projectId), getProjectWorkspace(accessToken, projectId)])
      if (version !== readVersion.current) return
      setShots(items); setChapters(workspace.script?.chapters || []); setError('')
    } catch (failure) { if (version === readVersion.current) setError(failure.message) }
    finally { if (version === readVersion.current) setLoading(false) }
  }
  useEffect(() => {
    setCreating(false); setForm(EMPTY_SHOT); setBaseline(JSON.stringify(EMPTY_SHOT))
    setSelection(restoredSelection()); void load()
    return () => { readVersion.current += 1 }
  }, [accessToken, projectId])

  useEffect(() => {
    const refresh = (event) => { if (String(event.detail?.projectId) === String(projectId)) void load() }
    window.addEventListener(CREATIVE_PROJECT_CHANGED, refresh)
    return () => window.removeEventListener(CREATIVE_PROJECT_CHANGED, refresh)
  }, [accessToken, projectId])

  useEffect(() => {
    if (creating) return
    const next = shotForm(selectedShot)
    setForm(next); setBaseline(JSON.stringify(next))
  }, [selectedShot?.id, creating])

  /** 正文更新或采用图片后，只有没有本地草稿才刷新编辑字段。 */
  useEffect(() => {
    if (!selectedShot || current.current.dirty || creating) return
    const next = shotForm(selectedShot)
    setForm(next); setBaseline(JSON.stringify(next))
  }, [selectedShot, creating])

  const navigateSelection = (patch) => {
    if (!confirmCreativeNavigation()) return
    const next = { ...selection, ...patch }
    const query = new URLSearchParams(window.location.search)
    for (const [key, value] of Object.entries(next)) { if (value && value !== 'all') query.set(key, value); else query.delete(key) }
    window.history.pushState(null, '', `${window.location.pathname}${query.size ? `?${query}` : ''}`)
    setSelection(next); setCreating(false); setMobileView('content')
  }
  const chooseShot = (shot) => navigateSelection({ shot: String(shot.id) })
  const chooseChapter = (id) => navigateSelection({ chapter: id, scene: '', shot: '' })
  const update = (field, value) => setForm((previous) => ({ ...previous, [field]: value }))
  const create = () => {
    if (!confirmCreativeNavigation()) return
    const next = { ...EMPTY_SHOT, chapterId: /^\d+$/.test(selection.chapter) ? Number(selection.chapter) : null,
      sceneName: selection.scene === '未命名场景' ? '' : selection.scene }
    setCreating(true); setForm(next); setBaseline(JSON.stringify(next)); setMobileView('content')
  }
  const save = async (event) => {
    event.preventDefault()
    if (saving || !form.title.trim()) return
    setSaving(true); setError('')
    const version = readVersion.current
    try {
      const payload = { ...form, durationSeconds: Number(form.durationSeconds) }
      const saved = creating ? await createStoryboardShot(accessToken, projectId, payload)
        : await updateStoryboardShot(accessToken, projectId, selectedShot.id, payload)
      if (version !== readVersion.current) return
      const next = shotForm(saved)
      setForm(next); setBaseline(JSON.stringify(next)); setCreating(false)
      setSelection({ chapter: saved.chapterId ? String(saved.chapterId) : 'unassigned', scene: '', shot: String(saved.id) })
      const query = new URLSearchParams({ chapter: saved.chapterId ? String(saved.chapterId) : 'unassigned', shot: String(saved.id) })
      window.history.replaceState(null, '', `${window.location.pathname}?${query}`)
      await load()
    } catch (failure) { if (version === readVersion.current) setError(failure.message) }
    finally { setSaving(false) }
  }
  const remove = async () => {
    if (!selectedShot || !window.confirm(`确定删除镜头「${selectedShot.title}」吗？`)) return
    setSaving(true)
    try { await deleteStoryboardShot(accessToken, projectId, selectedShot.id); setForm(EMPTY_SHOT); setBaseline(JSON.stringify(EMPTY_SHOT)); await load() }
    catch (failure) { setError(failure.message) }
    finally { setSaving(false) }
  }
  const move = async (direction) => {
    if (!selectedShot || saving) return
    const index = shots.findIndex((shot) => shot.id === selectedShot.id)
    const target = index + direction
    if (target < 0 || target >= shots.length) return
    const reordered = [...shots]
    ;[reordered[index], reordered[target]] = [reordered[target], reordered[index]]
    setSaving(true)
    try { setShots(await reorderStoryboardShots(accessToken, projectId, reordered.map((shot) => shot.id))) }
    catch (failure) { setError(failure.message) }
    finally { setSaving(false) }
  }
  const generateChapter = async () => {
    if (!/^\d+$/.test(selection.chapter) || saving) return
    setSaving(true)
    try {
      const chapter = chapters.find((item) => String(item.id) === selection.chapter)
      const run = await createManualRun(accessToken, { requestId: globalThis.crypto.randomUUID(), projectId,
        operationType: CREATIVE_OPERATION.GENERATE_CHAPTER_STORYBOARD, permissionPolicy: CREATIVE_PERMISSION.PROPOSE_CHANGES,
        target: { resourceType: CREATIVE_RESOURCE.CHAPTER, resourceId: Number(selection.chapter) },
        instruction: `为章节《${chapter?.title || selection.chapter}》生成分镜提案，等待批准后保存。` })
      openCreativeTask(run.runId, projectId)
    } catch (failure) { setError(failure.message) }
    finally { setSaving(false) }
  }
  const changeMedia = (type) => {
    setMediaType(type)
    const query = new URLSearchParams(window.location.search); query.set('media', type)
    window.history.replaceState(null, '', `${window.location.pathname}?${query}`)
  }

  if (!projects.length) return <section className="workspace-module workbench-empty"><Film size={34} /><h2>先创建一个项目</h2><button type="button" className="workspace-primary" onClick={onBackToProjects}>进入项目</button></section>
  const directoryChapters = [...chapters.map((chapter) => ({ id: String(chapter.id), title: chapter.title })),
    ...(shots.some((shot) => !shot.chapterId) ? [{ id: 'unassigned', title: '未归属章节' }] : [])]
  return <section className="workspace-module storyboard-studio">
    <button type="button" className="workspace-back" onClick={onBackToProjects}><ArrowLeft size={14} />全部项目</button>
    <header className="workbench-project-header"><Film size={24} /><div><h1>{project?.name || '分镜工作台'}</h1><small>分镜工作区 · {shots.length} 个镜头</small></div>
      <button type="button" className="workspace-secondary" onClick={() => openCreativeTask(undefined, projectId)}>AI 任务中心</button>
      <button type="button" className="workspace-primary" onClick={create}><Plus size={14} />新增镜头</button></header>
    <nav className="workflow-stepper">{[['OVERVIEW', '项目概览'], ['SCRIPT', '剧本'], ['STORYBOARD', '分镜'], ['GENERATION', '图片与视频']].map(([value, label]) => <button type="button" key={value}
      className={value === 'STORYBOARD' ? 'workflow-step active' : 'workflow-step'} onClick={() => {
        if (value === 'GENERATION') { setMobileView('media'); return }
        if (value !== 'STORYBOARD') onNavigateStage?.(value)
      }}>{label}</button>)}</nav>
    <ProjectCreativeTaskHistory accessToken={accessToken} projectId={projectId} />
    <div className="shot-studio-context"><WorkspaceSelect label="选择项目" value={String(projectId)} options={projects.map((item) => ({ value: String(item.id), label: item.name }))}
      onChange={(id) => onSelectProject(Number(id))} />
      <WorkspaceSelect label="筛选章节" value={selection.chapter} onChange={chooseChapter} options={[{ value: 'all', label: '全部章节' }, ...directoryChapters.map((item) => ({ value: item.id, label: item.title }))]} />
      <button type="button" className="workspace-secondary" disabled={!/^\d+$/.test(selection.chapter) || saving || dirty} onClick={generateChapter}><WandSparkles size={14} />生成本章分镜</button>
      {loading && <small className="workbench-muted">正在刷新…</small>}</div>
    {error && <div className="workspace-alert" role="alert">{error}</div>}
    <div className="workbench-tabs studio-mobile-tabs">{[['directory', '目录'], ['content', '镜头内容'], ['media', '图片与视频']].map(([value, label]) => <button type="button" className={mobileView === value ? 'active' : ''} key={value} onClick={() => setMobileView(value)}>{label}</button>)}</div>
    <div className="storyboard-studio-layout" data-view={mobileView}>
      <aside className="chapter-directory storyboard-directory"><header><strong>分镜目录</strong><small>{chapters.length} 章</small></header>
        <button type="button" className={selection.chapter === 'all' ? 'chapter-item active' : 'chapter-item'} onClick={() => chooseChapter('all')}><Film size={15} /><strong>全部镜头</strong></button>
        {directoryChapters.map((chapter) => {
          const chapterShots = shots.filter((shot) => chapter.id === 'unassigned' ? !shot.chapterId : String(shot.chapterId) === chapter.id)
          const scenes = [...new Set(chapterShots.map((shot) => shot.sceneName || '未命名场景'))]
          return <div key={chapter.id}><button type="button" className={selection.chapter === chapter.id ? 'chapter-item active' : 'chapter-item'} onClick={() => chooseChapter(chapter.id)}>
            <BookOpen size={14} /><span><strong>{chapter.title}</strong><small>{chapterShots.length} 个镜头</small></span></button>
            {scenes.map((scene) => <button type="button" key={scene} className={selection.chapter === chapter.id && selection.scene === scene ? 'scene-item active' : 'scene-item'}
              onClick={() => navigateSelection({ chapter: chapter.id, scene, shot: '' })}>{scene}</button>)}</div>
        })}
      </aside>
      {selectedShot || creating ? <form className="shot-detail-editor" onSubmit={save}><header><div><h2>{creating ? '新建镜头' : selectedShot.shotNumber || '镜头详情'}</h2><small className="workbench-muted">{dirty ? '有未保存的修改' : '已保存'}</small></div>
        <div className="workbench-action-row">{!creating && <button type="button" className="workbench-icon-button" aria-label="删除镜头" disabled={saving} onClick={remove}><Trash2 size={15} /></button>}
          <button type="submit" className="workspace-primary" disabled={saving || !form.title.trim() || !dirty}><Save size={14} />保存</button></div></header>
        <label className="workspace-field"><span>镜头标题</span><input required maxLength={PROJECT_LIMITS.shotTitle} value={form.title} onChange={(event) => update('title', event.target.value)} /></label>
        <div className="shot-field-grid"><label className="workspace-field"><span>所属章节</span><WorkspaceSelect label="镜头所属章节" value={form.chapterId ? String(form.chapterId) : ''}
          options={[{ value: '', label: '未归属章节' }, ...chapters.map((item) => ({ value: String(item.id), label: item.title }))]} onChange={(id) => update('chapterId', id ? Number(id) : null)} /></label>
          <label className="workspace-field"><span>场景</span><input maxLength={PROJECT_LIMITS.shotSceneName} value={form.sceneName} onChange={(event) => update('sceneName', event.target.value)} /></label>
          <label className="workspace-field"><span>景别</span><input maxLength={PROJECT_LIMITS.shotType} value={form.shotType} onChange={(event) => update('shotType', event.target.value)} placeholder="如：全景、特写" /></label>
          <label className="workspace-field"><span>运镜</span><input maxLength={PROJECT_LIMITS.shotCameraMovement} value={form.cameraMovement} onChange={(event) => update('cameraMovement', event.target.value)} placeholder="如：缓慢推进" /></label>
          <label className="workspace-field"><span>时长（秒）</span><input type="number" required min={0} max={PROJECT_LIMITS.shotDurationSeconds} value={form.durationSeconds} onChange={(event) => update('durationSeconds', Number(event.target.value))} /></label>
          <label className="workspace-field"><span>制作状态</span><WorkspaceSelect label="镜头状态" value={form.status} onChange={(value) => update('status', value)} options={Object.values(STORYBOARD_SHOT_STATUS).map((value) => ({ value, label: ({ DRAFT: '草稿', READY: '就绪', GENERATING: '生成中', COMPLETED: '已完成' })[value] || value }))} /></label></div>
        {[['content', '画面内容', 5, PROJECT_LIMITS.shotContent], ['dialogue', '对白', 3, PROJECT_LIMITS.shotDialogue], ['soundEffect', '声音与音效', 2, PROJECT_LIMITS.shotSoundEffect], ['notes', '制作备注', 2, PROJECT_LIMITS.shotNotes]].map(([field, label, rows, max]) => <label className="workspace-field" key={field}><span>{label}</span><textarea rows={rows} maxLength={max} value={form[field]} onChange={(event) => update(field, event.target.value)} /></label>)}
        <details><summary className="workbench-muted">参考图地址</summary><input aria-label="参考图地址" maxLength={PROJECT_LIMITS.shotImageUrl} value={form.imageUrl} onChange={(event) => update('imageUrl', event.target.value)} /></details>
      </form> : <main className="shot-detail-editor workbench-empty"><Film size={34} /><h2>让文字变成镜头</h2><p>选择章节生成分镜提案，或手动添加第一个镜头。</p><button type="button" className="workspace-primary" onClick={create}><Plus size={14} />新增镜头</button></main>}
      <aside className="shot-media-preview"><div className="workbench-tabs"><button type="button" className={mediaType === 'IMAGE' ? 'active' : ''} onClick={() => changeMedia('IMAGE')}>图片</button><button type="button" className={mediaType === 'VIDEO' ? 'active' : ''} onClick={() => changeMedia('VIDEO')}>视频</button></div>
        {selectedShot ? <>{mediaType === 'IMAGE' && <div className="shot-preview-frame"><MediaImage src={selectedShot.imageUrl} alt={selectedShot.title} fallback={<ImageOff size={30} />} showErrorText /></div>}
          <ImageGenerationPanel key={`${projectId}.${selectedShot.id}.${mediaType}`} accessToken={accessToken} projectId={projectId} selectedShot={selectedShot} shots={shots} mediaType={mediaType} />
          <GenerationJobsPanel key={`${projectId}.${selectedShot.id}.${mediaType}.history`} accessToken={accessToken} projectId={projectId} shotId={selectedShot.id} mediaType={mediaType} /></> : <p className="workbench-muted">保存并选择一个镜头后，可配置模型、生成图片或 Agnes 视频。</p>}
      </aside>
    </div>
    <section className="shot-filmstrip"><header><strong>镜头条 · {visibleShots.length} 个镜头</strong><div className="workbench-action-row">
      <button type="button" className="workbench-icon-button" aria-label="前移镜头" disabled={saving || !selectedShot || shots[0]?.id === selectedShot.id} onClick={() => move(-1)}><ArrowUp size={14} /></button>
      <button type="button" className="workbench-icon-button" aria-label="后移镜头" disabled={saving || !selectedShot || shots.at(-1)?.id === selectedShot.id} onClick={() => move(1)}><ArrowDown size={14} /></button>
      <button type="button" className="workspace-secondary" onClick={create}><Plus size={13} />添加镜头</button></div></header>
      <div className="shot-filmstrip-scroll">{visibleShots.map((shot) => <button type="button" key={shot.id} className={selectedShot?.id === shot.id ? 'shot-thumbnail active' : 'shot-thumbnail'} onClick={() => chooseShot(shot)}>
        <span className="shot-thumbnail-visual"><MediaImage src={shot.imageUrl} alt={shot.title} fallback={<ImageOff size={23} />} /></span><span className="shot-thumbnail-label"><strong>{shot.shotNumber || shot.title}</strong><small>{shot.durationSeconds}s</small></span></button>)}</div>
    </section>
  </section>
}
