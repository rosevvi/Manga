import { useEffect, useState } from 'react'
import {
  ArrowRight,
  Clapperboard,
  Heart,
  LayoutDashboard,
  LogOut,
  Menu,
  Play,
  Sparkles,
  Users,
  WandSparkles,
  X,
  Zap,
} from 'lucide-react'
import {
  clearAuthSession,
  getCurrentUser,
  readAuthSession,
  saveAuthSession,
  updateAuthSessionUser,
} from './api/authApi'
import Dashboard from './components/Dashboard'
import LanguageSelector from './components/LanguageSelector'
import LoginModal from './components/LoginModal'
import { APP_ROUTE } from './constants/routes'
import { useLanguage } from './i18n/LanguageContext'

const works = [
  {
    title: "Shadow's Awakening",
    creator: 'Akira',
    likes: '1.2K',
    image: '/assets/shadow-awakening.png',
  },
  {
    title: 'Winds of the Realm',
    creator: 'Yunshen',
    likes: '5.4K',
    image: '/assets/winds-realm.png',
  },
  {
    title: 'Lost in the Rain',
    creator: 'MangaLover',
    likes: '967',
    image: '/assets/lost-rain.png',
  },
  {
    title: 'Neon Genesis',
    creator: 'CyberFan',
    likes: '4.1K',
    image: '/assets/neon-genesis.png',
  },
  {
    title: 'Sakura Dream',
    creator: 'Hikari',
    likes: '1.5K',
    image: '/assets/sakura-dream.png',
  },
  {
    title: "Demon's Blade",
    creator: 'NightOwl',
    likes: '2.8K',
    image: '/assets/demon-blade.png',
  },
]

const features = [
  {
    titleKey: 'home.feature.ai.title',
    descriptionKey: 'home.feature.ai.description',
    icon: WandSparkles,
  },
  {
    titleKey: 'home.feature.easy.title',
    descriptionKey: 'home.feature.easy.description',
    icon: Zap,
  },
  {
    titleKey: 'home.feature.quality.title',
    descriptionKey: 'home.feature.quality.description',
    icon: Clapperboard,
  },
  {
    titleKey: 'home.feature.community.title',
    descriptionKey: 'home.feature.community.description',
    icon: Users,
  },
]

const NAVIGATION_ITEM = Object.freeze({ HOME: 'home', EXPLORE: 'explore', CREATE: 'create', RESOURCES: 'resources', PRICING: 'pricing' })
const navItems = [
  { id: NAVIGATION_ITEM.HOME, labelKey: 'home.nav.home' },
  { id: NAVIGATION_ITEM.EXPLORE, labelKey: 'home.nav.explore' },
  { id: NAVIGATION_ITEM.CREATE, labelKey: 'home.nav.create' },
  { id: NAVIGATION_ITEM.RESOURCES, labelKey: 'home.nav.resources' },
  { id: NAVIGATION_ITEM.PRICING, labelKey: 'home.nav.pricing' },
]

