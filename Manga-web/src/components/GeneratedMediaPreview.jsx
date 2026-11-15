import { useEffect, useRef, useState } from 'react'
import { Download, X } from 'lucide-react'
import { getGeneratedMediaDownload } from '../api/generationApi'
import { resolveMediaUrl } from '../api/mediaUrl'
import MediaImage from './MediaImage'

/** 媒体原地预览；下载按钮单独向服务端申请附件地址。 */
export default function GeneratedMediaPreview({ accessToken, job, src, video = false }) {
  const [expanded, setExpanded] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const [error, setError] = useState('')
  const dialog = useRef(null)
  const previewUrl = resolveMediaUrl(src)
  const label = `镜头 ${job.shotId} 的生成${video ? '视频' : '图片'}`

  useEffect(() => {
    if (!expanded) return
    const element = dialog.current
    element.showModal()
    return () => { if (element.open) element.close() }
  }, [expanded])

  const download = async () => {
    setDownloading(true); setError('')
    try {
      const result = await getGeneratedMediaDownload(accessToken, job.jobId)
      const url = resolveMediaUrl(result.url)
      if (!url) throw new Error('下载地址不可用，请重新加载产物')
      let objectUrl
      if (result.url.startsWith('/') && !result.url.startsWith('//') && new URL(url).origin !== window.location.origin) {
        const response = await fetch(url)
        if (!response.ok) throw new Error('下载失败，请稍后重试')
        objectUrl = URL.createObjectURL(await response.blob())
      }
      const anchor = document.createElement('a')
      anchor.href = objectUrl || url
      anchor.download = `shot-${job.shotId}.${video ? 'mp4' : 'png'}`
      anchor.rel = 'noreferrer'
      document.body.appendChild(anchor); anchor.click(); anchor.remove()
      if (objectUrl) window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
    } catch (failure) { setError(failure.message) }
    finally { setDownloading(false) }
  }

  return <div className="generated-media">
    {video ? (previewUrl ? <video controls playsInline preload="metadata" aria-label={label} src={previewUrl} />
      : <p role="status">视频预览不可用</p>)
      : <button type="button" className="generated-image-open" aria-label="打开图片预览" disabled={!previewUrl} onClick={() => setExpanded(true)}>
        <MediaImage src={src} alt={label} showErrorText />
      </button>}
    <button className="workspace-secondary" type="button" disabled={downloading} onClick={download}>
      <Download size={14} />{downloading ? '准备下载…' : video ? '下载视频' : '下载图片'}
    </button>
    {expanded && <dialog ref={dialog} className="generated-image-dialog" aria-label={label}
      onCancel={(event) => { event.preventDefault(); setExpanded(false) }}
      onKeyDown={(event) => { if (event.key === 'Escape') event.stopPropagation() }}
      onClick={(event) => { if (event.target === event.currentTarget) setExpanded(false) }}>
      <div><button type="button" className="workspace-secondary" aria-label="关闭图片预览" onClick={() => setExpanded(false)}><X size={18} /></button>
        <MediaImage src={src} alt={label} showErrorText />
        <button className="workspace-secondary" type="button" disabled={downloading} onClick={download}><Download size={14} />下载图片</button>
        {error && <p role="alert">{error}</p>}</div>
    </dialog>}
    {error && !expanded && <p role="alert">{error}</p>}
  </div>
}
