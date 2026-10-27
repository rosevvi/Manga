import { CHANGE_SET_KIND } from '../constants/changeSet.js'

/** 后端单轮指令的最大字符数。 */
const MAX_INSTRUCTION_CHARS = 12000

/** 生成章节完整字段差异，不隐藏清空内容或场景结构变化。 */
export function scriptDifferences(before, proposed) {
  const fields = [['title', '标题'], ['synopsis', '简介'], ['rawContent', '正文'], ['scenes', '场景和对白']]
  return fields.map(([field, label]) => {
    const previous = field === 'scenes' ? JSON.stringify(before?.[field] ?? [], null, 2) : before?.[field] ?? ''
    const next = field === 'scenes' ? JSON.stringify(proposed?.[field] ?? [], null, 2) : proposed?.[field] ?? ''
    return { field, label, previous, next, changed: previous !== next }
  })
}

/** 重新生成必须读取最新业务数据并创建新提案，不能再次应用旧提案。 */
export function regenerationInstruction(proposal) {
  const original = proposal.instruction ? `${proposal.instruction}\n` : ''
  let refresh
  if (proposal.kind === CHANGE_SET_KIND.STORYBOARD) {
    refresh = `请重新读取章节（ID：${proposal.chapterId}）及现有分镜，基于最新内容重新生成分镜草稿提案，等待我审批。`
  } else {
    refresh = proposal.chapterId
      ? `请重新读取章节（ID：${proposal.chapterId}），按上次任务目标重新提出完整改写提案，等待我审批。`
      : `请读取当前项目的最新剧本目录，重新生成标题为《${proposal.proposed.title}》的新章节提案，等待我审批。`
  }
  return `${original.slice(0, MAX_INSTRUCTION_CHARS - refresh.length)}${refresh}`
}
