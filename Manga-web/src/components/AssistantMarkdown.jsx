import { lazy, memo, Suspense, useEffect, useRef, useState } from 'react'
import './assistant-markdown.css'

const FRAME_MS = 32
const REMAINDER_FRAMES = 12
const MarkdownRenderer = lazy(() => import('./AssistantMarkdownRenderer'))

/** 在新文本到达后逐字补齐，按积压自动追赶，历史和关闭动效时直接输出。 */
function useRevealedText(text, streaming) {
  const [shown, setShown] = useState(() => streaming ? '' : text)
  const cursor = useRef(shown)
  const target = useRef(text)
  target.current = text
  useEffect(() => {
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)')
    if (!streaming || motion.matches || !text.startsWith(cursor.current)) {
      cursor.current = text
      setShown(text)
      return
    }
    if (cursor.current === text) return
    const interval = window.setInterval(() => {
      const next = target.current
      if (motion.matches || !next.startsWith(cursor.current)) {
        cursor.current = next
      } else {
        const pending = Array.from(next.slice(cursor.current.length))
        cursor.current += pending.slice(0, Math.max(1, Math.ceil(pending.length / REMAINDER_FRAMES))).join('')
      }
      setShown(cursor.current)
      if (cursor.current === next) window.clearInterval(interval)
    }, FRAME_MS)
    return () => window.clearInterval(interval)
  }, [text, streaming])
  return streaming ? shown : text
}

/** 使用同一安全 Markdown 渲染历史和实时正文，流动光标只在接收期间出现。 */
export default memo(function AssistantMarkdown({ text = '', streaming = false, fallback }) {
  const visible = useRevealedText(text, streaming)
  return <div className={`assistant-markdown${streaming ? ' streaming' : ''}`}>
    <Suspense fallback={fallback ?? <div className="process-prose">{visible}</div>}>
      <MarkdownRenderer text={visible} />
    </Suspense>
  </div>
})

/** 等待首个可见事件时体现实际执行状态。 */
export function AssistantThinking({ label = '正在思考' }) {
  return <span className="assistant-thinking" role="status"><span>{label}</span>
    <span className="assistant-thinking-dots" aria-hidden="true"><i /><i /><i /></span>
  </span>
}
