import { useEffect, useRef, useState } from 'react'
import { acknowledgeUnknownJob, cancelGenerationJob, listGenerationJobs, previewArtifactAdoption, adoptGeneratedArtifact, getGeneratedMediaDownload } from '../api/generationApi'
import { resolveMediaUrl } from '../api/mediaUrl'
import MediaImage from './MediaImage'
import { CREATIVE_PROJECT_CHANGED, notifyCreativeProjectChanged } from './creativeProjectEvents'
import { REVISION_CONFLICT_CODE } from '../constants/changeSet'
import { GENERATION_PAGE_SIZE, GENERATION_POLL_MS, GENERATION_STATUS_LABEL, GENERATION_TERMINAL } from '../constants/generation'
import './generation.css'

/** 所有卡片从持久化快照恢复；分页期间也轮询正在查看的页。 */
export default function GenerationJobsPanel({ accessToken, projectId, runId, conversationId, shotId, mediaType, compact = false }) {
  const [jobs, setJobs] = useState([])
  const [cursors, setCursors] = useState([null])
  const [error, setError] = useState('')
  const [refreshKey, setRefreshKey] = useState(0)
  const scope = useRef(0)
  const cursor = cursors.at(-1)
  useEffect(() => { setCursors([null]); setJobs([]) }, [accessToken, projectId, runId, conversationId, shotId, mediaType])
  useEffect(() => {
    const expected = ++scope.current
    let busy = false
    let timer
    setJobs([]); setError('')
    if (!accessToken || !projectId) return undefined
    const update = async () => {
      if (busy) return
      window.clearTimeout(timer)
      busy = true
      try {
        const result = await listGenerationJobs(accessToken, { projectId, runId, conversationId, beforeId: cursor, shotId, mediaType })
        if (scope.current === expected) {
          setJobs(result); setError('')
          if (result.some((job) => !GENERATION_TERMINAL.has(job.status))) timer = window.setTimeout(update, GENERATION_POLL_MS)
        }
      } catch (failure) {
        if (scope.current === expected) { setError(failure.message); timer = window.setTimeout(update, GENERATION_POLL_MS) }
      }
      finally { busy = false }
    }
    update()
    const changed = (event) => { if (String(event.detail?.projectId) === String(projectId)) void update() }
    window.addEventListener(CREATIVE_PROJECT_CHANGED, changed)
    return () => { scope.current += 1; window.clearTimeout(timer); window.removeEventListener(CREATIVE_PROJECT_CHANGED, changed) }
  }, [accessToken, projectId, runId, conversationId, cursor, refreshKey, shotId, mediaType])
  return <section className="generation-jobs" aria-label="媒体生成作业">
    {!compact && (jobs.length > 0 || error || cursors.length > 1) && <h3>媒体生成作业</h3>}
    {(compact ? jobs.filter((job) => job.artifactUrl).slice(0, 3) : jobs).map((job) => <GenerationCard key={job.jobId} job={job} accessToken={accessToken} onChanged={() => setRefreshKey((value) => value + 1)} />)}
    {(!jobs.length || compact && !jobs.some((job) => job.artifactUrl)) && <p className="workbench-muted">暂无生成产物。选择镜头和模型，创建媒体提案后在任务中心批准。</p>}
    {(cursors.length > 1 || jobs.length === GENERATION_PAGE_SIZE) && <div className="generation-actions">
      {cursors.length > 1 && <button className="workspace-secondary" type="button" onClick={() => setCursors((value) => value.slice(0, -1))}>较新作业</button>}
      {jobs.length === GENERATION_PAGE_SIZE && <button className="workspace-secondary" type="button" onClick={() => setCursors((value) => [...value, jobs.at(-1).id])}>更早作业</button>}
    </div>}
    {error && <p role="alert">{error}</p>}
  </section>
}

