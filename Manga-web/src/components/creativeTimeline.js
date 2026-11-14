/** 整轮分页成功才返回新游标；中断或失败时调用方保留旧游标，重试可安全重放。 */
export async function loadTimelineSince(loadPage, afterSequence, cancelled = () => false) {
  const items = []
  let cursor = afterSequence
  let more
  do {
    const page = await loadPage(cursor)
    if (cancelled()) return null
    if (page.hasMore && page.throughSequence <= cursor) throw new Error('时间线游标未推进，请重新加载任务')
    items.push(...page.items)
    cursor = page.throughSequence
    more = page.hasMore
  } while (more)
  return { items, cursor }
}

/** 根据服务端事实合并同一工具调用，重放、分页和重连不改变卡片身份及顺序。 */
export function timelineCards(events, showText, run) {
  const cards = []
  const tools = new Map()
  for (const event of events) {
    if (event.type === 'CONTENT_DELTA') {
      if (!showText) continue
      if (cards.at(-1)?.type === 'CONTENT_DELTA' && cards.at(-1).stepId === event.stepId) cards.at(-1).resultSummary += event.resultSummary || ''
      else cards.push({ ...event })
    } else if (event.type.startsWith('TOOL_')) {
      const key = `${event.stepId}.${event.ownerEpoch || 'legacy'}.${event.actionId}`
      if (tools.has(key)) {
        const previous = tools.get(key)
        Object.assign(previous, event, { id: previous.id, startedAt: event.startedAt || previous.startedAt })
      } else { const card = { ...event }; cards.push(card); tools.set(key, card) }
    } else {
      // 新代次、步骤终态和 Run 终态都会使尚未结束的旧工具失去执行权。
      if (['STEP_STARTED', 'STEP_FAILED', 'STEP_COMPLETED', 'RUN_FAILED', 'RUN_CANCELLED', 'RUN_COMPLETED'].includes(event.type)) {
        for (const tool of tools.values()) {
          const ended = event.type.startsWith('RUN_') || tool.stepId === event.stepId
            && (event.type !== 'STEP_STARTED' || tool.ownerEpoch && tool.ownerEpoch !== event.ownerEpoch)
          if (ended && tool.status === 'RUNNING') { tool.status = 'INTERRUPTED'; tool.finishedAt = event.occurredAt }
        }
      }
      cards.push({ ...event })
    }
  }
  if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(run?.status)) {
    for (const tool of tools.values()) if (tool.status === 'RUNNING') { tool.status = 'INTERRUPTED'; tool.finishedAt = run.finishedAt }
  }
  return cards
}
