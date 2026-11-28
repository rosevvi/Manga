import test from 'node:test'
import assert from 'node:assert/strict'
import { timelineCards, loadTimelineSince, createSequenceBuffer, unprojectedRootText, taskActionSequences, orderedExecutionSections, groupConversationThoughts } from '../src/components/creativeTimeline.js'
import { imageRegenerationRequest } from '../src/components/changeSetPreview.js'

const started = { id: 'r:1', sequence: 1, type: 'TOOL_STARTED', stepId: 's', ownerEpoch: 1, actionId: 'tool', status: 'RUNNING', startedAt: '2026-11-14T12:00:00' }

test('completion merges into original position and preserves start time after repeated pages', () => {
  const end = { ...started, id: 'r:3', sequence: 3, type: 'TOOL_COMPLETED', status: 'SUCCESS', resultSummary: '3 个镜头', startedAt: null }
  const events = [...new Map([started, { id: 'r:2', type: 'CONTENT_DELTA', resultSummary: '正文' }, end, end].map((event) => [event.id, event])).values()]
  const cards = timelineCards(events, true)
  assert.equal(cards.length, 2)
  assert.equal(cards[0].id, started.id)
  assert.equal(cards[0].status, 'SUCCESS')
  assert.equal(cards[0].startedAt, started.startedAt)
  assert.equal(started.status, 'RUNNING')
})

test('late and retried executions have separate tool cards and old running tools converge', () => {
  const next = { id: 'r:2', type: 'STEP_STARTED', stepId: 's', ownerEpoch: 2, occurredAt: '2026-11-14T12:01:00' }
  const tool = { ...started, id: 'r:3', ownerEpoch: 2 }
  const cards = timelineCards([started, next, tool], false)
  assert.equal(cards[0].status, 'INTERRUPTED')
  assert.equal(cards[2].status, 'RUNNING')
})

test('terminal snapshot ends unfinished tools even when legacy events omit completion', () => {
  const cards = timelineCards([started], false, { status: 'FAILED', finishedAt: '2026-11-14T12:02:00' })
  assert.equal(cards[0].status, 'INTERRUPTED')
  assert.equal(cards[0].finishedAt, '2026-11-14T12:02:00')
})

test('a failed later page does not consume the earlier page cursor', async () => {
  let cursor = 0
  const failing = async (after) => {
    if (after === 0) return { items: [started], throughSequence: 1, hasMore: true }
    throw new Error('connection interrupted')
  }
  await assert.rejects(async () => { cursor = (await loadTimelineSince(failing, cursor)).cursor }, /connection interrupted/)
  assert.equal(cursor, 0)
  const calls = []
  const restored = await loadTimelineSince(async (after) => {
    calls.push(after)
    return after === 0 ? { items: [started], throughSequence: 1, hasMore: true }
      : { items: [{ ...started, id: 'r:2', type: 'TOOL_COMPLETED', status: 'SUCCESS' }], throughSequence: 2, hasMore: false }
  }, cursor)
  assert.deepEqual(calls, [0, 1])
  assert.equal(restored.cursor, 2)
  assert.equal(timelineCards(restored.items, false)[0].status, 'SUCCESS')
})

test('out-of-order SSE waits for the gap and repeated replay never duplicates events', () => {
  const buffer = createSequenceBuffer()
  assert.deepEqual(buffer.push([{ id: 'r:2', sequence: 2 }]), [])
  assert.equal(buffer.hasGap, true)
  assert.deepEqual(buffer.push([{ id: 'r:1', sequence: 1 }]).map((item) => item.sequence), [1, 2])
  assert.equal(buffer.cursor, 2)
  assert.deepEqual(buffer.push([{ id: 'r:1', sequence: 1 }, { id: 'r:2', sequence: 2 }]), [])
})

test('successful pages can be rendered before a later page fails', async () => {
  const visible = []
  await assert.rejects(loadTimelineSince(async (cursor) => {
    if (!cursor) return { items: [{ sequence: 1 }], throughSequence: 1, hasMore: true }
    throw new Error('second page failed')
  }, 0, () => false, (items) => visible.push(...items)), /second page failed/)
  assert.deepEqual(visible.map((item) => item.sequence), [1])
})

test('reasoning and text blocks remain separate and worker text is visible in conversation details', () => {
  const events = [
    { id: 'r:1', sequence: 1, stepId: 'worker', parentStepId: 'root', type: 'REASONING_DELTA', resultSummary: '分析' },
    { id: 'r:2', sequence: 2, stepId: 'worker', parentStepId: 'root', type: 'CONTENT_DELTA', replyId: 'a', blockId: 'one', resultSummary: '正文甲' },
    { id: 'r:3', sequence: 3, stepId: 'worker', parentStepId: 'root', type: 'CONTENT_DELTA', replyId: 'a', blockId: 'two', resultSummary: '正文乙' },
  ]
  assert.equal(timelineCards(events, false).length, 3)
  assert.equal(timelineCards(events, false)[0].resultSummary, '分析')
})

