import { AUTH_CONFIG } from '../constants/auth'
import { authorizedRequest } from './authorizedApi'
import { CREATIVE_OPERATION, CREATIVE_PERMISSION, CREATIVE_TRIGGER } from '../constants/creative'

/** Creative Run 的稳定后端路径。 */
export const CREATIVE_RUN_ENDPOINT = '/creative-runs'

/** 创建会话型 Creative Run。 */
export function createConversationRun(accessToken, request) {
  return authorizedRequest(accessToken, CREATIVE_RUN_ENDPOINT, {
    method: 'POST',
    body: JSON.stringify({
      requestId: request.requestId,
      triggerSource: CREATIVE_TRIGGER.CONVERSATION,
      operationType: CREATIVE_OPERATION.ASSISTANT_CHAT,
      conversationId: request.conversationId || null,
      projectId: request.projectId || null,
      target: null,
      permissionPolicy: request.projectId ? CREATIVE_PERMISSION.PROPOSE_CHANGES : CREATIVE_PERMISSION.READ_ONLY,
      instruction: request.instruction,
    }),
  })
}

/** 创建页面手动触发的 Creative Run。 */
export function createManualRun(accessToken, request) {
  return authorizedRequest(accessToken, CREATIVE_RUN_ENDPOINT, {
    method: 'POST',
    body: JSON.stringify({ ...request, triggerSource: CREATIVE_TRIGGER.MANUAL, conversationId: null }),
  })
}

/** 查询当前用户拥有的 Run 快照。 */
export function getCreativeRun(accessToken, runId) {
  return authorizedRequest(accessToken, `${CREATIVE_RUN_ENDPOINT}/${encodeURIComponent(runId)}`)
}

/** 请求取消当前用户拥有的 Run。 */
export function cancelCreativeRun(accessToken, runId) {
  return authorizedRequest(accessToken, `${CREATIVE_RUN_ENDPOINT}/${encodeURIComponent(runId)}/cancel`, {
    method: 'POST',
  })
}

/**
 * 通过带 Authorization 请求头的 fetch 消费 SSE；每个事件到达后先交给调用方持久化 cursor，
 * 网络断开时调用方可用最后 sequence 重新调用本函数而不会重复业务事件。
 */
export async function streamCreativeRunEvents(accessToken, runId, afterSequence, onEvent, signal) {
  const query = new URLSearchParams({ afterSequence: String(afterSequence || 0) })
  const response = await fetch(
    `${AUTH_CONFIG.apiBaseUrl}${CREATIVE_RUN_ENDPOINT}/${encodeURIComponent(runId)}/events?${query}`,
    { headers: { Authorization: `${AUTH_CONFIG.bearerType} ${accessToken}` }, signal },
  )
  if (!response.ok || !response.body) throw new Error('Unable to open Creative Run event stream')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value, { stream: !done }).replaceAll('\r\n', '\n')
    const frames = buffer.split('\n\n')
    buffer = frames.pop() || ''
    frames.forEach((frame) => {
      const data = frame.split('\n').filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trimStart()).join('\n')
      if (data) onEvent(JSON.parse(data))
    })
    if (done) break
  }
}
