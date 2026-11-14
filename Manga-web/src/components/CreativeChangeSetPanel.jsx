import { useCallback, useEffect, useRef, useState } from 'react'
import { decideCreativeChangeSet, getCreativeChangeSet, listCreativeChangeSets } from '../api/creativeChangeSetApi'
import { APPROVAL_DECISION, APPROVAL_STATUS, APPROVAL_POLL_MS, CHANGE_SET_KIND, CHANGE_SET_STATUS, CHANGE_SET_STATUS_LABEL } from '../constants/changeSet'
import { scriptDifferences } from './changeSetPreview'
import './creative-change-set.css'
import { notifyCreativeProjectChanged } from './creativeProjectEvents'

/** 会话和手动任务共用审批面板；差异、评论和最终状态都由服务端恢复。 */
export default function CreativeChangeSetPanel({ accessToken, conversationId, runId, onDecided, onRegenerate }) {
  const [proposals, setProposals] = useState([])
  const [error, setError] = useState('')
  const [hasMore, setHasMore] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const scope = useRef(0)
  const version = useRef(0)
  const earlierLoaded = useRef(false)
  const knownStatuses = useRef(new Map())

  /** 避免切换登录身份或会话后应用旧请求响应。 */
  const refresh = useCallback(async () => {
    const expectedScope = scope.current
    const requestVersion = ++version.current
    const page = await listCreativeChangeSets(accessToken, { conversationId, runId })
    if (expectedScope !== scope.current || requestVersion !== version.current) return
    for (const proposal of page) {
      const previous = knownStatuses.current.get(proposal.changeSetId)
      if (previous && previous !== CHANGE_SET_STATUS.APPLIED && proposal.status === CHANGE_SET_STATUS.APPLIED) {
        notifyCreativeProjectChanged(proposal.projectId)
      }
      knownStatuses.current.set(proposal.changeSetId, proposal.status)
    }
    setProposals((current) => {
      const oldest = page.at(-1)?.id
      const previous = oldest ? current.filter((proposal) => proposal.id < oldest) : []
      return [...new Map([...previous, ...page].map((proposal) => [proposal.changeSetId, proposal])).values()]
        .sort((left, right) => right.id - left.id)
    })
    if (!earlierLoaded.current) setHasMore(page.length === 100)
  }, [accessToken, conversationId, runId])

  /** 轮询只获取事实快照，SSE 丢失或刷新页面也能恢复待审批卡片。 */
  useEffect(() => {
    const currentScope = ++scope.current
    earlierLoaded.current = false
    knownStatuses.current = new Map()
    setProposals([]); setHasMore(false); setError('')
    if (!accessToken || (!conversationId && !runId)) return undefined
    let timer
    const update = async () => {
      try { await refresh() }
      catch (failure) { if (scope.current === currentScope) setError(failure.message) }
      finally { if (scope.current === currentScope) timer = window.setTimeout(update, APPROVAL_POLL_MS) }
    }
    update()
    return () => { scope.current += 1; window.clearTimeout(timer) }
  }, [accessToken, conversationId, runId, refresh])

  /** 读取更早提案，审批卡片按稳定标识去重。 */
  const loadMore = async () => {
    const expected = scope.current
    setLoadingMore(true)
    try {
      const page = await listCreativeChangeSets(accessToken, { conversationId, runId, beforeId: proposals.at(-1)?.id })
      if (scope.current !== expected) return
      earlierLoaded.current = true
      setProposals((current) => [...new Map([...current, ...page].map((proposal) => [proposal.changeSetId, proposal])).values()]
        .sort((left, right) => right.id - left.id))
      setHasMore(page.length === 100)
    } catch (failure) { if (scope.current === expected) setError(failure.message) }
    finally { if (scope.current === expected) setLoadingMore(false) }
  }

  /** 决策后先读取持久化状态，再通知页面刷新实际业务数据。 */
  const decide = async (proposal, decision, comment) => {
    const expected = scope.current
    const result = await decideCreativeChangeSet(accessToken, proposal.changeSetId, decision, comment)
    if (result.status === APPROVAL_STATUS.APPROVED) notifyCreativeProjectChanged(proposal.projectId)
    if (scope.current !== expected) return
    if (result.status === APPROVAL_STATUS.APPROVED) knownStatuses.current.set(proposal.changeSetId, CHANGE_SET_STATUS.APPLIED)
    const latest = await getCreativeChangeSet(accessToken, proposal.changeSetId)
    if (scope.current !== expected) return
    setProposals((current) => current.map((value) => value.changeSetId === latest.changeSetId ? latest : value))
    await refresh()
    if (scope.current === expected) await onDecided?.()
  }

  return <section className="creative-proposals" aria-label="AI 变更提案">
    {proposals.map((proposal) => <ProposalCard key={proposal.changeSetId} proposal={proposal}
      onDecide={decide} onRegenerate={onRegenerate} />)}
    {hasMore && <button className="workspace-secondary" type="button" onClick={loadMore} disabled={loadingMore}>更早提案</button>}
    {error && <p role="alert">{error}</p>}
  </section>
}

