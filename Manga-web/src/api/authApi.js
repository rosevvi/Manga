import { AUTH_CONFIG, AUTH_ENDPOINTS, AUTH_REQUEST_ERROR, wechatQrLoginStatusEndpoint } from '../constants/auth'

/** 会话恢复共享同一个进行中的身份查询。 */
const pendingCurrentUsers = new Map()

/** 调用认证接口并将统一错误响应转换为可展示异常。 */
async function request(path, options = {}) {
  const controller = new AbortController()
  let timedOut = false
  const cancel = () => controller.abort(options.signal?.reason)
  if (options.signal?.aborted) cancel()
  else options.signal?.addEventListener('abort', cancel, { once: true })
  const timeout = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, AUTH_CONFIG.requestTimeoutMs)
  try {
    const response = await fetch(`${AUTH_CONFIG.apiBaseUrl}${path}`, {
      ...options,
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
    })
    const payload = await response.json().catch(() => null)
    if (!response.ok || !payload?.success) {
      const error = new Error(payload?.message ?? AUTH_REQUEST_ERROR.UNAVAILABLE)
      error.status = response.status
      error.code = payload?.code
      throw error
    }
    return payload.data
  } catch (error) {
    if (timedOut) throw new Error(AUTH_REQUEST_ERROR.TIMEOUT)
    throw error
  } finally {
    clearTimeout(timeout)
    options.signal?.removeEventListener('abort', cancel)
  }
}

/** 使用数据库账号和密码登录。 */
export function login(username, password) {
  return request(AUTH_ENDPOINTS.login, {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

/** 创建无需账号的短期游客身份。 */
export function loginAsGuest() {
  return request(AUTH_ENDPOINTS.guestLogin, { method: 'POST' })
}

/** 创建微信公众号扫码登录二维码。 */
export function createWechatQrLogin(options = {}) {
  return request(AUTH_ENDPOINTS.wechatQrLogin, { ...options, method: 'POST' })
}

/** 查询微信公众号扫码登录会话状态。 */
export function getWechatQrLoginStatus(loginToken, options = {}) {
  return request(wechatQrLoginStatusEndpoint(loginToken), options)
}

/** 使用访问令牌重新获取当前身份。 */
export function getCurrentUser(accessToken) {
  const pending = pendingCurrentUsers.get(accessToken)
  if (pending) return pending
  const read = request(AUTH_ENDPOINTS.currentUser, {
    headers: {
      Authorization: `${AUTH_CONFIG.bearerType} ${accessToken}`,
    },
  }).finally(() => pendingCurrentUsers.delete(accessToken))
  pendingCurrentUsers.set(accessToken, read)
  return read
}

/** 更新当前数据库用户的基础资料。 */
export function updateCurrentUser(accessToken, displayName) {
  return request(AUTH_ENDPOINTS.currentUser, {
    method: 'PUT',
    headers: {
      Authorization: `${AUTH_CONFIG.bearerType} ${accessToken}`,
    },
    body: JSON.stringify({ displayName }),
  })
}

/** 校验当前密码后修改登录密码。 */
export function changeCurrentPassword(accessToken, currentPassword, newPassword) {
  return request(AUTH_ENDPOINTS.currentUserPassword, {
    method: 'PUT',
    headers: {
      Authorization: `${AUTH_CONFIG.bearerType} ${accessToken}`,
    },
    body: JSON.stringify({ currentPassword, newPassword }),
  })
}

/** 从当前标签页会话中读取登录状态。 */
export function readAuthSession() {
  const rawSession = window.sessionStorage.getItem(AUTH_CONFIG.sessionKey)
  if (!rawSession) return null

  try {
    return JSON.parse(rawSession)
  } catch {
    window.sessionStorage.removeItem(AUTH_CONFIG.sessionKey)
    return null
  }
}

/** 保存后端签发的令牌和身份信息。 */
export function saveAuthSession(authResponse) {
  const session = {
    accessToken: authResponse.accessToken,
    user: authResponse.user,
  }
  window.sessionStorage.setItem(AUTH_CONFIG.sessionKey, JSON.stringify(session))
  return session
}

/** 替换会话中的最新用户资料并保留访问令牌。 */
export function updateAuthSessionUser(user) {
  const currentSession = readAuthSession()
  if (!currentSession) return null

  const nextSession = { ...currentSession, user }
  window.sessionStorage.setItem(AUTH_CONFIG.sessionKey, JSON.stringify(nextSession))
  return nextSession
}

/** 清理当前标签页中的认证信息。 */
export function clearAuthSession() {
  window.sessionStorage.removeItem(AUTH_CONFIG.sessionKey)
}
