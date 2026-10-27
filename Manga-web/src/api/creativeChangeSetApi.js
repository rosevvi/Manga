import { authorizedRequest } from './authorizedApi'

/** AI 提案与人工审批接口。 */
const CHANGE_SET_ENDPOINT = '/creative-change-sets'

/** 查询一个会话或一个手动运行的提案历史。 */
export function listCreativeChangeSets(accessToken, { conversationId, runId, beforeId }) {
  const query = new URLSearchParams()
  if (conversationId) query.set('conversationId', conversationId)
  if (runId) query.set('runId', runId)
  if (beforeId) query.set('beforeId', String(beforeId))
  return authorizedRequest(accessToken, `${CHANGE_SET_ENDPOINT}?${query}`)
}

/** 读取指定提案最新审批事实，用于已翻页卡片的定点刷新。 */
export function getCreativeChangeSet(accessToken, changeSetId) {
  return authorizedRequest(accessToken, `${CHANGE_SET_ENDPOINT}/${encodeURIComponent(changeSetId)}`)
}

/** 批准或拒绝冻结提案；服务端返回应用、冲突、拒绝或过期的实际结果。 */
export function decideCreativeChangeSet(accessToken, changeSetId, decision, comment) {
  return authorizedRequest(accessToken, `${CHANGE_SET_ENDPOINT}/${encodeURIComponent(changeSetId)}/decision`, {
    method: 'POST', body: JSON.stringify({ decision, comment: comment.trim() || null }),
  })
}
