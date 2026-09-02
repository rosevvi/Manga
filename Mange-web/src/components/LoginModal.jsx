import { useEffect, useState } from 'react'
import { ArrowLeft, ArrowRight, LoaderCircle, LogIn, QrCode, RefreshCw, UserRound, X } from 'lucide-react'
import { createWechatQrLogin, getWechatQrLoginStatus, login, loginAsGuest } from '../api/authApi'
import { AUTH_CONFIG, EXTERNAL_LOGIN_STATUS } from '../constants/auth'
import { useLanguage } from '../i18n/LanguageContext'

const EMPTY_CREDENTIALS = Object.freeze({ username: '', password: '' })
const LOGIN_MODE = Object.freeze({ account: 'account', guest: 'guest', wechat: 'wechat' })
const LOGIN_VIEW = Object.freeze({ PASSWORD: 'password', WECHAT: 'wechat' })

/** 提供账号密码、微信公众号和游客登录方式的弹窗。 */
function LoginModal({ onClose, onAuthenticated }) {
  const { translate } = useLanguage()
  const [view, setView] = useState(LOGIN_VIEW.PASSWORD)
  const [credentials, setCredentials] = useState(EMPTY_CREDENTIALS)
  const [loadingMode, setLoadingMode] = useState(null)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [wechatSession, setWechatSession] = useState(null)
  const [wechatStatus, setWechatStatus] = useState(null)

  useEffect(() => {
    if (view !== LOGIN_VIEW.WECHAT
        || !wechatSession
        || wechatStatus !== EXTERNAL_LOGIN_STATUS.WAITING) return undefined

    let cancelled = false
    let pollTimer

    /** 轮询扫码结果，并在微信确认后交付平台登录响应。 */
    const pollWechatStatus = async () => {
      try {
        const result = await getWechatQrLoginStatus(wechatSession.loginToken)
        if (cancelled) return
        if (result.authentication) {
          onAuthenticated(result.authentication)
          return
        }
        setWechatStatus(result.status)
        if (result.status === EXTERNAL_LOGIN_STATUS.WAITING) {
          pollTimer = window.setTimeout(pollWechatStatus, Math.max(
            wechatSession.pollInterval * 1000,
            AUTH_CONFIG.minimumWechatPollIntervalMs,
          ))
        }
      } catch (requestError) {
        if (!cancelled) setError(requestError.message)
      }
    }

    pollTimer = window.setTimeout(pollWechatStatus, Math.max(
      wechatSession.pollInterval * 1000,
      AUTH_CONFIG.minimumWechatPollIntervalMs,
    ))
    return () => {
      cancelled = true
      window.clearTimeout(pollTimer)
    }
  }, [onAuthenticated, view, wechatSession, wechatStatus])

  const updateCredential = (event) => {
    const { name, value } = event.target
    setCredentials((current) => ({ ...current, [name]: value }))
    setFieldErrors((current) => {
      if (!current[name]) return current
      const next = { ...current }
      delete next[name]
      return next
    })
  }

  /** 提交正式账号登录，并将令牌交给页面保存。 */
  const submitLogin = async (event) => {
    event.preventDefault()
    const nextFieldErrors = validateLoginForm(credentials, translate)
    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors)
      return
    }
    setError('')
    setLoadingMode(LOGIN_MODE.account)
    try {
      onAuthenticated(await login(credentials.username.trim(), credentials.password))
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setLoadingMode(null)
    }
  }

  /** 请求临时游客身份。 */
  const submitGuestLogin = async () => {
    setError('')
    setLoadingMode(LOGIN_MODE.guest)
    try {
      onAuthenticated(await loginAsGuest())
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setLoadingMode(null)
    }
  }

  /** 请求公众号二维码并进入扫码登录视图。 */
  const startWechatLogin = async () => {
    setView(LOGIN_VIEW.WECHAT)
    setError('')
    setWechatSession(null)
    setWechatStatus(null)
    setLoadingMode(LOGIN_MODE.wechat)
    try {
      const session = await createWechatQrLogin()
      setWechatSession(session)
      setWechatStatus(EXTERNAL_LOGIN_STATUS.WAITING)
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setLoadingMode(null)
    }
  }

  /** 返回账号密码登录并停止二维码轮询。 */
  const returnToPasswordLogin = () => {
    setView(LOGIN_VIEW.PASSWORD)
    setWechatSession(null)
    setWechatStatus(null)
    setError('')
  }

  const loading = loadingMode !== null

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="login-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="login-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <button className="modal-close" type="button" aria-label={translate('login.close')} onClick={onClose}>
          <X size={20} />
        </button>

        <div className="login-mark"><LogIn size={27} /></div>
        <p className="login-eyebrow">{translate('login.eyebrow')}</p>
        <h2 id="login-title">{translate(view === LOGIN_VIEW.WECHAT ? 'login.wechatTitle' : 'login.title')}</h2>
        <p className="login-intro">{translate(view === LOGIN_VIEW.WECHAT ? 'login.wechatIntro' : 'login.intro')}</p>

        {view === LOGIN_VIEW.PASSWORD ? (
          <>
            <form className="login-form" onSubmit={submitLogin} noValidate>
              <label htmlFor="login-username">{translate('login.username')}</label>
              <input
                id="login-username"
                name="username"
                type="text"
                autoComplete="username"
                maxLength={AUTH_CONFIG.usernameMaxLength}
                value={credentials.username}
                onChange={updateCredential}
              />
              {fieldErrors.username && <small className="login-field-error">{fieldErrors.username}</small>}
              <label htmlFor="login-password">{translate('login.password')}</label>
              <input
                id="login-password"
                name="password"
                type="password"
                autoComplete="current-password"
                maxLength={AUTH_CONFIG.passwordMaxLength}
                value={credentials.password}
                onChange={updateCredential}
              />
              {fieldErrors.password && <small className="login-field-error">{fieldErrors.password}</small>}

              {error && <p className="login-error" role="alert">{error}</p>}

              <button className="login-submit" type="submit" disabled={loading}>
                {loadingMode === LOGIN_MODE.account ? <LoaderCircle className="spin" size={18} /> : <ArrowRight size={18} />}
                <span>{translate('login.submit')}</span>
              </button>
            </form>

            <div className="login-divider"><span>{translate('login.or')}</span></div>
            <button className="wechat-login-button" type="button" disabled={loading} onClick={startWechatLogin}>
              {loadingMode === LOGIN_MODE.wechat ? <LoaderCircle className="spin" size={18} /> : <QrCode size={18} />}
              <span>{translate('login.wechat')}</span>
            </button>
            <button className="guest-login-button" type="button" disabled={loading} onClick={submitGuestLogin}>
              {loadingMode === LOGIN_MODE.guest ? <LoaderCircle className="spin" size={18} /> : <UserRound size={18} />}
              <span>{translate('login.guest')}</span>
            </button>
            <p className="guest-note">{translate('login.guestNote')}</p>
          </>
        ) : (
          <div className="wechat-login-panel">
            {wechatSession && wechatStatus === EXTERNAL_LOGIN_STATUS.WAITING && (
              <>
                <div className="wechat-qr-frame">
                  <img src={wechatSession.qrCodeUrl} alt={translate('login.wechatQrAlt')} />
                </div>
                <p className="wechat-login-status"><LoaderCircle className="spin" size={15} />{translate('login.wechatWaiting')}</p>
              </>
            )}
            {!wechatSession && loadingMode === LOGIN_MODE.wechat && <LoaderCircle className="wechat-panel-loader spin" size={30} />}
            {wechatStatus === EXTERNAL_LOGIN_STATUS.EXPIRED && <p className="login-error" role="alert">{translate('login.wechatExpired')}</p>}
            {wechatStatus === EXTERNAL_LOGIN_STATUS.CONSUMED && <p className="login-error" role="alert">{translate('login.wechatUnavailable')}</p>}
            {error && <p className="login-error" role="alert">{error}</p>}
            {(error || wechatStatus === EXTERNAL_LOGIN_STATUS.EXPIRED || wechatStatus === EXTERNAL_LOGIN_STATUS.CONSUMED) && (
              <button className="wechat-login-button" type="button" disabled={loading} onClick={startWechatLogin}>
                <RefreshCw size={17} />
                <span>{translate('login.wechatRetry')}</span>
              </button>
            )}
            <button className="wechat-back-button" type="button" onClick={returnToPasswordLogin}>
              <ArrowLeft size={16} />
              <span>{translate('login.backToPassword')}</span>
            </button>
          </div>
        )}
      </section>
    </div>
  )
}

function validateLoginForm(credentials, translate) {
  const errors = {}
  if (!credentials.username.trim()) {
    errors.username = translate('common.fieldRequired')
  }
  if (!credentials.password) {
    errors.password = translate('common.fieldRequired')
  }
  return errors
}

export default LoginModal
