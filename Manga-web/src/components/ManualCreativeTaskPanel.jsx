import { useEffect, useRef, useState } from 'react'
import { cancelCreativeRun, createManualRun, getCreativeRun } from '../api/creativeRunApi'
import { APPROVAL_POLL_MS, CHANGE_SET_KIND, CREATIVE_TERMINAL_STATUSES } from '../constants/changeSet'
import CreativeChangeSetPanel from './CreativeChangeSetPanel'
import { regenerationInstruction } from './changeSetPreview'
import { CREATIVE_OPERATION, CREATIVE_PERMISSION, CREATIVE_RESOURCE } from '../constants/creative'

/** 手动创作任务入口与会话独立，刷新后用持久化 Run ID 继续查看和审批。 */
export default function ManualCreativeTaskPanel({ accessToken, projectId, runId, onRunChanged, onApplied, onStatusChanged }) {
  const [run, setRun] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const scope = useRef(0)
  useEffect(() => {
    let stopped = false
    scope.current += 1
    setRun(null); setError('')
    const refresh = async () => {
      try {
        const snapshot = await getCreativeRun(accessToken, runId)
        if (!stopped) { setRun(snapshot); onStatusChanged?.(snapshot.status) }
      } catch (failure) { if (!stopped) setError(failure.message) }
    }
    refresh()
    const timer = window.setInterval(refresh, APPROVAL_POLL_MS)
    return () => { stopped = true; scope.current += 1; window.clearInterval(timer) }
  }, [accessToken, runId, onStatusChanged])

  /** 冲突和拒绝后启动新 Run，旧提案及其审批记录完整保留。 */
  const regenerate = async (proposal) => {
    if (busy || !CREATIVE_TERMINAL_STATUSES.has(run?.status)) return
    const expected = scope.current
    setBusy(true); setError('')
    try {
      const storyboard = proposal.kind === CHANGE_SET_KIND.STORYBOARD
      const next = await createManualRun(accessToken, {
        requestId: globalThis.crypto.randomUUID(), projectId, permissionPolicy: CREATIVE_PERMISSION.PROPOSE_CHANGES,
        operationType: storyboard ? CREATIVE_OPERATION.GENERATE_CHAPTER_STORYBOARD : CREATIVE_OPERATION.GENERATE_SCRIPT_CHAPTER,
        target: { resourceType: storyboard ? CREATIVE_RESOURCE.CHAPTER : CREATIVE_RESOURCE.PROJECT, resourceId: storyboard ? proposal.chapterId : projectId },
        instruction: regenerationInstruction(proposal),
      })
      if (scope.current === expected) onRunChanged(next.runId)
    } catch (failure) { if (scope.current === expected) setError(failure.message) }
    finally { if (scope.current === expected) setBusy(false) }
  }

  /** 停止手动任务，审批同步失效，已应用的结果仍可查看。 */
  const cancel = async () => {
    setBusy(true); setError('')
    try { await cancelCreativeRun(accessToken, runId); setRun(await getCreativeRun(accessToken, runId)) }
    catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }

  return <section className="script-analysis-panel">
    <h3>AI 创作任务 · {run?.status === 'WAITING' ? '等待审批' : run?.status || '正在加载'}</h3>
    {run && !CREATIVE_TERMINAL_STATUSES.has(run.status) && <button type="button" disabled={busy} onClick={cancel}>停止任务</button>}
    <CreativeChangeSetPanel accessToken={accessToken} runId={runId} onDecided={onApplied}
      onRegenerate={CREATIVE_TERMINAL_STATUSES.has(run?.status) && !busy ? regenerate : undefined} />
    {run?.errorMessage && <p role="alert">{run.errorMessage}</p>}
    {error && <p role="alert">{error}</p>}
  </section>
}
