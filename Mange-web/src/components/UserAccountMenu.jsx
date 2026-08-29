import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import {
  BellRing,
  Check,
  ChevronRight,
  CircleHelp,
  CreditCard,
  KeyRound,
  LoaderCircle,
  LogOut,
  Palette,
  ShieldCheck,
  SlidersHorizontal,
  UserRound,
  X,
} from 'lucide-react'
import { changeCurrentPassword, updateCurrentUser } from '../api/authApi'
import { AUTH_CONFIG, REGISTRATION_SOURCE } from '../constants/auth'
import { useLanguage } from '../i18n/LanguageContext'
import './user-account.css'

const ACCOUNT_PANEL = Object.freeze({
  PROFILE: 'profile',
  SECURITY: 'security',
  PREFERENCES: 'preferences',
  BILLING: 'billing',
  HELP: 'help',
})

const ACCOUNT_MENU_ITEMS = [
  { id: ACCOUNT_PANEL.PROFILE, labelKey: 'account.profile', descriptionKey: 'account.profileDescription', icon: UserRound },
  { id: ACCOUNT_PANEL.SECURITY, labelKey: 'account.security', descriptionKey: 'account.securityDescription', icon: ShieldCheck },
  { id: ACCOUNT_PANEL.PREFERENCES, labelKey: 'account.preferences', descriptionKey: 'account.preferencesDescription', icon: SlidersHorizontal },
  { id: ACCOUNT_PANEL.BILLING, labelKey: 'account.billing', descriptionKey: 'account.billingDescription', icon: CreditCard },
  { id: ACCOUNT_PANEL.HELP, labelKey: 'account.help', descriptionKey: 'account.helpDescription', icon: CircleHelp },
]

const PANEL_TITLES = Object.freeze({
  [ACCOUNT_PANEL.PROFILE]: 'account.profile',
  [ACCOUNT_PANEL.SECURITY]: 'account.security',
  [ACCOUNT_PANEL.PREFERENCES]: 'account.preferences',
  [ACCOUNT_PANEL.BILLING]: 'account.billing',
  [ACCOUNT_PANEL.HELP]: 'account.help',
})

const INITIAL_PREFERENCES = Object.freeze({
  emailUpdates: true,
  creationTips: true,
  securityAlerts: true,
})

const FEEDBACK_TYPE = Object.freeze({ SUCCESS: 'success', ERROR: 'error' })

