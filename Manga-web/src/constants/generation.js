/** 图片供应商与首批开放模型，和后端契约保持一致。 */
export const IMAGE_PROVIDER = Object.freeze({ OPENAI: 'OPENAI', FAKE: 'FAKE' })
/** 用户指定的真实图片模型和免费验证模型。 */
export const IMAGE_MODEL = Object.freeze({ OPENAI: 'gpt-image-2', FAKE: 'fake-image' })
/** 平台允许的单张图片尺寸。 */
export const IMAGE_SIZES = Object.freeze(['1024x1024', '1536x1024', '1024x1536'])
/** 作业状态标签，不将未知结果误标为失败后自动重试。 */
export const GENERATION_STATUS_LABEL = Object.freeze({
  PENDING_SUBMISSION: '排队等待提交', SUBMITTING: '正在生成', SUBMITTED: '供应商已受理', RUNNING: '供应商执行中',
  MATERIALIZING: '转存平台产物', SUCCEEDED: '产物已保存', FAILED: '未完成',
  CANCELLED: '已取消结果采用', UNKNOWN: '提交结果待核对',
})
/** 作业终态集合。 */
export const GENERATION_TERMINAL = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED'])
/** 每页数量，由后端固定限制。 */
export const GENERATION_PAGE_SIZE = 25
/** 卡片快照补偿间隔，单位毫秒。 */
export const GENERATION_POLL_MS = 3000
/** 图片支持的用户接入配置类型。 */
export const IMAGE_CONFIG_TYPES = new Set(['OPENAI', 'OPENAI_COMPATIBLE', 'NEWAPI'])
