import { useEffect, useState } from 'react'
import { getProjectScriptAnalysis } from '../api/projectApi'
import { CREATIVE_TERMINAL_STATUSES } from '../constants/changeSet'

const ANALYSIS_POLL_MS = 2000

/** 在剧本工作区恢复解析结果，只在运行中串行查询后续进度。 */
export default function useProjectScriptAnalysis(accessToken, projectId, scriptId) {
  const [state, setState] = useState(null)
  const [reload, setReload] = useState(0)
  const current = state?.scriptId === scriptId ? state : null
  const { latestRun, report, error } = current || {}
  const running = Boolean(latestRun && !CREATIVE_TERMINAL_STATUSES.has(latestRun.status))

  useEffect(() => {
    if (!scriptId) return undefined
    let stopped = false
    let timer
    const poll = async () => {
      try {
        const result = await getProjectScriptAnalysis(accessToken, projectId)
        if (stopped) return
        setState({ scriptId, ...result })
        if (result.latestRun && !CREATIVE_TERMINAL_STATUSES.has(result.latestRun.status)) {
          timer = window.setTimeout(poll, ANALYSIS_POLL_MS)
        }
      } catch (failure) {
        if (stopped) return
        setState((previous) => ({
          ...(previous?.scriptId === scriptId ? previous : {}),
          scriptId,
          error: failure.message,
        }))
        timer = window.setTimeout(poll, ANALYSIS_POLL_MS)
      }
    }
    void poll()
    return () => { stopped = true; window.clearTimeout(timer) }
  }, [accessToken, projectId, scriptId, reload])

  /** 立即显示受理状态，保留上次成功的报告直到新结果发布。 */
  const track = (run) => {
    setState((previous) => ({
      scriptId,
      report: previous?.scriptId === scriptId ? previous.report : null,
      latestRun: run,
    }))
    setReload((value) => value + 1)
  }

  return { latestRun, report, running, error, loading: Boolean(scriptId && !current), track }
}
