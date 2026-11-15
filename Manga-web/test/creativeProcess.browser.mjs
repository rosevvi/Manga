import assert from 'node:assert/strict'
import http from 'node:http'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'
import { chromium } from 'playwright-core'

/** 真浏览器使用生产组件和真实 SSE 分段，仅替换本机测试 API，不读取用户登录状态。 */
const output = resolve('../Manga-server/target/creative-process-browser')
await mkdir(output, { recursive: true })
const observed = { start: 0, graphResponses: 0, decisions: [], confirmed: false }
const at = new Date(Date.now() - new Date().getTimezoneOffset() * 60_000).toISOString().slice(0, -1)
const baseItem = { runId: 'stream-run', parentStepId: 'root', stepId: 'worker', ownerEpoch: 1,
  agentName: '剧本解析师', agentVersion: 9, planId: 'plan-1', round: 1, occurredAt: at, resourceLinks: [] }
const trace = [
  [0, { type: 'STEP_STARTED', actionName: 'STEP_STARTED', status: 'RUNNING' }],
  [150, { type: 'CONTENT_DELTA', resultSummary: '正在读取章节并整理场景。' }],
  [350, { type: 'REASONING_DELTA', resultSummary: '供应商返回的分析摘要。' }],
  [700, { type: 'TOOL_STARTED', actionName: 'get_chapter_content', actionId: 'read-1', actionLabel: '读取章节「初入」', status: 'RUNNING' }],
  [1100, { type: 'TOOL_COMPLETED', actionName: 'get_chapter_content', actionId: 'read-1', actionLabel: '读取章节「初入」', status: 'SUCCESS', resultSummary: '章节正文已读取' }],
  [1500, { type: 'CONTENT_DELTA', resultSummary: '已完成场景梳理。' }],
  [2200, { type: 'CONTENT_DELTA', resultSummary: '正在准备下一步。' }],
  [3200, { type: 'STEP_COMPLETED', actionName: 'STEP_COMPLETED', status: 'COMPLETED' }],
  [3600, { type: 'RUN_COMPLETED', stepId: null, parentStepId: null, actionName: 'RUN_COMPLETED', status: 'COMPLETED' }],
].map(([delay, item], index) => [delay, { ...baseItem, ...item, id: `stream-run:${index + 1}`, sequence: index + 1 }])
const elapsed = () => observed.start ? Date.now() - observed.start : 0
const visible = () => trace.filter(([delay]) => elapsed() >= delay).map(([, item]) => item)
const response = (res, data) => { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify({ success: true, data })) }

const api = http.createServer(async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Headers', 'Authorization,Content-Type')
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS')
  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return }
  const url = new URL(req.url, 'http://fixture')
  const repair = url.pathname.includes('repair-run')
  const runId = repair ? 'repair-run' : 'stream-run'
  if (url.pathname.endsWith('/decide')) {
    let body = ''
    for await (const chunk of req) body += chunk
    observed.decisions.push(JSON.parse(body)); observed.confirmed = true
    response(res, { decisionId: 'decision-1', status: 'CONFIRMED' }); return
  }
  if (url.pathname.endsWith('/repair-decisions')) {
    response(res, repair ? [{ decisionId: 'decision-1', runId, sourcePlanId: 'plan-1', round: 1,
      candidateStepId: 'worker', reviewStepId: 'review', status: observed.confirmed ? 'CONFIRMED' : 'PENDING',
      issues: ['补充结尾的情绪收束'], candidate: { chapterId: 1, shots: [{ title: '庭院开场', sceneName: '庭院', shotType: '全景', durationSeconds: 5, content: '角色走入庭院。' }] },
      expiresAt: at }] : []); return
  }
  if (url.pathname.endsWith('/events')) {
    if (!observed.start) observed.start = Date.now()
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-store' }); res.flushHeaders()
    const timers = []
    if (repair) { res.write(': waiting for user decision\n\n'); req.on('close', () => res.end()); return }
    const after = Number(url.searchParams.get('afterSequence') || url.searchParams.get('after') || 0)
    for (const [delay, activity] of trace) {
      if (activity.sequence <= after) continue
      timers.push(setTimeout(() => {
        const envelope = { runId, sequence: activity.sequence, eventType: activity.type, activity }
        res.write(`event: creative-event\ndata: ${JSON.stringify(envelope)}\n\n`)
        if (activity.type === 'RUN_COMPLETED') res.end()
      }, Math.max(0, delay - elapsed())))
    }
    req.on('close', () => timers.forEach(clearTimeout)); return
  }
  if (url.pathname.endsWith('/timeline')) {
    const after = Number(url.searchParams.get('afterSequence') || 0)
    const items = repair ? [] : visible().filter((item) => item.sequence > after)
    response(res, { items, throughSequence: items.at(-1)?.sequence || after, recoveryWatermark: visible().length, hasMore: false }); return
  }
  if (url.pathname.endsWith('/graph')) {
    if (!repair) await new Promise((done) => setTimeout(done, 2200))
    observed.graphResponses++
    const complete = !repair && elapsed() >= 3200
    response(res, { runId, status: repair ? 'WAITING' : complete ? 'COMPLETED' : 'ACTIVE', throughSequence: visible().length,
      plans: [{ planId: 'plan-1', title: '章节创作', round: 1, status: complete ? 'COMPLETED' : 'MATERIALIZED' }],
      nodes: [{ stepId: 'worker', parentStepId: 'root', planId: 'plan-1', profile: 'script_parser', profileId: 'script_parser', profileVersion: 9,
        displayName: '剧本解析师', status: complete ? 'COMPLETED' : repair ? 'WAITING_REPAIR_CONFIRMATION' : 'RUNNING', ownerEpoch: 1,
        resultSummary: complete ? '这是已提交的最终报告。' : null, issues: [], dependsOn: [] }] }); return
  }
  if (/\/creative-runs\/[^/]+$/.test(url.pathname)) {
    response(res, { runId, operationType: 'PARSE_SCRIPT', status: repair ? 'WAITING' : elapsed() >= 3600 ? 'COMPLETED' : 'ACTIVE',
      triggerSource: 'MANUAL', startedAt: at }); return
  }
  response(res, [])
})
await new Promise((ready) => api.listen(0, '127.0.0.1', ready))
const baseUrl = `http://127.0.0.1:${api.address().port}`
const vite = await createServer({ configFile: false, plugins: [react()], mode: 'test',
  define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify(`${baseUrl}/api/v1`) },
  server: { host: '127.0.0.1', port: 0 } })
