const HISTORY_INDEX = 'mangaWorkspaceIndex'

/** 读取应用内历史位置，用于撤回和重放浏览器的前进或后退。 */
export function workspaceHistoryIndex(state = window.history.state) {
  return Number.isInteger(state?.[HISTORY_INDEX]) ? state[HISTORY_INDEX] : null
}

/** 为直接打开的页面建立起点，保留当前入口的其他历史数据。 */
export function ensureWorkspaceHistoryIndex() {
  const index = workspaceHistoryIndex()
  if (index !== null) return index
  window.history.replaceState({ ...window.history.state, [HISTORY_INDEX]: 0 }, '')
  return 0
}

/** 记录应用内页面与镜头选择的前后顺序。 */
export function pushWorkspaceHistory(url) {
  const index = ensureWorkspaceHistoryIndex()
  window.history.pushState({ [HISTORY_INDEX]: index + 1 }, '', url)
}

/** 原地更新选择时沿用当前历史位置。 */
export function replaceWorkspaceHistory(url) {
  ensureWorkspaceHistoryIndex()
  window.history.replaceState(window.history.state, '', url)
}
