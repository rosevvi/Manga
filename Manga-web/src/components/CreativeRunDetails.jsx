import { useEffect, useRef, useState } from 'react'
import { Bot, CheckCircle2, Clock3, Square, Wrench } from 'lucide-react'
import { cancelCreativeRun, createManualRun, createConversationRun, getCreativeRun, getCreativeRunGraph, getCreativeRunTimeline, streamCreativeRunEvents } from '../api/creativeRunApi'
import { CREATIVE_TERMINAL_STATUSES } from '../constants/changeSet'
import { CREATIVE_TOOL_LABEL } from '../constants/creative'
import { AGENT_PROFILE_LABEL } from '../constants/collaboration'
import { RUN_STATUS_LABEL, creativeTaskName } from './creativeTaskEvents'
import CreativeChangeSetPanel from './CreativeChangeSetPanel'
import GenerationJobsPanel from './GenerationJobsPanel'
import CreativeRunGraph from './CreativeRunGraph'
import { regenerationInstruction } from './changeSetPreview'
import { timelineCards, loadTimelineSince } from './creativeTimeline'
import { notifyCreativeProjectChanged } from './creativeProjectEvents'

const POLL_MS = 3000
const EVENT_LABEL = { STEP_STARTED: 'Agent 开始执行', STEP_COMPLETED: 'Agent 执行完成', STEP_FAILED: 'Agent 执行失败',
  RUN_ACCEPTED: '任务已受理', RUN_STARTED: '开始创作', RUN_COMPLETED: '任务已完成', RUN_FAILED: '任务失败',
  RUN_CANCELLED: '任务已停止', RUN_CANCEL_REQUESTED: '已请求停止', PLAN_CREATED: '协作计划已建立',
  APPROVAL_REQUIRED: '提案等待审批', APPROVAL_DECIDED: '审批已处理', CHANGESET_PROPOSED: '提案已生成',
  CHANGESET_APPLIED: '提案已保存', CHANGESET_CONFLICTED: '内容版本冲突', JOB_SUBMITTED: '已提交供应商',
  JOB_PROGRESS: '供应商进度更新', JOB_COMPLETED: '媒体生成完成', JOB_FAILED: '媒体生成失败', ARTIFACT_CREATED: '产物已转存',
  ARTIFACT_ADOPTED: '图片已采用', DYNAMIC_AGENT_CREATED: '创建协作 Agent', DYNAMIC_AGENT_RESULT: '协作结果已提交' }