/** 单条提案始终展示冻结内容，审批按钮只提交决定与评论。 */
function ProposalCard({ proposal, onDecide, onRegenerate }) {
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const pending = proposal.status === CHANGE_SET_STATUS.PENDING
  const retryable = [CHANGE_SET_STATUS.CONFLICTED, CHANGE_SET_STATUS.REJECTED, CHANGE_SET_STATUS.EXPIRED].includes(proposal.status)
  const decide = async (decision) => {
    setBusy(true); setError('')
    try { await onDecide(proposal, decision, comment) }
    catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  const video = proposal.kind === CHANGE_SET_KIND.VIDEO_GENERATION
  const media = video || proposal.kind === CHANGE_SET_KIND.IMAGE_GENERATION
  return <article className={`creative-proposal ${proposal.status.toLowerCase()}`}>
    <header><strong>{media ? (video ? '视频生成与费用确认' : '图片生成与费用确认') : proposal.kind === CHANGE_SET_KIND.SCRIPT ? (proposal.chapterId ? '章节改写提案' : '新章节提案') : '分镜草稿提案'}</strong>
      <span>{media && proposal.status === CHANGE_SET_STATUS.APPLIED ? '已批准，请查看生成作业' : CHANGE_SET_STATUS_LABEL[proposal.status] || proposal.status}</span></header>
    {proposal.kind === CHANGE_SET_KIND.SCRIPT
      ? <div>{scriptDifferences(proposal.before, proposal.proposed).map((difference) => <details key={difference.field} open={difference.changed}>
        <summary>{difference.label} · {difference.changed ? '有变化' : '无变化'}</summary>
        <div className="creative-diff"><div><small>修改前</small><pre>{difference.previous || '（空）'}</pre></div>
          <div><small>批准后</small><pre>{difference.next || '（空）'}</pre></div></div>
      </details>)}</div>
      : media ? <div className="image-generation-preview">
        <dl><dt>目标镜头</dt><dd>#{proposal.proposed.shotId} · {proposal.before?.title}</dd>
          <dt>供应商 / 模型</dt><dd>{video ? 'Agnes' : proposal.proposed.provider} / {proposal.proposed.model}</dd>
          <dt>配置</dt><dd>{proposal.proposed.configId ? `#${proposal.proposed.configId}` : '免费模拟器'}</dd>
          {proposal.proposed.configSnapshot?.baseUrl && <><dt>生成端点</dt><dd>{proposal.proposed.configSnapshot.baseUrl}</dd></>}
          <dt>输出</dt><dd>{video ? `720P · ${proposal.proposed.aspectRatio} · ${proposal.proposed.seconds} 秒` : `${proposal.proposed.width} × ${proposal.proposed.height} · 1 张 PNG`}</dd>
          <dt>预计费用</dt><dd>{proposal.proposed.estimatedCost} {proposal.proposed.currency}</dd></dl>
        <p>批准后才会提交，实际费用以供应商账单为准。已提交的作业可能无法远程取消。</p>
        <details open><summary>批准的画面提示词</summary><pre>{proposal.proposed.prompt}</pre></details>
        <p>生成结果保存为独立平台产物。</p>
      </div>
      : <div><p>保留已有 {proposal.before?.length || 0} 个镜头，追加 {proposal.proposed.shots.length} 个草稿。</p>
        {proposal.proposed.shots.map((shot, index) => <details key={index} open={index === 0}>
          <summary>新增 {index + 1} · {shot.title}</summary><dl>
            {Object.entries({ 场景: shot.sceneName, 景别: shot.shotType, 运镜: shot.cameraMovement, 时长: shot.durationSeconds,
              画面: shot.content, 对白: shot.dialogue, 音效: shot.soundEffect, 备注: shot.notes }).map(([label, value]) =>
              <div key={label}><dt>{label}</dt><dd>{value ?? '（空）'}</dd></div>)}
          </dl></details>)}</div>}
    {pending && <><small>审批截止：{proposal.expiresAt?.replace('T', ' ')}（有效期由服务端校验）</small>
      <label>审批评论<textarea maxLength={2000} rows={2} value={comment} onChange={(event) => setComment(event.target.value)} disabled={busy} /></label>
      <div className="creative-proposal-actions"><button className="workspace-secondary" type="button" disabled={busy} onClick={() => decide(APPROVAL_DECISION.REJECT)}>拒绝</button>
        <button className="workspace-primary" type="button" disabled={busy} onClick={() => decide(APPROVAL_DECISION.APPROVE)}>{busy ? '处理中…' : media ? '确认费用并批准生成' : '批准并保存'}</button></div></>}
    {proposal.status === CHANGE_SET_STATUS.CONFLICTED && <p role="alert">项目、章节或生成配置已经变化，本提案没有应用。请基于最新内容重新生成。</p>}
    {retryable && onRegenerate && <button className="workspace-secondary" type="button" onClick={() => onRegenerate(proposal)}>基于最新内容重新生成</button>}
    {proposal.comment && <p>审批评论：{proposal.comment}</p>}
    {proposal.decidedBy && <small>审批人 #{proposal.decidedBy} · {proposal.decidedAt?.replace('T', ' ')}</small>}
    <details className="creative-proposal-trace"><summary>来源记录</summary><small>Run：{proposal.runId}<br />Step：{proposal.stepId}<br />提案：{proposal.changeSetId}<br />基础项目版本：{proposal.baseProjectRevision}
      {proposal.baseChapterRevision != null && <> · 章节版本：{proposal.baseChapterRevision}</>}</small></details>
    {error && <p role="alert">{error}</p>}
  </article>
}
