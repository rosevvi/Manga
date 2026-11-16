import { useMemo, useState } from 'react'
import { ArrowRight, CircleCheck, Clock3, Film, FolderOpen, ImageOff, Monitor, Palette, Pencil, Plus, Search, Shield, Trash2 } from 'lucide-react'
import { CUSTOM_ART_STYLE, PROJECT_ASPECT_RATIOS, PROJECT_STATUS, PROJECT_VISIBILITY_SCOPE } from '../constants/project'
import { useLanguage } from '../i18n/LanguageContext'
import MediaImage from './MediaImage'
import WorkspaceSelect from './WorkspaceSelect'
import './project-directory.css'

const ALL_PROJECTS = 'ALL'
const PROJECT_FILTERS = [ALL_PROJECTS, ...Object.values(PROJECT_STATUS)]
const PROJECT_SORT = { RECENT: 'recent', NAME: 'name', SHOTS: 'shots' }
const PROJECT_SORT_OPTIONS = [
  { value: PROJECT_SORT.RECENT, labelKey: 'projects.sortRecent' },
  { value: PROJECT_SORT.NAME, labelKey: 'projects.sortName' },
  { value: PROJECT_SORT.SHOTS, labelKey: 'projects.sortShots' },
]
const RECENT_PROJECT_LIMIT = 3

const lastEdited = (project) => project.updatedAt || project.createdAt
const shotCount = (project) => project.shotCount ?? 0

/** 使用项目接口已返回的统计、更新时间和封面构建项目入口。 */
export default function ProjectDirectory({ projects, loading, error, actionError, onOpen, onCreate, onEdit, onDelete }) {
  const { locale, translate } = useLanguage()
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState(ALL_PROJECTS)
  const [sort, setSort] = useState(PROJECT_SORT.RECENT)
  const dateFormat = useMemo(() => new Intl.DateTimeFormat(locale, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }), [locale])
  const recentProjects = useMemo(() => [...projects].sort((left, right) =>
    (Date.parse(lastEdited(right)) || 0) - (Date.parse(lastEdited(left)) || 0)), [projects])
  const keyword = query.trim().toLocaleLowerCase(locale)
  const visibleProjects = recentProjects.filter((project) => (
    (status === ALL_PROJECTS || project.status === status)
    && (!keyword || [
      project.name, project.description, project.genre, project.aspectRatio,
      project.artStyleName, project.artStyleDescription,
    ].some((value) => value?.toLocaleLowerCase(locale).includes(keyword)))
  ))
  if (sort === PROJECT_SORT.NAME) visibleProjects.sort((left, right) => left.name.localeCompare(right.name, locale))
  if (sort === PROJECT_SORT.SHOTS) visibleProjects.sort((left, right) => shotCount(right) - shotCount(left))
  const [featured, ...otherProjects] = visibleProjects
  const stats = [
    { icon: FolderOpen, label: 'projects.totalProjects', count: projects.length },
    { icon: Film, label: 'projects.totalShots', count: projects.reduce((sum, project) => sum + shotCount(project), 0) },
    { icon: Clock3, label: 'projectStatus.IN_PROGRESS', count: projects.filter((project) => project.status === PROJECT_STATUS.IN_PROGRESS).length },
    { icon: CircleCheck, label: 'projectStatus.COMPLETED', count: projects.filter((project) => project.status === PROJECT_STATUS.COMPLETED).length },
  ]

  return <div className="project-directory">
    <header className="workspace-heading project-directory-hero">
      <div>
        <span className="workspace-eyebrow">{translate('projects.eyebrow')}</span>
        <h1 id="projects-title">{translate('projects.title')}</h1>
        <p>{translate('projects.description')}</p>
      </div>
      <button className="workspace-primary" type="button" onClick={onCreate}>
        <Plus size={17} />{translate('projects.create')}
      </button>
    </header>

    <dl className="project-directory-stats" aria-label={translate('projects.stats')}>
      {stats.map(({ icon: Icon, label, count }) => <div key={label}>
        <Icon size={20} aria-hidden="true" />
        <dt>{translate(label)}</dt>
        <dd>{loading || error ? '—' : count.toLocaleString(locale)}</dd>
      </div>)}
    </dl>

    <div className="project-directory-toolbar">
      <label className="workspace-search project-directory-search">
        <Search size={17} aria-hidden="true" />
        <input type="search" value={query} onChange={(event) => setQuery(event.target.value)}
          aria-label={translate('projects.search')} placeholder={translate('projects.search')} />
      </label>
      <div className="project-segmented project-directory-filters" role="group" aria-label={translate('projects.field.status')}>
        {PROJECT_FILTERS.map((filter) => <button type="button" key={filter} aria-pressed={status === filter}
          className={status === filter ? 'active' : ''} onClick={() => setStatus(filter)}>
          {translate(filter === ALL_PROJECTS ? 'projects.all' : `projectStatus.${filter}`)}
        </button>)}
      </div>
      <div className="project-directory-sort">
        <span aria-live="polite">{!loading && !error && translate('projects.count', { count: visibleProjects.length })}</span>
        <WorkspaceSelect label={translate('projects.sort')} value={sort} onChange={setSort}
          options={PROJECT_SORT_OPTIONS.map(({ value, labelKey }) => ({ value, label: translate(labelKey) }))} />
      </div>
    </div>

    {error && <div className="workspace-alert" role="alert">{error}</div>}
    {actionError && <div className="workspace-alert" role="alert">{actionError}</div>}
    {loading ? <div className="workspace-state" role="status">{translate('common.loading')}</div>
      : featured ? <>
        <div className="project-directory-overview">
          <article className="project-feature">
            <button type="button" className="project-module-cover" aria-label={featured.name} onClick={() => onOpen(featured)}>
              <ProjectCover project={featured} />
            </button>
            <div className="project-feature-copy">
              <div className="project-feature-title">
                <h2>{featured.name}</h2>
                <ProjectStatus project={featured} translate={translate} />
              </div>
              <p>{featured.description || translate('projects.descriptionUnset')}</p>
              <ProjectFacts project={featured} translate={translate} />
              <ProjectActivity project={featured} dateFormat={dateFormat} translate={translate} />
              <div className="project-feature-actions">
                <button type="button" className="workspace-primary" onClick={() => onOpen(featured)}>
                  {translate('projects.open')}<ArrowRight size={15} aria-hidden="true" />
                </button>
                <button type="button" className="workspace-secondary" onClick={() => onEdit(featured)}>
                  <Pencil size={14} aria-hidden="true" />{translate('common.edit')}
                </button>
                <button type="button" className="workbench-icon-button" aria-label={translate('common.delete')}
                  title={translate('common.delete')} onClick={() => onDelete(featured)}><Trash2 size={15} /></button>
              </div>
            </div>
          </article>

          <section className="project-recent" aria-labelledby="project-recent-title">
            <h2 id="project-recent-title">{translate('projects.recent')}</h2>
            <ul>
              {recentProjects.slice(0, RECENT_PROJECT_LIMIT).map((project) => <li key={project.id}>
                <button type="button" onClick={() => onOpen(project)}>
                  <span className="project-recent-cover"><ProjectCover project={project} /></span>
                  <span className="project-recent-copy">
                    <strong>{project.name}</strong>
                    <ProjectDate project={project} dateFormat={dateFormat} />
                  </span>
                  <ArrowRight size={15} aria-hidden="true" />
                </button>
              </li>)}
            </ul>
          </section>
        </div>

        {otherProjects.length > 0 && <section className="project-directory-list" aria-labelledby="project-list-title">
          <h2 id="project-list-title">{translate('projects.mine')}</h2>
          <div className="project-module-grid">
            {otherProjects.map((project) => <article className="project-module-card" key={project.id}>
              <button type="button" className="project-module-cover" aria-label={project.name} onClick={() => onOpen(project)}>
                <ProjectCover project={project} />
                <ProjectStatus project={project} translate={translate} />
              </button>
              <div className="project-module-copy">
                <button type="button" className="project-module-title" onClick={() => onOpen(project)}><strong>{project.name}</strong></button>
                <p>{project.description || translate('projects.descriptionUnset')}</p>
                <ProjectFacts project={project} translate={translate} />
                <div className="project-module-footer">
                  <ProjectActivity project={project} dateFormat={dateFormat} translate={translate} />
                  <div>
                    <button type="button" aria-label={translate('common.edit')} title={translate('common.edit')} onClick={() => onEdit(project)}><Pencil size={14} /></button>
                    <button type="button" aria-label={translate('common.delete')} title={translate('common.delete')} onClick={() => onDelete(project)}><Trash2 size={14} /></button>
                  </div>
                </div>
              </div>
            </article>)}
          </div>
        </section>}
      </> : !error && <div className="workspace-empty">
        <span><FolderOpen size={28} /></span>
        <h2>{translate(projects.length ? 'projects.noResults' : 'projects.empty')}</h2>
        <p>{translate(projects.length ? 'projects.noResultsHint' : 'projects.emptyHint')}</p>
        {projects.length ? <button type="button" onClick={() => { setQuery(''); setStatus(ALL_PROJECTS) }}>
          {translate('projects.clearFilters')}
        </button> : <button type="button" onClick={onCreate}><Plus size={16} />{translate('projects.create')}</button>}
      </div>}
  </div>
}

