/** 与持久化协作图协议保持一致的角色显示名。 */
export const AGENT_PROFILE_LABEL = Object.freeze({
  manga_director: '创作导演', script_parser: '剧本解析师', script_writer: '剧本编剧',
  storyboard_director: '分镜导演', continuity_reviewer: '连续性评审师',
})
/** 步骤状态文案，不通过内部 Agent 输出猜测进度。 */
export const CREATIVE_STEP_STATUS_LABEL = Object.freeze({
  PENDING: '等待依赖', READY: '待执行', RUNNING: '执行中', WAITING_CHILDREN: '等待协作汇合',
  WAITING_APPROVAL: '等待审批', WAITING_JOB: '等待生成作业', WAITING_RETRY: '等待重试',
  COMPLETED: '已完成', FAILED: '失败', CANCEL_REQUESTED: '正在停止', CANCELLED: '已取消', SKIPPED: '已跳过',
})
/** 已进入终态的步骤无需再提供停止按钮。 */
export const CREATIVE_STEP_TERMINAL = new Set(['COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED'])
/** 持久化计划检查点的用户文案。 */
export const CREATIVE_PLAN_STATUS_LABEL = Object.freeze({
  DRAFT: '计划已提交', MATERIALIZED: '正在协作', COMPLETED: '已汇合', FAILED: '协作失败', CANCELLED: '已取消',
})
