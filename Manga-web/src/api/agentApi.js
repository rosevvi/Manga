import { authorizedRequest } from './authorizedApi'
import { AUTH_CONFIG } from '../constants/auth'

const BASE_PATH = '/agent'

export function createAgentRun(accessToken, request) {
  return authorizedRequest(accessToken, `${BASE_PATH}/runs`, {
    method: 'POST', body: JSON.stringify(request),
  })
}

export function cancelAgentRun(accessToken, runId) {
  return authorizedRequest(accessToken, `${BASE_PATH}/runs/${encodeURIComponent(runId)}/cancel`, { method: 'POST' })
}

export function getAgentRun(accessToken, runId) {
  return authorizedRequest(accessToken, `${BASE_PATH}/runs/${encodeURIComponent(runId)}`)
}

export function getAgentConversations(accessToken, projectId) {
  const query = projectId ? `?projectId=${encodeURIComponent(projectId)}` : ''
  return authorizedRequest(accessToken, `${BASE_PATH}/conversations${query}`)
}

export function getAgentMessages(accessToken, conversationId) {
  return authorizedRequest(accessToken, `${BASE_PATH}/conversations/${encodeURIComponent(conversationId)}/messages`)
}

export async function streamAgentEvents(accessToken, runId, afterSequence, onEvent, signal) {
  const response = await fetch(`${AUTH_CONFIG.apiBaseUrl}${BASE_PATH}/runs/${encodeURIComponent(runId)}/events?afterSequence=${afterSequence}`, {
    headers: { Accept: 'text/event-stream', Authorization: `${AUTH_CONFIG.bearerType} ${accessToken}` },
    signal,
  })
  if (!response.ok || !response.body) {
    const payload = await response.json().catch(() => null)
    throw new Error(payload?.message ?? 'Unable to connect to Manga assistant')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  const dispatch = (block) => {
    const lines = block.split(/\r?\n/)
    const id = lines.find((line) => line.startsWith('id:'))?.slice(3).trim()
    const data = lines.filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trimStart()).join('\n')
    if (!data) return
    try { onEvent?.(JSON.parse(data), id) } catch { /* Ignore a malformed server event and keep the stream alive. */ }
  }
  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done })
    const blocks = buffer.replace(/\r\n/g, '\n').split('\n\n')
    buffer = blocks.pop() ?? ''
    blocks.forEach(dispatch)
    if (done) break
  }
  if (buffer.trim()) dispatch(buffer)
}
