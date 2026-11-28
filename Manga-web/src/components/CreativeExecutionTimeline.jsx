import { useEffect, useRef, useState } from 'react'
import { Bot, CheckCircle2, ChevronDown, ChevronRight, LoaderCircle, Wrench } from 'lucide-react'
import { CREATIVE_STEP_STATUS_LABEL } from '../constants/collaboration'
import { CREATIVE_TERMINAL_STATUSES } from '../constants/changeSet'
import { CREATIVE_TOOL_LABEL } from '../constants/creative'
import { groupConversationThoughts, isAgentActivity, orderedExecutionSections, timelineCards } from './creativeTimeline'
import AssistantMarkdown, { AssistantThinking } from './AssistantMarkdown'
import { ConversationThought, ConversationToolResult } from './ConversationActivity'

const ACTIVE = new Set(['RUNNING', 'READY', 'WAITING_CHILDREN', 'WAITING_APPROVAL', 'WAITING_JOB', 'WAITING_REPAIR_CONFIRMATION'])
const TERMINAL_EVENTS = new Set(['RUN_COMPLETED', 'RUN_FAILED', 'RUN_CANCELLED'])
const TECHNICAL_LABEL = { RUN_ACCEPTED: '任务已受理', RUN_STARTED: '任务开始', PLAN_CREATED: '协作计划建立',
  STEP_CREATED: '步骤已建立', STEP_STARTED: '开始执行', STEP_COMPLETED: '步骤完成', STEP_FAILED: '步骤失败',
  RUN_COMPLETED: '任务完成', RUN_FAILED: '任务失败', RUN_CANCELLED: '任务结束', RUN_CANCEL_REQUESTED: '请求停止',
  REPAIR_CONFIRMATION_REQUIRED: '等待确认修订', REPAIR_CONFIRMATION_DECIDED: '修订确认已处理' }

