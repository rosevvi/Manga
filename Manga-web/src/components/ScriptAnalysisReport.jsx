import { FileSearch, LoaderCircle, WandSparkles } from 'lucide-react'
import AssistantMarkdown from './AssistantMarkdown'
import './script-analysis-report.css'

/** 在剧本工作区展示解析进度和格式化的最终报告。 */
export default function ScriptAnalysisReport({ analysis, starting, canStart, onStart }) {
  const { latestRun, report, running, loading, error } = analysis
  const pending = starting || running
  const failed = latestRun?.status === 'FAILED'
  const cancelled = latestRun?.status === 'CANCELLED'
  const time = report?.finishedAt
  const timestamp = Number.isFinite(Date.parse(time)) ? new Date(time).toLocaleString() : ''

  return <section className="script-analysis-report" aria-label="剧本解析报告">
    <header className="script-analysis-heading">
      <span className="script-analysis-icon"><FileSearch size={20} aria-hidden="true" /></span>
      <div><h2>剧本解析报告</h2><p>分析范围为整个剧本的已保存章节</p></div>
      {timestamp && <time dateTime={time}>{timestamp}</time>}
    </header>
    {pending && <p className="script-analysis-notice" role="status"><LoaderCircle size={15} className="spin" aria-hidden="true" />
      {report ? '正在重新解析，下面是上一次的报告。' : '剧本解析师正在阅读章节并整理报告。'}</p>}
    {error && <p className="workspace-alert" role="alert">暂时无法读取解析状态，正在重试。</p>}
    {failed && <p className="workspace-alert" role="alert">{latestRun.errorMessage || '本次剧本解析失败，请重新解析。'}</p>}
    {cancelled && <p className="script-analysis-notice" role="status">本次解析已取消。</p>}
    {report ? <article className="script-analysis-document">
      <AssistantMarkdown text={report.markdown} fallback={<p role="status">正在排版报告…</p>} />
    </article>
      : !pending && <div className="script-analysis-empty">
        <FileSearch size={26} aria-hidden="true" />
        <p>{loading ? '正在读取解析报告…' : latestRun?.status === 'COMPLETED'
          ? '本次解析没有生成报告内容。' : '还没有解析报告。'}</p>
        {!loading && canStart && <button type="button" className="workspace-primary" onClick={onStart}>
          <WandSparkles size={15} aria-hidden="true" />解析剧本
        </button>}
      </div>}
  </section>
}
