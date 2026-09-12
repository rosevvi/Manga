import { useEffect, useRef, useState } from 'react'
import { Bot, ChevronDown, LoaderCircle, Send, Sparkles, Square, X } from 'lucide-react'
import { cancelAgentRun, createAgentRun, getAgentConversations, getAgentMessages, getAgentRun, streamAgentEvents } from '../api/agentApi'
import { useLanguage } from '../i18n/LanguageContext'
import './manga-assistant.css'

const ACTIVE_RUN_KEY = 'manga.agent.activeRun'

function MangaAssistant({ accessToken, isGuest, projects, projectId }) {
  const { translate } = useLanguage()
  const [open, setOpen] = useState(false)
  const [boundProjectId, setBoundProjectId] = useState(projectId ?? '')
  const [conversationId, setConversationId] = useState(null)
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [run, setRun] = useState(null)
  const [error, setError] = useState('')
  const abortRef = useRef(null)
  const messagesEndRef = useRef(null)

  useEffect(() => { if (projectId) setBoundProjectId(String(projectId)) }, [projectId])
  useEffect(() => { messagesEndRef.current?.scrollIntoView({ block: 'end' }) }, [messages, open])

  useEffect(() => {
    if (!open || !accessToken || isGuest) return
    let cancelled = false
    const load = async () => {
      try {
        const conversations = await getAgentConversations(accessToken, boundProjectId || undefined)
        if (cancelled || conversations.length === 0) return
        const selected = conversations[0]
        const history = await getAgentMessages(accessToken, selected.conversationId)
        if (!cancelled) { setConversationId(selected.conversationId); setMessages(history) }
      } catch (requestError) { if (!cancelled) setError(requestError.message) }
    }
    load()
    return () => { cancelled = true }
  }, [open, accessToken, isGuest, boundProjectId])

  useEffect(() => {
    if (!open || !accessToken || isGuest || run) return
    const runId = window.sessionStorage.getItem(ACTIVE_RUN_KEY)
    if (!runId) return
    let cancelled = false
    const reconnect = async () => {
      try {
        const savedRun = await getAgentRun(accessToken, runId)
        if (cancelled || savedRun.status !== 'RUNNING') {
          window.sessionStorage.removeItem(ACTIVE_RUN_KEY)
          return
        }
        setRun(savedRun)
        await subscribe(savedRun)
      } catch {
        window.sessionStorage.removeItem(ACTIVE_RUN_KEY)
      }
    }
    reconnect()
    return () => { cancelled = true }
  }, [open, accessToken, isGuest, run])

  useEffect(() => () => abortRef.current?.abort(), [])

  const processEvent = (event) => {
    if (event.eventType === 'CONTENT') {
      const delta = event.payload?.delta || ''
      setMessages((current) => {
        const last = current.at(-1)
        if (last?.role === 'ASSISTANT' && last.pending) {
          return [...current.slice(0, -1), { ...last, content: last.content + delta }]
        }
        return [...current, { role: 'ASSISTANT', content: delta, pending: true, messageOrder: `stream-${event.sequenceNo}` }]
      })
    } else if (event.eventType === 'TOOL_CALL_STARTED') {
      setMessages((current) => [...current, { role: 'TOOL', content: event.payload?.toolName || 'get_project_context', messageOrder: `tool-${event.sequenceNo}` }])
    } else if (event.eventType === 'RUN_COMPLETED' || event.eventType === 'RUN_CANCELLED' || event.eventType === 'RUN_FAILED') {
      setMessages((current) => current.map((message) => message.pending ? { ...message, pending: false } : message))
      setRun(null)
      window.sessionStorage.removeItem(ACTIVE_RUN_KEY)
      if (event.eventType === 'RUN_FAILED') setError(event.payload?.message || translate('assistant.runFailed'))
    }
  }

  const subscribe = async (nextRun, afterSequence = 0) => {
    const controller = new AbortController()
    abortRef.current = controller
    try {
      await streamAgentEvents(accessToken, nextRun.runId, afterSequence, processEvent, controller.signal)
    } catch (requestError) {
      if (requestError.name !== 'AbortError') setError(requestError.message)
    } finally {
      if (!controller.signal.aborted) setRun(null)
    }
  }

  const submit = async (event) => {
    event.preventDefault()
    const message = input.trim()
    if (!message || run) return
    setError('')
    setInput('')
    try {
      const nextRun = await createAgentRun(accessToken, {
        conversationId,
        projectId: boundProjectId ? Number(boundProjectId) : null,
        message,
      })
      setConversationId(nextRun.conversationId)
      setRun(nextRun)
      setMessages((current) => [...current, { role: 'USER', content: message, messageOrder: `local-${Date.now()}` }])
      window.sessionStorage.setItem(ACTIVE_RUN_KEY, nextRun.runId)
      await subscribe(nextRun)
    } catch (requestError) { setError(requestError.message); setRun(null) }
  }

  const cancel = async () => {
    if (!run) return
    abortRef.current?.abort()
    try { await cancelAgentRun(accessToken, run.runId) } catch (requestError) { setError(requestError.message) }
    setRun(null)
    window.sessionStorage.removeItem(ACTIVE_RUN_KEY)
  }

  if (!accessToken || isGuest) return null
  return <aside className="manga-assistant" aria-label={translate('assistant.label')}>
    {open && <section className="manga-assistant-panel" role="dialog" aria-modal="false" aria-label={translate('assistant.title')}>
      <header>
        <div className="manga-assistant-title"><span><Bot size={17} /></span><div><strong>{translate('assistant.title')}</strong><small>{translate('assistant.subtitle')}</small></div></div>
        <button type="button" aria-label={translate('common.close')} title={translate('common.close')} onClick={() => setOpen(false)}><X size={17} /></button>
      </header>
      <label className="manga-assistant-project"><span>{translate('assistant.project')}</span><div><select value={boundProjectId} onChange={(event) => { setBoundProjectId(event.target.value); setConversationId(null); setMessages([]) }}><option value="">{translate('assistant.general')}</option>{projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}</select><ChevronDown size={14} /></div></label>
      <div className="manga-assistant-messages" aria-live="polite">
        {messages.length === 0 && <p className="manga-assistant-empty">{translate(boundProjectId ? 'assistant.projectEmpty' : 'assistant.generalEmpty')}</p>}
        {messages.map((message, index) => message.role === 'TOOL' ? <div className="manga-assistant-tool" key={`${message.messageOrder}-${index}`}><Sparkles size={13} />{translate('assistant.readingProject')}</div> : <article className={`manga-assistant-message ${message.role === 'USER' ? 'user' : 'assistant'}`} key={`${message.messageOrder}-${index}`}>{message.content || (message.pending ? <LoaderCircle className="spin" size={14} /> : '')}</article>)}
        <div ref={messagesEndRef} />
      </div>
      {error && <p className="manga-assistant-error" role="alert">{error}</p>}
      <form onSubmit={submit}><textarea rows="2" maxLength="12000" value={input} onChange={(event) => setInput(event.target.value)} placeholder={translate('assistant.placeholder')} disabled={Boolean(run)} />
        <div><span>{boundProjectId ? translate('assistant.boundProject') : translate('assistant.unbound')}</span>{run ? <button className="manga-assistant-stop" type="button" onClick={cancel} title={translate('assistant.stop')}><Square size={14} /></button> : <button className="manga-assistant-send" type="submit" disabled={!input.trim()} title={translate('assistant.send')}><Send size={15} /></button>}</div>
      </form>
    </section>}
    <button className="manga-assistant-launcher" type="button" aria-label={translate('assistant.open')} title={translate('assistant.open')} onClick={() => setOpen((value) => !value)}><Sparkles size={20} /></button>
  </aside>
}

export default MangaAssistant
