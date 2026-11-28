import { useEffect, useRef, useState } from 'react'
import { Clock3, Square } from 'lucide-react'
import { cancelCreativeRun, createManualRun, createConversationRun } from '../api/creativeRunApi'
import { CHANGE_SET_KIND, CREATIVE_TERMINAL_STATUSES } from '../constants/changeSet'
import { CREATIVE_OPERATION, CREATIVE_TRIGGER } from '../constants/creative'
import { AGENT_PROFILE_LABEL, CREATIVE_STEP_STATUS_LABEL } from '../constants/collaboration'
import { CHAPTER_EXTRACTION_STARTED, RUN_STATUS_LABEL, creativeTaskName } from './creativeTaskEvents'
import CreativeChangeSetPanel from './CreativeChangeSetPanel'
import GenerationJobsPanel from './GenerationJobsPanel'
import CreativeExecutionTimeline from './CreativeExecutionTimeline'
import CreativeRepairPanel from './CreativeRepairPanel'
import useCreativeRunActivity from './useCreativeRunActivity'
import { imageProposalInstruction, imageRegenerationRequest, regenerationInstruction } from './changeSetPreview'
import { taskActionSequences } from './creativeTimeline'
import './project-workspace.css'
import './project-module.css'
import './creative-workbench.css'

/** 在历史回答旁恢复执行过程，正文继续由消息历史展示。 */
export function ConversationTurnHistory({ accessToken, runId }) {
  const { run, graph, events, error, refreshRef } = useCreativeRunActivity(accessToken, runId, { historyOnly: true })
  return <div className="chat-history-activity">
    {error && <p role="alert" className="workspace-alert">{error}
      <button type="button" className="message-execution-link" onClick={() => refreshRef.current?.()}>重试读取</button></p>}
    {!error && !run && <p className="process-muted" role="status">正在读取本轮过程…</p>}
    <CreativeExecutionTimeline runId={runId} graph={graph} events={events} run={run} conversationView historyOnly />
  </div>
}

