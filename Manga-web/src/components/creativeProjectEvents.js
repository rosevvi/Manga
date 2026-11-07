/** 审批落库后通知当前控制台刷新项目事实。 */
export const CREATIVE_PROJECT_CHANGED = 'manga:creative-project-changed'

/** 事件只携带业务引用，页面重新读取有权限校验的服务端数据。 */
export function notifyCreativeProjectChanged(projectId) {
  window.dispatchEvent(new CustomEvent(CREATIVE_PROJECT_CHANGED, { detail: { projectId } }))
}
