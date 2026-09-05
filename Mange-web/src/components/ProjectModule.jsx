import { useEffect, useMemo, useState } from 'react'
import {
  Check,
  Eye,
  FolderOpen,
  ImageOff,
  Monitor,
  Palette,
  Pencil,
  Plus,
  Search,
  Shield,
  Sparkles,
  Trash2,
  Type,
  X,
} from 'lucide-react'
import { createProject, deleteProject, getArtStylePresets, updateProject } from '../api/projectApi'
import {
  CUSTOM_ART_STYLE,
  PROJECT_ASPECT_RATIOS,
  PROJECT_LIMITS,
  PROJECT_STATUS,
  PROJECT_TYPES,
  PROJECT_VISIBILITY_SCOPE,
} from '../constants/project'
import { useLanguage } from '../i18n/LanguageContext'
import ImageInput from './ImageInput'
import './project-module.css'

const EMPTY_PROJECT = Object.freeze({
  name: '',
  description: '',
  coverUrl: '',
  genre: PROJECT_TYPES[0],
  aspectRatio: PROJECT_ASPECT_RATIOS[0].value,
  visibilityScope: PROJECT_VISIBILITY_SCOPE.PRIVATE,
  status: PROJECT_STATUS.DRAFT,
  artStyle: '',
  artStyleDescription: '',
  artStyleImagePrompt: '',
  artStyleImageUrl: '',
})

const VISIBILITY_OPTIONS = Object.freeze([
  { value: PROJECT_VISIBILITY_SCOPE.PRIVATE, labelKey: 'projectVisibility.PRIVATE', icon: Shield },
  { value: PROJECT_VISIBILITY_SCOPE.TEAM, labelKey: 'projectVisibility.TEAM', icon: Eye },
  { value: PROJECT_VISIBILITY_SCOPE.PUBLIC, labelKey: 'projectVisibility.PUBLIC', icon: Sparkles },
])

