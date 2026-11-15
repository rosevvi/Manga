import { useEffect, useRef, useState } from 'react'
import { adoptGeneratedArtifact, previewArtifactAdoption } from '../api/generationApi'
import { getCreativeChangeSet } from '../api/creativeChangeSetApi'
import { resolveMediaUrl } from '../api/mediaUrl'
import { REVISION_CONFLICT_CODE } from '../constants/changeSet'
import { IMAGE_PROMPT_MAX_LENGTH } from '../constants/generation'
import { notifyCreativeProjectChanged } from './creativeProjectEvents'
import MediaImage from './MediaImage'

/** 在任务结果中展示采用前的参考图及重生成表单，写入时使用已展示的项目版本。 */
export default function TaskImageResultActions({ accessToken, job, onAdopted, onRegenerate, canRegenerate }) {
  const [preview, setPreview] = useState(null)
  const [previewVersion, setPreviewVersion] = useState(0)
  const [previewError, setPreviewError] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [conflicted, setConflicted] = useState(false)
  const [proposal, setProposal] = useState(null)
  const [prompt, setPrompt] = useState('')
  const action = useRef(false)
  const hasImage = Boolean(job.artifactUrl)

  useEffect(() => {
    if (!hasImage) return
    let disposed = false
    previewArtifactAdoption(accessToken, job.jobId).then((result) => {
      if (!disposed) { setPreview(result); setConflicted(false) }
    }).catch((failure) => { if (!disposed) setPreviewError(failure.message) })
    return () => { disposed = true }
  }, [accessToken, hasImage, job.jobId, previewVersion])

  const refreshPreview = () => {
    setPreview(null); setPreviewError(''); setError('')
    setPreviewVersion((value) => value + 1)
  }
  const perform = async (operation) => {
    if (action.current) return
    action.current = true
    setBusy(true); setError('')
    try { await operation() }
    catch (failure) {
      setError(failure.message)
      if (failure.code === REVISION_CONFLICT_CODE) setConflicted(true)
    } finally { action.current = false; setBusy(false) }
  }
  const target = preview?.target
  const isCurrent = target?.artifactId != null && String(target.artifactId) === String(job.artifactId)
  const apply = () => perform(async () => {
    await adoptGeneratedArtifact(accessToken, job.jobId, target.projectRevision)
    setPreview((value) => ({ ...value, target: { ...value.target, artifactId: job.artifactId } }))
    notifyCreativeProjectChanged(job.projectId)
    onAdopted()
  })
  const prepareRegeneration = () => perform(async () => {
    const original = await getCreativeChangeSet(accessToken, job.changeSetId)
    setPrompt(original.proposed?.prompt || '')
    setProposal(original)
  })

  return <section className="generation-adoption task-image-result" aria-label="图片生成结果">
    <strong>{!hasImage ? '要重新生成这张图吗？' : isCurrent ? '已设为当前参考图' : '将这张图设为当前分镜的参考图？'}</strong>
    {target && <small>分镜 #{target.shotId} · {target.title}</small>}
    {hasImage && <div className="generation-adoption-images">
      {!isCurrent && target?.imageUrl && <div><small>当前参考图</small><MediaImage src={target.imageUrl} alt="当前分镜参考图" showErrorText /></div>}
      <div><small>{isCurrent ? '当前参考图' : '新生成的图片'}</small>
        <a href={resolveMediaUrl(preview?.artifactUrl || job.artifactUrl) || undefined} target="_blank" rel="noreferrer">
          <MediaImage src={preview?.artifactUrl || job.artifactUrl} alt={`镜头 ${job.shotId} 的生成图片`} showErrorText />
        </a></div>
    </div>}
    {conflicted && <p role="alert">分镜或项目已有更新。刷新参考图后再确认。</p>}
    {previewError && <p role="alert">{previewError}</p>}
    <div className="generation-actions">
      {hasImage && ((conflicted || previewError)
        ? <button type="button" className="workspace-primary" disabled={busy} onClick={refreshPreview}>刷新参考图</button>
        : !isCurrent && <button type="button" className="workspace-primary" disabled={!target || busy} onClick={apply}>
          {target ? '设为当前分镜参考图' : '正在读取分镜…'}</button>)}
      {!proposal && <button type="button" className="workspace-secondary" disabled={busy || !canRegenerate} onClick={prepareRegeneration}>重新生成</button>}
    </div>
    {proposal && <form className="generation-regenerate" onSubmit={(event) => {
      event.preventDefault()
      if (prompt.trim() && canRegenerate) void perform(() => onRegenerate(proposal, job, prompt))
    }}>
      <label>下一张图的画面提示词<textarea value={prompt} onChange={(event) => setPrompt(event.target.value)}
        maxLength={IMAGE_PROMPT_MAX_LENGTH} rows={3} disabled={busy} required /></label>
      <small>沿用 {proposal.proposed?.model} 和原尺寸。新提案仍需确认后才会生成。</small>
      <div className="generation-actions">
        <button type="button" className="workspace-secondary" onClick={() => setProposal(null)} disabled={busy}>取消</button>
        <button type="submit" className="workspace-primary" disabled={busy || !canRegenerate || !prompt.trim()}>创建新一轮图片提案</button>
      </div>
    </form>}
    {error && !conflicted && <p role="alert">{error}</p>}
  </section>
}
