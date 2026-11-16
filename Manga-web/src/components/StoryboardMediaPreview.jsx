import { useEffect, useRef, useState } from 'react'
import { ChevronDown, Video } from 'lucide-react'
import { resolveMediaUrl } from '../api/mediaUrl'
import GeneratedMediaPreview from './GeneratedMediaPreview'
import ImageGenerationPanel from './ImageGenerationPanel'

/** 工作台与镜头条共用镜头响应中的视频，生成参数按需展开。 */
export default function StoryboardMediaPreview({ accessToken, projectId, shot, loading, prompt, unsavedShot }) {
  const [generationOpened, setGenerationOpened] = useState(false)
  const preview = shot?.videoPreview

  return <>
    <h2 className="storyboard-media-heading"><Video size={16} aria-hidden="true" />视频生成</h2>
    {preview?.url
      ? <GeneratedMediaPreview accessToken={accessToken} job={{ jobId: preview.jobId, shotId: shot.id }} src={preview.url} video />
      : <div className="shot-preview-frame shot-preview-placeholder" role="status">
        <Video size={25} aria-hidden="true" />
        <span>{loading ? '正在加载镜头…' : shot ? '当前镜头暂无视频' : '选择镜头后可生成视频'}</span>
      </div>}
    {shot && <details className="storyboard-video-generation" onToggle={(event) => {
      if (event.currentTarget.open) setGenerationOpened(true)
    }}>
      <summary className="workspace-secondary">生成视频<ChevronDown size={14} aria-hidden="true" /></summary>
      {generationOpened && <ImageGenerationPanel accessToken={accessToken} projectId={projectId}
        selectedShot={shot} mediaType="VIDEO" promptOverride={prompt} unsavedShot={unsavedShot} />}
    </details>}
  </>
}

/** 只在缩略图进入视口后读取视频元数据和首帧，点击镜头后由右侧播放器播放。 */
export function StoryboardVideoThumbnail({ src }) {
  const host = useRef(null)
  const [inView, setInView] = useState(false)
  const [failedUrl, setFailedUrl] = useState(null)
  const url = resolveMediaUrl(src)
  useEffect(() => {
    if (!url || inView) return
    if (typeof IntersectionObserver === 'undefined') {
      setInView(true)
      return
    }
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) setInView(true)
    })
    observer.observe(host.current)
    return () => observer.disconnect()
  }, [url, inView])

  let frameUrl
  if (inView && url && failedUrl !== url) {
    const frame = new URL(url)
    frame.hash = 't=0.1'
    frameUrl = frame.href
  }

  return <span className="shot-thumbnail-video" ref={host} aria-hidden="true">
    <Video size={20} />
    {frameUrl && <video src={frameUrl} muted playsInline preload="metadata" tabIndex={-1}
      onError={() => setFailedUrl(url)} />}
  </span>
}
