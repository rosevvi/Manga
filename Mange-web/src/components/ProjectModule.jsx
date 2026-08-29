import { useMemo, useState } from 'react'
import { FolderOpen, ImageOff, Pencil, Plus, Search, Trash2, X } from 'lucide-react'
import { createProject, deleteProject, updateProject } from '../api/projectApi'
import { PROJECT_LIMITS, PROJECT_STATUS } from '../constants/project'
import { useLanguage } from '../i18n/LanguageContext'
import './project-module.css'

const EMPTY_PROJECT = Object.freeze({
  name: '',
  description: '',
  coverUrl: '',
  genre: '',
  status: PROJECT_STATUS.DRAFT,
})

/** 展示当前用户项目，并提供搜索、创建、编辑与删除入口。 */
function ProjectModule({ accessToken, projects, loading, error, onProjectsChanged, onOpenStoryboard }) {
  const { translate } = useLanguage()
  const [query, setQuery] = useState('')
  const [editingProject, setEditingProject] = useState(undefined)
  const [form, setForm] = useState(EMPTY_PROJECT)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')

  const filteredProjects = useMemo(() => {
    const keyword = query.trim().toLocaleLowerCase()
    if (!keyword) return projects
    return projects.filter((project) =>
      [project.name, project.description, project.genre]
        .filter(Boolean)
        .some((value) => value.toLocaleLowerCase().includes(keyword)),
    )
  }, [projects, query])

  const openCreate = () => {
    setEditingProject(null)
    setForm(EMPTY_PROJECT)
    setFormError('')
  }

  const openEdit = (project) => {
    setEditingProject(project)
    setForm({
      name: project.name,
      description: project.description ?? '',
      coverUrl: project.coverUrl ?? '',
      genre: project.genre ?? '',
      status: project.status,
    })
    setFormError('')
  }

  const closeEditor = () => {
    setEditingProject(undefined)
    setFormError('')
  }

  const submitProject = async (event) => {
    event.preventDefault()
    setSaving(true)
    setFormError('')
    try {
      if (editingProject) {
        await updateProject(accessToken, editingProject.id, form)
      } else {
        await createProject(accessToken, form)
      }
      await onProjectsChanged()
      closeEditor()
    } catch (requestError) {
      setFormError(requestError.message)
    } finally {
      setSaving(false)
    }
  }

  const removeProject = async (project) => {
    if (!window.confirm(translate('projects.deleteConfirm', { name: project.name }))) return
    try {
      await deleteProject(accessToken, project.id)
      await onProjectsChanged()
    } catch (requestError) {
      setFormError(requestError.message)
    }
  }

  return (
    <section className="workspace-module" aria-labelledby="projects-title">
      <header className="workspace-heading">
        <div>
          <span className="workspace-eyebrow">{translate('projects.eyebrow')}</span>
          <h1 id="projects-title">{translate('projects.title')}</h1>
          <p>{translate('projects.description')}</p>
        </div>
        <button className="workspace-primary" type="button" onClick={openCreate}>
          <Plus size={17} />
          <span>{translate('projects.create')}</span>
        </button>
      </header>

      <div className="workspace-toolbar">
        <label className="workspace-search">
          <Search size={17} />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={translate('projects.search')}
          />
        </label>
        <span>{translate('projects.count', { count: filteredProjects.length })}</span>
      </div>

      {error && <div className="workspace-alert">{error}</div>}
      {loading ? (
        <div className="workspace-state">{translate('common.loading')}</div>
      ) : filteredProjects.length === 0 ? (
        <div className="workspace-empty">
          <span><FolderOpen size={28} /></span>
          <h2>{translate(query ? 'projects.noResults' : 'projects.empty')}</h2>
          <p>{translate(query ? 'projects.noResultsHint' : 'projects.emptyHint')}</p>
          {!query && <button type="button" onClick={openCreate}><Plus size={16} />{translate('projects.create')}</button>}
        </div>
      ) : (
        <div className="project-module-grid">
          {filteredProjects.map((project) => (
            <article className="project-module-card" key={project.id}>
              <button className="project-module-cover" type="button" onClick={() => onOpenStoryboard(project)}>
                {project.coverUrl ? <img src={project.coverUrl} alt="" /> : <ImageOff size={30} />}
                <span className={`workspace-status status-${project.status.toLowerCase().replace('_', '-')}`}>
                  {translate(`projectStatus.${project.status}`)}
                </span>
              </button>
              <div className="project-module-copy">
                <button className="project-module-title" type="button" onClick={() => onOpenStoryboard(project)}>
                  <strong>{project.name}</strong>
                  <span>{project.genre || translate('projects.genreUnset')}</span>
                </button>
                <p>{project.description || translate('projects.descriptionUnset')}</p>
                <div className="project-module-footer">
                  <span>{translate('projects.shotCount', { count: project.shotCount })}</span>
                  <div>
                    <button type="button" aria-label={translate('common.edit')} onClick={() => openEdit(project)}>
                      <Pencil size={15} />
                    </button>
                    <button type="button" aria-label={translate('common.delete')} onClick={() => removeProject(project)}>
                      <Trash2 size={15} />
                    </button>
                  </div>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}

      {editingProject !== undefined && (
        <div className="workspace-modal-backdrop" role="presentation" onMouseDown={closeEditor}>
          <form className="workspace-modal" onSubmit={submitProject} onMouseDown={(event) => event.stopPropagation()}>
            <header>
              <div>
                <span className="workspace-eyebrow">{translate('projects.editorEyebrow')}</span>
                <h2>{translate(editingProject ? 'projects.editTitle' : 'projects.createTitle')}</h2>
              </div>
              <button type="button" aria-label={translate('common.close')} onClick={closeEditor}><X size={19} /></button>
            </header>
            <div className="workspace-form-grid">
              <label className="workspace-field field-wide">
                <span>{translate('projects.field.name')}</span>
                <input required maxLength={PROJECT_LIMITS.name} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
              </label>
              <label className="workspace-field">
                <span>{translate('projects.field.genre')}</span>
                <input maxLength={PROJECT_LIMITS.genre} value={form.genre} onChange={(event) => setForm({ ...form, genre: event.target.value })} />
              </label>
              <label className="workspace-field">
                <span>{translate('projects.field.status')}</span>
                <select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value })}>
                  {Object.values(PROJECT_STATUS).map((status) => <option key={status} value={status}>{translate(`projectStatus.${status}`)}</option>)}
                </select>
              </label>
              <label className="workspace-field field-wide">
                <span>{translate('projects.field.cover')}</span>
                <input maxLength={PROJECT_LIMITS.coverUrl} value={form.coverUrl} onChange={(event) => setForm({ ...form, coverUrl: event.target.value })} placeholder="https://" />
              </label>
              <label className="workspace-field field-wide">
                <span>{translate('projects.field.description')}</span>
                <textarea maxLength={PROJECT_LIMITS.description} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} rows="4" />
              </label>
            </div>
            {formError && <div className="workspace-alert">{formError}</div>}
            <footer>
              <button type="button" onClick={closeEditor}>{translate('common.cancel')}</button>
              <button className="workspace-primary" type="submit" disabled={saving}>{translate(saving ? 'common.saving' : 'common.save')}</button>
            </footer>
          </form>
        </div>
      )}
    </section>
  )
}

export default ProjectModule
