import { authorizedRequest } from './authorizedApi'

/** 连续对话和消息投影的稳定后端路径。 */
export const CREATIVE_CONVERSATION_ENDPOINT = '/creative-conversations'

/** 分页查询认证用户的会话，可按项目过滤。 */
export function listCreativeConversations(accessToken, projectId, beforeId) {
  const query = new URLSearchParams()
  if (projectId) query.set('projectId', String(projectId))
  if (beforeId) query.set('beforeId', String(beforeId))
  return authorizedRequest(accessToken, `${CREATIVE_CONVERSATION_ENDPOINT}?${query}`)
}

/** 查询消息投影、工具状态和与之匹配的活动 Run cursor。 */
export function getCreativeConversationHistory(accessToken, conversationId, beforeId) {
  const query = new URLSearchParams()
  if (beforeId) query.set('beforeId', String(beforeId))
  return authorizedRequest(accessToken,
    `${CREATIVE_CONVERSATION_ENDPOINT}/${encodeURIComponent(conversationId)}/messages?${query}`)
}
