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
