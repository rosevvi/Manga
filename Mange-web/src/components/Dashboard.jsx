import { useCallback, useEffect, useState } from 'react'
import {
  ArrowRight,
  Bell,
  ChevronDown,
  CirclePlay,
  Clapperboard,
  Diamond,
  FolderOpen,
  Frame,
  GalleryVerticalEnd,
  Grid2X2,
  Heart,
  HelpCircle,
  Home,
  Image as ImageIcon,
  LayoutPanelTop,
  Menu,
  MoreVertical,
  Play,
  Plus,
  Search,
  Settings2,
  Sparkles,
  Trash2,
  UserRoundCheck,
  UsersRound,
  Video,
  WandSparkles,
  X,
} from 'lucide-react'
import './dashboard.css'
import LanguageSelector from './LanguageSelector'
import UserAccountMenu from './UserAccountMenu'
import ProjectModule from './ProjectModule'
import StoryboardModule from './StoryboardModule'
import AiProviderSettings from './AiProviderSettings'
import { getProjects } from '../api/projectApi'
import { useLanguage } from '../i18n/LanguageContext'

const SIDEBAR_NAV_ITEMS = [
  { id: 'home', labelKey: 'dashboard.nav.home', icon: Home },
  { id: 'projects', labelKey: 'dashboard.nav.projects', icon: FolderOpen },
  { id: 'assets', labelKey: 'dashboard.nav.assets', icon: ImageIcon },
  { id: 'characters', labelKey: 'dashboard.nav.characters', icon: UsersRound },
  { id: 'scenes', labelKey: 'dashboard.nav.scenes', icon: Frame },
  { id: 'storyboards', labelKey: 'dashboard.nav.storyboards', icon: LayoutPanelTop },
  { id: 'ai-tools', labelKey: 'dashboard.nav.aiTools', icon: WandSparkles },
  { id: 'ai-settings', labelKey: 'dashboard.nav.aiSettings', icon: Settings2 },
  { id: 'creations', labelKey: 'dashboard.nav.creations', icon: GalleryVerticalEnd },
  { id: 'favorites', labelKey: 'dashboard.nav.favorites', icon: Heart },
  { id: 'trash', labelKey: 'dashboard.nav.trash', icon: Trash2 },
]

const AI_TOOL_ITEMS = [
  {
    titleKey: 'dashboard.tool.video.title',
    descriptionKey: 'dashboard.tool.video.description',
    icon: Clapperboard,
    tone: 'purple',
  },
  {
    titleKey: 'dashboard.tool.storyboard.title',
    descriptionKey: 'dashboard.tool.storyboard.description',
    icon: LayoutPanelTop,
    tone: 'blue',
  },
  {
    titleKey: 'dashboard.tool.character.title',
    descriptionKey: 'dashboard.tool.character.description',
    icon: UserRoundCheck,
    tone: 'violet',
  },
  {
    titleKey: 'dashboard.tool.extend.title',
    descriptionKey: 'dashboard.tool.extend.description',
    icon: CirclePlay,
    tone: 'green',
  },
]

const CREATIONS = [
  {
    title: "Demon's Blade EP.3",
    views: '3.2K',
    days: 2,
    duration: '01:24',
    image: '/assets/manga-duel-hero.png',
  },
  {
    title: 'Lost in the Rain',
    views: '1.1K',
    days: 3,
    duration: '01:36',
    image: '/assets/lost-rain.png',
  },
  {
    title: 'Awakening',
    views: '2.7K',
    days: 4,
    duration: '01:36',
    image: '/assets/shadow-awakening.png',
  },
  {
    title: 'Moonlit Promise',
    views: '1.9K',
    days: 5,
    duration: '01:12',
    image: '/assets/sakura-dream.png',
  },
]

const CREATION_FILTERS = [
  { id: 'all', labelKey: 'dashboard.filter.all' },
  { id: 'videos', labelKey: 'dashboard.filter.videos' },
  { id: 'storyboards', labelKey: 'dashboard.filter.storyboards' },
  { id: 'drafts', labelKey: 'dashboard.filter.drafts' },
]
const DEFAULT_USER_NAME = 'Sakura'

