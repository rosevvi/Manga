import { Bot, Plus, Send, X, MessageSquare, ListTodo } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { createConversationRun, listProjectCreativeRuns } from '../api/creativeRunApi'
import { getCreativeConversationHistory, listCreativeConversations } from '../api/creativeConversationApi'
import { CREATIVE_MESSAGE_ROLE, CREATIVE_MESSAGE_STATUS } from '../constants/creative'
import './manga-assistant.css'
import './creative-workbench.css'
import CreativeRunDetails from './CreativeRunDetails'
import WorkspaceSelect from './WorkspaceSelect'
import { OPEN_CREATIVE_TASK, RUN_STATUS_LABEL, creativeTaskName } from './creativeTaskEvents'
import { listAgentCatalog } from '../api/agentProfileApi'
import { CREATIVE_PERMISSION } from '../constants/creative'

/** 仅保存当前会话选择，不保存正文和密钥。 */
const SELECTION_STORAGE_PREFIX = 'manga.creative.conversation.'

/** 生成启动请求幂等标识。 */
function nextRequestId() {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/** 使用服务端消息投影展示连续对话；SSE 只唤醒刷新，不在本地拼接正文。 */
export default function MangaAssistant({ accessToken, userId, projectId }) {
  const [open, setOpen] = useState(false)
  const [hasOpened, setHasOpened] = useState(false)
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
  const [agentCatalog, setAgentCatalog] = useState([])
  const [selectedAgent, setSelectedAgent] = useState('')
  const [tab, setTab] = useState('conversations')
  const [projectOnly, setProjectOnly] = useState(true)
  const [tasks, setTasks] = useState([])
  const [taskCursor, setTaskCursor] = useState(null)
  const [selectedTask, setSelectedTask] = useState(null)
  const [taskListVersion, setTaskListVersion] = useState(0)
  const [taskMoreLoading, setTaskMoreLoading] = useState(false)
  const taskSelectRef = useRef(null)
  const taskListScope = useRef(0)
  const taskHistoryLoaded = useRef(false)
  const selectedRef = useRef(null)
  const activeRef = useRef(null)
  const scopeRef = useRef(0)
  const historyVersionRef = useRef(0)
  const busy = submitting || Boolean(activeRun)
  const conversationProjectId = conversations.find((item) => item.conversationId === conversationId)?.projectId || projectId
  const filterProject = projectOnly ? projectId : null
  const selectionKey = `${SELECTION_STORAGE_PREFIX}${userId}.${filterProject || 'all'}`

  /** 页面发起任务后定位到同一个任务中心；跨项目任务也可按已授权 Run ID 查询。 */
  useEffect(() => {
    const show = (event) => {
      const id = event.detail?.runId
      setHasOpened(true); setOpen(true); setTab('tasks')
      try { sessionStorage.setItem(`${selectionKey}.tab`, 'tasks') } catch { /* 仅保存选择。 */ }
      if (id) {
        taskSelectRef.current = id; setSelectedTask(id)
        try { sessionStorage.setItem(`${selectionKey}.run`, id) } catch { /* 仅保存选择。 */ }
      }
      setTaskListVersion((current) => current + 1)
    }
    window.addEventListener(OPEN_CREATIVE_TASK, show)
    try {
      const id = sessionStorage.getItem(`${selectionKey}.run`); taskSelectRef.current = id; setSelectedTask(id)
      setTab(sessionStorage.getItem(`${selectionKey}.tab`) === 'tasks' ? 'tasks' : 'conversations')
    }
    catch { /* 服务端列表仍可恢复。 */ }
    return () => window.removeEventListener(OPEN_CREATIVE_TASK, show)
  }, [selectionKey])

  /** 任务目录使用主键游标；只有打开中心时轮询目录，查询串行执行。 */
  useEffect(() => {
    if (!open || !accessToken) return undefined
    const expected = ++taskListScope.current
    let stopped = false
    let timer
    setTasks([]); setTaskCursor(null); setTaskMoreLoading(false); taskHistoryLoaded.current = false
    const refresh = async () => {
      try {
        const page = await listProjectCreativeRuns(accessToken, filterProject)
        if (stopped) return
        const incoming = page.activeRun ? [page.activeRun, ...page.items] : page.items
        setTasks((previous) => [...new Map([...previous, ...incoming].map((item) => [item.runId, item])).values()].sort((a, b) => b.id - a.id))
        if (!taskHistoryLoaded.current) setTaskCursor(page.nextBeforeId)
        if (!taskSelectRef.current) { taskSelectRef.current = page.activeRun?.runId || page.items[0]?.runId; setSelectedTask(taskSelectRef.current) }
        timer = window.setTimeout(refresh, page.activeRun ? 5000 : 30000)
      } catch (failure) {
        if (!stopped) { setError(failure.message); timer = window.setTimeout(refresh, 5000) }
      }
    }
    void refresh()
    return () => { stopped = true; if (taskListScope.current === expected) taskListScope.current += 1; window.clearTimeout(timer) }
  }, [accessToken, filterProject, open, taskListVersion])

  const selectTask = (id) => {
    taskSelectRef.current = id; setSelectedTask(id); setTab('tasks')
    try { sessionStorage.setItem(`${selectionKey}.run`, id); sessionStorage.setItem(`${selectionKey}.tab`, 'tasks') } catch { /* 仅保存选择。 */ }
  }

  const chooseTab = (next) => {
    setTab(next)
    try { sessionStorage.setItem(`${selectionKey}.tab`, next) } catch { /* 服务端状态仍可恢复。 */ }
  }

  const moreTasks = async () => {
    if (!taskCursor || taskMoreLoading) return
    const expected = taskListScope.current
    setTaskMoreLoading(true)
    try {
      const page = await listProjectCreativeRuns(accessToken, filterProject, taskCursor)
      if (taskListScope.current !== expected) return
      taskHistoryLoaded.current = true
      setTasks((current) => [...new Map([...current, ...page.items].map((item) => [item.runId, item])).values()].sort((a, b) => b.id - a.id))
      setTaskCursor(page.nextBeforeId)
    } catch (failure) { if (taskListScope.current === expected) setError(failure.message) }
    finally { if (taskListScope.current === expected) setTaskMoreLoading(false) }
  }

  /** 弹层约束焦点并支持 Escape，关闭后释放所有执行详情订阅。 */
  const dialogRef = useRef(null)
  useEffect(() => {
    if (!open) return undefined
    const previous = document.activeElement
    dialogRef.current?.focus()
    const keydown = (event) => {
      if (event.key === 'Escape') setOpen(false)
      if (event.key !== 'Tab') return
      const elements = [...dialogRef.current.querySelectorAll('button:not(:disabled),textarea,input,[tabindex="0"]')]
      const first = elements[0]; const last = elements.at(-1)
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
    }
    document.addEventListener('keydown', keydown)
    const oldOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.removeEventListener('keydown', keydown); document.body.style.overflow = oldOverflow; previous?.focus() }
  }, [open])

  /** 打开会话时重新读取发布目录，管理员启停和发布不依赖前端本地缓存。 */
  useEffect(() => {
    if (!open || !accessToken) return undefined
    let stopped = false
    listAgentCatalog(accessToken).then((items) => { if (!stopped) setAgentCatalog(items.filter((item) => item.rootSelectable)) })
      .catch((failure) => { if (!stopped) setError(failure.message) })
    return () => { stopped = true }
  }, [accessToken, open])

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
    const list = await listCreativeConversations(accessToken, filterProject)
    if (scope !== scopeRef.current) return []
    setConversations((current) => [...new Map([...current, ...list]
      .map((conversation) => [conversation.conversationId, conversation])).values()]
      .sort((left, right) => right.id - left.id))
    setHasMoreConversations(list.length > 0)
    return list
  }, [accessToken, filterProject])

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

  /** 首次展开后恢复会话，切换项目时隔离草稿和服务端状态。 */
  useEffect(() => {
    if (!hasOpened || !accessToken) return undefined
    const scope = ++scopeRef.current
    let savedConversationId
    try { savedConversationId = sessionStorage.getItem(selectionKey) }
    catch { savedConversationId = null }
    selectConversation(null)
    setConversations([])
    setSubmitting(false)
    setInstruction('')
    setLoading(true)
    refreshList().then((list) => {
      if (scope === scopeRef.current) selectConversation(savedConversationId || list[0]?.conversationId || null)
    }).catch((failure) => {
      if (scope === scopeRef.current) setError(failure.message)
    }).finally(() => {
      if (scope === scopeRef.current) setLoading(false)
    })
    return () => { scopeRef.current += 1 }
  }, [accessToken, hasOpened, refreshList, selectConversation, selectionKey])

  /** 每次选择会话都读取完整投影和活动 Run 状态。 */
  useEffect(() => {
    if (!open || tab !== 'conversations' || !conversationId || !accessToken) return
    setLoading(true)
    refreshHistory(conversationId).catch((failure) => {
      if (selectedRef.current === conversationId) setError(failure.message)
    }).finally(() => {
      if (selectedRef.current === conversationId) setLoading(false)
    })
  }, [accessToken, conversationId, open, refreshHistory, tab])

  /** 新建一轮真实对话，所有正文从服务端 Message 投影读取。 */
  const submit = async (event) => {
    event.preventDefault()
    const prompt = instruction.trim()
    if (!prompt || !accessToken || busy) return
    const scope = scopeRef.current
    setSubmitting(true)
    setError('')
    try {
      const run = await createConversationRun(accessToken, {
        requestId: nextRequestId(), instruction: prompt,
        projectId: conversationProjectId,
        conversationId,
        agentProfileId: selectedAgent || null,
        permissionPolicy: selectedAgent ? agentCatalog.find((item) => item.profileId === selectedAgent)?.permissionPolicy
          : conversationProjectId ? CREATIVE_PERMISSION.PROPOSE_CHANGES : CREATIVE_PERMISSION.READ_ONLY,
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
      const page = await listCreativeConversations(accessToken, filterProject, beforeId)
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
  if (!accessToken) return null
  const detailRunId = activeRun?.runId || messages.at(-1)?.runId
  return <aside className={`manga-assistant ${open ? 'open' : ''}`}>
    <button className="assistant-toggle" type="button" onClick={() => { setHasOpened(true); setOpen((value) => !value) }} aria-label="打开 AI 创作任务中心">
      {open ? <X size={20} /> : <Bot size={22} />}
    </button>
    {open && <div className="task-center-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setOpen(false) }}>
      <section className="task-center" role="dialog" aria-modal="true" aria-labelledby="task-center-title" tabIndex={-1} ref={dialogRef}>
        <header className="task-center-header"><div><span className="task-center-brand"><Bot size={20} /></span><div><h1 id="task-center-title">AI 创作任务中心</h1><small>漫剧助手 · 创作任务 · 执行记录</small></div></div>
          <div>{projectId && <label className="task-filter"><input type="checkbox" checked={projectOnly} onChange={(event) => setProjectOnly(event.target.checked)} />当前项目</label>}
            <button type="button" className="workbench-icon-button" aria-label="关闭任务中心" onClick={() => setOpen(false)}><X size={20} /></button></div>
        </header>
        <div className="task-center-body">
          <nav className="task-center-sidebar" aria-label="创作目录">
            <div className="workbench-tabs"><button type="button" className={tab === 'conversations' ? 'active' : ''} onClick={() => chooseTab('conversations')}><MessageSquare size={14} />会话</button>
              <button type="button" className={tab === 'tasks' ? 'active' : ''} onClick={() => chooseTab('tasks')}><ListTodo size={14} />创作任务</button></div>
            {tab === 'conversations' ? <>
              <button type="button" className="task-new" disabled={submitting} onClick={() => selectConversation(null)}><Plus size={15} />发起新会话</button>
              <div className="task-directory">{conversations.map((conversation) => <button key={conversation.conversationId} type="button"
                className={conversationId === conversation.conversationId ? 'selected' : ''} onClick={() => selectConversation(conversation.conversationId)}>
                <MessageSquare size={15} /><span><strong>{conversation.title}</strong><small>{conversation.updatedAt?.replace('T', ' ').slice(0, 16) || '创作对话'}</small></span></button>)}
                {!conversations.length && <p className="workbench-muted">创建会话，开始你的故事。</p>}</div>
              {hasMoreConversations && <button type="button" className="task-load-more" disabled={loading} onClick={loadMoreConversations}>加载更早会话</button>}
            </> : <>
              <span className="directory-caption">进行中</span><div className="task-directory">{tasks.filter((task) => !['COMPLETED', 'FAILED', 'CANCELLED'].includes(task.status)).map((task) => <TaskDirectoryItem key={task.runId} task={task} selected={selectedTask} onSelect={selectTask} />)}</div>
              <span className="directory-caption">历史记录</span><div className="task-directory">{tasks.filter((task) => ['COMPLETED', 'FAILED', 'CANCELLED'].includes(task.status)).map((task) => <TaskDirectoryItem key={task.runId} task={task} selected={selectedTask} onSelect={selectTask} />)}</div>
              {!tasks.length && <p className="workbench-muted">还没有创作任务。</p>}
              {taskCursor && <button type="button" className="task-load-more" disabled={taskMoreLoading} onClick={moreTasks}>加载更早任务</button>}
            </>}
          </nav>
          <main className="task-center-main">
            {tab === 'tasks' ? selectedTask
              ? <div className="task-center-scroll"><CreativeRunDetails key={selectedTask} accessToken={accessToken} runId={selectedTask} onRunChanged={(id) => { selectTask(id); setTaskListVersion((value) => value + 1) }} /></div>
              : <div className="workbench-empty"><Bot size={36} /><h2>每一步创作，都有迹可循</h2><p>从左侧选择任务，查看 Agent、工具调用、审批和生成结果。</p></div>
              : <>
                <div className="task-conversation-header"><div><h2>{conversations.find((item) => item.conversationId === conversationId)?.title || '新的创作对话'}</h2><small>{busy ? '正在创作，执行状态可在下方展开查看' : '从一个灵感，走向完整的故事'}</small></div>
                  <WorkspaceSelect label="选择专业 Agent" disabled={busy || loading} value={selectedAgent} onChange={setSelectedAgent}
                    options={[{ value: '', label: '平台创作导演' }, ...agentCatalog.map((agent) => ({ value: agent.profileId, label: `${agent.displayName} · v${agent.version}` }))]} /></div>
                <div className="task-center-scroll assistant-output" aria-live="polite">
                  {nextBeforeId && <button className="task-load-more" type="button" disabled={loading} onClick={loadEarlier}>加载更早消息</button>}
                  {!messages.length && <div className="workbench-empty"><Bot size={36} /><h2>让故事开始发生</h2><p>{loading ? '正在恢复会话…' : '描述你的故事、角色或想调整的章节。'}</p></div>}
                  {messages.filter((message) => message.role !== CREATIVE_MESSAGE_ROLE.TOOL).map((message) => <article className={`assistant-message ${message.role.toLowerCase()}`} key={message.projectionKey}>
                    <strong>{message.role === CREATIVE_MESSAGE_ROLE.USER ? '你' : '漫剧助手'}</strong>
                    <p>{message.content || (message.status === CREATIVE_MESSAGE_STATUS.ACTIVE ? '正在创作…' : '本轮未生成回答。')}</p>
                    {message.role === CREATIVE_MESSAGE_ROLE.ASSISTANT && <button type="button" className="message-execution-link" onClick={() => selectTask(message.runId)}>查看本轮执行详情</button>}
                  </article>)}
                  {detailRunId && <CreativeRunDetails key={detailRunId} accessToken={accessToken} runId={detailRunId} showText={false}
                    onChanged={() => { if (selectedRef.current === conversationId) void refreshHistory(conversationId).catch((failure) => {
                      if (selectedRef.current === conversationId) setError(failure.message)
                    }) }} onRunChanged={selectTask}
                    onRegenerate={setInstruction} />}
                </div>
                <form className="task-composer" onSubmit={submit}><textarea aria-label="创作需求" value={instruction} onChange={(event) => setInstruction(event.target.value)} rows={3} maxLength={12000} placeholder="描述你的漫剧创作任务…" />
                  <div><small>业务内容先预览，经你批准后保存</small><button className="workspace-primary" type="submit" disabled={!instruction.trim() || busy || loading}><Send size={15} />发送</button></div></form>
              </>}
            {error && <div className="workspace-alert" role="alert">{error}</div>}
          </main>
        </div>
      </section>
    </div>}
  </aside>
}

function TaskDirectoryItem({ task, selected, onSelect }) {
  return <button type="button" className={selected === task.runId ? 'selected' : ''} onClick={() => onSelect(task.runId)}>
    <span className={`task-dot ${task.status.toLowerCase()}`} /><span><strong>{creativeTaskName(task)}</strong><small>{RUN_STATUS_LABEL[task.status] || task.status} · {task.createdAt?.slice(5, 16).replace('T', ' ')}</small></span>
  </button>
}
