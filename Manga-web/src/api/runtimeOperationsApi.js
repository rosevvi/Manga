import { authorizedRequest } from './authorizedApi'

/** 管理员运维入口；归档导出可能包含用户内容，不写入浏览器持久化存储。 */
const OPERATIONS_ENDPOINT = '/admin/creative-operations'
export const OPERATIONS_NAV = 'creative-operations'
/** 运行状态、近 24 小时调用及供应商保护摘要。 */
export const getRuntimeOperations = (token) => authorizedRequest(token, OPERATIONS_ENDPOINT)
/** 按调用 ID 倒序查询模型用量。 */
export const getRunModelUsage = (token, runId, beforeId) => authorizedRequest(token,
  `${OPERATIONS_ENDPOINT}/runs/${encodeURIComponent(runId)}/usage${beforeId ? `?beforeId=${beforeId}` : ''}`)
