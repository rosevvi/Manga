import { useEffect, useState } from 'react'
import { ChevronRight, ListTodo } from 'lucide-react'
import { listProjectCreativeRuns } from '../api/creativeRunApi'
import { RUN_STATUS_LABEL, creativeTaskName } from './creativeTaskEvents'

const TASK_REFRESH_INTERVAL_MS = 4000

/** 项目任务会话按项目读取后台任务，包括接入会话之前的执行历史。 */
export default function ProjectTaskConversation({ accessToken, projectId, onSelect }) {
  const [recent, setRecent] = useState(null)
  const [earlier, setEarlier] = useState(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    let timer
    async function refresh() {
      try {
        const page = await listProjectCreativeRuns(accessToken, projectId)
        if (active) {
          setRecent(page)
          setError('')
        }
      } catch (failure) {
        if (active) setError(failure.message)
      } finally {
        if (active) timer = window.setTimeout(refresh, TASK_REFRESH_INTERVAL_MS)
      }
    }
    void refresh()
    return () => { active = false; window.clearTimeout(timer) }
  }, [accessToken, projectId])

  const cursor = earlier ? earlier.nextBeforeId : recent?.nextBeforeId
  async function loadMore() {
    if (loadingMore || !cursor) return
    setLoadingMore(true)
    try {
      const page = await listProjectCreativeRuns(accessToken, projectId, cursor)
      setEarlier((previous) => ({ items: [...(previous?.items ?? []), ...page.items], nextBeforeId: page.nextBeforeId }))
      setError('')
    } catch (failure) {
      setError(failure.message)
    } finally { setLoadingMore(false) }
  }

  const tasks = new Map((earlier?.items ?? []).map((task) => [task.runId, task]))
  recent?.items.forEach((task) => tasks.set(task.runId, task))
  if (recent?.activeRun) tasks.set(recent.activeRun.runId, recent.activeRun)
  const items = [...tasks.values()].sort((left, right) => right.id - left.id)
  return <section className="project-task-history" aria-label="项目任务">
    <h2><ListTodo size={20} aria-hidden="true" />项目任务</h2>
    {error && <p className="workspace-alert" role="alert">{error}</p>}
    {items.length ? <ul>{items.map((task) => <li key={task.runId}>
      <button type="button" className="project-task-entry" onClick={() => onSelect(task.runId)}>
        <span>
          <strong>{creativeTaskName(task)}</strong>
          {task.createdAt && <time dateTime={task.createdAt}>{new Date(task.createdAt).toLocaleString()}</time>}
        </span>
        <span className={`task-status ${task.status.toLowerCase()}`}>{RUN_STATUS_LABEL[task.status]}</span>
        <ChevronRight size={15} aria-hidden="true" />
      </button>
    </li>)}</ul> : !error && <p className="workbench-muted">{recent ? '本项目暂无任务' : '正在读取任务…'}</p>}
    {cursor && <button type="button" className="task-load-more" disabled={loadingMore} onClick={loadMore}>加载更早任务</button>}
  </section>
}