/** 展示当前用户项目，并提供搜索、创建、编辑与删除入口。 */
function ProjectModule({ accessToken, projects, loading, error, onProjectsChanged, onOpenStoryboard }) {
  const { translate } = useLanguage()
  const [query, setQuery] = useState('')
  const [editingProject, setEditingProject] = useState(undefined)
  const [form, setForm] = useState(EMPTY_PROJECT)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [artStylePresets, setArtStylePresets] = useState([])
  const [presetsLoading, setPresetsLoading] = useState(false)
  const [artStyleMode, setArtStyleMode] = useState('preset')

  useEffect(() => {
    if (editingProject === undefined || !accessToken) return
    setPresetsLoading(true)
    getArtStylePresets(accessToken)
      .then(setArtStylePresets)
      .catch((requestError) => setFormError(requestError.message))
      .finally(() => setPresetsLoading(false))
  }, [accessToken, editingProject])

  const filteredProjects = useMemo(() => {
    const keyword = query.trim().toLocaleLowerCase()
    if (!keyword) return projects
    return projects.filter((project) =>
      [
        project.name,
        project.description,
        project.genre,
        project.aspectRatio,
        project.artStyleName,
        project.artStyleDescription,
      ]
        .filter(Boolean)
        .some((value) => value.toLocaleLowerCase().includes(keyword)),
    )
  }, [projects, query])

  const openCreate = () => {
    setEditingProject(null)
    setArtStyleMode('preset')
    setForm(EMPTY_PROJECT)
    setFormError('')
    setFieldErrors({})
  }

  const openEdit = (project) => {
    setEditingProject(project)
    setArtStyleMode(project.artStyle === CUSTOM_ART_STYLE ? 'custom' : 'preset')
    setForm({
      name: project.name,
      description: project.description ?? '',
      coverUrl: project.coverUrl ?? '',
      genre: project.genre ?? PROJECT_TYPES[0],
      aspectRatio: project.aspectRatio ?? PROJECT_ASPECT_RATIOS[0].value,
      visibilityScope: project.visibilityScope ?? PROJECT_VISIBILITY_SCOPE.PRIVATE,
      status: project.status,
      artStyle: project.artStyle ?? '',
      artStyleDescription: project.artStyleDescription ?? '',
      artStyleImagePrompt: project.artStyleImagePrompt ?? '',
      artStyleImageUrl: project.artStyleImageUrl ?? '',
    })
    setFormError('')
    setFieldErrors({})
  }

  const closeEditor = () => {
    setEditingProject(undefined)
    setFormError('')
    setFieldErrors({})
  }

  const updateForm = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => {
      if (!current[field]) return current
      const next = { ...current }
      delete next[field]
      return next
    })
  }

  const selectPreset = (preset) => {
    setForm((current) => ({
      ...current,
      artStyle: current.artStyle === preset.key ? '' : preset.key,
      artStyleDescription: '',
      artStyleImagePrompt: '',
      artStyleImageUrl: '',
    }))
  }

  const switchArtStyleMode = (mode) => {
    setArtStyleMode(mode)
    setForm((current) => ({
      ...current,
      artStyle: mode === 'custom' ? CUSTOM_ART_STYLE : '',
      artStyleDescription: mode === 'custom' ? current.artStyleDescription : '',
      artStyleImagePrompt: mode === 'custom' ? current.artStyleImagePrompt : '',
      artStyleImageUrl: mode === 'custom' ? current.artStyleImageUrl : '',
    }))
  }

  const submitProject = async (event) => {
    event.preventDefault()
    const nextFieldErrors = validateProjectForm(form, translate)
    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors)
      return
    }
    setSaving(true)
    setFormError('')
    try {
      const payload = {
        ...form,
        artStyle: artStyleMode === 'custom' ? CUSTOM_ART_STYLE : form.artStyle,
        artStyleDescription: artStyleMode === 'custom' ? form.artStyleDescription : '',
        artStyleImagePrompt: artStyleMode === 'custom' ? form.artStyleImagePrompt : '',
        artStyleImageUrl: artStyleMode === 'custom' ? form.artStyleImageUrl : '',
      }
      if (editingProject) {
        await updateProject(accessToken, editingProject.id, payload)
      } else {
        await createProject(accessToken, payload)
      }
      await onProjectsChanged()
      closeEditor()
    } catch (requestError) {
      setFormError(requestError.message)
    } finally {
      setSaving(false)
    }
  }

  const renderImageUploadField = (field, label, maxLength, uploadSubDir, wide = false) => (
    <section className={wide ? 'workspace-field project-image-field field-wide' : 'workspace-field project-image-field'}>
      <span>{label}</span>
      <ImageInput
        accessToken={accessToken}
        value={form[field]}
        maxLength={maxLength}
        uploadSubDir={uploadSubDir}
        onChange={(value) => updateForm(field, value)}
        onError={setFormError}
        placeholder={translate('projects.uploadUrlPlaceholder')}
      />
      {fieldErrors[field] && <small className="workspace-field-error">{fieldErrors[field]}</small>}
    </section>
  )

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
      {formError && editingProject === undefined && <div className="workspace-alert">{formError}</div>}
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
          {filteredProjects.map((project) => {
            const statusClass = `status-${project.status.toLowerCase().replace('_', '-')}`
            const styleName = project.artStyleName || (project.artStyle === CUSTOM_ART_STYLE ? translate('projects.customArtStyle') : translate('projects.artStyleUnset'))
            return (
              <article className="project-module-card" key={project.id}>
                <button className="project-module-cover" type="button" onClick={() => onOpenStoryboard(project)}>
                  {project.coverUrl ? <img src={project.coverUrl} alt="" /> : <ImageOff size={30} />}
                  <span className={`workspace-status ${statusClass}`}>
                    {translate(`projectStatus.${project.status}`)}
                  </span>
                </button>
                <div className="project-module-copy">
                  <button className="project-module-title" type="button" onClick={() => onOpenStoryboard(project)}>
                    <strong>{project.name}</strong>
                    <span>{project.genre || translate('projects.genreUnset')}</span>
                  </button>
                  <p>{project.description || translate('projects.descriptionUnset')}</p>
                  <div className="project-module-meta">
                    <span><Monitor size={12} />{project.aspectRatio || PROJECT_ASPECT_RATIOS[0].value}</span>
                    <span><Palette size={12} />{styleName}</span>
                    <span><Shield size={12} />{translate(`projectVisibility.${project.visibilityScope || PROJECT_VISIBILITY_SCOPE.PRIVATE}`)}</span>
                  </div>
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
            )
          })}
        </div>
      )}

      {editingProject !== undefined && (
        <div className="workspace-modal-backdrop" role="presentation" onMouseDown={closeEditor}>
          <form className="workspace-modal project-editor" onSubmit={submitProject} noValidate onMouseDown={(event) => event.stopPropagation()}>
            <header>
              <div>
                <span className="workspace-eyebrow">{translate('projects.editorEyebrow')}</span>
                <h2>{editingProject ? form.name || translate('projects.editTitle') : translate('projects.createTitle')}</h2>
              </div>
              <button type="button" aria-label={translate('common.close')} onClick={closeEditor}><X size={19} /></button>
            </header>
            <div className="workspace-form-grid">
              <label className="workspace-field field-wide">
                <span>{translate('projects.field.name')}</span>
                <input maxLength={PROJECT_LIMITS.name} value={form.name} onChange={(event) => updateForm('name', event.target.value)} />
                {fieldErrors.name && <small className="workspace-field-error">{fieldErrors.name}</small>}
              </label>

              <section className="project-editor-section field-wide" aria-label={translate('projects.field.genre')}>
                <div className="project-editor-section-title"><Type size={14} /><span>{translate('projects.field.genre')}</span></div>
                <div className="project-choice-row">
                  {PROJECT_TYPES.map((type) => (
                    <button
                      className={form.genre === type ? 'project-choice active' : 'project-choice'}
                      key={type}
                      type="button"
                      onClick={() => updateForm('genre', type)}
                    >
                      {form.genre === type && <Check size={12} />}
                      <span>{type}</span>
                    </button>
                  ))}
                </div>
              </section>

              <section className="project-editor-section field-wide" aria-label={translate('projects.field.aspectRatio')}>
                <div className="project-editor-section-title"><Monitor size={14} /><span>{translate('projects.field.aspectRatio')}</span></div>
                <div className="project-ratio-grid">
                  {PROJECT_ASPECT_RATIOS.map((ratio) => (
                    <button
                      className={form.aspectRatio === ratio.value ? 'project-ratio active' : 'project-ratio'}
                      key={ratio.value}
                      type="button"
                      onClick={() => updateForm('aspectRatio', ratio.value)}
                    >
                      <strong>{translate(ratio.labelKey)}</strong>
                      <span>{translate(ratio.descriptionKey)}</span>
                    </button>
                  ))}
                </div>
              </section>

              <section className="project-editor-section">
                <div className="project-editor-section-title"><Sparkles size={14} /><span>{translate('projects.field.status')}</span></div>
                <div className="project-choice-row">
                  {Object.values(PROJECT_STATUS).map((status) => (
                    <button
                      className={form.status === status ? 'project-choice active' : 'project-choice'}
                      key={status}
                      type="button"
                      onClick={() => updateForm('status', status)}
                    >
                      {form.status === status && <Check size={12} />}
                      <span>{translate(`projectStatus.${status}`)}</span>
                    </button>
                  ))}
                </div>
              </section>
              <section className="project-editor-section">
                <div className="project-editor-section-title"><Shield size={14} /><span>{translate('projects.field.visibility')}</span></div>
                <div className="project-choice-row">
                  {VISIBILITY_OPTIONS.map(({ value, labelKey, icon: Icon }) => (
                    <button
                      className={form.visibilityScope === value ? 'project-choice active' : 'project-choice'}
                      key={value}
                      type="button"
                      onClick={() => updateForm('visibilityScope', value)}
                    >
                      <Icon size={12} />
                      <span>{translate(labelKey)}</span>
                    </button>
                  ))}
                </div>
              </section>
              {renderImageUploadField('coverUrl', translate('projects.field.cover'), PROJECT_LIMITS.coverUrl, 'project-covers', true)}

              <section className="project-editor-section field-wide" aria-label={translate('projects.field.artStyle')}>
                <div className="project-editor-section-heading">
                  <div className="project-editor-section-title"><Palette size={14} /><span>{translate('projects.field.artStyle')}</span></div>
                  <div className="project-segmented" role="group" aria-label={translate('projects.field.artStyle')}>
                    <button className={artStyleMode === 'preset' ? 'active' : ''} type="button" onClick={() => switchArtStyleMode('preset')}>
                      {translate('projects.artStylePreset')}
                    </button>
                    <button className={artStyleMode === 'custom' ? 'active' : ''} type="button" onClick={() => switchArtStyleMode('custom')}>
                      {translate('projects.artStyleCustom')}
                    </button>
                  </div>
                </div>

                {artStyleMode === 'preset' ? (
                  <div className="project-style-grid">
                    {presetsLoading ? (
                      <div className="project-style-loading">{translate('common.loading')}</div>
                    ) : artStylePresets.map((preset) => (
                      <button
                        className={form.artStyle === preset.key ? 'project-style-card active' : 'project-style-card'}
                        key={preset.key}
                        type="button"
                        onClick={() => selectPreset(preset)}
                      >
                        <span className="project-style-thumb">
                          {preset.referenceImagePath ? <img src={preset.referenceImagePath} alt="" /> : <ImageOff size={18} />}
                        </span>
                        <strong>{preset.name}</strong>
                      </button>
                    ))}
                  </div>
                ) : (
                  <div className="project-custom-style">
                    {renderImageUploadField('artStyleImageUrl', translate('projects.field.artStyleImageUrl'), PROJECT_LIMITS.artStyleImageUrl, 'art-style-references')}
                    <label className="workspace-field">
                      <span>{translate('projects.field.artStyleImagePrompt')}</span>
                      <textarea maxLength={PROJECT_LIMITS.artStyleImagePrompt} value={form.artStyleImagePrompt} onChange={(event) => updateForm('artStyleImagePrompt', event.target.value)} rows="3" />
                    </label>
                    <label className="workspace-field field-wide">
                      <span>{translate('projects.field.artStyleDescription')}</span>
                      <textarea maxLength={PROJECT_LIMITS.artStyleDescription} value={form.artStyleDescription} onChange={(event) => updateForm('artStyleDescription', event.target.value)} rows="3" />
                    </label>
                  </div>
                )}
              </section>

              <label className="workspace-field field-wide">
                <span>{translate('projects.field.description')}</span>
                <textarea maxLength={PROJECT_LIMITS.description} value={form.description} onChange={(event) => updateForm('description', event.target.value)} rows="4" />
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

function validateProjectForm(form, translate) {
  const errors = {}
  if (!form.name.trim()) {
    errors.name = translate('common.fieldRequired')
  }
  return errors
}

export default ProjectModule
