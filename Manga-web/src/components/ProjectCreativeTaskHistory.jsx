import { useEffect, useRef, useState } from 'react'
import { listProjectCreativeRuns } from '../api/creativeRunApi'
import { APPROVAL_POLL_MS } from '../constants/changeSet'
import { CREATIVE_OPERATION } from '../constants/creative'

/** 按项目从数据库恢复任务，包括已生成提案和仍在执行的任务。 */
export default function ProjectCreativeTaskHistory({ accessToken, projectId, selectedRunId, reloadKey, onSelect, onActiveChanged }) {
  const [items, setItems] = useState([])
  const [cursor, setCursor] = useState(null)
  const [error, setError] = useState('')
  const [loadingMore, setLoadingMore] = useState(false)
  const callbacks = useRef({ selectedRunId, onSelect, onActiveChanged })
  callbacks.current = { selectedRunId, onSelect, onActiveChanged }
  const scope = useRef(0)
  const earlierLoaded = useRef(false)

  /** 首次优先恢复活动任务，后续轮询保持用户选择的历史任务。 */
  useEffect(() => {
    const current = ++scope.current
    let busy = false
    setItems([]); setCursor(null); setError(''); setLoadingMore(false); earlierLoaded.current = false
    const refresh = async () => {
      if (busy) return
      busy = true
      try {
        const page = await listProjectCreativeRuns(accessToken, projectId)
        if (scope.current !== current) return
        const incoming = page.activeRun ? [...page.items, page.activeRun] : page.items
        setItems((previous) => [...new Map([...previous, ...incoming].map((item) => [item.runId, item])).values()]
          .sort((left, right) => right.id - left.id))
        if (!earlierLoaded.current) setCursor(page.nextBeforeId)
        callbacks.current.onActiveChanged(Boolean(page.activeRun))
        if (!callbacks.current.selectedRunId) callbacks.current.onSelect(page.activeRun?.runId || page.items[0]?.runId || null)
        setError('')
      } catch (failure) { if (scope.current === current) setError(failure.message) }
      finally { busy = false }
    }
    refresh()
    const timer = window.setInterval(refresh, APPROVAL_POLL_MS)
    return () => { scope.current += 1; window.clearInterval(timer) }
  }, [accessToken, projectId, reloadKey])

  /** 用稳定主键查询更早任务，不要求浏览器预先知道 Run ID。 */
  const more = async () => {
    const current = scope.current
    setLoadingMore(true)
    try {
      const page = await listProjectCreativeRuns(accessToken, projectId, cursor)
      if (scope.current !== current) return
      earlierLoaded.current = true
      setItems((previous) => [...new Map([...previous, ...page.items].map((item) => [item.runId, item])).values()]
        .sort((left, right) => right.id - left.id))
      setCursor(page.nextBeforeId)
    } catch (failure) { if (scope.current === current) setError(failure.message) }
    finally { if (scope.current === current) setLoadingMore(false) }
  }

  return <section className="script-import-panel">
    <h3>手动创作任务</h3>
    <select aria-label="选择项目创作任务" value={selectedRunId || ''} onChange={(event) => onSelect(event.target.value || null)}>
      <option value="">选择任务</option>
      {items.map((item) => <option key={item.runId} value={item.runId}>
        {item.operationType === CREATIVE_OPERATION.GENERATE_SHOT_IMAGE ? `镜头 ${item.targetId} 图片`
          : item.operationType === CREATIVE_OPERATION.GENERATE_SCRIPT_CHAPTER ? '章节提案' : `章节 ${item.targetId} 分镜`}
        {' · '}{item.status}{' · '}{item.createdAt?.replace('T', ' ')}
      </option>)}
    </select>
    {cursor && <button type="button" disabled={loadingMore} onClick={more}>更早任务</button>}
    {error && <p role="alert">{error}</p>}
  </section>
}
