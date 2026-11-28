import { useEffect, useRef, useState } from 'react'
import { ChevronRight, RotateCcw, ScanText } from 'lucide-react'
import { getChapterExtraction } from '../api/projectAssetApi'
import { createManualRun } from '../api/creativeRunApi'
import { CREATIVE_OPERATION, CREATIVE_PERMISSION, CREATIVE_RESOURCE } from '../constants/creative'
import { CREATIVE_TERMINAL_STATUSES } from '../constants/changeSet'
import { notifyCreativeProjectChanged } from './creativeProjectEvents'
import { CHAPTER_EXTRACTION_STARTED, openCreativeTask, RUN_STATUS_LABEL } from './creativeTaskEvents'

const STATUS_POLL_INTERVAL_MS = 3000
const STATUS_RETRY_INTERVAL_MS = 5000
const STATUS_RETRY_LIMIT = 3

/** 跟踪本章持久化识别任务，在提交完成后刷新资产和章节摘要。 */
export default function ChapterAssetExtractionStatus({ accessToken, projectId, chapterId, onComplete }) {
  const [attempt, setAttempt] = useState(null)
  const [loading, setLoading] = useState(true)
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const completed = useRef(null)
  useEffect(() => {
    const onRetry = ({ detail }) => {
      if (String(detail.projectId) === String(projectId) && String(detail.chapterId) === String(chapterId)) {
        setReload((value) => value + 1)
      }
    }
    window.addEventListener(CHAPTER_EXTRACTION_STARTED, onRetry)
    return () => window.removeEventListener(CHAPTER_EXTRACTION_STARTED, onRetry)
  }, [projectId, chapterId])
  useEffect(() => {
    let active = true
    let timer
    let failedReads = 0
    async function check() {
      try {
        const latest = await getChapterExtraction(accessToken, projectId, chapterId)
        if (!active) return
        failedReads = 0
        setAttempt(latest)
        setError('')
        if (latest && !CREATIVE_TERMINAL_STATUSES.has(latest.status)) {
          timer = window.setTimeout(check, STATUS_POLL_INTERVAL_MS)
        } else if (latest?.status === 'COMPLETED' && completed.current !== latest.runId) {
          completed.current = latest.runId
          void onComplete()
          notifyCreativeProjectChanged(projectId)
        }
      } catch (failure) {
        if (!active) return
        const transient = failure instanceof TypeError || failure.status === 429 || failure.status >= 500
        if (transient && failedReads < STATUS_RETRY_LIMIT) {
          failedReads += 1
          setError('暂时无法读取识别状态，正在重试。')
          timer = window.setTimeout(check, STATUS_RETRY_INTERVAL_MS)
        } else {
          setError(failure.message)
        }
      } finally {
        if (active) setLoading(false)
      }
    }
    void check()
    return () => { active = false; window.clearTimeout(timer) }
  }, [accessToken, projectId, chapterId, reload, onComplete])

  async function start() {
    setStarting(true)
    setError('')
    try {
      const run = await createManualRun(accessToken, { projectId, requestId: globalThis.crypto.randomUUID(),
        operationType: CREATIVE_OPERATION.EXTRACT_CHAPTER_ASSETS, permissionPolicy: CREATIVE_PERMISSION.AUTOMATION,
        target: { resourceType: CREATIVE_RESOURCE.CHAPTER, resourceId: chapterId }, instruction: '识别当前章节资产并登记。' })
      setAttempt(run)
      setReload((value) => value + 1)
    } catch (failure) {
      if (failure.code === 'ACTIVE_RUN_CONFLICT') setReload((value) => value + 1)
      else setError(failure.message)
    } finally { setStarting(false) }
  }

  const running = attempt && !CREATIVE_TERMINAL_STATUSES.has(attempt.status)
  return <div className="chapter-extraction">
    <div className="workbench-action-row">
      {attempt && <button type="button" className="workspace-secondary chapter-extraction-details"
        data-failed={attempt.status === 'FAILED' || undefined} onClick={() => openCreativeTask(attempt.runId, projectId)}>
        <ScanText size={15} aria-hidden="true" />资产识别 · {RUN_STATUS_LABEL[attempt.status]}<ChevronRight size={14} aria-hidden="true" /></button>}
      {!running && !loading && <button type="button" className="workspace-primary" disabled={starting} onClick={start}>
        {attempt ? <RotateCcw size={15} aria-hidden="true" /> : <ScanText size={15} aria-hidden="true" />}
        {attempt ? '重新识别' : '识别章节资产'}</button>}
    </div>
    <div aria-live="polite">
      {running && attempt.status !== 'CANCEL_REQUESTED' && <p className="chapter-extraction-note">
        {attempt.status === 'QUEUED' ? '章节资产识别已排队。' : 'AI 正在识别本章的角色、场景和道具。'}完成后会自动更新本页。
      </p>}
    </div>
    {(error || attempt?.errorMessage) && <p className="workspace-alert" role="alert">{error || attempt.errorMessage}
      {error && <button type="button" className="workspace-secondary" onClick={() => setReload((value) => value + 1)}>刷新状态</button>}</p>}
  </div>
}
