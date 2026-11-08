import { useEffect, useState } from 'react'
import { getRuntimeOperations, getRunModelUsage, settleModelCall } from '../api/runtimeOperationsApi'

/** JDBC Map 列名在不同数据库驱动中大小写可能不同，统一为展示字段。 */
const normalize = (row) => Object.fromEntries(Object.entries(row).map(([key, value]) => [key.toLowerCase(), value]))
/** 未报告费用不显示为零；部分用量和正式账单由后端状态区分。 */
const money = (value) => value == null ? '未知 / 未定价' : `$${Number(value).toFixed(6)}`

/** 管理员运行面板，显式查询与账单确认，避免高频后台轮询。 */
export default function RuntimeOperations({ accessToken }) {
  const [snapshot, setSnapshot] = useState(null)
  const [runId, setRunId] = useState('')
  const [loadedRun, setLoadedRun] = useState('')
  const [calls, setCalls] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [settlementCall, setSettlementCall] = useState(null)
  const [settledAmount, setSettledAmount] = useState('')
  const [settlementNote, setSettlementNote] = useState('')

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
      if (!append) setSettlementCall(null)
    } catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }

  /** 金额必须由管理员依据账单填写，不用规划预留或 token 计算值代填。 */
  async function confirmSettlement(event) {
    event.preventDefault()
    setBusy(true); setError('')
    try {
      await settleModelCall(accessToken, settlementCall, { amountUsd: settledAmount, note: settlementNote })
      setCalls(await getRunModelUsage(accessToken, loadedRun))
      setSettlementCall(null)
    } catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }

  return <section className="dashboard-welcome" aria-label="创作运行监测">
    <h1>创作运行监测</h1>
    <p>调用费用为已报告 token 的计算值；失败、取消或未知请求可能只有部分用量。媒体生成费用单独核对。</p>
    <button type="button" disabled={busy} onClick={refresh}>刷新监测</button>
    {error && <p role="alert">{error}</p>}
    {snapshot && <>
      <h2>近 24 小时模型调用</h2>
      <p>最近 100 个配置中，熔断冷却：{snapshot.openCircuits}</p>
      <table><thead><tr><th>状态</th><th>调用数</th><th>已报告输入 / 输出</th><th>可计算费用</th><th>已核对金额</th><th>费用未知调用</th></tr></thead>
        <tbody>{snapshot.usage.map(normalize).map((row) => <tr key={row.status}>
          <td>{row.status}</td><td>{row.calls}</td><td>{row.input_tokens ?? '—'} / {row.output_tokens ?? '—'}</td>
          <td>{money(row.calculated_usd)}</td><td>{row.settled_usd == null ? '未对账' : money(row.settled_usd)}</td><td>{row.unpriced_or_unknown_calls}</td>
        </tr>)}</tbody></table>
      <h2>运行状态分布</h2>
      <ul>{snapshot.backlog.map(normalize).map((row) => <li key={row.status}>{row.status}：{row.runs}</li>)}</ul>
      <h2>供应商保护</h2>
      <ul>{snapshot.providerGates.map((gate) => <li key={gate.configId}>
        配置 {gate.configId} · 分钟窗已受理 {gate.windowRequests} · 连续故障 {gate.consecutiveFailures} · 冷却至 {gate.openUntil || '—'}
      </li>)}</ul>
    </>}
    <h2>按 Run 追踪调用</h2>
    <input aria-label="Run ID" value={runId} onChange={(event) => setRunId(event.target.value)} placeholder="输入 runId" />
    <button type="button" disabled={busy || !runId.trim()} onClick={() => loadUsage()}>查询用量</button>
    <p>{loadedRun}</p>
    <table><thead><tr><th>调用 / 步骤</th><th>模型</th><th>状态</th><th>输入 / 输出</th><th>计算费用</th><th>账单核对</th><th>失败类型</th></tr></thead>
      <tbody>{calls.map((call) => <tr key={call.callId}>
        <td>{call.callId}<br />{call.stepId} · epoch {call.ownerEpoch}</td><td>{call.modelName}</td><td>{call.status}</td>
        <td>{call.inputTokens ?? '—'} / {call.outputTokens ?? '—'}</td><td>{money(call.calculatedUsd)}</td>
        <td>{call.settledUsd != null ? money(call.settledUsd) : call.status !== 'STARTED' && <button type="button" disabled={busy} onClick={() => {
          setSettlementCall(call.callId); setSettledAmount(''); setSettlementNote('')
        }}>核对账单</button>}</td><td>{call.failureType || '—'}</td>
      </tr>)}</tbody></table>
    {calls.length > 0 && calls.length % 100 === 0 && <button type="button" disabled={busy} onClick={() => loadUsage(true)}>加载更早调用</button>}
    {settlementCall && <form onSubmit={confirmSettlement}>
      <h3>确认账单：{settlementCall}</h3>
      <p>请先核对供应商账单。确认后不可覆盖；此操作仅记录金额，不会向供应商付款。</p>
      <label>实际核对金额（USD）<input type="number" required min="0" max="999999999999" step="0.00000001" value={settledAmount} onChange={(event) => setSettledAmount(event.target.value)} /></label>
      <label>账单编号或依据<input required maxLength={500} value={settlementNote} onChange={(event) => setSettlementNote(event.target.value)} /></label>
      <button type="submit" disabled={busy}>确认记录账单</button>
      <button type="button" disabled={busy} onClick={() => setSettlementCall(null)}>取消</button>
    </form>}
  </section>
}
