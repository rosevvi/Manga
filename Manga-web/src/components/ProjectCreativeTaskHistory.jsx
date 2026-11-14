import { useEffect, useRef, useState } from 'react'
import { Bot } from 'lucide-react'
import { listProjectCreativeRuns } from '../api/creativeRunApi'
import { CREATIVE_PROJECT_CHANGED } from './creativeProjectEvents'
import { openCreativeTask, creativeTaskName, RUN_STATUS_LABEL } from './creativeTaskEvents'

/** 页面仅保留紧凑任务状态；详情、历史和审批统一在任务中心中查看。 */
export default function ProjectCreativeTaskHistory({ accessToken, projectId, reloadKey, onActiveChanged }) {
  const [task, setTask] = useState(null)
  const [error, setError] = useState('')
  const callback = useRef(onActiveChanged)
  callback.current = onActiveChanged
  useEffect(() => {
    let stopped = false
    let busy = false
    let timer
    let active = false
    setTask(null); setError('')
    const refresh = async () => {
      if (busy || stopped || document.hidden || !projectId) return
      busy = true
      window.clearTimeout(timer)
      try {
        const page = await listProjectCreativeRuns(accessToken, projectId)
        if (stopped) return
        setTask(page.activeRun || page.items[0] || null)
        active = Boolean(page.activeRun)
        callback.current?.(active)
        setError('')
      } catch (failure) { if (!stopped) setError(failure.message) }
      finally { busy = false; if (!stopped) timer = window.setTimeout(refresh, active ? 5000 : 30000) }
    }
    const changed = (event) => { if (String(event.detail?.projectId) === String(projectId)) void refresh() }
    void refresh()
    window.addEventListener(CREATIVE_PROJECT_CHANGED, changed)
    document.addEventListener('visibilitychange', refresh)
    return () => { stopped = true; window.clearTimeout(timer); window.removeEventListener(CREATIVE_PROJECT_CHANGED, changed); document.removeEventListener('visibilitychange', refresh) }
  }, [accessToken, projectId, reloadKey])
  return <section className="compact-task-status" aria-label="项目创作任务">
    <div><Bot size={16} /><span>{task ? `${creativeTaskName(task)} · ${RUN_STATUS_LABEL[task.status] || task.status}` : '创作任务与历史记录'}</span></div>
    <button type="button" className="workspace-secondary" onClick={() => openCreativeTask(task?.runId, projectId)}>查看任务</button>
    {error && <small role="alert">{error}</small>}
  </section>
}
