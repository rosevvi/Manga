/** 配置状态与固定角色族对应后端协议，专业 Agent 标识由数据库返回。 */
export const AGENT_VERSION_STATUS = Object.freeze({ DRAFT: 'DRAFT', PUBLISHED: 'PUBLISHED' })
/** 界面权限判断只决定入口可见性，后端仍独立鉴权。 */
export const AGENT_ADMIN_ROLE = 'ADMIN'
/** 控制台管理入口标识。 */
export const AGENT_PROFILE_NAV = 'agent-profiles'
/** 管理审计页长度，与服务端固定分页保持一致。 */
export const AGENT_AUDIT_PAGE_SIZE = 50
/** 固定业务角色族；新增专业定义不需要新增角色。 */
export const AGENT_ROLE_LABEL = Object.freeze({ manga_director: '创作导演', script_parser: '分析专家',
  script_writer: '剧本编剧', storyboard_director: '分镜导演', continuity_reviewer: '连续性评审师' })
/** 管理员可配置的模型选择策略。 */
export const AGENT_MODEL_SELECTION_LABEL = Object.freeze({ USER_DEFAULT: '用户默认模型', USER_SELECTABLE: '用户可选模型', INHERIT_PARENT: '仅继承父模型（Worker）' })
/** 可配置预算字段与显示名。 */
export const AGENT_BUDGET_LABEL = Object.freeze({ maxSteps: 'Run 最大步骤数', maxDepth: '最大深度', maxParallel: '最大并行数',
  maxRepairRounds: '最大返修轮数', maxInvocations: '模型执行次数上限', maxReservedTurns: '累计推理轮数预留' })
/** 审计操作的业务说明。 */
export const AGENT_ACTION_LABEL = Object.freeze({ INITIALIZED: '初始化', CREATED: '创建', DRAFT_CREATED: '新建草稿',
  DRAFT_UPDATED: '修改草稿', PUBLISHED: '发布', ROLLED_BACK: '回滚', ENABLED: '启用', DISABLED: '禁用' })
/** 默认新专业配置不具有业务写入权限。 */
export function newAgentDefinition() {
  return { schemaVersion: 1, role: 'script_parser', displayName: '', description: '', systemPrompt: '',
    capabilities: ['get_project_overview', 'get_script_structure', 'get_chapter_content'], permissionPolicy: 'READ_ONLY', maxTurns: 8,
    model: { selection: 'USER_SELECTABLE', providers: [], models: [] },
    budget: { maxSteps: 32, maxDepth: 3, maxParallel: 3, maxRepairRounds: 2, maxInvocations: 24, maxReservedTurns: 256 } }
}
