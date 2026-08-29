import { useEffect, useMemo, useState } from 'react'
import {
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  Clock3,
  ImageOff,
  LayoutPanelTop,
  Pencil,
  Plus,
  Trash2,
  X,
} from 'lucide-react'
import {
  createStoryboardShot,
  deleteStoryboardShot,
  getStoryboardShots,
  reorderStoryboardShots,
  updateStoryboardShot,
} from '../api/projectApi'
import { PROJECT_LIMITS, STORYBOARD_SHOT_STATUS } from '../constants/project'
import { useLanguage } from '../i18n/LanguageContext'
import './project-module.css'

const EMPTY_SHOT = Object.freeze({
  title: '',
  sceneName: '',
  shotType: '',
  cameraMovement: '',
  durationSeconds: 0,
  content: '',
  dialogue: '',
  soundEffect: '',
  imageUrl: '',
  notes: '',
  status: STORYBOARD_SHOT_STATUS.DRAFT,
})

/** 展示项目分镜时间线，并提供镜头编辑和顺序调整。 */
function StoryboardModule({ accessToken, projects, selectedProjectId, onSelectProject, onBackToProjects }) {
  const { translate } = useLanguage()
  const [shots, setShots] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [editingShot, setEditingShot] = useState(undefined)
  const [form, setForm] = useState(EMPTY_SHOT)
  const [saving, setSaving] = useState(false)

  const projectId = selectedProjectId ?? projects[0]?.id ?? null
  const selectedProject = useMemo(
    () => projects.find((project) => project.id === projectId),
    [projectId, projects],
  )

  const loadShots = async () => {
    if (!projectId) {
      setShots([])
      return
    }
    setLoading(true)
    setError('')
    try {
      setShots(await getStoryboardShots(accessToken, projectId))
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadShots()
  }, [projectId, accessToken])

  const openCreate = () => {
    setEditingShot(null)
    setForm(EMPTY_SHOT)
    setError('')
  }

  const openEdit = (shot) => {
    setEditingShot(shot)
    setForm({
      title: shot.title,
      sceneName: shot.sceneName ?? '',
      shotType: shot.shotType ?? '',
      cameraMovement: shot.cameraMovement ?? '',
      durationSeconds: shot.durationSeconds,
      content: shot.content ?? '',
      dialogue: shot.dialogue ?? '',
      soundEffect: shot.soundEffect ?? '',
      imageUrl: shot.imageUrl ?? '',
      notes: shot.notes ?? '',
      status: shot.status,
    })
    setError('')
  }

  const closeEditor = () => setEditingShot(undefined)

  const submitShot = async (event) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      const payload = { ...form, durationSeconds: Number(form.durationSeconds) }
      if (editingShot) {
        await updateStoryboardShot(accessToken, projectId, editingShot.id, payload)
      } else {
        await createStoryboardShot(accessToken, projectId, payload)
      }
      closeEditor()
      await loadShots()
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setSaving(false)
    }
  }

  const removeShot = async (shot) => {
    if (!window.confirm(translate('storyboards.deleteConfirm', { number: shot.shotNumber }))) return
    try {
      await deleteStoryboardShot(accessToken, projectId, shot.id)
      await loadShots()
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  const moveShot = async (index, direction) => {
    const targetIndex = index + direction
    if (targetIndex < 0 || targetIndex >= shots.length) return
    const reordered = [...shots]
    ;[reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]]
    setShots(reordered)
    try {
      setShots(await reorderStoryboardShots(accessToken, projectId, reordered.map((shot) => shot.id)))
    } catch (requestError) {
      setError(requestError.message)
      await loadShots()
    }
  }

  if (projects.length === 0) {
    return (
      <section className="workspace-module">
        <div className="workspace-empty standalone-empty">
          <span><LayoutPanelTop size={28} /></span>
          <h1>{translate('storyboards.noProject')}</h1>
          <p>{translate('storyboards.noProjectHint')}</p>
          <button type="button" onClick={onBackToProjects}><Plus size={16} />{translate('projects.create')}</button>
        </div>
      </section>
    )
  }

  return (
    <section className="workspace-module" aria-labelledby="storyboards-title">
      <header className="workspace-heading storyboard-heading">
        <div>
          <button className="workspace-back" type="button" onClick={onBackToProjects}>
            <ArrowLeft size={15} />{translate('storyboards.back')}
          </button>
          <span className="workspace-eyebrow">{translate('storyboards.eyebrow')}</span>
          <h1 id="storyboards-title">{selectedProject?.name}</h1>
          <p>{translate('storyboards.description', { count: shots.length })}</p>
        </div>
        <button className="workspace-primary" type="button" onClick={openCreate}>
          <Plus size={17} />{translate('storyboards.create')}
        </button>
      </header>

      <div className="workspace-toolbar storyboard-toolbar">
        <label>
          <span>{translate('storyboards.project')}</span>
          <select value={projectId} onChange={(event) => onSelectProject(Number(event.target.value))}>
            {projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
          </select>
        </label>
        <span>{translate('storyboards.totalDuration', { seconds: shots.reduce((sum, shot) => sum + shot.durationSeconds, 0) })}</span>
      </div>

      {error && <div className="workspace-alert">{error}</div>}
      {loading ? (
        <div className="workspace-state">{translate('common.loading')}</div>
      ) : shots.length === 0 ? (
        <div className="workspace-empty">
          <span><LayoutPanelTop size={28} /></span>
          <h2>{translate('storyboards.empty')}</h2>
          <p>{translate('storyboards.emptyHint')}</p>
          <button type="button" onClick={openCreate}><Plus size={16} />{translate('storyboards.create')}</button>
        </div>
      ) : (
        <div className="storyboard-grid">
          {shots.map((shot, index) => (
            <article className="storyboard-card" key={shot.id}>
              <div className="storyboard-preview">
                {shot.imageUrl ? <img src={shot.imageUrl} alt="" /> : <ImageOff size={29} />}
                <strong>{shot.shotNumber}</strong>
                <span><Clock3 size={13} />{translate('storyboards.seconds', { seconds: shot.durationSeconds })}</span>
              </div>
              <div className="storyboard-card-copy">
                <div className="storyboard-card-title">
                  <div>
                    <span>{shot.sceneName || translate('storyboards.sceneUnset')}</span>
                    <h2>{shot.title}</h2>
                  </div>
                  <span className={`workspace-status status-${shot.status.toLowerCase()}`}>{translate(`shotStatus.${shot.status}`)}</span>
                </div>
                <div className="storyboard-tags">
                  {shot.shotType && <span>{shot.shotType}</span>}
                  {shot.cameraMovement && <span>{shot.cameraMovement}</span>}
                </div>
                <p>{shot.content || translate('storyboards.contentUnset')}</p>
                {shot.dialogue && <blockquote>“{shot.dialogue}”</blockquote>}
                <footer>
                  <div>
                    <button disabled={index === 0} type="button" aria-label={translate('storyboards.moveUp')} onClick={() => moveShot(index, -1)}><ArrowUp size={15} /></button>
                    <button disabled={index === shots.length - 1} type="button" aria-label={translate('storyboards.moveDown')} onClick={() => moveShot(index, 1)}><ArrowDown size={15} /></button>
                  </div>
                  <div>
                    <button type="button" aria-label={translate('common.edit')} onClick={() => openEdit(shot)}><Pencil size={15} /></button>
                    <button type="button" aria-label={translate('common.delete')} onClick={() => removeShot(shot)}><Trash2 size={15} /></button>
                  </div>
                </footer>
              </div>
            </article>
          ))}
        </div>
      )}

      {editingShot !== undefined && (
        <div className="workspace-modal-backdrop" role="presentation" onMouseDown={closeEditor}>
          <form className="workspace-modal storyboard-editor" onSubmit={submitShot} onMouseDown={(event) => event.stopPropagation()}>
            <header>
              <div>
                <span className="workspace-eyebrow">{translate('storyboards.editorEyebrow')}</span>
                <h2>{translate(editingShot ? 'storyboards.editTitle' : 'storyboards.createTitle')}</h2>
              </div>
              <button type="button" aria-label={translate('common.close')} onClick={closeEditor}><X size={19} /></button>
            </header>
            <div className="workspace-form-grid">
              <label className="workspace-field field-wide"><span>{translate('storyboards.field.title')}</span><input required maxLength={PROJECT_LIMITS.shotTitle} value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} /></label>
              <label className="workspace-field"><span>{translate('storyboards.field.scene')}</span><input maxLength={PROJECT_LIMITS.shotSceneName} value={form.sceneName} onChange={(event) => setForm({ ...form, sceneName: event.target.value })} /></label>
              <label className="workspace-field"><span>{translate('storyboards.field.status')}</span><select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value })}>{Object.values(STORYBOARD_SHOT_STATUS).map((status) => <option key={status} value={status}>{translate(`shotStatus.${status}`)}</option>)}</select></label>
              <label className="workspace-field"><span>{translate('storyboards.field.shotType')}</span><input maxLength={PROJECT_LIMITS.shotType} value={form.shotType} onChange={(event) => setForm({ ...form, shotType: event.target.value })} /></label>
              <label className="workspace-field"><span>{translate('storyboards.field.camera')}</span><input maxLength={PROJECT_LIMITS.shotCameraMovement} value={form.cameraMovement} onChange={(event) => setForm({ ...form, cameraMovement: event.target.value })} /></label>
              <label className="workspace-field"><span>{translate('storyboards.field.duration')}</span><input type="number" min="0" max={PROJECT_LIMITS.shotDurationSeconds} value={form.durationSeconds} onChange={(event) => setForm({ ...form, durationSeconds: event.target.value })} /></label>
              <label className="workspace-field"><span>{translate('storyboards.field.image')}</span><input maxLength={PROJECT_LIMITS.shotImageUrl} value={form.imageUrl} onChange={(event) => setForm({ ...form, imageUrl: event.target.value })} placeholder="https://" /></label>
              <label className="workspace-field field-wide"><span>{translate('storyboards.field.content')}</span><textarea maxLength={PROJECT_LIMITS.shotContent} rows="3" value={form.content} onChange={(event) => setForm({ ...form, content: event.target.value })} /></label>
              <label className="workspace-field field-wide"><span>{translate('storyboards.field.dialogue')}</span><textarea maxLength={PROJECT_LIMITS.shotDialogue} rows="2" value={form.dialogue} onChange={(event) => setForm({ ...form, dialogue: event.target.value })} /></label>
              <label className="workspace-field"><span>{translate('storyboards.field.sound')}</span><textarea maxLength={PROJECT_LIMITS.shotSoundEffect} rows="2" value={form.soundEffect} onChange={(event) => setForm({ ...form, soundEffect: event.target.value })} /></label>
              <label className="workspace-field"><span>{translate('storyboards.field.notes')}</span><textarea maxLength={PROJECT_LIMITS.shotNotes} rows="2" value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} /></label>
            </div>
            {error && <div className="workspace-alert">{error}</div>}
            <footer><button type="button" onClick={closeEditor}>{translate('common.cancel')}</button><button className="workspace-primary" type="submit" disabled={saving}>{translate(saving ? 'common.saving' : 'common.save')}</button></footer>
          </form>
        </div>
      )}
    </section>
  )
}

export default StoryboardModule
