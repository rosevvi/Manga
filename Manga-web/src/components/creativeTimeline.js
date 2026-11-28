/** 整轮分页成功才返回新游标；中断或失败时调用方保留旧游标，重试可安全重放。 */
export async function loadTimelineSince(loadPage, afterSequence, cancelled = () => false, onPage = () => {}) {
  const items = []
  let cursor = afterSequence
  let more
  do {
    const page = await loadPage(cursor)
    if (cancelled()) return null
    if (page.hasMore && page.throughSequence <= cursor) throw new Error('时间线游标未推进，请重新加载任务')
    items.push(...page.items)
    cursor = page.throughSequence
    onPage(page.items, cursor)
    more = page.hasMore
  } while (more)
  return { items, cursor }
}

/** 根据服务端事实合并同一工具调用，重放、分页和重连不改变卡片身份及顺序。 */
export function timelineCards(events, showText, run) {
  const cards = []
  const tools = new Map()
  for (const event of events) {
    if (['CONTENT_DELTA', 'REASONING_DELTA'].includes(event.type)) {
      if (!showText && event.type === 'CONTENT_DELTA' && !event.parentStepId) continue
      const last = cards.at(-1)
      if (last?.type === event.type && last.stepId === event.stepId && last.ownerEpoch === event.ownerEpoch
        && last.replyId === event.replyId && last.blockId === event.blockId) {
        last.resultSummary += event.resultSummary || ''
        last.lastSequence = event.sequence
      }
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

/** 已提交事件只按连续序号应用，重复和乱序推送不改变正文或工具身份。 */
export function createSequenceBuffer(afterSequence = 0) {
  let cursor = afterSequence
  const pending = new Map()
  return {
    get cursor() { return cursor },
    get hasGap() { return pending.size > 0 && !pending.has(cursor + 1) },
    push(items) {
      for (const item of items) if (item.sequence > cursor) pending.set(item.sequence, item)
      const ready = []
      while (pending.has(cursor + 1)) {
        const item = pending.get(++cursor)
        pending.delete(cursor)
        ready.push(item)
      }
      return ready
    },
  }
}

/** 只把主 Agent 中消息尚未覆盖的正文叠加到当前快照，不混入 Worker 或供应商分析。 */
export function unprojectedRootText(events, runId, throughSequence = 0) {
  return events.filter((event) => event.type === 'CONTENT_DELTA' && event.runId === runId
    && !event.parentStepId && event.sequence > throughSequence).map((event) => event.resultSummary || '').join('')
}

const APPROVAL_EVENTS = new Set(['APPROVAL_REQUIRED', 'APPROVAL_DECIDED'])
const MEDIA_EVENTS = new Set(['APPROVAL_DECIDED', 'JOB_SUBMITTED', 'JOB_PROGRESS', 'JOB_COMPLETED', 'JOB_FAILED',
  'JOB_CANCELLED', 'ARTIFACT_CREATED', 'ARTIFACT_ADOPTED'])

/** 正文、分析和工具片段继续增量渲染，只有决策和作业事件唤醒对应事实查询。 */
export function taskActionSequences(events) {
  return {
    approval: events.findLast((event) => APPROVAL_EVENTS.has(event.type))?.sequence || 0,
    media: events.findLast((event) => MEDIA_EVENTS.has(event.type))?.sequence || 0,
  }
}

/** 主 Agent 在派发前后的公开输出分段，协作轮次插入其首次出现的事件位置。 */
export function orderedExecutionSections(rootNodes, plans, cards) {
  const planSections = plans.map((plan) => ({
    plan, id: `plan:${plan.planId}`,
    sequence: cards.find((item) => item.planId === plan.planId)?.sequence ?? Infinity,
  })).sort((left, right) => left.sequence - right.sequence || (left.plan.round || 0) - (right.plan.round || 0))
  const sections = [...planSections]
  for (const node of rootNodes) {
    const byBoundary = new Map()
    const items = cards.filter((item) => item.stepId === node.stepId && isAgentActivity(item.type))
    for (const item of items) {
      const preceding = planSections.findLast((section) => section.sequence < item.sequence)
      const boundaryId = preceding?.plan.planId
      if (!byBoundary.has(boundaryId)) byBoundary.set(boundaryId, [])
      byBoundary.get(boundaryId).push(item)
    }
    if (!byBoundary.size) byBoundary.set(undefined, [])
    const groups = [...byBoundary.entries()]
    groups.forEach(([boundaryId, segment], index) => sections.push({
      node, items: segment, sequence: segment[0]?.sequence ?? 0, current: index === groups.length - 1,
      id: boundaryId ? `agent:${node.stepId}:after:${boundaryId}` : `agent:${node.stepId}`,
    }))
  }
  return sections.sort((left, right) => left.sequence - right.sequence)
}

/** 公开正文、供应商分析和工具调用属于 Agent 的执行内容。 */
export function isAgentActivity(type) {
  return type === 'CONTENT_DELTA' || type === 'REASONING_DELTA' || type.startsWith('TOOL_')
}

/** 合并同一段连续思考，工具调用、回复和执行代次划分思考段落。 */
export function groupConversationThoughts(items) {
  const groups = []
  for (const item of items) {
    const last = groups.at(-1)
    if (item.type === 'REASONING_DELTA' && last?.type === item.type && last.stepId === item.stepId
      && last.ownerEpoch === item.ownerEpoch && last.replyId === item.replyId) {
      groups[groups.length - 1] = { ...last, resultSummary: (last.resultSummary || '') + (item.resultSummary || '') }
    } else {
      groups.push(item)
    }
  }
  return groups
}