/** 封面优先使用服务端生成的预览地址。 */
function ProjectCover({ project }) {
  return <MediaImage src={project.coverPreviewUrl || project.coverUrl} fallback={<ImageOff size={24} aria-hidden="true" />} />
}

function ProjectStatus({ project, translate }) {
  const status = project.status || PROJECT_STATUS.DRAFT
  return <span className={`workspace-status status-${status.toLowerCase().replaceAll('_', '-')}`}>
    {translate(`projectStatus.${status}`)}
  </span>
}

function ProjectFacts({ project, translate }) {
  const style = project.artStyleName || (project.artStyle === CUSTOM_ART_STYLE ? translate('projects.customArtStyle') : null)
  return <ul className="project-facts">
    {project.genre && <li>{project.genre}</li>}
    <li><Monitor size={13} aria-hidden="true" />{project.aspectRatio || PROJECT_ASPECT_RATIOS[0].value}</li>
    {style && <li><Palette size={13} aria-hidden="true" />{style}</li>}
    <li><Shield size={13} aria-hidden="true" />{translate(`projectVisibility.${project.visibilityScope || PROJECT_VISIBILITY_SCOPE.PRIVATE}`)}</li>
  </ul>
}

function ProjectDate({ project, dateFormat }) {
  const updated = lastEdited(project)
  return Number.isFinite(Date.parse(updated)) && <time dateTime={updated}>{dateFormat.format(new Date(updated))}</time>
}

function ProjectActivity({ project, dateFormat, translate }) {
  return <div className="project-activity">
    <span><Film size={14} aria-hidden="true" />{translate('projects.shotCount', { count: shotCount(project) })}</span>
    {Number.isFinite(Date.parse(lastEdited(project))) && <span title={translate('projects.recent')}>
      <Clock3 size={14} aria-hidden="true" /><ProjectDate project={project} dateFormat={dateFormat} />
    </span>}
  </div>
}