/** 对话内展示回复和业务操作，显式打开执行记录时展示运行详情。 */
export default function CreativeRunDetails({ accessToken, runId, assistantFallback, originalInstruction, onRunChanged, onChanged, onRegenerate,
  conversationView = false, stopInComposer = false }) {
  const [busy, setBusy] = useState(false)
  const [clock, setClock] = useState(Date.now())
  const callbacks = useRef({ onRunChanged, onRegenerate })
  callbacks.current = { onRunChanged, onRegenerate }
  const { run, graph, events, repairs, error, setError, appliedSequence, refreshRef } = useCreativeRunActivity(accessToken, runId, { onChanged })
  const terminal = CREATIVE_TERMINAL_STATUSES.has(run?.status)
  useEffect(() => {
    if (conversationView || terminal) return
    const timer = window.setInterval(() => setClock(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [conversationView, terminal])
  const current = ['RUNNING', 'WAITING_REPAIR_CONFIRMATION', 'WAITING_APPROVAL', 'WAITING_JOB', 'READY', 'WAITING_CHILDREN']
    .map((status) => graph?.nodes.find((node) => node.status === status)).find(Boolean)
  const stage = current ? `${current.displayName || AGENT_PROFILE_LABEL[current.profileId] || '创作步骤'} · ${CREATIVE_STEP_STATUS_LABEL[current.status]}`
    : RUN_STATUS_LABEL[run?.status]

  const stop = async () => {
    setBusy(true)
    try { await cancelCreativeRun(accessToken, runId); await refreshRef.current?.() }
    catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  /** 失败任务始终创建新 requestId，旧 Run 与提案保留；不重发外部媒体生成请求。 */
  const regenerate = async (proposal) => {
    if (!run || busy || !CREATIVE_TERMINAL_STATUSES.has(run.status)) return
    setBusy(true)
    try {
      const conversational = run.triggerSource === CREATIVE_TRIGGER.CONVERSATION
      const prompt = run.operationType === CREATIVE_OPERATION.EXTRACT_CHAPTER_ASSETS ? '重新识别当前章节资产并登记。'
        : proposal?.kind === CHANGE_SET_KIND.VIDEO_GENERATION ? JSON.stringify(proposal.proposed)
        : proposal?.kind === CHANGE_SET_KIND.IMAGE_GENERATION && proposal.proposed.shotId != null ? imageProposalInstruction(proposal.proposed)
          : proposal ? regenerationInstruction(proposal) : conversational ? originalInstruction
            : `重新执行${creativeTaskName(run)}。读取当前目标的最新内容，生成提案并等待审批。`
      if (!prompt?.trim()) return
      if (conversational && callbacks.current.onRegenerate) { callbacks.current.onRegenerate(prompt); return }
      const request = { requestId: globalThis.crypto.randomUUID(), projectId: run.projectId,
        operationType: run.operationType, target: { resourceType: run.targetType, resourceId: run.targetId },
        conversationId: run.conversationId, permissionPolicy: run.permissionPolicy, instruction: prompt }
      const next = conversational ? await createConversationRun(accessToken, request) : await createManualRun(accessToken, request)
      if (run.operationType === CREATIVE_OPERATION.EXTRACT_CHAPTER_ASSETS) {
        window.dispatchEvent(new CustomEvent(CHAPTER_EXTRACTION_STARTED, {
          detail: { projectId: run.projectId, chapterId: run.targetId },
        }))
      }
      callbacks.current.onRunChanged?.(next.runId)
    } catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  /** 产物重生成使用原镜头的新手动提案，任务详情直接切换到新运行。 */
  const regenerateImage = async (proposal, job, prompt) => {
    const next = await createManualRun(accessToken, imageRegenerationRequest(proposal, job, prompt, globalThis.crypto.randomUUID()))
    callbacks.current.onRunChanged?.(next.runId)
  }
  const elapsed = run?.startedAt ? Math.max(0, Math.floor(((run.finishedAt ? new Date(run.finishedAt).getTime() : clock) - new Date(run.startedAt).getTime()) / 1000)) : 0
  const last = events.at(-1)
  const { approval, media } = taskActionSequences(events)
  const canRetry = ['FAILED', 'CANCELLED'].includes(run?.status)
    && (run.triggerSource === CREATIVE_TRIGGER.CONVERSATION && originalInstruction?.trim() && callbacks.current.onRegenerate
      || run.triggerSource === CREATIVE_TRIGGER.MANUAL && ['PARSE_SCRIPT', 'GENERATE_SCRIPT_CHAPTER', 'GENERATE_CHAPTER_STORYBOARD', CREATIVE_OPERATION.EXTRACT_CHAPTER_ASSETS].includes(run.operationType))
  return <section className="task-detail" data-event-sequence={appliedSequence}>
    {!conversationView && <header className="task-detail-header"><div><h2>{creativeTaskName(run)}</h2><p><span className={`task-status ${run?.status?.toLowerCase()}`}>{stage || '正在恢复任务…'}</span><span><Clock3 size={12} /> {elapsed} 秒</span></p>
      <small>最后活动 {last?.occurredAt?.replace('T', ' ') || '等待执行记录'}</small></div>
      {!stopInComposer && run && !CREATIVE_TERMINAL_STATUSES.has(run.status) && <button type="button" className="workspace-secondary" disabled={busy} onClick={stop}><Square size={13} />停止</button>}
      {canRetry && <button type="button" className="workspace-primary" disabled={busy} onClick={() => regenerate()}>重新生成</button>}
    </header>}
    {run?.errorMessage && <div className="workspace-alert" role="alert">{!conversationView && <strong>{run.errorCode}</strong>}<p>{run.errorMessage}</p></div>}
    <CreativeExecutionTimeline runId={runId} graph={graph} events={events} run={run} assistantFallback={assistantFallback}
      conversationView={conversationView} />
    {conversationView && canRetry && <button type="button" className="workspace-secondary" disabled={busy} onClick={() => regenerate()}>重试本轮</button>}
    <CreativeRepairPanel accessToken={accessToken} runId={runId} decisions={repairs} onChanged={() => refreshRef.current?.()} />
    <CreativeChangeSetPanel accessToken={accessToken} runId={runId} wakeSequence={approval} onDecided={() => refreshRef.current?.()}
      onRegenerate={terminal ? regenerate : undefined} />
    {run?.projectId && <GenerationJobsPanel accessToken={accessToken} projectId={run.projectId} runId={runId}
      wakeSequence={media} waitingForJob={graph?.nodes.some((node) => node.status === 'WAITING_JOB')}
      canRegenerate={terminal} onImageRegenerate={regenerateImage} onAdopted={() => refreshRef.current?.()} />}
    {error && <p role="alert" className="workspace-alert">{error}</p>}
  </section>
}
