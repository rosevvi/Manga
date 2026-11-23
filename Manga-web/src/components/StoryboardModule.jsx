import { useEffect, useMemo, useRef, useState } from 'react'
import {
  ArrowDown, ArrowUp, AudioLines, ChevronLeft, ChevronRight, FileText, Film,
  Image as ImageIcon, MessageSquare, Pencil, Plus, Save, Trash2, WandSparkles, X,
} from 'lucide-react'
import { createStoryboardShot, deleteStoryboardShot, getProjectWorkspace, getStoryboardShots, reorderStoryboardShots, updateStoryboardShot } from '../api/projectApi'
import { createManualRun } from '../api/creativeRunApi'
import { PROJECT_LIMITS, STORYBOARD_SHOT_STATUS } from '../constants/project'
import { CREATIVE_OPERATION, CREATIVE_PERMISSION, CREATIVE_RESOURCE } from '../constants/creative'
import { useLanguage } from '../i18n/LanguageContext'
import { CREATIVE_PROJECT_CHANGED } from './creativeProjectEvents'
import { openCreativeTask } from './creativeTaskEvents'
import useUnsavedChanges, { useConfirmCreativeNavigation } from './useUnsavedChanges'
import { useWorkspaceConfirm } from './WorkspaceConfirmDialog'
import { pushWorkspaceHistory, replaceWorkspaceHistory } from './workspaceHistory'
import StoryboardDirectory from './StoryboardDirectory'
import StoryboardMediaPreview, { StoryboardVideoThumbnail } from './StoryboardMediaPreview'
import ProjectStudioHeader from './ProjectStudioHeader'
import WorkspaceSelect from './WorkspaceSelect'
import './project-module.css'
import './project-workspace.css'
import './creative-workbench.css'

const EMPTY_SHOT = Object.freeze({ chapterId: null, title: '', sceneName: '', shotType: '', cameraMovement: '',
  durationSeconds: 5, content: '', dialogue: '', soundEffect: '', imageUrl: '', notes: '', videoPrompt: null, status: STORYBOARD_SHOT_STATUS.DRAFT })
const SHOT_STATUS_LABELS = {
  [STORYBOARD_SHOT_STATUS.DRAFT]: '草稿',
  [STORYBOARD_SHOT_STATUS.READY]: '就绪',
  [STORYBOARD_SHOT_STATUS.COMPLETED]: '已完成',
}
const SHOT_TEXT_FIELDS = [
  ['content', '画面内容', PROJECT_LIMITS.shotContent, ImageIcon],
  ['dialogue', '对白', PROJECT_LIMITS.shotDialogue, MessageSquare],
  ['soundEffect', '声音与音效', PROJECT_LIMITS.shotSoundEffect, AudioLines],
  ['videoPrompt', '视频提示词', PROJECT_LIMITS.shotVideoPrompt, FileText],
]
const SHOT_METADATA_FIELDS = [
  ['sceneName', '场景', PROJECT_LIMITS.shotSceneName],
  ['shotType', '景别', PROJECT_LIMITS.shotType],
  ['cameraMovement', '运镜', PROJECT_LIMITS.shotCameraMovement],
]
const ALL = 'all'
const UNASSIGNED = 'unassigned'
const UNNAMED_SCENE = '未命名场景'
const chapterOf = (shot) => shot.chapterId ? String(shot.chapterId) : UNASSIGNED
const sceneOf = (shot) => shot.sceneName || UNNAMED_SCENE

/** 旧镜头沿用画面或标题，显式清空的提示词保持为空。 */
const shotVideoPrompt = (shot) => shot?.videoPrompt ?? (shot?.content?.trim() || shot?.title?.trim() || '')

/** 只取写入接口接受的字段，保留隐藏的备注和参考图。 */
function shotForm(shot) {
  return Object.fromEntries(Object.keys(EMPTY_SHOT).map((field) => [field, shot?.[field] ?? EMPTY_SHOT[field]]))
}
function restoredSelection() {
  const query = new URLSearchParams(window.location.search)
  return { chapter: query.get('chapter') || ALL, scene: query.get('scene') || '', shot: query.get('shot') || '' }
}