/** 不提供未知提交的重试按钮，核对操作需要用户明确声明。 */
function GenerationCard({ job, accessToken, onChanged }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [checked, setChecked] = useState(false)
  const [note, setNote] = useState('')
  const [adoption, setAdoption] = useState(null)
  const [adoptionConflict, setAdoptionConflict] = useState(false)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  const elapsed = Math.max(0, Math.floor(((job.finishedAt ? new Date(job.finishedAt).getTime() : Date.now()) - new Date(job.submittedAt || job.createdAt).getTime()) / 1000))
  const act = async (operation) => {
    setBusy(true); setError('')
    try { await operation(); onChanged() }
    catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  /** 先读取当前事实，再由用户明确确认替换，不在查看产物时自动写业务数据。 */
  const previewAdoption = async () => {
    setBusy(true); setError('')
    try {
      const preview = await previewArtifactAdoption(accessToken, job.jobId)
      if (mounted.current) { setAdoption(preview); setAdoptionConflict(false) }
    } catch (failure) { if (mounted.current) setError(failure.message) }
    finally { if (mounted.current) setBusy(false) }
  }
  /** 冲突后必须重新预览再确认；网络失败可用同一版本重放，不能静默覆盖最新编辑。 */
  const confirmAdoption = async () => {
    if (!adoption || adoptionConflict || busy) return
    setBusy(true); setError('')
    try {
      await adoptGeneratedArtifact(accessToken, job.jobId, adoption.target.projectRevision)
      notifyCreativeProjectChanged(job.projectId)
      if (mounted.current) { setAdoption(null); onChanged() }
    } catch (failure) {
      if (mounted.current) { setError(failure.message); if (failure.code === REVISION_CONFLICT_CODE) setAdoptionConflict(true) }
    } finally { if (mounted.current) setBusy(false) }
  }
  const video = job.provider === 'AGNES'
  const download = () => act(async () => {
    const result = await getGeneratedMediaDownload(accessToken, job.jobId)
    const url = resolveMediaUrl(result.url)
    if (!url) throw new Error('下载地址不可用，请重新加载产物')
    const anchor = document.createElement('a')
    anchor.href = url; anchor.download = `shot-${job.shotId}.mp4`; anchor.rel = 'noreferrer'
    document.body.appendChild(anchor); anchor.click(); anchor.remove()
  })
  return <article className="generation-card">
    <header><strong>镜头 #{job.shotId} · {job.provider}</strong><span>{GENERATION_STATUS_LABEL[job.status] || job.status}</span></header>
    {job.progress != null && <progress max="100" value={job.progress} aria-label={`生成进度 ${job.progress}%`} />}
    <small>{job.progress != null ? `${job.progress}% · ` : ''}耗时 {elapsed} 秒 · 更新于 {job.updatedAt?.replace('T', ' ')}</small>
    {job.cancelRequested && <p>已撤销结果采用，供应商可能继续执行并计费。</p>}
    {job.errorMessage && <p role="status">{job.errorMessage}</p>}
    {job.reconciliationNote && <p>核对说明：{job.reconciliationNote}</p>}
    {video && job.artifactUrl && <video controls preload="metadata" aria-label="镜头视频" src={resolveMediaUrl(job.artifactUrl)} />}
    {job.artifactUrl && <a href={resolveMediaUrl(job.artifactUrl) || undefined} target="_blank" rel="noreferrer">
      {!video && <MediaImage src={job.artifactUrl} alt={`镜头 ${job.shotId} 的生成产物`} showErrorText />}
      <span>查看平台产物 #{job.artifactId}</span>
    </a>}
    {video && job.artifactUrl && <button type="button" className="workspace-secondary" disabled={busy} onClick={download}>下载视频</button>}
    {job.artifactUrl && !video && <button className="workspace-secondary" type="button" disabled={busy} onClick={previewAdoption}>用作镜头图片</button>}
    {job.adoptedAt && <small>最近采用：{job.adoptedAt.replace('T', ' ')} · 用户 #{job.adoptedBy}</small>}
    {adoption && <section className="generation-adoption" aria-label="确认采用图片">
      <strong>替换镜头「{adoption.target.title}」的图片</strong>
      <div className="generation-adoption-images"><div><small>当前图片</small>
        <MediaImage src={adoption.target.imageUrl} alt="镜头当前图片" fallback={<p>暂无图片</p>} showErrorText /></div>
        <div><small>拟采用图片</small><MediaImage src={adoption.artifactUrl} alt="拟采用的生成图片" showErrorText /></div></div>
      <p>只替换该镜头图片，不修改正文、对白或备注。预览后若项目发生变化，需要重新确认。</p>
      {adoptionConflict && <p role="alert">项目已被修改，尚未采用。请重新预览最新图片。</p>}
      <div className="generation-actions"><button className="workspace-secondary" type="button" disabled={busy} onClick={() => { setAdoption(null); setAdoptionConflict(false) }}>取消</button>
        {adoptionConflict ? <button className="workspace-secondary" type="button" disabled={busy} onClick={previewAdoption}>重新预览</button>
          : <button className="workspace-primary" type="button" disabled={busy} onClick={confirmAdoption}>确认采用</button>}</div>
    </section>}
    {!GENERATION_TERMINAL.has(job.status) && !job.cancelRequested && <button className="workspace-secondary" type="button" disabled={busy} onClick={() => act(() => cancelGenerationJob(accessToken, job.jobId))}>取消结果采用</button>}
    {job.status === 'UNKNOWN' && <div className="generation-reconcile">
      <p>系统不会自动重新提交。请在供应商核对执行结果及账单，再结束平台等待。</p>
      <label><input type="checkbox" checked={checked} onChange={(event) => setChecked(event.target.checked)} />我已核对供应商，理解此操作不会退款或远程取消</label>
      <textarea rows={2} maxLength={2000} placeholder="核对说明（请勿填写密钥）" value={note} onChange={(event) => setNote(event.target.value)} />
      <button className="workspace-secondary" type="button" disabled={busy || !checked || !note.trim()} onClick={() => act(() => acknowledgeUnknownJob(accessToken, job.jobId, note))}>记录核对并结束等待</button>
    </div>}
    <details><summary>来源记录</summary><small>Run：{job.runId}<br />Step：{job.stepId}<br />提案：{job.changeSetId}<br />Job：{job.jobId}</small></details>
    {error && <p role="alert">{error}</p>}
  </article>
}
