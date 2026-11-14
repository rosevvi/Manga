import { AUTH_CONFIG } from '../constants/auth'

/** 同一账号的同一路径共用进行中的 GET，完成后立即释放。 */
const pendingReads = new Map()

/** 调用需要登录的后端接口并解析统一响应。 */
export function authorizedRequest(accessToken, path, options) {
  if (options) return request(accessToken, path, options)

  const reads = pendingReads.get(accessToken) ?? new Map()
  const pending = reads.get(path)
  if (pending) return pending

  const read = request(accessToken, path, {}).finally(() => {
    reads.delete(path)
    if (!reads.size) pendingReads.delete(accessToken)
  })
  reads.set(path, read)
  pendingReads.set(accessToken, reads)
  return read
}

/** 所有读写保持原有认证和错误响应处理。 */
async function request(accessToken, path, options) {
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
    const failure = new Error(payload?.message ?? 'Unable to connect to Manga server')
    failure.code = payload?.code
    failure.status = response.status
    throw failure
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