/** 漫剧系统控制台首页，承载创作入口、项目与作品概览。 */
function Dashboard({ authSession, onExit, onLogout, onUserUpdated }) {
  const { translate } = useLanguage()
  const [activeNav, setActiveNav] = useState(SIDEBAR_NAV_ITEMS[0].id)
  const [activeFilter, setActiveFilter] = useState(CREATION_FILTERS[0].id)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [projects, setProjects] = useState([])
  const [projectsLoading, setProjectsLoading] = useState(false)
  const [projectsError, setProjectsError] = useState('')
  const [selectedProjectId, setSelectedProjectId] = useState(null)
  const displayName = authSession?.user?.displayName || DEFAULT_USER_NAME
  const accessToken = authSession?.accessToken
  const isGuest = Boolean(authSession?.user?.guest)

  /** 刷新当前用户项目，游客保持只读控制台体验。 */
  const loadProjects = useCallback(async () => {
    if (!accessToken || isGuest) {
      setProjects([])
      setProjectsError(isGuest ? translate('projects.guestDenied') : '')
      return
    }
    setProjectsLoading(true)
    setProjectsError('')
    try {
      setProjects(await getProjects(accessToken))
    } catch (requestError) {
      setProjectsError(requestError.message)
    } finally {
      setProjectsLoading(false)
    }
  }, [accessToken, isGuest, translate])

  useEffect(() => {
    loadProjects()
  }, [loadProjects])

  /** 切换控制台功能并在窄屏下关闭侧栏。 */
  const selectNavigation = (navigationId) => {
    setActiveNav(navigationId)
    setSidebarOpen(false)
  }

  /** 进入指定项目的分镜工作区。 */
  const openStoryboard = (project) => {
    setSelectedProjectId(project.id)
    selectNavigation('storyboards')
  }

  return (
    <div className="dashboard-shell">
      <aside className={sidebarOpen ? 'dashboard-sidebar open' : 'dashboard-sidebar'}>
        <button className="dashboard-logo" type="button" onClick={onExit}>
          Manga
          <Sparkles size={12} />
        </button>

        <nav className="dashboard-nav" aria-label={translate('dashboard.navigation')}>
          {SIDEBAR_NAV_ITEMS.map(({ id, labelKey, icon: Icon }) => (
            <button
              className={activeNav === id ? 'dashboard-nav-item active' : 'dashboard-nav-item'}
              key={id}
              type="button"
              onClick={() => selectNavigation(id)}
            >
              <Icon size={17} strokeWidth={1.8} />
              <span>{translate(labelKey)}</span>
            </button>
          ))}
        </nav>

        <div className="dashboard-sidebar-bottom">
          <section className="plan-card" aria-label={translate('dashboard.plan.current')}>
            <div className="plan-title-row">
              <span className="plan-gem"><Diamond size={15} fill="currentColor" /></span>
              <strong>{translate('dashboard.plan.name')}</strong>
              <ChevronDown size={15} />
            </div>
            <div className="plan-progress"><span /></div>
            <div className="plan-credits">
              <span>{translate('dashboard.plan.credits')}</span>
              <strong>6,240 / 10,000</strong>
            </div>
            <button type="button">{translate('dashboard.plan.upgrade')}</button>
          </section>

          <button className="help-button" type="button">
            <HelpCircle size={17} fill="currentColor" />
            <span>{translate('dashboard.helpDocs')}</span>
            <ChevronDown size={15} />
          </button>
        </div>
      </aside>

      {sidebarOpen && <button className="sidebar-scrim" type="button" aria-label={translate('dashboard.closeMenu')} onClick={() => setSidebarOpen(false)} />}

      <header className="dashboard-topbar">
        <button className="dashboard-menu-button" type="button" aria-label={translate(sidebarOpen ? 'dashboard.closeMenu' : 'dashboard.openMenu')} onClick={() => setSidebarOpen((open) => !open)}>
          {sidebarOpen ? <X size={20} /> : <Menu size={20} />}
        </button>
        <div className="dashboard-top-actions">
          <button type="button" aria-label={translate('dashboard.search')}><Search size={18} /></button>
          <button type="button" aria-label={translate('dashboard.notifications')}><Bell size={18} /></button>
          <LanguageSelector compact />
          <UserAccountMenu
            authSession={authSession}
            displayName={displayName}
            onLogout={onLogout}
            onUserUpdated={onUserUpdated}
          />
        </div>
      </header>

      <main className="dashboard-main">
        {activeNav === 'projects' ? (
          <ProjectModule
            accessToken={accessToken}
            projects={projects}
            loading={projectsLoading}
            error={projectsError}
            onProjectsChanged={loadProjects}
            onOpenStoryboard={openStoryboard}
          />
        ) : activeNav === 'storyboards' ? (
          <StoryboardModule
            accessToken={accessToken}
            projects={projects}
            selectedProjectId={selectedProjectId}
            onSelectProject={setSelectedProjectId}
            onBackToProjects={() => selectNavigation('projects')}
          />
        ) : activeNav === 'ai-settings' ? (
          <AiProviderSettings accessToken={accessToken} isGuest={isGuest} />
        ) : (
          <>
        <section className="dashboard-welcome" aria-labelledby="dashboard-title">
          <div>
            <h1 id="dashboard-title">{translate('dashboard.welcome', { name: displayName })} <span aria-hidden="true">👋</span></h1>
            <p>{translate('dashboard.welcomeDescription')}</p>
          </div>
          <button className="new-project-button" type="button" onClick={() => selectNavigation('projects')}><Plus size={17} /><span>{translate('dashboard.newProject')}</span></button>
        </section>

        <section className="ai-tools-grid" aria-label={translate('dashboard.aiToolsLabel')}>
          {AI_TOOL_ITEMS.map(({ titleKey, descriptionKey, icon: Icon, tone }, index) => (
            <button className="ai-tool-card" type="button" key={titleKey} onClick={() => index === 1 && selectNavigation('storyboards')}>
              <span className={`ai-tool-icon ${tone}`}><Icon size={22} /></span>
              <span className="ai-tool-copy"><strong>{translate(titleKey)}</strong><small>{translate(descriptionKey)}</small></span>
              <ArrowRight className="ai-tool-arrow" size={17} />
            </button>
          ))}
        </section>

        <section className="dashboard-hero" aria-labelledby="creation-hero-title">
          <img src="/assets/manga-duel-hero.png" alt={translate('dashboard.hero.imageAlt')} />
          <div className="dashboard-hero-overlay" />
          <div className="dashboard-hero-copy">
            <h2 id="creation-hero-title">{translate('dashboard.hero.title')}</h2>
            <p>{translate('dashboard.hero.description')}</p>
            <button type="button"><WandSparkles size={16} /><span>{translate('dashboard.hero.action')}</span></button>
          </div>
        </section>

        <section className="dashboard-section" aria-labelledby="recent-projects-title">
          <div className="dashboard-section-heading">
            <h2 id="recent-projects-title">{translate('dashboard.recentProjects')}</h2>
            <button type="button" onClick={() => selectNavigation('projects')}>{translate('common.viewAll')} <ArrowRight size={14} /></button>
          </div>
          <div className="recent-project-grid">
            {projects.slice(0, 4).map((project) => (
              <button className="recent-project-card" type="button" key={project.id} onClick={() => openStoryboard(project)}>
                <div className="project-image-wrap">
                  {project.coverUrl ? <img src={project.coverUrl} alt="" /> : <span className="project-cover-placeholder"><ImageIcon size={28} /></span>}
                  <MoreVertical size={17} />
                </div>
                <div className="project-card-meta">
                  <div><h3>{project.name}</h3><p>{new Date(project.updatedAt).toLocaleDateString()}</p></div>
                  <span>{translate('projects.shotCount', { count: project.shotCount })}</span>
                </div>
              </button>
            ))}
            {!projectsLoading && projects.length === 0 && <p className="recent-project-empty">{projectsError || translate('projects.emptyHint')}</p>}
          </div>
        </section>

        <section className="dashboard-section creations-section" aria-labelledby="my-creations-title">
          <div className="dashboard-section-heading creation-heading">
            <div>
              <h2 id="my-creations-title">{translate('dashboard.creations')}</h2>
              <div className="creation-filters" role="group" aria-label={translate('dashboard.filters.label')}>
                {CREATION_FILTERS.map(({ id, labelKey }) => (
                  <button className={activeFilter === id ? 'active' : ''} type="button" key={id} onClick={() => setActiveFilter(id)}>{translate(labelKey)}</button>
                ))}
              </div>
            </div>
            <div className="creation-view-actions">
              <button type="button">{translate('common.viewAll')} <ArrowRight size={14} /></button>
              <button className="grid-view-button" type="button" aria-label={translate('dashboard.gridView')}><Grid2X2 size={15} /></button>
            </div>
          </div>
          <div className="creation-grid">
            {CREATIONS.map((creation) => (
              <article className="creation-card" key={creation.title}>
                <div className="creation-preview">
                  <img src={creation.image} alt="" />
                  <span className="creation-duration"><Play size={12} fill="currentColor" /> {creation.duration}</span>
                  <span className="creation-resolution"><Video size={12} /> {creation.duration}</span>
                </div>
                <div className="creation-card-copy">
                  <div><h3>{creation.title}</h3><p>{translate('dashboard.creation.meta', { views: creation.views, days: creation.days })}</p></div>
                  <MoreVertical size={17} />
                </div>
              </article>
            ))}
          </div>
        </section>
          </>
        )}
      </main>
    </div>
  )
}

export default Dashboard
