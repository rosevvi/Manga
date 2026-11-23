import { BookOpen, ChevronRight, Clapperboard, Film, Video } from 'lucide-react'
import { useLanguage } from '../i18n/LanguageContext'
import MediaImage from './MediaImage'
import { openCreativeTask } from './creativeTaskEvents'

const STUDIO_STAGES = [
  ['OVERVIEW', '项目概览', Clapperboard],
  ['SCRIPT', '剧本', BookOpen],
  ['STORYBOARD', '分镜', Film],
  ['GENERATION', '视频', Video],
]

/** 项目工作区共用页头和四阶段导航，当前页提供阶段操作。 */
export default function ProjectStudioHeader({
  project, projectId, stage, summary, loading, onBack, onNavigateStage, children,
}) {
  const { locale, translate } = useLanguage()
  const stageLabel = STUDIO_STAGES.find(([value]) => value === stage)?.[1]
  const updated = project?.updatedAt
  const metadata = [project?.genre, summary].filter(Boolean).join(' · ')

  return <>
    <nav className="studio-breadcrumb" aria-label="当前位置">
      <button type="button" onClick={onBack}>项目</button><ChevronRight size={13} aria-hidden="true" />
      {project && <><button type="button" onClick={() => onNavigateStage('OVERVIEW')}>{project.name}</button><ChevronRight size={13} aria-hidden="true" /></>}
      <span aria-current="page">{stageLabel}</span>
    </nav>
    <header className="studio-project-header">
      <div className="project-workspace-cover">
        <MediaImage src={project?.coverPreviewUrl || project?.coverUrl} fallback={<Clapperboard size={24} aria-hidden="true" />} />
      </div>
      <div className="studio-project-title">
        <h1 title={project?.name}>{project?.name || stageLabel}</h1>
        <div className="studio-project-meta">
          <small title={metadata}>{metadata}</small>
          {Number.isFinite(Date.parse(updated)) && <time dateTime={updated} title={translate('projects.recent')}>
            {new Date(updated).toLocaleString(locale, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
          </time>}
          {project?.status && <span className={`workspace-status status-${project.status.toLowerCase().replaceAll('_', '-')}`}>
            {translate(`projectStatus.${project.status}`)}
          </span>}
        </div>
      </div>
      <div className="studio-project-actions">
        <button type="button" className="workspace-secondary" onClick={() => openCreativeTask(undefined, projectId)}>项目管理助手</button>
        {children}
      </div>
    </header>
    <nav className="workflow-stepper studio-workflow" aria-label="项目创作阶段">
      {STUDIO_STAGES.map(([value, label, Icon]) => <button type="button" key={value}
        className={value === stage ? 'workflow-step active' : 'workflow-step'} aria-current={value === stage ? 'page' : undefined}
        onClick={() => onNavigateStage(value)}>
        <Icon size={15} aria-hidden="true" />{label}
      </button>)}
      {loading && <small role="status" className="studio-loading">正在刷新…</small>}
    </nav>
  </>
}
