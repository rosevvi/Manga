import { useEffect, useState } from 'react'
import { hasRunningCreativeTasks } from '../api/creativeRunApi'
import { CHAPTER_EXTRACTION_STARTED, OPEN_CREATIVE_TASK } from './creativeTaskEvents'

const ACTIVE_REFRESH_MS = 2000
const IDLE_REFRESH_MS = 5000

/** 关闭对话框时仍从服务端恢复任务状态，隐藏标签页暂停轮询。 */
export default function useCreativeTaskActivity(accessToken, activityVersion) {
  const [running, setRunning] = useState(false)
  useEffect(() => {
    if (!accessToken) return undefined
    let disposed = false
    let pending = false
    let timer

    async function refresh() {
      window.clearTimeout(timer)
      if (pending || document.hidden) return
      pending = true
      let delay = IDLE_REFRESH_MS
      try {
        const active = await hasRunningCreativeTasks(accessToken)
        if (disposed) return
        setRunning(active)
        if (active) delay = ACTIVE_REFRESH_MS
      } catch {
        // 保留上次结果，下次轮询恢复。
      } finally {
        pending = false
        if (!disposed && !document.hidden) timer = window.setTimeout(refresh, delay)
      }
    }
    void refresh()
    document.addEventListener('visibilitychange', refresh)
    window.addEventListener(OPEN_CREATIVE_TASK, refresh)
    window.addEventListener(CHAPTER_EXTRACTION_STARTED, refresh)
    return () => {
      disposed = true
      window.clearTimeout(timer)
      document.removeEventListener('visibilitychange', refresh)
      window.removeEventListener(OPEN_CREATIVE_TASK, refresh)
      window.removeEventListener(CHAPTER_EXTRACTION_STARTED, refresh)
    }
  }, [accessToken, activityVersion])
  return running
}
