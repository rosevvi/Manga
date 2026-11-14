/** 页面手动任务与助手共用任务中心，不存储运行正文。 */
export const OPEN_CREATIVE_TASK = 'manga:open-creative-task'

/** 定位一个已持久化任务；未指定 Run 时打开任务列表。 */
export function openCreativeTask(runId, projectId) {
  window.dispatchEvent(new CustomEvent(OPEN_CREATIVE_TASK, { detail: { runId, projectId } }))
}

export const RUN_STATUS_LABEL = Object.freeze({
  QUEUED: '等待执行', ACTIVE: '运行中', WAITING: '等待中', COMPLETED: '已完成', FAILED: '失败',
  CANCELLED: '已停止', CANCEL_REQUESTED: '正在停止', RUNNING: '运行中', READY: '等待执行',
  WAITING_APPROVAL: '等待审批', WAITING_JOB: '等待供应商', WAITING_CHILDREN: '等待协作',
  SUCCESS: '成功', ERROR: '失败', INTERRUPTED: '已中断', DENIED: '已拒绝',
})

export function creativeTaskName(run) {
  const names = { ASSISTANT_CHAT: '漫剧创作对话', PARSE_SCRIPT: '剧本解析', GENERATE_SCRIPT_CHAPTER: '生成章节',
    GENERATE_CHAPTER_STORYBOARD: '生成章节分镜', GENERATE_SHOT_IMAGE: '生成镜头图片', GENERATE_SHOT_VIDEO: '生成镜头视频' }
  return `${names[run?.operationType] || '创作任务'}${run?.targetId ? ` · #${run.targetId}` : ''}`
}