/** 分镜工作台：目录、镜头编辑、媒体生成和底部镜头条共用一份服务端快照。 */
export default function StoryboardModule({ accessToken, projectId, stage, onBackToProjects, onNavigateStage }) {
  const { translate } = useLanguage()
  const confirm = useWorkspaceConfirm()
  const confirmCreativeNavigation = useConfirmCreativeNavigation()
  const [project, setProject] = useState(null)
  const [shots, setShots] = useState([])
  const [chapters, setChapters] = useState([])
  const [selection, setSelection] = useState(restoredSelection)
  const [form, setForm] = useState(EMPTY_SHOT)
  const [baseline, setBaseline] = useState(JSON.stringify(EMPTY_SHOT))
  const [creating, setCreating] = useState(false)
  const [editingShotId, setEditingShotId] = useState(null)
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [mobileView, setMobileView] = useState(() => stage === 'GENERATION' ? 'media' : 'content')
  const [railStatus, setRailStatus] = useState(ALL)
  const filmstrip = useRef(null)
  const shotDetails = useRef(null)
  const mediaPreview = useRef(null)
  const readVersion = useRef(0)
  const editContext = useRef(0)
  const current = useRef(false)
  const dirty = JSON.stringify(form) !== baseline
  useUnsavedChanges(dirty)
  current.current = dirty

  const chapterGroups = useMemo(() => {
    const groups = new Map(chapters.map(({ id, title }) => [String(id), { id: String(id), title, shots: [] }]))
    for (const shot of shots) {
      const key = chapterOf(shot)
      if (!groups.has(key)) groups.set(key, { id: key, title: key === UNASSIGNED ? '未归属章节' : '未知章节', shots: [] })
      groups.get(key).shots.push(shot)
    }
    return [...groups.values()]
  }, [chapters, shots])
  const visibleShots = useMemo(() => shots.filter((shot) => (selection.chapter === ALL || chapterOf(shot) === selection.chapter)
    && (!selection.scene || sceneOf(shot) === selection.scene)), [shots, selection.chapter, selection.scene])
  const railShots = useMemo(() => railStatus === ALL ? visibleShots : visibleShots.filter((shot) => shot.status === railStatus), [visibleShots, railStatus])
  const selectedShot = !creating ? visibleShots.find((shot) => String(shot.id) === selection.shot) || visibleShots[0] : null
  const editing = creating || (selectedShot != null && editingShotId === selectedShot.id)
  const chapterId = selection.chapter === ALL ? selectedShot?.chapterId : /^\d+$/.test(selection.chapter) ? Number(selection.chapter) : null

  useEffect(() => { setMobileView(stage === 'GENERATION' ? 'media' : 'content') }, [stage])

  /** 镜头变化时回到内容顶部，只横向滚动镜头条以露出选中项。 */
  useEffect(() => {
    if (shotDetails.current) shotDetails.current.scrollTop = 0
    if (mediaPreview.current) mediaPreview.current.scrollTop = 0
  }, [selectedShot?.id])
  useEffect(() => {
    const rail = filmstrip.current
    const active = rail?.querySelector('[aria-current="true"]')
    if (!active) return
    const left = active.offsetLeft
    const right = left + active.offsetWidth
    if (left < rail.scrollLeft) rail.scrollLeft = left
    else if (right > rail.scrollLeft + rail.clientWidth) rail.scrollLeft = right - rail.clientWidth
  }, [selectedShot?.id, railShots])

  /** 返回键、刷新和分享 URL 均可恢复章节、场景、镜头。 */
  useEffect(() => {
    const pop = () => {
      editContext.current += 1
      setSelection(restoredSelection()); setCreating(false); setEditingShotId(null)
    }
    window.addEventListener('popstate', pop)
    return () => window.removeEventListener('popstate', pop)
  }, [])

  const load = async () => {
    const version = ++readVersion.current
    if (!projectId) return
    setLoading(true)
    try {
      const [items, workspace] = await Promise.all([
        getStoryboardShots(accessToken, projectId, { videoPreview: true }), getProjectWorkspace(accessToken, projectId),
      ])
      if (version !== readVersion.current) return
      setProject(workspace.project); setShots(items); setChapters(workspace.script?.chapters || []); setError('')
    } catch (failure) { if (version === readVersion.current) setError(failure.message) }
    finally { if (version === readVersion.current) setLoading(false) }
  }
  useEffect(() => {
    setCreating(false); setEditingShotId(null); setForm(EMPTY_SHOT); setBaseline(JSON.stringify(EMPTY_SHOT))
    setSelection(restoredSelection()); void load()
    return () => { readVersion.current += 1; editContext.current += 1 }
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
  }, [selectedShot?.id, creating, editingShotId])

  /** 收到产物更新时保留正文草稿，并同步其他页面可能已采用的参考图。 */
  useEffect(() => {
    if (!selectedShot || creating) return
    const next = shotForm(selectedShot)
    if (!current.current) { setForm(next); setBaseline(JSON.stringify(next)); return }
    const retained = { imageUrl: next.imageUrl, notes: next.notes }
    setForm((value) => ({ ...value, ...retained }))
    setBaseline((value) => JSON.stringify({ ...JSON.parse(value), ...retained }))
  }, [selectedShot, creating])

  const navigateSelection = async (patch, view = 'content') => {
    if (saving || !(await confirmCreativeNavigation())) return false
    const next = { ...selection, ...patch }
    const query = new URLSearchParams(window.location.search)
    query.delete('media')
    for (const [key, value] of Object.entries(next)) { if (value && value !== ALL) query.set(key, value); else query.delete(key) }
    pushWorkspaceHistory(`${window.location.pathname}${query.size ? `?${query}` : ''}`)
    setSelection(next); setCreating(false); setEditingShotId(null); setMobileView(view)
    return true
  }
  const chooseShot = async (shot, chapter = selection.chapter) => {
    const scene = chapter === selection.chapter && sceneOf(shot) === selection.scene ? selection.scene : ''
    if (await navigateSelection({ chapter, scene, shot: String(shot.id) }, mobileView === 'media' ? 'media' : 'content')
        && railStatus !== ALL && shot.status !== railStatus) setRailStatus(ALL)
  }
  const chooseChapter = async (id) => {
    if (!(await navigateSelection({ chapter: id, scene: '', shot: '' }, mobileView))) return false
    setRailStatus(ALL)
    return true
  }
  const update = (field, value) => setForm((previous) => ({ ...previous, [field]: value }))
  const create = async () => {
    if (saving || !(await confirmCreativeNavigation())) return
    const next = { ...EMPTY_SHOT, chapterId: chapterId ?? null,
      sceneName: selection.scene === UNNAMED_SCENE ? '' : selection.scene }
    setCreating(true); setEditingShotId(null); setForm(next); setBaseline(JSON.stringify(next)); setMobileView('content')
  }
  /** 每次从当前服务端镜头开启草稿，退出时沿用未保存确认。 */
  const toggleEditing = async () => {
    if (saving || (editing && !(await confirmCreativeNavigation()))) return
    const next = shotForm(selectedShot)
    setForm(next)
    setBaseline(JSON.stringify(next))
    setCreating(false)
    setEditingShotId(editing ? null : selectedShot.id)
  }
  const save = async (event) => {
    event.preventDefault()
    if (!editing || saving || !dirty || !form.title.trim()) return
    setSaving(true); setError('')
    const version = editContext.current
    try {
      const payload = { ...form, durationSeconds: Number(form.durationSeconds) }
      const saved = creating ? await createStoryboardShot(accessToken, projectId, payload)
        : await updateStoryboardShot(accessToken, projectId, selectedShot.id, payload)
      if (version !== editContext.current) return
      const next = shotForm(saved)
      setForm(next); setBaseline(JSON.stringify(next)); setCreating(false); setEditingShotId(null)
      setShots((items) => creating ? [...items, saved]
        : items.map((item) => item.id === saved.id ? { ...saved, videoPreview: item.videoPreview } : item))
      setSelection({ chapter: chapterOf(saved), scene: '', shot: String(saved.id) })
      if (railStatus !== ALL && saved.status !== railStatus) setRailStatus(ALL)
      const query = new URLSearchParams({ chapter: chapterOf(saved), shot: String(saved.id) })
      replaceWorkspaceHistory(`${window.location.pathname}?${query}`)
      await load()
    } catch (failure) { if (version === editContext.current) setError(failure.message) }
    finally { setSaving(false) }
  }
  const remove = async () => {
    if (!selectedShot || !(await confirm({
      title: translate('confirm.deleteShotTitle'), message: translate('confirm.deleteShotMessage', { title: selectedShot.title }),
      confirmLabel: translate('common.delete'), danger: true,
    }))) return
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
    try {
      const updatedShots = await reorderStoryboardShots(accessToken, projectId, reordered.map((shot) => shot.id))
      setShots((currentShots) => {
        const previews = new Map(currentShots.map((shot) => [shot.id, shot.videoPreview]))
        return updatedShots.map((shot) => ({ ...shot, videoPreview: previews.get(shot.id) }))
      })
    }
    catch (failure) { setError(failure.message) }
    finally { setSaving(false) }
  }
  const generateChapter = async () => {
    if (!chapterId || saving || dirty) return
    setSaving(true)
    try {
      const chapter = chapters.find((item) => String(item.id) === String(chapterId))
      const run = await createManualRun(accessToken, { requestId: globalThis.crypto.randomUUID(), projectId,
        operationType: CREATIVE_OPERATION.GENERATE_CHAPTER_STORYBOARD, permissionPolicy: CREATIVE_PERMISSION.PROPOSE_CHANGES,
        target: { resourceType: CREATIVE_RESOURCE.CHAPTER, resourceId: chapterId },
        instruction: `为章节《${chapter?.title || chapterId}》生成分镜提案，等待批准后保存。` })
      openCreativeTask(run.runId, projectId)
    } catch (failure) { setError(failure.message) }
    finally { setSaving(false) }
  }
  const currentIndex = shots.findIndex((shot) => shot.id === selectedShot?.id)
  const openedChapter = chapters.find((item) => String(item.id) === String(chapterId))
  const railFilters = [[ALL, '全部'], ...Object.entries(SHOT_STATUS_LABELS)]
  return <section className="workspace-module project-studio">
    <ProjectStudioHeader project={project} projectId={projectId} stage={stage} loading={loading}
      summary={[`${shots.length} 个镜头`, openedChapter?.title].filter(Boolean).join(' · ')} onBack={onBackToProjects}
      onNavigateStage={(value) => {
        if (value !== stage) onNavigateStage(value)
        else if (value === 'GENERATION') setMobileView('media')
      }}>
      <button type="button" className="workspace-secondary" disabled={!chapterId || saving || dirty}
        title={openedChapter ? `为《${openedChapter.title}》生成分镜` : '请选择剧本章节'} onClick={generateChapter}>
        <WandSparkles size={15} />生成分镜
      </button>
      <button type="button" className="workspace-primary" disabled={!project || saving} onClick={create}><Plus size={15} />新增镜头</button>
    </ProjectStudioHeader>
    {error && <div className="workspace-alert" role="alert">{error}</div>}
    <div className="workbench-tabs studio-mobile-tabs" role="group" aria-label="分镜工作区">
      {[
        ['directory', '分镜目录'], ['content', '镜头内容'], ['media', '视频'],
      ].map(([value, label]) => <button type="button" className={mobileView === value ? 'active' : ''} aria-pressed={mobileView === value}
        key={value} onClick={() => setMobileView(value)}>{label}</button>)}
    </div>
    <div className="project-studio-layout storyboard-studio-layout" data-view={mobileView}>
      <StoryboardDirectory groups={chapterGroups} allChapters={ALL} chapter={selection.chapter}
        selectedShot={selectedShot} chapterOf={chapterOf}
        onChapterChange={chooseChapter} onShotChange={chooseShot}
        onCreate={create} saving={!project || saving} />
      {selectedShot || creating ? <form className="shot-detail-editor" ref={shotDetails} onSubmit={save}>
        <header className="shot-detail-heading">
          <div>
            <div className="shot-detail-kicker">
              {!creating && selectedShot.shotNumber && <small className="shot-detail-number">{selectedShot.shotNumber}</small>}
              {editing && dirty && <small className="shot-detail-draft">有未保存的修改</small>}
            </div>
            <h2>{editing
              ? <input className="shot-inline-title" aria-label="镜头标题" placeholder={creating ? '新建镜头' : '未命名镜头'}
                autoFocus required disabled={saving} maxLength={PROJECT_LIMITS.shotTitle} value={form.title}
                onChange={(event) => update('title', event.target.value)} />
              : selectedShot.title || '未命名镜头'}</h2>
          </div>
          <div className="workbench-action-row shot-detail-actions">
            {!creating && <button type="button" className="workbench-icon-button" aria-label="前移镜头" title="前移镜头"
              disabled={saving || editing || currentIndex <= 0} onClick={() => move(-1)}><ArrowUp size={14} /></button>}
            {!creating && <button type="button" className="workbench-icon-button" aria-label="后移镜头" title="后移镜头"
              disabled={saving || editing || currentIndex >= shots.length - 1} onClick={() => move(1)}><ArrowDown size={14} /></button>}
            {!creating && <button type="button" className="workbench-icon-button" aria-label="删除镜头" title="删除镜头" disabled={saving || editing} onClick={remove}><Trash2 size={15} /></button>}
            <button type="button" className="workbench-icon-button" aria-label={editing ? '取消编辑' : '编辑镜头'}
              title={editing ? '取消编辑' : '编辑镜头'} disabled={saving} onClick={toggleEditing}>
              {editing ? <X size={16} /> : <Pencil size={15} />}
            </button>
            <button type="submit" className="workbench-icon-button shot-save-button" aria-label="保存镜头" title="保存镜头"
              disabled={!editing || saving || !form.title.trim() || !dirty}><Save size={16} /></button>
          </div>
        </header>
        <ShotDetailsFields shot={editing ? form : selectedShot} chapters={chapters}
          editing={editing} saving={saving} onChange={update} />
      </form> : <div className="shot-detail-editor workbench-empty"><Film size={34} /><h2>让文字变成镜头</h2><p>选择章节生成分镜提案，或手动添加第一个镜头。</p><button type="button" className="workspace-primary" disabled={!project || saving} onClick={create}><Plus size={14} />新增镜头</button></div>}
      <aside className="shot-media-preview" ref={mediaPreview} aria-label="镜头视频" data-active={stage === 'GENERATION'}>
        <StoryboardMediaPreview key={selectedShot?.id} accessToken={accessToken}
          projectId={projectId} shot={selectedShot} loading={loading} prompt={shotVideoPrompt(selectedShot)} unsavedShot={editing && dirty} />
      </aside>
      <section className="shot-filmstrip" aria-label="镜头条">
        <header>
          <strong><Film size={15} aria-hidden="true" />镜头（{visibleShots.length}）</strong>
          <div className="shot-filmstrip-controls">
            <div className="shot-filmstrip-filters" role="group" aria-label="按镜头状态筛选">
              {railFilters.map(([value, label]) => <button type="button" key={value} aria-pressed={railStatus === value}
                onClick={() => setRailStatus(value)}>
                {label} {value === ALL ? visibleShots.length : visibleShots.filter((shot) => shot.status === value).length}
              </button>)}
            </div>
            <div className="workbench-action-row">
              <button type="button" className="workbench-icon-button" aria-label="向左浏览镜头" title="向左浏览镜头"
                disabled={!railShots.length} onClick={() => filmstrip.current?.scrollBy({ left: -filmstrip.current.clientWidth * 0.8, behavior: 'smooth' })}><ChevronLeft size={15} /></button>
              <button type="button" className="workbench-icon-button" aria-label="向右浏览镜头" title="向右浏览镜头"
                disabled={!railShots.length} onClick={() => filmstrip.current?.scrollBy({ left: filmstrip.current.clientWidth * 0.8, behavior: 'smooth' })}><ChevronRight size={15} /></button>
            </div>
          </div>
        </header>
        {railShots.length ? <div className="shot-filmstrip-scroll" ref={filmstrip}>
          {railShots.map((shot) => <button type="button" key={shot.id} className="shot-thumbnail"
            aria-current={selectedShot?.id === shot.id || undefined} title={shot.title} onClick={() => chooseShot(shot)}>
            <span className="shot-thumbnail-visual">
              <StoryboardVideoThumbnail src={shot.videoPreview?.url} />
              {shot.durationSeconds != null && <small>{shot.durationSeconds} 秒</small>}
            </span>
            <span className="shot-thumbnail-label">
              <span className="shot-thumbnail-status" data-status={shot.status} title={SHOT_STATUS_LABELS[shot.status]} />
              {shot.shotNumber && <span>{shot.shotNumber}</span>}<strong>{shot.title || '未命名镜头'}</strong>
            </span>
          </button>)}
        </div> : <p className="shot-filmstrip-empty">{visibleShots.length ? '当前状态暂无镜头' : '暂无镜头'}</p>}
      </section>
    </div>
  </section>
}

