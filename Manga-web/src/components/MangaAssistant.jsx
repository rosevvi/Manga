import { Bot, Plus, Send, Square, X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { cancelCreativeRun, createConversationRun, streamCreativeRunEvents } from '../api/creativeRunApi'
import { getCreativeConversationHistory, listCreativeConversations } from '../api/creativeConversationApi'
import { CREATIVE_MESSAGE_ROLE, CREATIVE_MESSAGE_STATUS, CREATIVE_TOOL_LABEL, CREATIVE_TOOL_STATUS_LABEL } from '../constants/creative'
import './manga-assistant.css'

/** 网络重连间隔，单位：毫秒。 */
const RECONNECT_DELAY_MS = 1500
/** 合并 SSE 唤醒的间隔，避免每个文本片段都触发 HTTP 请求。 */
const REFRESH_DELAY_MS = 100
/** 活动运行补偿查询间隔，单位：毫秒。 */
const HISTORY_POLL_MS = 3000
/** 仅记录当前选择的会话标识，正文和运行状态始终从服务端恢复。 */
const SELECTION_STORAGE_PREFIX = 'manga.creative.conversation.'

/** 生成启动请求幂等标识。 */
function nextRequestId() {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/** 使用服务端消息投影展示连续对话；SSE 只唤醒刷新，不在本地拼接正文。 */
export default function MangaAssistant({ accessToken, userId, projectId }) {
  const selectionKey = `${SELECTION_STORAGE_PREFIX}${userId}.${projectId || 'all'}`
  const [open, setOpen] = useState(false)
  const [instruction, setInstruction] = useState('')
  const [conversations, setConversations] = useState([])
  const [hasMoreConversations, setHasMoreConversations] = useState(false)
  const [conversationId, setConversationId] = useState(null)
  const [messages, setMessages] = useState([])
  const [nextBeforeId, setNextBeforeId] = useState(null)
  const [activeRun, setActiveRun] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const selectedRef = useRef(null)
  const activeRef = useRef(null)
  const scopeRef = useRef(0)
  const historyVersionRef = useRef(0)
  const busy = submitting || Boolean(activeRun)

  /** 切换会话时隔离旧异步响应、消息和 SSE cursor。 */
  const selectConversation = useCallback((id) => {
    selectedRef.current = id
    activeRef.current = null
    historyVersionRef.current += 1
    setConversationId(id)
    setMessages([])
    setActiveRun(null)
    setNextBeforeId(null)
    setError('')
    try {
      if (id) sessionStorage.setItem(selectionKey, id)
      else sessionStorage.removeItem(selectionKey)
    } catch { /* 存储不可用时仍可直接查询服务端历史。 */ }
  }, [selectionKey])

  /** 查询会话列表，丢弃旧登录用户或项目的晚到响应。 */
  const refreshList = useCallback(async () => {
    const scope = scopeRef.current
    const list = await listCreativeConversations(accessToken, projectId)
    if (scope !== scopeRef.current) return []
    setConversations((current) => [...new Map([...current, ...list]
      .map((conversation) => [conversation.conversationId, conversation])).values()]
      .sort((left, right) => right.id - left.id))
    setHasMoreConversations(list.length > 0)
    return list
  }, [accessToken, projectId])

  /** 用消息快照替换当前正文；稳定投影键用于合并已加载的更早历史。 */
  const refreshHistory = useCallback(async (id) => {
    const version = ++historyVersionRef.current
    const snapshot = await getCreativeConversationHistory(accessToken, id)
    if (selectedRef.current !== id || version !== historyVersionRef.current) return null
    setMessages((previous) => {
      const firstId = snapshot.messages[0]?.id
      const earlier = firstId ? previous.filter((message) => message.id < firstId) : []
      const byKey = new Map([...earlier, ...snapshot.messages].map((message) => [message.projectionKey, message]))
      return [...byKey.values()].sort((left, right) => left.id - right.id)
    })
    setNextBeforeId((previous) => previous || snapshot.nextBeforeId)
    activeRef.current = snapshot.activeRun
    setActiveRun(snapshot.activeRun)
    setError('')
    setConversations((current) => current.some((conversation) => conversation.conversationId === id)
      ? current : [snapshot.conversation, ...current])
    return snapshot
  }, [accessToken])

  /** 用户或项目变化后，从服务端列表恢复最近会话，不读取临时本地正文。 */
  useEffect(() => {
    const scope = ++scopeRef.current
    let savedConversationId
    try { savedConversationId = sessionStorage.getItem(selectionKey) }
    catch { savedConversationId = null }
    selectConversation(null)
    setConversations([])
    setSubmitting(false)
    setInstruction('')
    if (!accessToken) return undefined
    setLoading(true)
    refreshList().then((list) => {
      if (scope === scopeRef.current) selectConversation(savedConversationId || list[0]?.conversationId || null)
    }).catch((failure) => {
      if (scope === scopeRef.current) setError(failure.message)
    }).finally(() => {
      if (scope === scopeRef.current) setLoading(false)
    })
    return () => { scopeRef.current += 1 }
  }, [accessToken, userId, projectId, refreshList, selectConversation, selectionKey])

  /** 每次选择会话都读取完整投影和活动 Run 状态。 */
  useEffect(() => {
    if (!conversationId || !accessToken) return
    setLoading(true)
    refreshHistory(conversationId).catch((failure) => {
      if (selectedRef.current === conversationId) setError(failure.message)
    }).finally(() => {
      if (selectedRef.current === conversationId) setLoading(false)
    })
  }, [accessToken, conversationId, refreshHistory])

  /** 按投影 cursor 续接 SSE；刷新串行合并，断线和正常提前关闭都能重连。 */
  useEffect(() => {
    const runId = activeRun?.runId
    if (!runId || !conversationId) return undefined
    let stopped = false
    let controller
    let reconnectTimer
    let refreshTimer
    let refreshing = false
    let dirty = false
    const refresh = async () => {
      dirty = true
      if (refreshing || stopped) return
      refreshing = true
      try {
        while (dirty && !stopped) {
          dirty = false
          await refreshHistory(conversationId)
        }
      } catch (failure) {
        if (!stopped) setError(failure.message)
      } finally { refreshing = false }
    }
    const connect = async () => {
      if (stopped || activeRef.current?.runId !== runId) return
      controller = new AbortController()
      try {
        await streamCreativeRunEvents(accessToken, runId, activeRef.current.projectedThroughSequence,
          () => {
            if (!refreshTimer) refreshTimer = window.setTimeout(() => {
              refreshTimer = null
              refresh()
            }, REFRESH_DELAY_MS)
          }, controller.signal)
      } catch (failure) {
        if (!stopped && !controller.signal.aborted) setError(failure.message)
      }
      if (stopped) return
      await refresh()
      if (!stopped && activeRef.current?.runId === runId) {
        reconnectTimer = window.setTimeout(connect, RECONNECT_DELAY_MS)
      }
    }
    connect()
    const pollTimer = window.setInterval(refresh, HISTORY_POLL_MS)
    return () => {
      stopped = true
      controller?.abort()
      window.clearTimeout(reconnectTimer)
      window.clearTimeout(refreshTimer)
      window.clearInterval(pollTimer)
    }
  }, [accessToken, activeRun?.runId, conversationId, refreshHistory])

  /** 新增一轮真实 Director 对话，正文随后从服务端 Message 恢复。 */
  const submit = async (event) => {
    event.preventDefault()
    const prompt = instruction.trim()
    if (!prompt || !accessToken || busy) return
    const scope = scopeRef.current
    setSubmitting(true)
    setError('')
    try {
      const run = await createConversationRun(accessToken, {
        requestId: nextRequestId(), instruction: prompt, projectId, conversationId,
      })
      if (scope !== scopeRef.current) return
      setInstruction('')
      if (selectedRef.current !== run.conversationId) selectConversation(run.conversationId)
      await refreshHistory(run.conversationId)
      await refreshList()
    } catch (failure) {
      if (scope === scopeRef.current) setError(failure.message)
    } finally {
      if (scope === scopeRef.current) setSubmitting(false)
    }
  }

  /** 按历史主键游标加载更早消息，与实时投影按稳定键去重合并。 */
  const loadEarlier = async () => {
    if (!nextBeforeId || !conversationId || loading) return
    const id = conversationId
    setLoading(true)
    try {
      const snapshot = await getCreativeConversationHistory(accessToken, id, nextBeforeId)
      if (selectedRef.current !== id) return
      setMessages((current) => {
        const merged = new Map([...snapshot.messages, ...current].map((message) => [message.projectionKey, message]))
        return [...merged.values()].sort((left, right) => left.id - right.id)
      })
      setNextBeforeId(snapshot.nextBeforeId)
    } catch (failure) { if (selectedRef.current === id) setError(failure.message) }
    finally { if (selectedRef.current === id) setLoading(false) }
  }

  /** 加载较早会话，避免下拉列表只覆盖服务端第一页。 */
  const loadMoreConversations = async () => {
    const beforeId = conversations.at(-1)?.id
    if (!beforeId || loading) return
    const scope = scopeRef.current
    setLoading(true)
    try {
      const page = await listCreativeConversations(accessToken, projectId, beforeId)
      if (scope !== scopeRef.current) return
      setConversations((current) => [...new Map([...current, ...page]
        .map((conversation) => [conversation.conversationId, conversation])).values()]
        .sort((left, right) => right.id - left.id))
      setHasMoreConversations(page.length > 0)
    } catch (failure) {
      if (scope === scopeRef.current) setError(failure.message)
    } finally {
      if (scope === scopeRef.current) setLoading(false)
    }
  }

  /** 请求取消后，从投影读取实际状态，不在本地伪造终态。 */
  const cancel = async () => {
    if (!activeRun) return
    try {
      await cancelCreativeRun(accessToken, activeRun.runId)
      await refreshHistory(conversationId)
    } catch (failure) { setError(failure.message) }
  }

  if (!accessToken) return null
  return (
    <aside className={`manga-assistant ${open ? 'open' : ''}`}>
      <button className="assistant-toggle" type="button" onClick={() => setOpen((value) => !value)} aria-label="AI 漫剧助手">
        {open ? <X size={20} /> : <Bot size={22} />}
      </button>
      {open && <section className="assistant-panel">
        <header><div><strong>AI 漫剧助手</strong><span>{activeRun?.status || '就绪'}</span></div></header>
        <div className="assistant-conversations">
          <select aria-label="选择历史会话" value={conversationId || ''} disabled={submitting}
            onChange={(event) => selectConversation(event.target.value || null)}>
            <option value="">新会话</option>
            {conversations.map((conversation) => <option key={conversation.conversationId} value={conversation.conversationId}>{conversation.title}</option>)}
          </select>
          {hasMoreConversations && <button type="button" disabled={loading || submitting} onClick={loadMoreConversations}>更多</button>}
          <button type="button" disabled={submitting} onClick={() => selectConversation(null)}><Plus size={14} />新会话</button>
        </div>
        <div className="assistant-output" aria-live="polite">
          {nextBeforeId && <button className="assistant-history-more" type="button" disabled={loading} onClick={loadEarlier}>加载更早消息</button>}
          {!messages.length && (loading ? '正在加载会话…' : '告诉我你想创作或调整什么。')}
          {messages.map((message) => message.role === CREATIVE_MESSAGE_ROLE.TOOL
            ? <div className="assistant-tool" key={message.projectionKey}>{CREATIVE_TOOL_LABEL[message.toolName] || '读取项目资料'} · {CREATIVE_TOOL_STATUS_LABEL[message.toolStatus] || message.toolStatus}</div>
            : <article className={`assistant-message ${message.role.toLowerCase()}`} key={message.projectionKey}>
              <strong>{message.role === CREATIVE_MESSAGE_ROLE.USER ? '你' : '漫剧助手'}</strong>
              <p>{message.content || (message.status === CREATIVE_MESSAGE_STATUS.ACTIVE ? '正在思考…' : '本轮未生成回答。')}</p>
              {message.role === CREATIVE_MESSAGE_ROLE.ASSISTANT && message.status === CREATIVE_MESSAGE_STATUS.FAILED && <small>本轮执行失败，可以重新发送。</small>}
              {message.role === CREATIVE_MESSAGE_ROLE.ASSISTANT && message.status === CREATIVE_MESSAGE_STATUS.CANCELLED && <small>本轮已停止。</small>}
            </article>)}
          {error && <small>{error}</small>}
        </div>
        <form onSubmit={submit}>
          <textarea value={instruction} onChange={(event) => setInstruction(event.target.value)}
            rows="3" maxLength="12000" placeholder="描述你的漫剧创作任务…" />
          <div>
            {activeRun && <button className="assistant-cancel" type="button" onClick={cancel}><Square size={14} />停止</button>}
            <button className="assistant-send" type="submit" disabled={!instruction.trim() || busy || loading}><Send size={15} />发送</button>
          </div>
        </form>
      </section>}
    </aside>
  )
}