/** 提供控制台用户下拉菜单以及账号设置弹窗。 */
function UserAccountMenu({ authSession, displayName, onLogout, onUserUpdated }) {
  const { locale, translate } = useLanguage()
  const [menuOpen, setMenuOpen] = useState(false)
  const [activePanel, setActivePanel] = useState(null)
  const [displayNameInput, setDisplayNameInput] = useState(displayName)
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [preferences, setPreferences] = useState(INITIAL_PREFERENCES)
  const [loading, setLoading] = useState(false)
  const [feedback, setFeedback] = useState(null)
  const user = authSession?.user
  const canManageAccount = Boolean(authSession?.accessToken && !user?.guest)
  const isWechatAccount = user?.registrationSource === REGISTRATION_SOURCE.WECHAT_OFFICIAL_ACCOUNT

  useEffect(() => setDisplayNameInput(displayName), [displayName])

  useEffect(() => {
    document.body.style.overflow = activePanel ? 'hidden' : ''
    return () => {
      document.body.style.overflow = ''
    }
  }, [activePanel])

  /** 打开指定设置面板并重置临时提示。 */
  const openPanel = (panel) => {
    setActivePanel(panel)
    setMenuOpen(false)
    setFeedback(null)
  }

  /** 关闭设置弹窗并清理密码输入。 */
  const closePanel = () => {
    setActivePanel(null)
    setFeedback(null)
    setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' })
  }

  /** 保存可编辑的当前用户显示名称。 */
  const submitProfile = async (event) => {
    event.preventDefault()
    const nextDisplayName = displayNameInput.trim()
    if (!canManageAccount) {
      setFeedback({ type: FEEDBACK_TYPE.ERROR, message: translate('account.guestProfileDenied') })
      return
    }
    if (nextDisplayName.length < AUTH_CONFIG.displayNameMinLength || nextDisplayName.length > AUTH_CONFIG.displayNameMaxLength) {
      setFeedback({ type: FEEDBACK_TYPE.ERROR, message: translate('account.displayNameInvalid') })
      return
    }

    setLoading(true)
    setFeedback(null)
    try {
      const updatedUser = await updateCurrentUser(authSession.accessToken, nextDisplayName)
      onUserUpdated(updatedUser)
      setFeedback({ type: FEEDBACK_TYPE.SUCCESS, message: translate('account.profileSaved') })
    } catch (error) {
      setFeedback({ type: FEEDBACK_TYPE.ERROR, message: error.message })
    } finally {
      setLoading(false)
    }
  }

  /** 校验两次新密码输入并提交密码修改。 */
  const submitPassword = async (event) => {
    event.preventDefault()
    if (!canManageAccount) {
      setFeedback({ type: FEEDBACK_TYPE.ERROR, message: translate('account.guestPasswordDenied') })
      return
    }
    if (passwordForm.newPassword.length < AUTH_CONFIG.passwordMinLength) {
      setFeedback({ type: FEEDBACK_TYPE.ERROR, message: translate('account.newPasswordTooShort') })
      return
    }
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setFeedback({ type: FEEDBACK_TYPE.ERROR, message: translate('account.passwordMismatch') })
      return
    }

    setLoading(true)
    setFeedback(null)
    try {
      await changeCurrentPassword(
        authSession.accessToken,
        passwordForm.currentPassword,
        passwordForm.newPassword,
      )
      setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' })
      setFeedback({ type: FEEDBACK_TYPE.SUCCESS, message: translate('account.passwordSaved') })
    } catch (error) {
      setFeedback({ type: FEEDBACK_TYPE.ERROR, message: error.message })
    } finally {
      setLoading(false)
    }
  }

  /** 切换本地偏好设置。 */
  const togglePreference = (preference) => {
    setPreferences((current) => ({ ...current, [preference]: !current[preference] }))
  }

  return (
    <div className="user-account-menu">
      <button
        className="dashboard-profile"
        type="button"
        aria-expanded={menuOpen}
        onClick={() => setMenuOpen((open) => !open)}
      >
        <img src="/assets/shadow-awakening.png" alt="" />
        <span>{displayName}</span>
        <ChevronRight size={13} className={menuOpen ? 'profile-chevron open' : 'profile-chevron'} />
      </button>

      {menuOpen && (
        <div className="account-dropdown" role="menu">
          <div className="account-dropdown-header">
            <img src="/assets/shadow-awakening.png" alt="" />
            <div>
              <strong>{displayName}</strong>
              <span>@{user?.username ?? 'preview'}</span>
            </div>
            <span className="account-role">{user?.roles?.[0] ?? 'USER'}</span>
          </div>
          <div className="account-menu-list">
            {ACCOUNT_MENU_ITEMS.map(({ id, labelKey, descriptionKey, icon: Icon }) => (
              <button type="button" role="menuitem" key={id} onClick={() => openPanel(id)}>
                <span className="account-menu-icon"><Icon size={17} /></span>
                <span><strong>{translate(labelKey)}</strong><small>{translate(descriptionKey)}</small></span>
                <ChevronRight size={15} />
              </button>
            ))}
          </div>
          <button className="account-logout" type="button" role="menuitem" onClick={onLogout}>
            <LogOut size={16} />
            <span>{translate('account.signOut')}</span>
          </button>
        </div>
      )}

      {activePanel && createPortal(
        <div className="account-modal-backdrop" role="presentation" onMouseDown={closePanel}>
          <section
            className="account-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="account-modal-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <header className="account-modal-header">
              <div><span>{translate('account.section')}</span><h2 id="account-modal-title">{translate(PANEL_TITLES[activePanel])}</h2></div>
              <button type="button" aria-label={translate('account.closeSettings')} onClick={closePanel}><X size={19} /></button>
            </header>

            {activePanel === ACCOUNT_PANEL.PROFILE && (
              <form className="account-panel-form" onSubmit={submitProfile}>
                <div className="profile-summary-card">
                  <img src="/assets/shadow-awakening.png" alt="" />
                  <div><strong>{displayName}</strong><span>@{user?.username ?? 'preview'}</span></div>
                  <span>{user?.status ?? (user?.guest ? 'GUEST' : 'ACTIVE')}</span>
                </div>
                <label htmlFor="profile-display-name">{translate('account.displayName')}</label>
                <input id="profile-display-name" value={displayNameInput} maxLength={AUTH_CONFIG.displayNameMaxLength} onChange={(event) => setDisplayNameInput(event.target.value)} />
                <label htmlFor="profile-username">{translate('account.username')}</label>
                <input id="profile-username" value={user?.username ?? 'preview'} disabled />
                <div className="account-detail-grid">
                  <div><span>{translate('account.userId')}</span><strong>{user?.id ?? '—'}</strong></div>
                  <div><span>{translate('account.roles')}</span><strong>{user?.roles?.join(', ') ?? 'USER'}</strong></div>
                  <div><span>{translate('account.loginMethod')}</span><strong>{registrationSourceLabel(user?.registrationSource, translate)}</strong></div>
                  <div><span>{translate('account.created')}</span><strong>{formatAccountDate(user?.createdAt, locale)}</strong></div>
                  <div><span>{translate('account.updated')}</span><strong>{formatAccountDate(user?.updatedAt, locale)}</strong></div>
                </div>
                <Feedback feedback={feedback} />
                <div className="account-form-actions"><button type="button" onClick={closePanel}>{translate('common.cancel')}</button><button type="submit" disabled={loading}>{loading ? <LoaderCircle className="spin" size={16} /> : <Check size={16} />} {translate('account.save')}</button></div>
              </form>
            )}

            {activePanel === ACCOUNT_PANEL.SECURITY && isWechatAccount && (
              <div className="account-settings-list">
                <div className="security-notice"><KeyRound size={20} /><div><strong>{translate('account.wechatSecurityTitle')}</strong><p>{translate('account.wechatSecurityHint')}</p></div></div>
              </div>
            )}

            {activePanel === ACCOUNT_PANEL.SECURITY && !isWechatAccount && (
              <form className="account-panel-form" onSubmit={submitPassword}>
                <div className="security-notice"><KeyRound size={20} /><div><strong>{translate('account.securityNotice')}</strong><p>{translate('account.securityHint')}</p></div></div>
                <label htmlFor="current-password">{translate('account.currentPassword')}</label>
                <input id="current-password" type="password" autoComplete="current-password" value={passwordForm.currentPassword} onChange={(event) => setPasswordForm((current) => ({ ...current, currentPassword: event.target.value }))} />
                <label htmlFor="new-password">{translate('account.newPassword')}</label>
                <input id="new-password" type="password" autoComplete="new-password" value={passwordForm.newPassword} onChange={(event) => setPasswordForm((current) => ({ ...current, newPassword: event.target.value }))} />
                <label htmlFor="confirm-password">{translate('account.confirmPassword')}</label>
                <input id="confirm-password" type="password" autoComplete="new-password" value={passwordForm.confirmPassword} onChange={(event) => setPasswordForm((current) => ({ ...current, confirmPassword: event.target.value }))} />
                <Feedback feedback={feedback} />
                <div className="account-form-actions"><button type="button" onClick={closePanel}>{translate('common.cancel')}</button><button type="submit" disabled={loading}>{loading ? <LoaderCircle className="spin" size={16} /> : <ShieldCheck size={16} />} {translate('account.changePassword')}</button></div>
              </form>
            )}

            {activePanel === ACCOUNT_PANEL.PREFERENCES && (
              <div className="account-settings-list">
                <section className="preference-card"><Palette size={19} /><div><strong>{translate('account.appearance')}</strong><p>{translate('account.appearanceHint')}</p></div><span>{translate('account.dark')}</span></section>
                <section className="preference-card"><BellRing size={19} /><div><strong>{translate('account.emailUpdates')}</strong><p>{translate('account.emailUpdatesHint')}</p></div><Toggle enabled={preferences.emailUpdates} onChange={() => togglePreference('emailUpdates')} label={translate('account.emailUpdates')} /></section>
                <section className="preference-card"><SlidersHorizontal size={19} /><div><strong>{translate('account.creationTips')}</strong><p>{translate('account.creationTipsHint')}</p></div><Toggle enabled={preferences.creationTips} onChange={() => togglePreference('creationTips')} label={translate('account.creationTips')} /></section>
                <section className="preference-card"><ShieldCheck size={19} /><div><strong>{translate('account.securityAlerts')}</strong><p>{translate('account.securityAlertsHint')}</p></div><Toggle enabled={preferences.securityAlerts} onChange={() => togglePreference('securityAlerts')} label={translate('account.securityAlerts')} /></section>
              </div>
            )}

            {activePanel === ACCOUNT_PANEL.BILLING && (
              <div className="billing-panel"><span className="billing-plan-icon"><CreditCard size={23} /></span><p>{translate('account.currentPlan')}</p><h3>{translate('account.planName')}</h3><span>{translate('account.creditsRemaining')}</span><div className="billing-progress"><span /></div><button type="button">{translate('account.manageSubscription')}</button></div>
            )}

            {activePanel === ACCOUNT_PANEL.HELP && (
              <div className="help-panel"><CircleHelp size={34} /><h3>{translate('account.helpTitle')}</h3><p>{translate('account.helpText')}</p><div><button type="button">{translate('account.documentation')}</button><button type="button">{translate('account.contactSupport')}</button></div></div>
            )}
          </section>
        </div>,
        document.body,
      )}
    </div>
  )
}

/** 将后端时间转换为简洁的本地日期。 */
function formatAccountDate(value, locale) {
  if (!value) return '—'
  return new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'short', day: 'numeric' }).format(new Date(value))
}

/** 将账号来源转换为当前语言下的登录方式名称。 */
function registrationSourceLabel(source, translate) {
  return source === REGISTRATION_SOURCE.WECHAT_OFFICIAL_ACCOUNT
    ? translate('account.loginMethod.wechat')
    : translate('account.loginMethod.password')
}

/** 展示表单提交反馈。 */
function Feedback({ feedback }) {
  if (!feedback) return null
  return <p className={`account-feedback ${feedback.type}`}>{feedback.message}</p>
}

/** 渲染偏好设置开关。 */
function Toggle({ enabled, onChange, label }) {
  return <button className={enabled ? 'preference-toggle enabled' : 'preference-toggle'} type="button" role="switch" aria-checked={enabled} aria-label={label} onClick={onChange}><span /></button>
}

export default UserAccountMenu