await vite.listen()
const origin = `http://127.0.0.1:${vite.httpServer.address().port}`
const browser = await chromium.launch(process.env.MANGA_BROWSER_EXECUTABLE
  ? { executablePath: process.env.MANGA_BROWSER_EXECUTABLE, headless: true }
  : { channel: process.env.MANGA_BROWSER_CHANNEL || 'msedge', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
const errors = []
page.on('pageerror', (error) => errors.push(error.message))
try {
  await page.goto(`${origin}/test/fixtures/creative-process.html`)
  await page.getByText('正在读取章节并整理场景。', { exact: true }).waitFor({ timeout: 1800 })
  assert.equal(observed.graphResponses, 0, 'slow snapshot must not gate streamed text')
  await page.getByText('供应商返回的分析摘要。', { exact: true }).waitFor({ timeout: 1500 })
  await page.screenshot({ path: resolve(output, 'streaming-desktop.png'), fullPage: true })
  const agent = page.locator('[data-disclosure-id="agent:worker"] > button')
  await agent.click(); assert.equal(await agent.getAttribute('aria-expanded'), 'false')
  await page.waitForTimeout(1700)
  assert.equal(await agent.getAttribute('aria-expanded'), 'false', 'later deltas must respect manual collapse')
  await page.reload()
  await agent.waitFor(); assert.equal(await agent.getAttribute('aria-expanded'), 'false', 'refresh must restore collapse')
  await page.waitForTimeout(2500)
  const plan = page.locator('[data-disclosure-id="plan:plan-1"] > button')
  if (await plan.getAttribute('aria-expanded') === 'false') await plan.click()
  await agent.click()
  await page.getByRole('button', { name: '最终结果', exact: true }).click()
  await page.getByText('这是已提交的最终报告。', { exact: true }).waitFor()
  await page.setViewportSize({ width: 390, height: 844 })
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true, 'mobile layout must not overflow horizontally')
  await page.screenshot({ path: resolve(output, 'process-mobile.png'), fullPage: true })
  await page.getByRole('button', { name: '全部收起', exact: true }).click()
  assert.equal(await plan.getAttribute('aria-expanded'), 'false')

  await page.goto(`${origin}/test/fixtures/creative-process.html?run=repair-run`)
  await page.getByRole('button', { name: '确认修订', exact: true }).waitFor()
  await page.getByText('查看候选草稿', { exact: true }).click()
  await page.getByText('角色走入庭院。', { exact: true }).waitFor()
  await page.screenshot({ path: resolve(output, 'repair-mobile.png'), fullPage: true })
  await page.getByLabel('补充修订意见').fill('保留原有镜头结构')
  await page.getByRole('button', { name: '确认修订', exact: true }).click()
  await page.getByText('第 1 轮评审 · 已授权修订', { exact: true }).waitFor()
  assert.equal(observed.decisions.length, 1)
  assert.equal(observed.decisions[0].action, 'CONFIRM_REPAIR')
  assert.equal(observed.decisions[0].comment, '保留原有镜头结构')
  assert.deepEqual(errors, [])
  console.log('Browser checks passed: early streaming, provider analysis, collapse, refresh, mobile, repair preview and confirmation')
  console.log(`Screenshots: ${output}`)
} finally {
  await browser.close(); await vite.close()
  api.closeAllConnections(); await new Promise((done) => api.close(done))
}
