import { useState } from 'react'
import { decideRepair } from '../api/creativeRunApi'

const STATUS = { PENDING: '等待确认修订', CONFIRMED: '已授权修订', STOPPED: '已结束', EXPIRED: '确认已过期', CONFLICTED: '内容版本已变化' }

/** 确认只创建下一轮候选；正式保存仍由 ChangeSet 审批完成。 */
export default function CreativeRepairPanel({ accessToken, runId, decisions, onChanged }) {
  const [comments, setComments] = useState({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const decide = async (decision, action) => {
    setBusy(true); setError('')
    try {
      await decideRepair(accessToken, runId, decision.decisionId, { requestId: crypto.randomUUID(), action,
        comment: comments[decision.decisionId] || null })
      await onChanged()
    } catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  if (!decisions.length) return null
  return <section className="repair-decisions" aria-label="修订确认">{decisions.map((decision) => <article className="repair-decision" key={decision.decisionId}>
    <header><strong>第 {decision.round} 轮评审 · {STATUS[decision.status]}</strong><small>修订候选，不直接保存到项目</small></header>
    <ul>{decision.issues.map((issue, index) => <li key={index}>{issue}</li>)}</ul>
    <details><summary>查看候选草稿</summary><div className="repair-candidate">
      {decision.candidate?.shots ? decision.candidate.shots.map((shot, index) => <article key={index}>
        <strong>{index + 1}. {shot.title}</strong><small>{shot.sceneName} · {shot.shotType} · {shot.durationSeconds} 秒</small>
        <p>{shot.content}</p>{shot.dialogue && <p>对白 · {shot.dialogue}</p>}
      </article>) : <><strong>{decision.candidate?.title}</strong><p>{decision.candidate?.rawContent || decision.candidate?.synopsis || '本轮没有可展示的候选正文。'}</p></>}
    </div></details>
    {decision.status === 'PENDING' && <>
      <p className="process-muted">等待确认期间暂停执行计时。最迟确认：{decision.expiresAt?.replace('T', ' ')}</p>
      <textarea aria-label="补充修订意见" maxLength={2000} rows={3} placeholder="可补充希望调整的内容…"
        value={comments[decision.decisionId] || ''} onChange={(event) => setComments((current) => ({ ...current, [decision.decisionId]: event.target.value }))} />
      <div className="repair-actions"><button type="button" className="workspace-primary" disabled={busy} onClick={() => decide(decision, 'CONFIRM_REPAIR')}>确认修订</button>
        <button type="button" className="workspace-secondary" disabled={busy} onClick={() => decide(decision, 'STOP')}>结束任务</button></div>
    </>}
    {decision.status === 'CONFLICTED' && <p role="alert">项目或章节已变化，请通过任务顶部“重新生成”入口基于最新内容开始。</p>}
  </article>)}{error && <p role="alert">{error}</p>}</section>
}
