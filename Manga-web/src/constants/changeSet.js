/** 与后端不可变提案协议对应的业务类型。 */
export const CHANGE_SET_KIND = Object.freeze({ SCRIPT: 'SCRIPT', STORYBOARD: 'STORYBOARD' })
/** 与后端状态机对应的提案状态。 */
export const CHANGE_SET_STATUS = Object.freeze({
  PENDING: 'PENDING', APPLIED: 'APPLIED', REJECTED: 'REJECTED', CONFLICTED: 'CONFLICTED', EXPIRED: 'EXPIRED', CANCELLED: 'CANCELLED',
})
/** 用户可主动提交的审批决定。 */
export const APPROVAL_DECISION = Object.freeze({ APPROVE: 'APPROVE', REJECT: 'REJECT' })
/** 提案生命周期展示文案。 */
export const CHANGE_SET_STATUS_LABEL = Object.freeze({
  PENDING: '等待审批', APPLIED: '已批准并保存', REJECTED: '已拒绝', CONFLICTED: '版本冲突', EXPIRED: '已过期', CANCELLED: '已取消',
})
/** 审批变化补偿轮询间隔，单位毫秒。 */
export const APPROVAL_POLL_MS = 3000
/** 恢复运行终态集合。 */
export const CREATIVE_TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED', 'CANCELLED'])
