import { authorizedRequest } from './authorizedApi'

/** 专业 Agent 配置与公开发布目录路径。 */
const ADMIN_AGENT_ENDPOINT = '/admin/agent-profiles'
const CATALOG_ENDPOINT = '/agent-profiles'
/** 对动态标识进行 URL 编码。 */
const profilePath = (key) => `${ADMIN_AGENT_ENDPOINT}/${encodeURIComponent(key)}`
const versionPath = (key, version) => `${profilePath(key)}/versions/${version}`
/** 管理写入共用 JSON 请求协议。 */
const write = (token, path, value, method = 'POST') => authorizedRequest(token, path, { method, body: JSON.stringify(value) })

/** 当前启用的发布目录，不含 Prompt。 */
export const listAgentCatalog = (token) => authorizedRequest(token, CATALOG_ENDPOINT)
/** 管理员身份目录。 */
export const listAdminAgents = (token) => authorizedRequest(token, ADMIN_AGENT_ENDPOINT)
/** 当前指针、revision 与版本摘要。 */
export const getAdminAgent = (token, key) => authorizedRequest(token, profilePath(key))
/** 精确版本内容。 */
export const getAgentVersion = (token, key, version) => authorizedRequest(token, versionPath(key, version))
/** 已有业务能力白名单。 */
export const getAgentCapabilities = (token) => authorizedRequest(token, `${ADMIN_AGENT_ENDPOINT}/capabilities`)
/** 已接入对话模型协议。 */
export const getAgentModelProviders = (token) => authorizedRequest(token, `${ADMIN_AGENT_ENDPOINT}/model-providers`)
/** 创建身份与首个草稿。 */
export const createAgentProfile = (token, value) => write(token, ADMIN_AGENT_ENDPOINT, value)
/** 不改变发布指针的新草稿。 */
export const createAgentDraft = (token, key, value) => write(token, `${profilePath(key)}/versions`, value)
/** 只编辑尚未发布的版本。 */
export const updateAgentDraft = (token, key, version, value) => write(token, versionPath(key, version), value, 'PUT')
/** 校验并发布指定版本。 */
export const publishAgentVersion = (token, key, version, value) => write(token, `${versionPath(key, version)}/publish`, value)
/** 切回已经发布过的版本。 */
export const rollbackAgentVersion = (token, key, version, value) => write(token, `${versionPath(key, version)}/rollback`, value)
/** 启停只影响后续选择。 */
export const setAgentEnabled = (token, key, value) => write(token, `${profilePath(key)}/enabled`, value)
/** 无模型调用的编译预览。 */
export const previewAgentVersion = (token, key, version, value) => write(token, `${versionPath(key, version)}/preview`, value)
/** 明确启动一个有费用、只读的模型测试 Run。 */
export const testAgentVersion = (token, key, version, value) => write(token, `${versionPath(key, version)}/test`, value)
/** 审计游标分页。 */
export const getAgentAudits = (token, key, beforeId) => authorizedRequest(token,
  `${profilePath(key)}/audits${beforeId ? `?beforeId=${beforeId}` : ''}`)
