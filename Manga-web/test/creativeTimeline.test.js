import test from 'node:test'
import assert from 'node:assert/strict'
import { timelineCards, loadTimelineSince } from '../src/components/creativeTimeline.js'

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
