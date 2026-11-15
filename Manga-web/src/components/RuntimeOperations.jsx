import { useEffect, useState } from 'react'
import { getRuntimeOperations, getRunModelUsage } from '../api/runtimeOperationsApi'
import './runtime-operations.css'

/** JDBC Map 列名在不同数据库驱动中大小写可能不同，统一为展示字段。 */
const normalize = (row) => Object.fromEntries(Object.entries(row).map(([key, value]) => [key.toLowerCase(), value]))
/** 管理员运行面板，按需查询调用量和状态，避免高频后台轮询。 */
export default function RuntimeOperations({ accessToken }) {
  const [snapshot, setSnapshot] = useState(null)
  const [runId, setRunId] = useState('')
  const [loadedRun, setLoadedRun] = useState('')
  const [calls, setCalls] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let current = true
    getRuntimeOperations(accessToken).then((value) => { if (current) setSnapshot(value) })
      .catch((failure) => { if (current) setError(failure.message) })
    return () => { current = false }
  }, [accessToken])

  /** 显式获取监测快照，不发起任何供应商调用。 */
  async function refresh() {
    setBusy(true); setError('')
    try { setSnapshot(await getRuntimeOperations(accessToken)) }
    catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }

  /** 查询或继续加载一个确定 Run 的账本，禁止把不同 Run 的分页混合。 */
  async function loadUsage(append = false) {
    const target = append ? loadedRun : runId.trim()
    if (!target) return
    setBusy(true); setError('')
    try {
      const page = await getRunModelUsage(accessToken, target, append ? calls.at(-1)?.id : undefined)
      setCalls((previous) => append ? [...previous, ...page] : page)
      setLoadedRun(target)
    } catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }

  return <section className="workspace-module runtime-operations" aria-label="创作运行监测">
    <header className="workspace-heading">
      <div>
        <h1>创作运行监测</h1>
        <p>失败、取消或状态未知的调用可能只报告部分 token 用量。</p>
      </div>
      <button className="workspace-secondary" type="button" disabled={busy} onClick={refresh}>刷新监测</button>
    </header>
    {error && <p className="workspace-alert" role="alert">{error}</p>}
    {snapshot && <>
      <h2>近 24 小时模型调用</h2>
      <p>最近 100 个配置中，熔断冷却：{snapshot.openCircuits}</p>
      <div className="runtime-table"><table><thead><tr><th>状态</th><th>调用数</th><th>已报告输入 / 输出</th></tr></thead>
        <tbody>{snapshot.usage.map(normalize).map((row) => <tr key={row.status}>
          <td>{row.status}</td><td>{row.calls}</td><td>{row.input_tokens ?? '—'} / {row.output_tokens ?? '—'}</td>
        </tr>)}</tbody></table></div>
      <h2>运行状态分布</h2>
      <ul>{snapshot.backlog.map(normalize).map((row) => <li key={row.status}>{row.status}：{row.runs}</li>)}</ul>
      <h2>供应商保护</h2>
      <ul>{snapshot.providerGates.map((gate) => <li key={gate.configId}>
        配置 {gate.configId} · 分钟窗已受理 {gate.windowRequests} · 连续故障 {gate.consecutiveFailures} · 冷却至 {gate.openUntil || '—'}
      </li>)}</ul>
    </>}
    <h2>按 Run 追踪调用</h2>
    <form className="runtime-actions" onSubmit={(event) => { event.preventDefault(); void loadUsage() }}>
      <input aria-label="Run ID" value={runId} onChange={(event) => setRunId(event.target.value)} placeholder="输入 runId" />
      <button className="workspace-primary" type="submit" disabled={busy || !runId.trim()}>查询用量</button>
    </form>
    {loadedRun && <p>{loadedRun}</p>}
    {loadedRun && <div className="runtime-table"><table><thead><tr><th>调用 / 步骤</th><th>模型</th><th>状态</th><th>输入 / 输出</th><th>失败类型</th></tr></thead>
      <tbody>{calls.map((call) => <tr key={call.callId}>
        <td>{call.callId}<br />{call.stepId} · epoch {call.ownerEpoch}</td><td>{call.modelName}</td><td>{call.status}</td>
        <td>{call.inputTokens ?? '—'} / {call.outputTokens ?? '—'}</td><td>{call.failureType || '—'}</td>
      </tr>)}</tbody></table></div>}
    {calls.length > 0 && calls.length % 100 === 0 && <button className="workspace-secondary" type="button" disabled={busy} onClick={() => loadUsage(true)}>加载更早调用</button>}
  </section>
}
