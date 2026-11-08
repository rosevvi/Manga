import { useEffect, useRef, useState } from 'react'
import { cancelCreativeStep, getCreativeRunGraph } from '../api/creativeRunApi'
import { APPROVAL_POLL_MS, CREATIVE_TERMINAL_STATUSES } from '../constants/changeSet'
import { AGENT_PROFILE_LABEL, CREATIVE_PLAN_STATUS_LABEL, CREATIVE_STEP_STATUS_LABEL, CREATIVE_STEP_TERMINAL } from '../constants/collaboration'
import './creative-run-graph.css'

/** 通过持久化快照恢复协作树和依赖；有界轮询补偿断线，不拼接内部 Agent 文本。 */
export default function CreativeRunGraph({ accessToken, runId }) {
  const [graph, setGraph] = useState(null)
  const [error, setError] = useState('')
  const [stopping, setStopping] = useState(false)
  const scope = useRef(0)
  useEffect(() => {
    if (!runId) return undefined
    scope.current += 1
    let stopped = false
    let timer
    setGraph(null); setError(''); setStopping(false)
    const refresh = async () => {
      try {
        const snapshot = await getCreativeRunGraph(accessToken, runId)
        if (stopped) return
        setGraph(snapshot); setError('')
        if (!CREATIVE_TERMINAL_STATUSES.has(snapshot.status)) timer = window.setTimeout(refresh, APPROVAL_POLL_MS)
      } catch (failure) {
        if (!stopped) { setError(failure.message); timer = window.setTimeout(refresh, APPROVAL_POLL_MS) }
      }
    }
    refresh()
    return () => { stopped = true; scope.current += 1; window.clearTimeout(timer) }
  }, [accessToken, runId])

  /** 停止作用于整条协作链，按钮直接说明范围，避免误解为只取消一个分支。 */
  const stop = async (stepId) => {
    const expected = scope.current
    setStopping(true); setError('')
    try { await cancelCreativeStep(accessToken, runId, stepId) }
    catch (failure) { if (scope.current === expected) setError(failure.message) }
    finally { if (scope.current === expected) setStopping(false) }
  }
  const namedRoot = graph?.nodes.some((node) => node.sandbox || (node.profileId && node.profileId !== node.profile))
  if (!runId || (!graph?.plans.length && !namedRoot && !error)) return null
  const workers = graph?.nodes.filter((node) => node.parentStepId && node.profile) || []
  const progressNodes = graph?.plans.length ? workers : graph?.nodes.filter((node) => node.profile) || []
  const complete = progressNodes.filter((node) => node.status === 'COMPLETED').length
  const labels = new Map(graph?.nodes.map((node) => [node.stepId, node.nodeKey || AGENT_PROFILE_LABEL[node.profile] || '汇合节点']))
  return <section className="creative-run-graph" aria-label="AI 协作计划">
    <h4>{graph?.plans.length ? 'AI 协作' : 'Agent 执行'} · 已完成 {complete}/{progressNodes.length} 个步骤</h4>
    {error && <p role="alert">{error}</p>}
    {graph?.plans.map((plan) => <p className="creative-plan-title" key={plan.planId}>{plan.title} · 第 {plan.round} 轮 · {CREATIVE_PLAN_STATUS_LABEL[plan.status] || plan.status}</p>)}
    <ol>
      {graph?.nodes.map((node) => <li key={node.stepId} className={`creative-step creative-step-${node.status.toLowerCase()}`}>
        <div><strong>{node.displayName || AGENT_PROFILE_LABEL[node.profile] || '全部成功汇合'}</strong>
          <span>{CREATIVE_STEP_STATUS_LABEL[node.status] || node.status}</span></div>
        {node.nodeKey && <small>{node.nodeKey} · 第 {node.depth} 层</small>}
        {node.profileId?.startsWith('runtime-') && <small>运行时临时专业 Agent · 只读 · 禁止继续委派</small>}
        {node.dependsOn.length > 0 && <small>前置：{node.dependsOn.map((id) => labels.get(id) || id).join('、')}</small>}
        {node.waitingReason && <p>{node.waitingReason}</p>}
        {node.resultSummary && <p>{node.resultSummary}</p>}
        {node.verdict && <p>评审：{node.verdict === 'PASS' ? '通过' : '需要修订'}</p>}
        {node.issues?.length > 0 && <ul>{node.issues.map((issue, index) => <li key={index}>{issue}</li>)}</ul>}
        {node.artifactIds?.length > 0 && <small>产物：{node.artifactIds.join('、')}</small>}
        {node.errorMessage && <p role="alert">{node.errorMessage}</p>}
        <details><summary>步骤信息</summary><small>{node.stepId} · {node.profileId} · v{node.profileVersion} · 代次 {node.ownerEpoch} · 尝试 {node.attemptNo}{node.sandbox ? ' · 只读测试' : ''}</small></details>
        {!CREATIVE_STEP_TERMINAL.has(node.status) && !CREATIVE_TERMINAL_STATUSES.has(graph.status) &&
          <button type="button" disabled={stopping || node.status === 'CANCEL_REQUESTED'} onClick={() => stop(node.stepId)}>停止此步骤及整个任务</button>}
      </li>)}
    </ol>
    {graph?.budget && <small>模型执行 {graph.budget.usedInvocations}/{graph.budget.maxInvocations} 次 · 最大同时执行 {graph.budget.maxParallel} 步 · 推理轮数预留 {graph.budget.reservedTurns}/{graph.budget.maxReservedTurns}</small>}
  </section>
}
