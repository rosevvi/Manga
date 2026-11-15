import { CHANGE_SET_KIND } from '../constants/changeSet.js'
import { CREATIVE_OPERATION, CREATIVE_PERMISSION, CREATIVE_RESOURCE } from '../constants/creative.js'

/** 后端单轮指令的最大字符数。 */
const MAX_INSTRUCTION_CHARS = 12000

/** 用用户选择的参数创建图片提案指令，凭据和端点由服务端解析。 */
export function imageProposalInstruction({ shotId, provider, configId, model, prompt, width, height }) {
  return `请读取目标镜头，按以下参数调用 propose_image_generation 提交单张图片提案，等待我批准。参数：${JSON.stringify({ shotId, provider, configId, model, prompt, width, height })}`
}

/** 重生成绑定产物的原始镜头，并复用原提案的模型、配置和尺寸。 */
export function imageRegenerationRequest(proposal, job, prompt, requestId) {
  if (proposal.kind !== CHANGE_SET_KIND.IMAGE_GENERATION || proposal.changeSetId !== job.changeSetId
    || String(proposal.projectId) !== String(job.projectId)
    || String(proposal.proposed?.shotId) !== String(job.shotId) || !prompt.trim()) {
    throw new Error('无法恢复这张图片的生成参数')
  }
  return { requestId, projectId: job.projectId, operationType: CREATIVE_OPERATION.GENERATE_SHOT_IMAGE,
    target: { resourceType: CREATIVE_RESOURCE.STORYBOARD_SHOT, resourceId: job.shotId },
    permissionPolicy: CREATIVE_PERMISSION.PROPOSE_CHANGES,
    instruction: imageProposalInstruction({ ...proposal.proposed, prompt: prompt.trim() }) }
}

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
  } else if (proposal.kind === CHANGE_SET_KIND.IMAGE_GENERATION) {
    refresh = `请读取镜头（ID：${proposal.proposed.shotId}）最新内容，按上次明确选择的供应商、模型和图片参数重新提出图片生成提案，等待我审批。`
  } else {
    refresh = proposal.chapterId
      ? `请重新读取章节（ID：${proposal.chapterId}），按上次任务目标重新提出完整改写提案，等待我审批。`
      : `请读取当前项目的最新剧本目录，重新生成标题为《${proposal.proposed.title}》的新章节提案，等待我审批。`
  }
  return `${original.slice(0, MAX_INSTRUCTION_CHARS - refresh.length)}${refresh}`
}
