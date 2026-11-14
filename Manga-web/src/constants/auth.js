/** 认证请求默认允许等待的时长，单位毫秒。 */
const DEFAULT_AUTH_REQUEST_TIMEOUT_MS = 20_000
const configuredAuthTimeout = Number(import.meta.env.VITE_AUTH_REQUEST_TIMEOUT_MS)

/** 认证接口与浏览器会话使用的集中配置。 */
export const AUTH_CONFIG = Object.freeze({
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1',
  sessionKey: 'manga.auth.session',
  bearerType: 'Bearer',
  usernameMaxLength: 64,
  displayNameMinLength: 2,
  displayNameMaxLength: 64,
  passwordMinLength: 8,
  passwordMaxLength: 128,
  minimumWechatPollIntervalMs: 1000,
  /** 请求和响应正文读取的整体时限，避免 fetch 永久挂起。 */
  requestTimeoutMs: Number.isFinite(configuredAuthTimeout) && configuredAuthTimeout > 0
    ? configuredAuthTimeout : DEFAULT_AUTH_REQUEST_TIMEOUT_MS,
})

/** 认证网络错误的统一公开描述。 */
export const AUTH_REQUEST_ERROR = Object.freeze({
  USER_NOT_FOUND: 'USER_NOT_FOUND',
  TIMEOUT: '登录请求超时，请检查网络后重试',
  UNAVAILABLE: '无法连接登录服务，请稍后重试',
})

/** 外部登录会话状态。 */
export const EXTERNAL_LOGIN_STATUS = Object.freeze({
  WAITING: 'WAITING',
  CONFIRMED: 'CONFIRMED',
  CONSUMED: 'CONSUMED',
  EXPIRED: 'EXPIRED',
})

/** 平台账号初始注册来源。 */
export const REGISTRATION_SOURCE = Object.freeze({
  PASSWORD: 'PASSWORD',
  WECHAT_OFFICIAL_ACCOUNT: 'WECHAT_OFFICIAL_ACCOUNT',
})

/** 后端认证资源路径。 */
export const AUTH_ENDPOINTS = Object.freeze({
  login: '/auth/login',
  guestLogin: '/auth/guest',
  wechatQrLogin: '/auth/wechat/qr',
  currentUser: '/users/me',
  currentUserPassword: '/users/me/password',
})

/** 构造指定微信扫码登录会话的状态接口。 */
export function wechatQrLoginStatusEndpoint(loginToken) {
  return `${AUTH_ENDPOINTS.wechatQrLogin}/${encodeURIComponent(loginToken)}`
}