/** 阅读和编辑共用字段位置，编辑控件只替换各字段的正文。 */
function ShotDetailsFields({ shot, chapters, editing, saving, onChange }) {
  const chapter = chapters.find((item) => String(item.id) === String(shot.chapterId))
  const chapterName = chapter?.title || (shot.chapterId ? '—' : '未归属章节')
  const status = SHOT_STATUS_LABELS[shot.status] || shot.status
  return <fieldset className="shot-reading" disabled={saving}>
    <dl className="shot-reading-meta">
      <div>
        <dt>所属章节</dt>
        <dd>{editing ? <WorkspaceSelect label="所属章节" value={shot.chapterId ? String(shot.chapterId) : ''}
          options={[{ value: '', label: '未归属章节' }, ...chapters.map((item) => ({ value: String(item.id), label: item.title }))]}
          onChange={(id) => onChange('chapterId', id ? Number(id) : null)} />
          : <span className="shot-inline-value" title={chapterName}>{chapterName}</span>}</dd>
      </div>
      {SHOT_METADATA_FIELDS.map(([field, label, max]) => <div key={field}>
        <dt>{label}</dt>
        <dd>{editing ? <input className="shot-inline-input" aria-label={label} maxLength={max}
          placeholder="—" value={shot[field]} onChange={(event) => onChange(field, event.target.value)} />
          : <span className="shot-inline-value" title={shot[field]}>{shot[field] || '—'}</span>}</dd>
      </div>)}
      <div>
        <dt>时长</dt>
        <dd className="shot-inline-duration">{editing ? <input type="number" className="shot-inline-input shot-inline-duration-value"
          aria-label="时长（秒）" required min={0} max={PROJECT_LIMITS.shotDurationSeconds} value={shot.durationSeconds}
          onChange={(event) => onChange('durationSeconds', Number(event.target.value))} />
          : <span className="shot-inline-duration-value">{shot.durationSeconds ?? '—'}</span>}<span aria-hidden={editing || undefined}>秒</span></dd>
      </div>
      <div>
        <dt>制作状态</dt>
        <dd className="shot-inline-status" data-status={shot.status}>
          {editing ? <WorkspaceSelect label="制作状态" value={shot.status}
            options={Object.entries(SHOT_STATUS_LABELS).map(([value, label]) => ({ value, label }))}
            onChange={(value) => onChange('status', value)} />
            : status ? <span className="shot-reading-status">{status}</span> : '—'}
        </dd>
      </div>
    </dl>
    <dl className="shot-reading-text">{SHOT_TEXT_FIELDS.map(([field, label, max, Icon]) => {
      const value = field === 'videoPrompt' ? shotVideoPrompt(shot) : shot[field] || ''
      return <div key={field}>
        <dt><Icon size={16} aria-hidden="true" />{label}</dt>
        <dd className={editing ? 'shot-inline-text' : value ? undefined : 'shot-reading-empty'}>
          {editing ? <>
            <span className="shot-inline-text-mirror" aria-hidden="true">{value || '—'}{'\u200b'}</span>
            <textarea className="shot-inline-text-input" rows={1} aria-label={label} maxLength={max}
              placeholder="—" value={value} onChange={(event) => onChange(field, event.target.value)} />
          </> : value || '—'}
        </dd>
      </div>
    })}</dl>
  </fieldset>
}