/** 仅在任务中心挂载；SSE 唤醒串行快照更新，关闭和切换立即释放订阅。 */
export default function CreativeRunDetails({ accessToken, runId, showText = true, onRunChanged, onChanged, onRegenerate }) {
  const [run, setRun] = useState(null)
  const [events, setEvents] = useState([])
  const [stage, setStage] = useState('')
  const [graph, setGraph] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [clock, setClock] = useState(Date.now())
  const callbacks = useRef({ onChanged, onRunChanged, onRegenerate })
  callbacks.current = { onChanged, onRunChanged, onRegenerate }
  const refreshRef = useRef(null)

  useEffect(() => {
    let stopped = false
    let refreshing = false
    let dirty = false
    let cursor = 0
    let streamCursor = 0
    let latestRun
    let controller
    let timer
    let reconnect
    let wakeTimer
    setRun(null); setEvents([]); setGraph(null); setStage(''); setError('')
    const refresh = async () => {
      dirty = true
      if (refreshing || stopped) return
      refreshing = true
      try {
        do {
          dirty = false
          const [snapshot, graph] = await Promise.all([getCreativeRun(accessToken, runId), getCreativeRunGraph(accessToken, runId)])
          const page = await loadTimelineSince((after) => getCreativeRunTimeline(accessToken, runId, after), cursor, () => stopped)
          if (stopped || !page) return
          const incoming = page.items
          cursor = page.cursor
          streamCursor = Math.max(streamCursor, cursor)
          const wasActive = latestRun && !CREATIVE_TERMINAL_STATUSES.has(latestRun.status)
          latestRun = snapshot
          if (wasActive && CREATIVE_TERMINAL_STATUSES.has(snapshot.status) && snapshot.projectId) notifyCreativeProjectChanged(snapshot.projectId)
          setRun(snapshot)
          setGraph(graph)
          setEvents((previous) => [...new Map([...previous, ...incoming].map((item) => [item.id, item])).values()].sort((a, b) => a.sequence - b.sequence))
          const current = graph.nodes?.find((node) => ['WAITING_APPROVAL', 'WAITING_JOB', 'RUNNING', 'WAITING_CHILDREN', 'READY'].includes(node.status))
          setStage(current ? `${current.displayName || AGENT_PROFILE_LABEL[current.profileId] || current.profileId || '创作步骤'} · ${RUN_STATUS_LABEL[current.status] || current.status}` : RUN_STATUS_LABEL[snapshot.status])
          setError('')
          callbacks.current.onChanged?.(snapshot)
        } while (dirty && !stopped)
      } catch (failure) { if (!stopped) setError(failure.message) }
      finally {
        refreshing = false
        if (!stopped && !CREATIVE_TERMINAL_STATUSES.has(latestRun?.status)) {
          window.clearTimeout(timer)
          timer = window.setTimeout(refresh, POLL_MS)
        }
      }
    }
    const connect = async () => {
      if (stopped || CREATIVE_TERMINAL_STATUSES.has(latestRun?.status)) return
      controller = new AbortController()
      try {
        await streamCreativeRunEvents(accessToken, runId, streamCursor, (event) => {
          streamCursor = Math.max(streamCursor, event.sequence)
          if (!wakeTimer) wakeTimer = window.setTimeout(() => { wakeTimer = null; void refresh() }, 300)
        }, controller.signal)
      } catch (failure) { if (!stopped && !controller.signal.aborted) setError(failure.message) }
      if (!stopped) { await refresh(); reconnect = window.setTimeout(connect, 1500) }
    }
    refreshRef.current = refresh
    void refresh().then(connect)
    const tick = window.setInterval(() => setClock(Date.now()), 1000)
    return () => {
      stopped = true; controller?.abort(); window.clearTimeout(timer); window.clearTimeout(reconnect)
      window.clearTimeout(wakeTimer); window.clearInterval(tick); refreshRef.current = null
    }
  }, [accessToken, runId])

  const stop = async () => {
    setBusy(true)
    try { await cancelCreativeRun(accessToken, runId); await refreshRef.current?.() }
    catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  /** 失败任务始终创建新 requestId，旧 Run 与提案保留；不重发外部付费媒体请求。 */
  const regenerate = async (proposal) => {
    if (!run || busy || !CREATIVE_TERMINAL_STATUSES.has(run.status)) return
    setBusy(true)
    try {
      const prompt = proposal?.kind === 'VIDEO_GENERATION' ? JSON.stringify(proposal.proposed)
        : proposal?.kind === 'IMAGE_GENERATION' ? `请读取目标镜头，并严格使用以下参数重新提出图片生成提案，等待审批：${JSON.stringify(proposal.proposed)}`
          : proposal ? regenerationInstruction(proposal) : `重新执行${creativeTaskName(run)}。读取当前目标的最新内容，生成提案并等待审批。`
      if (run.triggerSource === 'CONVERSATION' && callbacks.current.onRegenerate) { callbacks.current.onRegenerate(prompt); return }
      const request = { requestId: globalThis.crypto.randomUUID(), projectId: run.projectId,
        operationType: run.operationType, target: { resourceType: run.targetType, resourceId: run.targetId },
        conversationId: run.conversationId, permissionPolicy: run.permissionPolicy, instruction: prompt }
      const next = run.triggerSource === 'CONVERSATION' ? await createConversationRun(accessToken, request) : await createManualRun(accessToken, request)
      callbacks.current.onRunChanged?.(next.runId)
    } catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  const elapsed = run?.startedAt ? Math.max(0, Math.floor(((run.finishedAt ? new Date(run.finishedAt).getTime() : clock) - new Date(run.startedAt).getTime()) / 1000)) : 0
  const last = events.at(-1)
  const canRetry = run?.triggerSource === 'MANUAL' && ['FAILED', 'CANCELLED'].includes(run.status)
    && ['PARSE_SCRIPT', 'GENERATE_SCRIPT_CHAPTER', 'GENERATE_CHAPTER_STORYBOARD'].includes(run.operationType)
  return <section className="task-detail">
    <header className="task-detail-header"><div><h2>{creativeTaskName(run)}</h2><p><span className={`task-status ${run?.status?.toLowerCase()}`}>{stage || '正在恢复任务…'}</span><span><Clock3 size={12} /> {elapsed} 秒</span></p>
      <small>最后活动 {last?.occurredAt?.replace('T', ' ') || '等待执行记录'}</small></div>
      {run && !CREATIVE_TERMINAL_STATUSES.has(run.status) && <button type="button" className="workspace-secondary" disabled={busy} onClick={stop}><Square size={13} />停止</button>}
      {canRetry && <button type="button" className="workspace-primary" disabled={busy} onClick={() => regenerate()}>重新生成</button>}
    </header>
    {run?.errorMessage && <div className="workspace-alert" role="alert"><strong>{run.errorCode}</strong><p>{run.errorMessage}</p></div>}
    {graph && <CreativeRunGraph accessToken={accessToken} runId={runId} snapshot={graph} />}
    <div className="execution-timeline">
      {timelineCards(events, showText, run).filter((item) => item.type === 'CONTENT_DELTA' || item.type.startsWith('TOOL_') || EVENT_LABEL[item.type]).map((item) => item.type === 'CONTENT_DELTA'
        ? <article key={item.id} className="timeline-prose"><Bot size={16} /><p>{item.resultSummary}</p></article>
        : <details className={`execution-card ${item.status === 'ERROR' || item.type.endsWith('FAILED') ? 'failed' : ''}`} key={item.id}>
          <summary>{item.type.startsWith('TOOL_') ? <Wrench size={16} /> : <CheckCircle2 size={16} />}<strong>{CREATIVE_TOOL_LABEL[item.actionName] || EVENT_LABEL[item.type] || item.actionName}</strong>
            <span>{RUN_STATUS_LABEL[item.status] || ''}</span><time>{item.occurredAt?.slice(11, 19)}</time></summary>
          <div className="execution-card-body"><small>{AGENT_PROFILE_LABEL[item.agentName] || item.agentName || '创作运行时'}{item.agentVersion ? ` · v${item.agentVersion}` : ''}{item.ownerEpoch ? ` · 第 ${item.ownerEpoch} 次执行` : ''}</small>
            {item.inputSummary && <p>输入 · {item.inputSummary}</p>}{item.resultSummary && <p>结果 · {item.resultSummary}</p>}
            {item.failureReason && <p role="alert">{item.failureReason}</p>}
            {item.resourceLinks?.map((link) => <a key={link.href} href={link.href}>{link.label}</a>)}
            {item.startedAt && item.finishedAt && <small>耗时 {Math.max(0, (new Date(item.finishedAt) - new Date(item.startedAt)) / 1000).toFixed(1)} 秒</small>}
            {!item.inputSummary && !item.resultSummary && !item.failureReason && <p>执行状态已记录{item.actionName?.startsWith('TOOL') ? '' : '，暂无额外摘要'}。</p>}
          </div>
        </details>)}
    </div>
    <CreativeChangeSetPanel accessToken={accessToken} runId={runId} onDecided={() => refreshRef.current?.()}
      onRegenerate={CREATIVE_TERMINAL_STATUSES.has(run?.status) ? regenerate : undefined} />
    {run?.projectId && <GenerationJobsPanel accessToken={accessToken} projectId={run.projectId} runId={runId} />}
    {error && <p role="alert" className="workspace-alert">{error}</p>}
  </section>
}