function App() {
  const { translate } = useLanguage()
  const [currentPath, setCurrentPath] = useState(() => window.location.pathname)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [loginOpen, setLoginOpen] = useState(false)
  const [authSession, setAuthSession] = useState(() => readAuthSession())
  const [savedWorks, setSavedWorks] = useState([])

  useEffect(() => {
    const handleHistoryChange = () => setCurrentPath(window.location.pathname)
    window.addEventListener('popstate', handleHistoryChange)
    return () => window.removeEventListener('popstate', handleHistoryChange)
  }, [])

  useEffect(() => {
    document.body.style.overflow = modalOpen || loginOpen ? 'hidden' : ''
    return () => {
      document.body.style.overflow = ''
    }
  }, [modalOpen, loginOpen])

  useEffect(() => {
    if (!authSession?.accessToken) return

    getCurrentUser(authSession.accessToken)
      .then((user) => setAuthSession((current) => current && { ...current, user }))
      .catch(() => {
        clearAuthSession()
        setAuthSession(null)
      })
  }, [])

  /** 保存登录结果并关闭弹窗。 */
  const handleAuthenticated = (authResponse) => {
    setAuthSession(saveAuthSession(authResponse))
    setLoginOpen(false)
  }

  /** 注销当前浏览器会话。 */
  const handleLogout = () => {
    clearAuthSession()
    setAuthSession(null)
  }

  /** 同步后端返回的最新用户资料到页面和浏览器会话。 */
  const handleUserUpdated = (user) => {
    setAuthSession(updateAuthSessionUser(user))
  }

  /** 使用浏览器历史记录切换前端页面。 */
  const navigateToPath = (path) => {
    window.history.pushState(null, '', path)
    setCurrentPath(path)
    window.scrollTo({ top: 0 })
  }

  const navigateTo = (itemId) => {
    setMobileMenuOpen(false)

    if (itemId === NAVIGATION_ITEM.HOME) {
      window.scrollTo({ top: 0, behavior: 'smooth' })
      return
    }

    if (itemId === NAVIGATION_ITEM.EXPLORE) {
      document.getElementById('featured')?.scrollIntoView({ behavior: 'smooth' })
      return
    }

    if (itemId === NAVIGATION_ITEM.CREATE) {
      setModalOpen(true)
      return
    }

    document.getElementById('features')?.scrollIntoView({ behavior: 'smooth' })
  }

  const toggleSaved = (title) => {
    setSavedWorks((current) =>
      current.includes(title)
        ? current.filter((savedTitle) => savedTitle !== title)
        : [...current, title],
    )
  }

  if (currentPath === APP_ROUTE.CONSOLE || currentPath.startsWith(`${APP_ROUTE.CONSOLE}/projects/`)) {
    return (
      <Dashboard
        authSession={authSession}
        onExit={() => navigateToPath(APP_ROUTE.HOME)}
        onLogout={() => {
          handleLogout()
          navigateToPath(APP_ROUTE.HOME)
        }}
        onUserUpdated={handleUserUpdated}
      />
    )
  }

  return (
    <div className="site-shell">
      <header className="topbar">
        <button className="brand" type="button" onClick={() => navigateTo(NAVIGATION_ITEM.HOME)}>
          Manga
          <Sparkles className="brand-spark" size={13} strokeWidth={2.2} />
        </button>

        <nav className="desktop-nav" aria-label={translate('home.nav.primary')}>
          {navItems.map(({ id, labelKey }) => (
            <button
              className={id === NAVIGATION_ITEM.HOME ? 'nav-link active' : 'nav-link'}
              key={id}
              type="button"
              onClick={() => navigateTo(id)}
            >
              {translate(labelKey)}
            </button>
          ))}
        </nav>

        <div className="header-actions">
          <LanguageSelector />
          {authSession ? (
            <>
              <button className="console-button" type="button" onClick={() => navigateToPath(APP_ROUTE.CONSOLE)}>
                <LayoutDashboard size={15} />
                <span>{translate('home.console')}</span>
              </button>
              <div className="account-control">
                <span className="account-name" title={authSession.user.username}>
                  {authSession.user.displayName}
                </span>
                <button className="logout-button" type="button" aria-label={translate('home.signOut')} onClick={handleLogout}>
                  <LogOut size={16} />
                </button>
              </div>
            </>
          ) : (
            <button className="sign-in-button" type="button" onClick={() => setLoginOpen(true)}>
              {translate('home.signIn')}
            </button>
          )}
          <button
            className="menu-button"
            type="button"
            aria-label={translate(mobileMenuOpen ? 'home.closeNavigation' : 'home.openNavigation')}
            aria-expanded={mobileMenuOpen}
            onClick={() => setMobileMenuOpen((open) => !open)}
          >
            {mobileMenuOpen ? <X size={22} /> : <Menu size={22} />}
          </button>
        </div>

        {mobileMenuOpen && (
          <nav className="mobile-nav" aria-label={translate('home.nav.mobile')}>
            {navItems.map(({ id, labelKey }) => (
              <button key={id} type="button" onClick={() => navigateTo(id)}>
                {translate(labelKey)}
              </button>
            ))}
            {authSession ? (
              <>
                <button type="button" onClick={() => navigateToPath(APP_ROUTE.CONSOLE)}>{translate('home.console')}</button>
                <button type="button" onClick={handleLogout}>{translate('home.signOut')}</button>
              </>
            ) : (
              <button type="button" onClick={() => { setMobileMenuOpen(false); setLoginOpen(true) }}>
                {translate('home.signIn')}
              </button>
            )}
          </nav>
        )}
      </header>

      <main>
        <section className="hero" aria-labelledby="hero-title">
          <img
            className="hero-image"
            src="/assets/manga-duel-hero.png"
            alt={translate('home.hero.imageAlt')}
          />
          <div className="hero-shade" />
          <div className="hero-energy" aria-hidden="true" />

          <div className="hero-content">
            <h1 id="hero-title">{translate('home.hero.title')}</h1>
            <p>{translate('home.hero.description')}</p>
            <button className="primary-cta" type="button" onClick={() => setModalOpen(true)}>
              <span>{translate('home.hero.action')}</span>
              <ArrowRight size={20} />
            </button>
          </div>

        </section>

        <section className="featured-section" id="featured" aria-labelledby="featured-title">
          <div className="section-heading">
            <h2 id="featured-title">{translate('home.featured.title')}</h2>
            <button className="view-all-button" type="button" onClick={() => setModalOpen(true)}>
              <span>{translate('common.viewAll')}</span>
              <ArrowRight size={17} />
            </button>
          </div>

          <div className="works-grid">
            {works.map((work) => {
              const isSaved = savedWorks.includes(work.title)

              return (
                <article className="work-card" key={work.title}>
                  <button
                    className="work-preview"
                    type="button"
                    aria-label={translate('home.work.play', { title: work.title })}
                    onClick={() => setModalOpen(true)}
                  >
                    <img src={work.image} alt="" />
                    <span className="work-play">
                      <Play size={20} fill="currentColor" />
                    </span>
                  </button>
                  <div className="work-meta">
                    <div>
                      <h3>{work.title}</h3>
                      <p>
                        <Play size={12} fill="currentColor" />
                        <span>{translate('home.work.by', { creator: work.creator })}</span>
                      </p>
                    </div>
                    <button
                      className={isSaved ? 'save-button saved' : 'save-button'}
                      type="button"
                      aria-label={translate(isSaved ? 'home.work.remove' : 'home.work.save', { title: work.title })}
                      onClick={() => toggleSaved(work.title)}
                    >
                      <Heart size={15} fill={isSaved ? 'currentColor' : 'none'} />
                      <span>{work.likes}</span>
                    </button>
                  </div>
                </article>
              )
            })}
          </div>
        </section>

        <section className="feature-strip" id="features" aria-label={translate('home.features.label')}>
          {features.map(({ titleKey, descriptionKey, icon: Icon }) => (
            <article className="feature-item" key={titleKey}>
              <div className="feature-icon">
                <Icon size={27} strokeWidth={1.8} />
              </div>
              <div>
                <h3>{translate(titleKey)}</h3>
                <p>{translate(descriptionKey)}</p>
              </div>
            </article>
          ))}
        </section>
      </main>

      <footer className="footer">
        <span>Manga</span>
        <p>{translate('home.footer.tagline')}</p>
      </footer>

      {modalOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => setModalOpen(false)}>
          <section
            className="creation-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="modal-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <button
              className="modal-close"
              type="button"
              aria-label={translate('common.close')}
              onClick={() => setModalOpen(false)}
            >
              <X size={20} />
            </button>
            <div className="modal-icon">
              <WandSparkles size={30} />
            </div>
            <h2 id="modal-title">{translate('home.modal.title')}</h2>
            <p>{translate('home.modal.description')}</p>
            <button className="modal-action" type="button" onClick={() => setModalOpen(false)}>
              {translate('home.modal.action')}
              <ArrowRight size={18} />
            </button>
          </section>
        </div>
      )}

      {loginOpen && (
        <LoginModal onClose={() => setLoginOpen(false)} onAuthenticated={handleAuthenticated} />
      )}
    </div>
  )
}

export default App
