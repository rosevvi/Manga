import { AUTH_CONFIG } from '../constants/auth'

/** 上传路径以 API 为基准，随前端发布的资源从前端站点读取。 */
export function resolveMediaUrl(value) {
  if (!value?.trim()) return ''
  try {
    const path = value.trim()
    const base = path.startsWith('/assets/')
      ? window.location.origin
      : new URL(AUTH_CONFIG.apiBaseUrl, window.location.origin)
    const url = new URL(path, base)
    return ['https:', 'http:', 'blob:'].includes(url.protocol) ? url.href : ''
  } catch {
    return ''
  }
}
