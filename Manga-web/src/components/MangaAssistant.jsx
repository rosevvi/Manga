import { ArrowLeft, Bot, ChevronDown, ChevronRight, Folder, Hand, ListTodo, MessageSquare, Plus, Send, ShieldAlert, ShieldCheck, Square, SquarePen, X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { cancelCreativeRun, createConversationRun } from '../api/creativeRunApi'
import { getCreativeConversationHistory, listCreativeConversations } from '../api/creativeConversationApi'
import { listAssistantModels } from '../api/agentProfileApi'
import { CREATIVE_MESSAGE_ROLE, CREATIVE_PERMISSION } from '../constants/creative'
import { CREATIVE_TERMINAL_STATUSES } from '../constants/changeSet'
import { OPEN_CREATIVE_TASK } from './creativeTaskEvents'
import AssistantMarkdown, { AssistantThinking } from './AssistantMarkdown'
import CreativeRunDetails, { ConversationTurnHistory } from './CreativeRunDetails'
import WorkspaceSelect from './WorkspaceSelect'
import ProjectTaskConversation from './ProjectTaskConversation'
import useCreativeTaskActivity from './useCreativeTaskActivity'
import './manga-assistant.css'
import './creative-workbench.css'

const SELECTION_STORAGE_PREFIX = 'manga.creative.conversation.'
const APPROVAL_MODE = Object.freeze({ ASK: 'ASK', ASSISTED: 'ASSISTED', FULL: 'FULL' })
const APPROVAL_OPTIONS = [
  { value: APPROVAL_MODE.ASK, label: '请求批准', icon: Hand, description: '保存章节、分镜或提交生成前始终询问' },
  { value: APPROVAL_MODE.ASSISTED, label: '帮我批准', icon: ShieldCheck, description: '新增内容自动应用，覆盖章节或媒体生成时询问' },
  { value: APPROVAL_MODE.FULL, label: '完全访问权限', icon: ShieldAlert, danger: true, description: '本轮自动执行当前项目的变更及媒体生成，保留修订确认' },
]

/** 项目管理助手按项目组织会话，执行详情、审批与产物都在同一对话框里。 */
export default function MangaAssistant({ accessToken, userId, projectId, projects = [], onOpened }) {
  const [open, setOpen] = useState(false)
  const [instruction, setInstruction] = useState('')
  const [conversations, setConversations] = useState([])
  const [hasMore, setHasMore] = useState(false)
  const [conversationId, setConversationId] = useState(null)
  const [workspace, setWorkspace] = useState(undefined)
  const [taskProjectId, setTaskProjectId] = useState(null)
  const [activityVersion, setActivityVersion] = useState(0)
  const [lastRunId, setLastRunId] = useState(null)
  const [linkedRunId, setLinkedRunId] = useState(null)
  const [historyRunId, setHistoryRunId] = useState(null)
  const [linkedSnapshot, setLinkedSnapshot] = useState(null)
  const [messages, setMessages] = useState([])
  const [nextBeforeId, setNextBeforeId] = useState(null)
  const [activeRun, setActiveRun] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [modelOptions, setModelOptions] = useState([])
  const [selectedModel, setSelectedModel] = useState('')
  const [approvalMode, setApprovalMode] = useState(APPROVAL_MODE.ASK)
  const [stopRequested, setStopRequested] = useState(null)
  const selectedRef = useRef(null)
  const pagingMessages = useRef(false)
  const scopeRef = useRef(0)
  const historyVersion = useRef(0)
  const dialogRef = useRef(null)
  const selectionKey = SELECTION_STORAGE_PREFIX + userId
  const hasBackgroundTasks = useCreativeTaskActivity(accessToken, activityVersion)
  const conversation = conversations.find((item) => item.conversationId === conversationId)
  const linkedRun = linkedSnapshot?.runId === linkedRunId ? linkedSnapshot : null
  const workspaceId = linkedRun ? linkedRun.projectId
    : conversation ? conversation.projectId : workspace === undefined ? projectId : workspace
  const focusedRun = linkedRunId ? linkedRun : activeRun
  const executing = focusedRun && !CREATIVE_TERMINAL_STATUSES.has(focusedRun.status)
  const busy = submitting || Boolean(executing) || Boolean(linkedRunId && !linkedRun)

  /** 选择会话或新草稿，丢弃上一会话的在途查询。 */
  const selectConversation = useCallback((id, boundProject) => {
    selectedRef.current = id
    pagingMessages.current = false
    historyVersion.current += 1
    setConversationId(id)
    setTaskProjectId(null)
    if (boundProject !== undefined) setWorkspace(boundProject)
    setLinkedRunId(null)
    setHistoryRunId(null)
    setLastRunId(null)
    setMessages([])
    setActiveRun(null)
    setNextBeforeId(null)
    setApprovalMode(APPROVAL_MODE.ASK)
    setLoading(false)
    setError('')
    try {
      sessionStorage.removeItem(`${selectionKey}.operation`)
      sessionStorage.removeItem(`${selectionKey}.projectTasks`)
      if (id) sessionStorage.setItem(selectionKey, id)
      else sessionStorage.removeItem(selectionKey)
    } catch { /* 浏览器禁用会话存储时仍可打开历史。 */ }
  }, [selectionKey])

  /** 保存页面操作的执行引用，刷新后可以恢复同一个审批或结果。 */
  const showOperation = useCallback((id) => {
    setLinkedRunId(id)
    try { sessionStorage.setItem(`${selectionKey}.operation`, id) } catch { /* 仅保存执行引用。 */ }
  }, [selectionKey])

  /** 定位同一个项目任务会话，保留页面直接打开的执行引用。 */
  const openProjectTasks = useCallback((id, runId) => {
    selectConversation(null, id)
    setTaskProjectId(id)
    try { sessionStorage.setItem(`${selectionKey}.projectTasks`, String(id)) } catch { /* 仍可通过项目目录恢复。 */ }
    if (runId) showOperation(runId)
  }, [selectConversation, selectionKey, showOperation])

  useEffect(() => {
    const show = ({ detail }) => {
      if (detail?.projectId != null) openProjectTasks(detail.projectId, detail.runId)
      else {
        selectConversation(null)
        if (detail?.runId) showOperation(detail.runId)
      }
      setOpen(true)
    }
    window.addEventListener(OPEN_CREATIVE_TASK, show)
    return () => window.removeEventListener(OPEN_CREATIVE_TASK, show)
  }, [openProjectTasks, selectConversation, showOperation])

  const refreshList = useCallback(async (beforeId) => {
    const scope = scopeRef.current
    const page = await listCreativeConversations(accessToken, null, beforeId)
    if (scope !== scopeRef.current) return
    setConversations((current) => [...new Map([...current, ...page].map((item) => [item.conversationId, item])).values()]
      .sort((left, right) => right.id - left.id))
    setHasMore(page.length > 0)
  }, [accessToken])

  useEffect(() => {
    if (!open || !accessToken) return
    const scope = ++scopeRef.current
    void onOpened?.()
    void refreshList().catch((failure) => { if (scope === scopeRef.current) setError(failure.message) })
    void listAssistantModels(accessToken).then((items) => {
      if (scope !== scopeRef.current) return
      setModelOptions(items)
      setSelectedModel((value) => items.some((model) => String(model.configId) === value) ? value : '')
    }).catch((failure) => { if (scope === scopeRef.current) setError(failure.message) })
    return () => { scopeRef.current += 1 }
  }, [accessToken, onOpened, open, refreshList])

  const toggle = () => {
    if (!open && !selectedRef.current && !linkedRunId && workspace === undefined) {
      let restored = false
      try {
        const operation = sessionStorage.getItem(`${selectionKey}.operation`)
        const savedProject = sessionStorage.getItem(`${selectionKey}.projectTasks`)
        const saved = sessionStorage.getItem(selectionKey)
        if (savedProject) openProjectTasks(savedProject, operation)
        else {
          if (saved) selectConversation(saved)
          if (operation) showOperation(operation)
        }
        restored = Boolean(savedProject || saved || operation)
      } catch { /* 会话仍可从侧边栏选择。 */ }
      if (!restored && projectId) openProjectTasks(projectId)
    }
    setOpen(!open)
  }

  /** 限定焦点范围，关闭后交还给原入口。 */
  useEffect(() => {
    if (!open) return
    const previous = document.activeElement
    const dialog = dialogRef.current
    dialog.focus()
    const keydown = (event) => {
      if (event.key === 'Escape') setOpen(false)
      if (event.key !== 'Tab') return
      const targets = [...dialog.querySelectorAll('button:not(:disabled):not([tabindex="-1"]),textarea:not(:disabled),a[href],summary')]
        .filter((item) => item.getClientRects().length)
      const first = targets[0]; const last = targets.at(-1)
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
    }
    document.addEventListener('keydown', keydown)
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.removeEventListener('keydown', keydown); document.body.style.overflow = overflow; previous?.focus() }
  }, [open])

  /** 消息历史与实时执行分别恢复，按投影键合并分页记录。 */
  const refreshHistory = useCallback(async (id, beforeId) => {
    const version = historyVersion.current
    const snapshot = await getCreativeConversationHistory(accessToken, id, beforeId)
    if (selectedRef.current !== id || version !== historyVersion.current) return
    setMessages((current) => [...new Map([...current, ...snapshot.messages].map((item) => [item.projectionKey, item])).values()]
      .sort((left, right) => left.id - right.id))
    if (beforeId) {
      pagingMessages.current = true
      setNextBeforeId(snapshot.nextBeforeId)
    }
    else {
      if (!pagingMessages.current) setNextBeforeId(snapshot.nextBeforeId)
      setActiveRun(snapshot.activeRun)
      setConversations((current) => [snapshot.conversation, ...current.filter((item) => item.conversationId !== id)])
    }
  }, [accessToken])

  useEffect(() => {
    if (!open || !conversationId) return
    setLoading(true)
    refreshHistory(conversationId).catch((failure) => { if (selectedRef.current === conversationId) setError(failure.message) })
      .finally(() => { if (selectedRef.current === conversationId) setLoading(false) })
  }, [conversationId, open, refreshHistory])

  const newConversation = (boundProject) => {
    selectConversation(null, boundProject)
    setInstruction('')
    setLoading(false)
  }
  const chooseWorkspace = (value) => {
    if (String(workspaceId ?? '') === value) return
    newConversation(value ? Number(value) : null)
  }

  /** 始终使用默认项目管理助手，审批预授权仅对本轮绑定项目有效。 */
  const submit = async (event) => {
    event.preventDefault()
    const prompt = instruction.trim()
    if (!prompt || !accessToken || busy || loading) return
    const scope = scopeRef.current
    const selected = selectedRef.current
    setSubmitting(true); setError('')
    try {
      const autoApplyChanges = Boolean(workspaceId) && approvalMode !== APPROVAL_MODE.ASK
      const run = await createConversationRun(accessToken, {
        requestId: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`,
        instruction: prompt, projectId: workspaceId, conversationId,
        modelConfigId: selectedModel ? Number(selectedModel) : null,
        permissionPolicy: workspaceId ? CREATIVE_PERMISSION.PROPOSE_CHANGES : CREATIVE_PERMISSION.READ_ONLY,
        autoApplyChanges, autoApproveRiskyChanges: autoApplyChanges && approvalMode === APPROVAL_MODE.FULL,
      })
      setActivityVersion((value) => value + 1)
      if (scope !== scopeRef.current || selected !== selectedRef.current) return
      setInstruction('')
      if (selected !== run.conversationId) selectConversation(run.conversationId, run.projectId)
      setLinkedRunId(null)
      try { sessionStorage.removeItem(`${selectionKey}.operation`) } catch { /* 仅清理执行引用。 */ }
      setLastRunId(run.runId)
      setActiveRun(run)
      setApprovalMode(APPROVAL_MODE.ASK)
      await Promise.all([refreshHistory(run.conversationId), refreshList()])
    } catch (failure) { if (scope === scopeRef.current) setError(failure.message) }
    finally { setSubmitting(false) }
  }

  const stop = async () => {
    if (!executing || stopRequested === focusedRun.runId) return
    setStopRequested(focusedRun.runId); setError('')
    try { await cancelCreativeRun(accessToken, focusedRun.runId) }
    catch (failure) { setError(failure.message); setStopRequested(null) }
  }

  const detailRunId = linkedRunId || activeRun?.runId || lastRunId || messages.at(-1)?.runId
  const inlineRun = Boolean(conversationId) && !linkedRunId
  const assistantFallback = messages.filter((message) => message.runId === detailRunId && message.role === CREATIVE_MESSAGE_ROLE.ASSISTANT)
    .map((message) => message.content).filter(Boolean).join('\n\n')
  const originalInstruction = messages.find((message) =>
    message.runId === detailRunId && message.role === CREATIVE_MESSAGE_ROLE.USER)?.content
  const workspaceOptions = [{ value: '', label: '绑定项目', icon: Folder },
    ...projects.map((project) => ({ value: String(project.id), label: project.name, icon: Folder }))]
  if (workspaceId && !workspaceOptions.some((option) => option.value === String(workspaceId))) {
    workspaceOptions.push({ value: String(workspaceId), label: `项目 #${workspaceId}`, icon: Folder })
  }
  const stopping = executing && (stopRequested === focusedRun.runId || focusedRun.status === 'CANCEL_REQUESTED')
  if (!accessToken) return null
  return <aside className={`manga-assistant ${open ? 'open' : ''}`}>
    <button className="assistant-toggle" data-running={hasBackgroundTasks || undefined} type="button" onClick={toggle}
      aria-expanded={open} aria-label={hasBackgroundTasks ? '打开项目管理助手，有后台任务运行中' : '打开项目管理助手'}>
      {open ? <X size={20} /> : <Bot size={22} />}
    </button>
    {open && <div className="task-center-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setOpen(false) }}>
      <section className="task-center" role="dialog" aria-modal="true" aria-labelledby="task-center-title" tabIndex={-1} ref={dialogRef}>
        <header className="task-center-header"><div><span className="task-center-brand"><Bot size={20} /></span>
          <div><h1 id="task-center-title">项目管理助手</h1><small>{taskProjectId ? '项目任务' : conversation?.title || '新会话'}</small></div></div>
          <button type="button" className="workbench-icon-button" aria-label="关闭助手" onClick={() => setOpen(false)}><X size={20} /></button></header>
        <div className="task-center-body">
          <nav className="task-center-sidebar" aria-label="项目会话">
            <ProjectConversationDirectory projects={projects} conversations={conversations} workspaceId={workspaceId}
              taskProjectId={taskProjectId} onProjectTasks={openProjectTasks}
              conversationId={conversationId} disabled={submitting} onNew={newConversation}
              onSelect={(item) => selectConversation(item.conversationId, item.projectId)} />
            {hasMore && <button className="task-load-more" type="button" disabled={loading || !conversations.length} onClick={() => {
              const oldest = Math.min(...conversations.map((item) => item.id))
              setLoading(true)
              void refreshList(oldest).catch((failure) => setError(failure.message)).finally(() => setLoading(false))
            }}>加载更早会话</button>}
          </nav>
          <main className="task-center-main">
            <div className="task-center-scroll assistant-output" aria-live="polite">
              {taskProjectId && !linkedRunId && <ProjectTaskConversation
                key={taskProjectId} accessToken={accessToken} projectId={taskProjectId} onSelect={showOperation} />}
              {taskProjectId && linkedRunId && <button className="workspace-secondary project-task-back" type="button"
                onClick={() => openProjectTasks(taskProjectId)}><ArrowLeft size={15} />返回项目任务</button>}
              {nextBeforeId && <button className="task-load-more" type="button" disabled={loading} onClick={() => {
                setLoading(true)
                void refreshHistory(conversationId, nextBeforeId).catch((failure) => setError(failure.message)).finally(() => setLoading(false))
              }}>加载更早消息</button>}
              {!taskProjectId && !messages.length && !detailRunId && <div className="workbench-empty"><Bot size={36} /><h2>今天要创作什么？</h2>
                {submitting ? <AssistantThinking label="正在提交" /> : <p>{loading ? '正在恢复会话…' : '选择项目后，可以直接在这里创作。'}</p>}</div>}
              {messages.filter((message) => message.role !== CREATIVE_MESSAGE_ROLE.TOOL
                && (message.role !== CREATIVE_MESSAGE_ROLE.ASSISTANT || message.runId !== detailRunId)).map((message) => {
                if (message.role !== CREATIVE_MESSAGE_ROLE.ASSISTANT) return <article className="chat-user-turn" aria-label="你" key={message.projectionKey}>
                  <p>{message.content}</p>
                </article>
                const expanded = historyRunId === message.runId
                return <article className="chat-assistant-turn" key={message.projectionKey}>
                  <AssistantTurnHeading>
                    <button type="button" className="message-execution-link" aria-expanded={expanded}
                      onClick={() => setHistoryRunId(expanded ? null : message.runId)}>{expanded ? '收起本轮过程' : '查看本轮过程'}</button>
                  </AssistantTurnHeading>
                  {expanded && <ConversationTurnHistory accessToken={accessToken} runId={message.runId} />}
                  <div className="chat-reply-block"><AssistantMarkdown text={message.content || '本轮未生成回答。'} /></div>
                </article>
              })}
              {linkedRunId && conversationId && <button className="workspace-secondary" type="button" onClick={() => setLinkedRunId(null)}>返回当前轮</button>}
              {detailRunId && <article className={inlineRun ? 'chat-assistant-turn' : undefined}>
                {inlineRun && <AssistantTurnHeading />}
                <CreativeRunDetails key={detailRunId} accessToken={accessToken} runId={detailRunId}
                assistantFallback={assistantFallback} originalInstruction={originalInstruction}
                conversationView={inlineRun} stopInComposer={!taskProjectId} onChanged={(run) => {
                  if (linkedRunId) setLinkedSnapshot(run)
                  else if (selectedRef.current === conversationId && conversationId) {
                    if (CREATIVE_TERMINAL_STATUSES.has(run.status)) setLastRunId(run.runId)
                    setActiveRun(CREATIVE_TERMINAL_STATUSES.has(run.status) ? null : run)
                    void refreshHistory(conversationId).catch((failure) => { if (selectedRef.current === conversationId) setError(failure.message) })
                  }
                }} onRunChanged={showOperation} onRegenerate={setInstruction} />
              </article>}
            </div>
            {!taskProjectId && <form className="task-composer" onSubmit={submit}>
              <div className="assistant-project-binding">
                <WorkspaceSelect label="绑定项目" value={String(workspaceId ?? '')}
                  options={workspaceId ? workspaceOptions.map((item) => item.value ? item : { ...item, label: '解除项目绑定' }) : workspaceOptions}
                  onChange={chooseWorkspace} disabled={busy || loading} />
              </div>
              <div className="assistant-compose-surface">
                <textarea aria-label="创作需求" value={instruction} onChange={(event) => setInstruction(event.target.value)} rows={3} maxLength={12000}
                  onKeyDown={(event) => {
                    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing || event.keyCode === 229) return
                    event.preventDefault()
                    if (!busy && !loading && instruction.trim()) event.currentTarget.form.requestSubmit()
                  }} placeholder="随心输入" />
                <div className="task-composer-toolbar">
                  <div className="task-composer-options assistant-approval">
                    <WorkspaceSelect label="审批方式" disabled={busy || loading || !workspaceId} value={approvalMode}
                      onChange={setApprovalMode} options={APPROVAL_OPTIONS} />
                  </div>
                  <div className="task-composer-options assistant-send-actions">
                    <WorkspaceSelect label="对话模型" disabled={busy || loading} value={selectedModel} onChange={setSelectedModel}
                      options={[{ value: '', label: '默认模型' }, ...modelOptions.map((model) => ({ value: String(model.configId), label: model.model, description: model.name }))]} />
                    {executing ? <button className="workspace-primary" type="button" onClick={stop} disabled={stopping}>
                      <Square size={14} fill="currentColor" />{stopping ? '正在停止' : '停止'}</button>
                      : <button className="workspace-primary" type="submit" disabled={!instruction.trim() || busy || loading}><Send size={14} />{submitting ? '发送中' : '发送'}</button>}
                  </div>
                </div>
              </div>
            </form>}
            {error && <div className="workspace-alert" role="alert">{error}</div>}
          </main>
        </div>
      </section>
    </div>}
  </aside>
}

/** 会话中的助手回复与该轮工具过程共用署名。 */
function AssistantTurnHeading({ children }) {
  return <header className="chat-assistant-heading"><Bot size={17} aria-hidden="true" /><strong>项目管理助手</strong>{children}</header>
}

/** 侧边栏只显示项目和会话，空项目也能直接发起对话。 */
function ProjectConversationDirectory({ projects, conversations, workspaceId, taskProjectId, conversationId, disabled, onNew, onSelect, onProjectTasks }) {
  const [collapsed, setCollapsed] = useState({})
  const groups = new Map(projects.map((project) => [String(project.id), { ...project, conversations: [] }]))
  for (const conversation of conversations) {
    const key = String(conversation.projectId ?? '')
    if (!groups.has(key)) groups.set(key, { id: conversation.projectId, name: key ? `项目 #${key}` : '未绑定项目', conversations: [] })
    groups.get(key).conversations.push(conversation)
  }
  if (taskProjectId && !groups.has(String(taskProjectId))) {
    groups.set(String(taskProjectId), { id: taskProjectId, name: `项目 #${taskProjectId}`, conversations: [] })
  }
  return <>
    <div className="assistant-directory-heading"><strong>项目</strong><button className="workbench-icon-button" type="button"
      aria-label="新建会话" title="新建会话" disabled={disabled} onClick={() => onNew(workspaceId ?? null)}><SquarePen size={15} /></button></div>
    {[...groups].map(([id, group]) => {
      const visible = !collapsed[id]
      return <section className="assistant-project-group" key={id}>
        <div className={`assistant-project-row${id === String(workspaceId ?? '') ? ' selected' : ''}`}>
          <button type="button" aria-expanded={visible} onClick={() => setCollapsed((current) => ({ ...current, [id]: visible }))}>
            {visible ? <ChevronDown size={13} /> : <ChevronRight size={13} />}<Folder size={15} /><span>{group.name}</span>
          </button>
          <button type="button" disabled={disabled} aria-label={`在${group.name}新建会话`} title="新建会话" onClick={() => {
            setCollapsed((current) => ({ ...current, [id]: false }))
            onNew(group.id ?? null)
          }}><Plus size={15} /></button>
        </div>
        {visible && <div className="task-directory assistant-project-conversations">
          {group.id != null && <button type="button" disabled={disabled}
            className={String(taskProjectId) === id ? 'selected' : ''}
            onClick={() => onProjectTasks(group.id)}>
            <ListTodo size={14} /><span><strong>项目任务</strong></span>
          </button>}
          {group.conversations.map((item) => <button key={item.conversationId} type="button" disabled={disabled}
            className={conversationId === item.conversationId ? 'selected' : ''} onClick={() => onSelect(item)}>
            <MessageSquare size={13} /><span><strong>{item.title}</strong></span>
          </button>)}
          {!group.conversations.length && <small>暂无聊天</small>}
        </div>}
      </section>
    })}
  </>
}
