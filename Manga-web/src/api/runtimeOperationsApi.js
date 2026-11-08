import { authorizedRequest } from './authorizedApi'

/** 管理员运维入口；归档导出可能包含用户内容，不写入浏览器持久化存储。 */
const OPERATIONS_ENDPOINT = '/admin/creative-operations'
export const OPERATIONS_NAV = 'creative-operations'
/** 运行状态、近 24 小时调用及供应商保护摘要。 */
export const getRuntimeOperations = (token) => authorizedRequest(token, OPERATIONS_ENDPOINT)
/** 账本按调用 ID 倒序分页，费用为空表示未知或未定价。 */
export const getRunModelUsage = (token, runId, beforeId) => authorizedRequest(token,
  `${OPERATIONS_ENDPOINT}/runs/${encodeURIComponent(runId)}/usage${beforeId ? `?beforeId=${beforeId}` : ''}`)
/** 明确核对账单后提交一次确认，不会触发供应商扣款。 */
export const settleModelCall = (token, callId, value) => authorizedRequest(token,
  `${OPERATIONS_ENDPOINT}/calls/${encodeURIComponent(callId)}/settlement`, { method: 'POST', body: JSON.stringify(value) })
