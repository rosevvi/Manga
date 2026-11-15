import { useEffect, useRef, useState } from 'react'
import { getCreativeRun, getCreativeRunGraph, getCreativeRunTimeline, getRepairDecisions, streamCreativeRunEvents } from '../api/creativeRunApi'
import { CREATIVE_TERMINAL_STATUSES } from '../constants/changeSet'
import { createSequenceBuffer, loadTimelineSince } from './creativeTimeline'
import { notifyCreativeProjectChanged } from './creativeProjectEvents'

/** 快照补充结构，SSE 直接推进事件；二者独立，慢快照不会阻止正文更新。 */
export default function useCreativeRunActivity(accessToken, runId, callbacks) {
  const [run, setRun] = useState(null)
  const [graph, setGraph] = useState(null)
  const [events, setEvents] = useState([])
  const [repairs, setRepairs] = useState([])
  const [error, setError] = useState('')
  const [appliedSequence, setAppliedSequence] = useState(0)
  const refreshRef = useRef(null)
  const callbackRef = useRef(callbacks)
  callbackRef.current = callbacks

  useEffect(() => {
    let stopped = false
    let terminal = false
    let snapshotPending = false
    let snapshotAgain = false
    let replayPending = false
    let replayAgain = false
    let latestRun
    let latestGraph
    let timer
    let reconnect
    let wakeTimer
    let controller
    const sequence = createSequenceBuffer()
    const all = new Map()
    setRun(null); setGraph(null); setEvents([]); setRepairs([]); setError(''); setAppliedSequence(0)

    const accept = (items) => {
      if (stopped) return
      const ready = sequence.push(items)
      if (!ready.length) return
      for (const item of ready) all.set(item.sequence, { ...item, runId })
      const current = [...all.values()]
      setEvents(current); setAppliedSequence(sequence.cursor)
      if (ready.some((item) => ['RUN_COMPLETED', 'RUN_FAILED', 'RUN_CANCELLED'].includes(item.type))) terminal = true
    }

    const replay = async () => {
      if (replayPending) { replayAgain = true; return }
      replayPending = true
      try {
        do {
          replayAgain = false
          await loadTimelineSince((after) => getCreativeRunTimeline(accessToken, runId, after), sequence.cursor,
            () => stopped, (items) => accept(items))
        } while (replayAgain && !stopped)
      } catch (failure) { if (!stopped) setError(failure.message) }
      finally { replayPending = false }
    }

    const snapshot = async () => {
      if (snapshotPending) { snapshotAgain = true; return }
      snapshotPending = true
      try {
        do {
          snapshotAgain = false
          await Promise.all([
            getCreativeRun(accessToken, runId).then((value) => {
              if (stopped) return
              if (latestRun && !CREATIVE_TERMINAL_STATUSES.has(latestRun.status)
                && CREATIVE_TERMINAL_STATUSES.has(value.status) && value.projectId) notifyCreativeProjectChanged(value.projectId)
              latestRun = value; setRun(value); callbackRef.current.onChanged?.(value)
            }),
            getCreativeRunGraph(accessToken, runId).then((value) => {
              if (stopped) return
              latestGraph = value; setGraph(value)
              if (sequence.cursor < value.throughSequence) void replay()
            }),
            getRepairDecisions(accessToken, runId).then((value) => { if (!stopped) setRepairs(value) }),
          ])
          if (!stopped) setError('')
        } while (snapshotAgain && !stopped)
      } catch (failure) { if (!stopped) setError(failure.message) }
      finally {
        snapshotPending = false
        if (!stopped && (!terminal || sequence.cursor < (latestGraph?.throughSequence || 0))) {
          window.clearTimeout(timer); timer = window.setTimeout(snapshot, 3000)
        }
      }
    }

    const connect = async () => {
      if (stopped || terminal) return
      controller = new AbortController()
      try {
        await streamCreativeRunEvents(accessToken, runId, sequence.cursor, (event) => {
          if (stopped) return
          if (event.activity) accept([{ ...event.activity, runId: event.runId }])
          if (!event.activity || sequence.hasGap) void replay()
          if (!['CONTENT_DELTA', 'REASONING_DELTA'].includes(event.eventType) && !wakeTimer) {
            wakeTimer = window.setTimeout(() => { wakeTimer = null; void snapshot() }, 300)
          }
        }, controller.signal)
      } catch (failure) { if (!stopped && !controller.signal.aborted) setError(failure.message) }
      if (!stopped && !terminal) { void replay(); reconnect = window.setTimeout(connect, 1500) }
    }

    refreshRef.current = async () => { await Promise.all([snapshot(), replay()]) }
    void snapshot(); void replay(); void connect()
    return () => {
      stopped = true; controller?.abort(); window.clearTimeout(timer); window.clearTimeout(reconnect)
      window.clearTimeout(wakeTimer); refreshRef.current = null
    }
  }, [accessToken, runId])

  return { run, graph, events, repairs, error, setError, appliedSequence, refreshRef }
}
