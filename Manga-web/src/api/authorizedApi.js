import { AUTH_CONFIG } from '../constants/auth'

/** 调用需要登录的后端接口并解析统一响应。 */
export async function authorizedRequest(accessToken, path, options = {}) {
  const response = await fetch(`${AUTH_CONFIG.apiBaseUrl}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `${AUTH_CONFIG.bearerType} ${accessToken}`,
      ...options.headers,
    },
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok || !payload?.success) {
    throw new Error(payload?.message ?? 'Unable to connect to Manga server')
  }
  return payload.data
}

/** 上传表单文件到需要登录的后端接口并解析统一响应。 */
export async function authorizedFormRequest(accessToken, path, formData, options = {}) {
  const response = await fetch(`${AUTH_CONFIG.apiBaseUrl}${path}`, {
    ...options,
    method: options.method ?? 'POST',
    headers: {
      Authorization: `${AUTH_CONFIG.bearerType} ${accessToken}`,
      ...options.headers,
    },
    body: formData,
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok || !payload?.success) {
    throw new Error(payload?.message ?? 'Unable to connect to Manga server')
  }
  return payload.data
}
