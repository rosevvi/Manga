/** 与服务端消息投影协议对应的角色编码。 */
export const CREATIVE_MESSAGE_ROLE = Object.freeze({
  /** 用户原始指令。 */ USER: 'USER',
  /** 助手可见回答。 */ ASSISTANT: 'ASSISTANT',
  /** 工具执行状态。 */ TOOL: 'TOOL',
})

/** 助手展示需要识别的运行状态。 */
export const CREATIVE_MESSAGE_STATUS = Object.freeze({
  /** 正在生成内容。 */ ACTIVE: 'ACTIVE',
  /** 运行执行失败。 */ FAILED: 'FAILED',
  /** 用户已取消运行。 */ CANCELLED: 'CANCELLED',
})

/** 将内部稳定工具编码映射为用户可以理解的操作名称。 */
export const CREATIVE_TOOL_LABEL = Object.freeze({
  /** 项目基本信息。 */ get_project_overview: '读取项目信息',
  /** 剧本目录与元数据。 */ get_script_structure: '读取剧本结构',
  /** 指定章节正文。 */ get_chapter_content: '读取章节内容',
  /** 分镜镜头列表。 */ list_storyboard_shots: '读取分镜列表',
})

/** 公开工具状态的界面提示。 */
export const CREATIVE_TOOL_STATUS_LABEL = Object.freeze({
  /** 工具正在运行。 */ RUNNING: '进行中',
  /** 工具已成功完成。 */ SUCCESS: '已完成',
  /** 工具执行失败。 */ ERROR: '失败',
  /** 工具被中断。 */ INTERRUPTED: '已停止',
  /** 工具调用被拒绝。 */ DENIED: '未授权',
})