/** 按计划、Agent 与工具组织服务端事实；手动折叠状态不会被增量更新覆盖。 */
export default function CreativeExecutionTimeline({ runId, graph, events, run, assistantFallback, conversationView = false, historyOnly = false }) {
  const storageKey = `manga.execution-folds.${runId}`
  const [folds, setFolds] = useState(() => {
    try { return JSON.parse(sessionStorage.getItem(storageKey) || '{}') }
    catch { return {} }
  })
  const [newContent, setNewContent] = useState(false)
  const anchor = useRef(null)
  const follow = useRef(true)
  const scroller = useRef(null)
  const observedHeight = useRef(0)
  const cards = timelineCards(events, !historyOnly, run)
  const hasAssistantText = cards.some((item) => item.type === 'CONTENT_DELTA' && !item.parentStepId)
  const nodes = new Map((graph?.nodes || []).filter((node) => node.profile).map((node) => [node.stepId, node]))
  for (const event of events) {
    if (event.stepId && !nodes.has(event.stepId) && (event.agentName || ['CONTENT_DELTA', 'REASONING_DELTA'].includes(event.type))) {
      nodes.set(event.stepId, { stepId: event.stepId, parentStepId: event.parentStepId, planId: event.planId,
        displayName: event.agentName || '创作 Agent', status: 'RUNNING', ownerEpoch: event.ownerEpoch })
    }
  }
  const plans = [...(graph?.plans || [])]
  for (const event of events) {
    if (event.planId && !plans.some((plan) => plan.planId === event.planId)) {
      plans.push({ planId: event.planId, round: event.round, title: event.type === 'PLAN_CREATED' && event.actionLabel || '创作协作' })
    }
  }
  const sections = orderedExecutionSections([...nodes.values()].filter((node) => !node.planId), plans, cards)
  const terminal = historyOnly || CREATIVE_TERMINAL_STATUSES.has(run?.status) || events.some((item) => TERMINAL_EVENTS.has(item.type))
  const waitingForReply = conversationView && !terminal && !assistantFallback
    && !sections.some((section) => section.plan || section.items?.length)

  const setExpanded = (id, open) => {
    setFolds((previous) => {
      const next = { ...previous, [id]: open }
      try { sessionStorage.setItem(storageKey, JSON.stringify(next)) } catch { /* 存储不可用时仍支持本次折叠。 */ }
      return next
    })
  }
  const disclosure = (id, title, content, defaultOpen = false, className = '') => {
    const open = folds[id] ?? defaultOpen
    return <section className={`process-disclosure ${className}`} data-disclosure-id={id} key={id}>
      <button type="button" className="process-disclosure-heading" aria-expanded={open} onClick={() => setExpanded(id, !open)}>
        {open ? <ChevronDown size={15} /> : <ChevronRight size={15} />}{title}
      </button>
      {open && <div className="process-disclosure-content">{content}</div>}
    </section>
  }

  useEffect(() => {
    if (historyOnly) return undefined
    const element = anchor.current?.closest('.task-center-scroll')
    const detail = anchor.current?.closest('.task-detail')
    if (!element || !detail) return undefined
    scroller.current = element
    const onScroll = () => {
      follow.current = element.scrollHeight - element.scrollTop - element.clientHeight < 80
      if (follow.current) setNewContent(false)
    }
    element.addEventListener('scroll', onScroll, { passive: true })
    onScroll()
    const observer = new ResizeObserver(() => {
      const height = detail.getBoundingClientRect().height
      if (height > observedHeight.current) {
        if (follow.current) element.scrollTop = element.scrollHeight
        else if (observedHeight.current) setNewContent(true)
      }
      observedHeight.current = height
    })
    observer.observe(detail)
    return () => { observer.disconnect(); element.removeEventListener('scroll', onScroll) }
  }, [historyOnly])

  useEffect(() => {
    if (historyOnly || !events.length) return
    if (follow.current) {
      const frame = requestAnimationFrame(() => { if (scroller.current) scroller.current.scrollTop = scroller.current.scrollHeight })
      return () => cancelAnimationFrame(frame)
    }
    setNewContent(true)
  }, [events, historyOnly])

  const renderCard = (item, latestToolId, streaming) => {
    if (item.type === 'CONTENT_DELTA') return <div key={item.id} className={conversationView ? 'chat-reply-block' : undefined}>
      <AssistantMarkdown text={item.resultSummary} streaming={streaming} />
    </div>
    if (item.type === 'REASONING_DELTA') {
      const id = `reasoning:${item.id}`
      if (conversationView) {
        const open = folds[id] ?? streaming
        return <ConversationThought key={id} item={item} streaming={streaming} open={open} onToggle={() => setExpanded(id, !open)} />
      }
      return disclosure(id, <><Bot size={14} /><strong>供应商分析</strong></>,
        <AssistantMarkdown text={item.resultSummary} streaming={streaming} />, streaming, 'process-reasoning')
    }
    const working = item.status === 'RUNNING'
    const toolId = `tool:${item.stepId}:${item.ownerEpoch}:${item.actionId}`
    if (conversationView) {
      const open = folds[toolId] ?? false
      return <ConversationToolResult key={toolId} item={item} open={open} onToggle={() => setExpanded(toolId, !open)} />
    }
    return disclosure(toolId, <>
      {working ? <LoaderCircle size={15} className="process-spinner" /> : <Wrench size={15} />}
      <strong>{item.actionLabel || CREATIVE_TOOL_LABEL[item.actionName] || item.actionName}</strong>
      {item.callCount > 1 && <small>第 {item.callCount} 次调用</small>}
      {item.reused && <small>复用结果</small>}
      <span className={`process-state ${item.status?.toLowerCase()}`}>{working ? '执行中' : item.status === 'SUCCESS' ? '成功' : item.status === 'ERROR' ? '失败' : '已中断'}</span>
    </>, <>
      {item.inputSummary && <p>目标 · {item.inputSummary}</p>}
      {item.resultSummary && <p>{item.resultSummary}</p>}
      {item.failureReason && <p className="process-error" role="alert">{item.failureReason}</p>}
      <small>{item.startedAt?.replace('T', ' ')}{item.finishedAt && ` → ${item.finishedAt.replace('T', ' ')}`}</small>
      {!item.inputSummary && !item.resultSummary && !item.failureReason && <small>执行中时显示状态，完成后更新结果。</small>}
    </>, working || item.id === latestToolId, `process-tool ${item.status === 'ERROR' ? 'failed' : ''}`)
  }

  const renderAgent = (node, items = cards.filter((item) => item.stepId === node.stepId && isAgentActivity(item.type)),
    id = `agent:${node.stepId}`, current = true) => {
    const lastStatus = [...events].reverse().find((item) => item.stepId === node.stepId && ['STEP_STARTED', 'STEP_COMPLETED', 'STEP_FAILED'].includes(item.type))
    const status = lastStatus && (graph?.throughSequence || 0) < lastStatus.sequence
      ? ({ STEP_STARTED: 'RUNNING', STEP_COMPLETED: 'COMPLETED', STEP_FAILED: 'FAILED' })[lastStatus.type] : node.status
    const active = current && ACTIVE.has(status)
    const latestToolId = items.findLast((item) => item.type.startsWith('TOOL_'))?.id
    const visibleItems = conversationView ? groupConversationThoughts(items) : items
    const renderedItems = visibleItems.map((item, index) => renderCard(item, latestToolId,
      !terminal && current && status === 'RUNNING' && index === visibleItems.length - 1))
    // 根步骤的可见正文属于聊天；工具卡片和协作步骤继续使用各自的边界。
    if (conversationView && !node.parentStepId && !node.planId) {
      return items.length ? <div className="assistant-inline-response" key={id}>{renderedItems}</div> : null
    }
    const directorHasOutput = !node.parentStepId && items.some((item) => item.type === 'CONTENT_DELTA')
    return disclosure(id, <>
      {current && status === 'RUNNING' ? <LoaderCircle size={16} className="process-spinner" /> : status === 'COMPLETED' ? <CheckCircle2 size={16} /> : <Bot size={16} />}
      <strong>{node.displayName || node.profileId || '创作 Agent'}</strong><span className="process-state">{current ? CREATIVE_STEP_STATUS_LABEL[status] || status : '已交给协作 Agent'}</span>
    </>, <>
      {renderedItems}
      {!items.length && (status === 'RUNNING' ? <AssistantThinking /> : <p className="process-muted">{node.waitingReason || (active ? '正在准备执行…' : '该步骤未输出额外过程文字。')}</p>)}
      {current && node.resultSummary && disclosure(`result:${node.stepId}`, <strong>最终结果</strong>, <>
        <AssistantMarkdown text={node.resultSummary} />
        {node.verdict && <p>评审：{node.verdict === 'PASS' ? '通过' : '需要修订'}</p>}
        {!!node.issues?.length && <ul>{node.issues.map((issue, index) => <li key={index}>{issue}</li>)}</ul>}
      </>)}
      {current && node.errorMessage && <p role="alert" className="process-error">{node.errorMessage}</p>}
      {current && disclosure(`meta:${node.stepId}`, '执行信息', <small>步骤 {node.stepId} · {node.profileId} · v{node.profileVersion} · 当前代次 {node.ownerEpoch}</small>)}
    </>, active || directorHasOutput, 'process-agent')
  }

  return <div className={`creative-process${conversationView ? ' conversation-process' : ''}`} ref={anchor}>
    {!conversationView && <div className="process-toolbar"><strong>执行过程</strong><button className="workspace-secondary" type="button" onClick={() => {
      const next = { ...folds }
      for (const node of nodes.values()) next[`agent:${node.stepId}`] = false
      for (const section of sections) next[section.id] = false
      next.technical = false
      setFolds(next)
      try { sessionStorage.setItem(storageKey, JSON.stringify(next)) } catch { /* 仅影响刷新后的折叠恢复。 */ }
    }}>全部收起</button></div>}
    {waitingForReply && <AssistantThinking />}
    {sections.map(({ id, plan, node, items, current }) => {
      if (node) return renderAgent(node, items, id, current)
      const workers = [...nodes.values()].filter((node) => node.planId === plan.planId)
      return disclosure(id, <strong>{plan.round ? `第 ${plan.round} 轮 · ` : ''}{plan.title}</strong>, workers.map((worker) => renderAgent(worker)),
        workers.some((node) => ACTIVE.has(node.status)), 'process-plan')
    })}
    {assistantFallback && !hasAssistantText && <div className={conversationView ? 'chat-reply-block' : undefined}>
      <AssistantMarkdown text={assistantFallback} /></div>}
    {!conversationView && disclosure('technical', '任务与调度记录', <ol className="process-technical">{events.filter((item) =>
      !['CONTENT_DELTA', 'REASONING_DELTA'].includes(item.type) && !item.type.startsWith('TOOL_')).map((item) =>
      <li key={item.id}><time>{item.occurredAt?.slice(11, 19)}</time> {TECHNICAL_LABEL[item.type] || item.actionName || item.type}
        {item.failureReason && <p role="alert">{item.failureReason}</p>}</li>)}</ol>)}
    {newContent && <button type="button" className="process-new-content" onClick={() => {
      follow.current = true; setNewContent(false)
      if (scroller.current) scroller.current.scrollTop = scroller.current.scrollHeight
    }}>有新内容 · 回到最新</button>}
  </div>
}