test('message watermarks remove projected text without mixing worker or provider analysis', () => {
  const events = [
    { runId: 'r', sequence: 1, type: 'CONTENT_DELTA', resultSummary: '已投影' },
    { runId: 'r', sequence: 2, type: 'CONTENT_DELTA', resultSummary: '新增正文' },
    { runId: 'r', sequence: 3, type: 'CONTENT_DELTA', parentStepId: 'root', resultSummary: '子 Agent' },
    { runId: 'r', sequence: 4, type: 'REASONING_DELTA', resultSummary: '供应商分析' },
  ]
  assert.equal(unprojectedRootText(events, 'r', 1), '新增正文')
  assert.equal(unprojectedRootText(events, 'r', 4), '')
})

test('conversation groups consecutive thoughts without moving tool results or mixing attempts', () => {
  const thought = { type: 'REASONING_DELTA', stepId: 'director', ownerEpoch: 1, replyId: 'model-reply' }
  const items = [
    { ...thought, id: 'first', resultSummary: '正在查询' },
    { ...thought, id: 'next', resultSummary: '项目。' },
    { ...started, id: 'tool' },
    { ...thought, id: 'after-tool', resultSummary: '继续分析。' },
    { ...thought, id: 'new-attempt', ownerEpoch: 2, resultSummary: '新尝试。' },
  ]
  const grouped = groupConversationThoughts(items)
  assert.deepEqual(grouped.map(({ id }) => id), ['first', 'tool', 'after-tool', 'new-attempt'])
  assert.equal(grouped[0].resultSummary, '正在查询项目。')
  assert.equal(items[0].resultSummary, '正在查询')
})

test('collaboration appears between the director output before and after delegation', () => {
  const root = { stepId: 'director' }
  const plan = { planId: 'plan', round: 1 }
  const cards = [
    { type: 'CONTENT_DELTA', stepId: 'director', sequence: 2 },
    { type: 'PLAN_CREATED', stepId: 'director', planId: 'plan', sequence: 5 },
    { type: 'CONTENT_DELTA', stepId: 'worker', planId: 'plan', sequence: 9 },
    { type: 'CONTENT_DELTA', stepId: 'director', sequence: 12 },
  ]
  const sections = orderedExecutionSections([root], [plan], cards)
  assert.deepEqual(sections.map((section) => section.id), ['agent:director', 'plan:plan', 'agent:director:after:plan'])
  assert.deepEqual(sections[0].items.map((item) => item.sequence), [2])
  assert.deepEqual(sections[2].items.map((item) => item.sequence), [12])
})

test('committed approval and media events wake their panels without waking on token deltas', () => {
  const events = [
    { type: 'APPROVAL_REQUIRED', sequence: 2 }, { type: 'CONTENT_DELTA', sequence: 3 },
    { type: 'APPROVAL_DECIDED', sequence: 8 }, { type: 'ARTIFACT_CREATED', sequence: 12 },
    { type: 'TOOL_COMPLETED', sequence: 15 },
  ]
  assert.deepEqual(taskActionSequences(events.slice(0, 2)), { approval: 2, media: 0 })
  assert.deepEqual(taskActionSequences(events), { approval: 8, media: 12 })
})

test('regeneration starts a new proposal for the original shot with only public generation inputs', () => {
  const job = { changeSetId: 'proposal', projectId: 8, shotId: 12 }
  const proposed = { shotId: 12, provider: 'AGNES', configId: 23, model: 'agnes-image-2.5-flash',
    width: 1248, height: 832, prompt: 'old prompt', configSnapshot: { baseUrl: 'provider' } }
  const proposal = { ...job, kind: 'IMAGE_GENERATION', proposed }
  const request = imageRegenerationRequest(proposal, job, ' different lighting  ', 'new-request')
  assert.equal(request.requestId, 'new-request')
  assert.equal(request.operationType, 'GENERATE_SHOT_IMAGE')
  assert.deepEqual(request.target, { resourceType: 'STORYBOARD_SHOT', resourceId: job.shotId })
  const start = request.instruction.indexOf('{')
  assert.deepEqual(JSON.parse(request.instruction.slice(start)), { shotId: 12, provider: 'AGNES', configId: 23,
    model: 'agnes-image-2.5-flash', width: 1248, height: 832, prompt: 'different lighting' })
  assert.throws(() => imageRegenerationRequest(proposal, { ...job, shotId: 13 }, 'new', 'request'), /无法恢复/)
})
