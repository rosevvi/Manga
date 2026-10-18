import { Bot, Send, Square, X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { cancelCreativeRun, createConversationRun, getCreativeRun, streamCreativeRunEvents } from '../api/creativeRunApi'
import './manga-assistant.css'

const STORAGE_PREFIX = 'manga.creative.run.'
const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED', 'CANCELLED'])

/** 为每个登录用户隔离浏览器端 Run 游标。 */
function storageKey(userId) {
  return `${STORAGE_PREFIX}${userId || 'anonymous'}`
}

/** 创建浏览器兼容的幂等请求标识。 */
function nextRequestId() {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/**
 * 全局 AI 漫剧助手。Run、conversationId、增量文本和 cursor 会写入 sessionStorage，
 * 页面刷新或 SSE 断线后从最后已处理 sequence 继续。
 */
export default function MangaAssistant({ accessToken, userId, projectId }) {
  const key = storageKey(userId)
  const restored = useRef(JSON.parse(sessionStorage.getItem(key) || 'null'))
  const cursorRef = useRef(restored.current?.lastSequence || 0)
  const stateRef = useRef(restored.current || null)
  const abortRef = useRef(null)
  const reconnectRef = useRef(null)
  const [open, setOpen] = useState(false)
  const [instruction, setInstruction] = useState('')
  const [content, setContent] = useState(restored.current?.content || '')
  const [status, setStatus] = useState(restored.current?.status || '')
  const [error, setError] = useState('')

  /** 原子更新内存与会话存储中的恢复快照。 */
  const persist = useCallback((patch) => {
    const next = { ...(stateRef.current || {}), ...patch }
    stateRef.current = next
    sessionStorage.setItem(key, JSON.stringify(next))
  }, [key])

  /** 应用一条严格递增事件；重复事件不会重复拼接文本。 */
  const applyEvent = useCallback((event) => {
    if (event.sequence <= cursorRef.current) return
    cursorRef.current = event.sequence
    let nextContent = stateRef.current?.content || ''
    if (event.eventType === 'CONTENT_DELTA') {
      nextContent += event.payload?.delta || ''
      setContent(nextContent)
    }
    const terminal = event.eventType === 'RUN_COMPLETED' ? 'COMPLETED'
      : event.eventType === 'RUN_FAILED' ? 'FAILED'
        : event.eventType === 'RUN_CANCELLED' ? 'CANCELLED' : null
    if (terminal) setStatus(terminal)
    persist({ lastSequence: event.sequence, content: nextContent, status: terminal || stateRef.current?.status })
  }, [persist])

  /** 从当前 cursor 连接 SSE；非终态断线会短暂退避后自动续连。 */
  const connect = useCallback(async (runId) => {
    abortRef.current?.abort()
    setError('')
    const controller = new AbortController()
    abortRef.current = controller
    try {
      await streamCreativeRunEvents(accessToken, runId, cursorRef.current, applyEvent, controller.signal)
    } catch (streamError) {
      if (controller.signal.aborted || TERMINAL_STATUSES.has(stateRef.current?.status)) return
      setError(streamError.message)
      reconnectRef.current = window.setTimeout(() => connect(runId), 1500)
    }
  }, [accessToken, applyEvent])

  /** 页面恢复时先查 Run 快照，再决定是否续连 SSE。 */
  useEffect(() => {
    const saved = stateRef.current
    if (!accessToken || !saved?.runId) return undefined
    getCreativeRun(accessToken, saved.runId).then((run) => {
      setStatus(run.status)
      persist({ status: run.status, conversationId: run.conversationId })
      if (!TERMINAL_STATUSES.has(run.status)) connect(run.runId)
    }).catch((restoreError) => setError(restoreError.message))
    return () => {
      abortRef.current?.abort()
      window.clearTimeout(reconnectRef.current)
    }
  }, [accessToken, connect, persist])

  /** 创建新 Run 并立即从零游标订阅。 */
  const submit = async (event) => {
    event.preventDefault()
    const prompt = instruction.trim()
    if (!prompt || !accessToken) return
    setError('')
    setContent('')
    setStatus('QUEUED')
    cursorRef.current = 0
    try {
      const run = await createConversationRun(accessToken, {
        requestId: nextRequestId(), instruction: prompt, projectId,
        conversationId: stateRef.current?.projectId === projectId ? stateRef.current?.conversationId : null,
      })
      persist({ runId: run.runId, conversationId: run.conversationId, projectId,
        lastSequence: 0, content: '', status: run.status })
      setInstruction('')
      connect(run.runId)
    } catch (requestError) {
      setStatus('FAILED')
      setError(requestError.message)
    }
  }

  /** 请求取消当前活动 Run。 */
  const cancel = async () => {
    if (!stateRef.current?.runId) return
    await cancelCreativeRun(accessToken, stateRef.current.runId)
    setStatus('CANCEL_REQUESTED')
    persist({ status: 'CANCEL_REQUESTED' })
  }

  if (!accessToken) return null
  return (
    <aside className={`manga-assistant ${open ? 'open' : ''}`}>
      <button className="assistant-toggle" type="button" onClick={() => setOpen((value) => !value)} aria-label="AI 漫剧助手">
        {open ? <X size={20} /> : <Bot size={22} />}
      </button>
      {open && <section className="assistant-panel">
        <header><div><strong>AI 漫剧助手</strong><span>{status || 'READY'}</span></div></header>
        <div className="assistant-output">{content || '告诉我你想创作或调整什么。'}{error && <small>{error}</small>}</div>
        <form onSubmit={submit}>
          <textarea value={instruction} onChange={(event) => setInstruction(event.target.value)} rows="3" maxLength="12000" placeholder="描述你的漫剧创作任务…" />
          <div>
            {!TERMINAL_STATUSES.has(status) && status && <button className="assistant-cancel" type="button" onClick={cancel}><Square size={14} />停止</button>}
            <button className="assistant-send" type="submit" disabled={!instruction.trim()}><Send size={15} />发送</button>
          </div>
        </form>
      </section>}
    </aside>
  )
}
