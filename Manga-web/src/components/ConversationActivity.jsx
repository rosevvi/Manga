import { useEffect, useRef } from 'react'
import { Brain, Check, ChevronDown, CircleAlert, LoaderCircle, Wrench } from 'lucide-react'
import { CREATIVE_TOOL_LABEL } from '../constants/creative'
import AssistantMarkdown from './AssistantMarkdown'
import './conversation-activity.css'

const TOOL_STATUS = {
  RUNNING: { label: '执行中', icon: LoaderCircle },
  SUCCESS: { label: '已完成', icon: Check },
  ERROR: { label: '失败', icon: CircleAlert },
  DENIED: { label: '已拒绝', icon: CircleAlert },
  INTERRUPTED: { label: '已中断', icon: CircleAlert },
}

/** 流式思考在独立区域滚动，让同一轮的工具和回复保持可读。 */
export function ConversationThought({ item, streaming, open, onToggle }) {
  const viewport = useRef(null)
  const prose = useRef(null)
  const follow = useRef(true)
  useEffect(() => {
    if (!streaming || !open) return undefined
    const element = viewport.current
    follow.current = true
    const observer = new ResizeObserver(() => {
      if (follow.current) element.scrollTop = element.scrollHeight
    })
    observer.observe(prose.current)
    return () => observer.disconnect()
  }, [open, streaming])
  return <section className="chat-thought" data-streaming={streaming || undefined}>
    <button type="button" className="chat-thought-toggle" aria-expanded={open} onClick={onToggle}>
      {streaming ? <LoaderCircle size={14} className="process-spinner" /> : <Brain size={14} />}
      <span>{streaming ? '思考中' : '思考过程'}</span>
      <ChevronDown size={13} className="chat-activity-chevron" aria-hidden="true" />
    </button>
    {open && <div className="chat-thought-viewport" ref={viewport} tabIndex={0} aria-label="思考过程"
      onScroll={({ currentTarget }) => {
        follow.current = currentTarget.scrollHeight - currentTarget.scrollTop - currentTarget.clientHeight < 24
      }}>
      <div ref={prose}><AssistantMarkdown text={item.resultSummary} streaming={streaming} /></div>
    </div>}
  </section>
}

/** 工具结果摘要始终可见，展开后查看目标和运行信息。 */
export function ConversationToolResult({ item, open, onToggle }) {
  const { label, icon: StatusIcon } = TOOL_STATUS[item.status] ?? { label: '等待状态', icon: Wrench }
  const working = item.status === 'RUNNING'
  const result = item.failureReason || item.resultSummary
  return <section className="chat-tool" data-status={item.status?.toLowerCase()} data-expanded={open || undefined}>
    <button type="button" aria-expanded={open} className="chat-tool-toggle" onClick={onToggle}>
      <span className="chat-tool-icon"><Wrench size={15} aria-hidden="true" /></span>
      <strong>{item.actionLabel || CREATIVE_TOOL_LABEL[item.actionName] || item.actionName}</strong>
      <span className="chat-tool-status"><StatusIcon size={13} className={working ? 'process-spinner' : undefined} aria-hidden="true" />{label}</span>
      <ChevronDown size={14} className="chat-activity-chevron" aria-hidden="true" />
    </button>
    {result && <p className="chat-tool-result" role={item.failureReason ? 'alert' : undefined}>{result}</p>}
    {open && <div className="chat-tool-meta">
      {item.inputSummary && <p>{item.inputSummary}</p>}
      <div>
        {item.startedAt && <time dateTime={item.startedAt}>{item.startedAt.replace('T', ' ')}</time>}
        {item.finishedAt && <span>完成 {item.finishedAt.slice(11, 19)}</span>}
        {item.callCount > 1 && <span>第 {item.callCount} 次调用</span>}
        {item.reused && <span>复用结果</span>}
        {!item.inputSummary && !item.startedAt && !item.finishedAt && !result && <span>等待工具返回结果</span>}
      </div>
    </div>}
  </section>
}
