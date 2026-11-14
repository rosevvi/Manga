import { authorizedRequest } from './authorizedApi'
/** 作业 API 的客户端根路径。 */
const ENDPOINT = '/generation-jobs'
/** 下载前刷新签名并复核所有权，返回地址只短期使用。 */
export function getGeneratedMediaDownload(accessToken, jobId) {
  return authorizedRequest(accessToken, `${ENDPOINT}/${encodeURIComponent(jobId)}/download`, { cache: 'no-store' })
}
/** 每次采用前读取最新图片与项目版本，不沿用生成时的旧 revision。 */
export function previewArtifactAdoption(accessToken, jobId) {
  return authorizedRequest(accessToken, `${ENDPOINT}/${encodeURIComponent(jobId)}/adoption-preview`)
}
/** 确认只提交预览 revision，图片地址由服务器决定。 */
export function adoptGeneratedArtifact(accessToken, jobId, expectedProjectRevision) {
  return authorizedRequest(accessToken, `${ENDPOINT}/${encodeURIComponent(jobId)}/adopt`, {
    method: 'POST', body: JSON.stringify({ expectedProjectRevision }),
  })
}
/** 图片选项不读取或返回配置密钥。 */
export function listVideoGenerationOptions(accessToken) { return authorizedRequest(accessToken, `${ENDPOINT}/video-options`) }
/** 返回用户启用的图片模型。 */
export function listImageGenerationOptions(accessToken) { return authorizedRequest(accessToken, `${ENDPOINT}/options`) }
/** 从服务端恢复当前页，支持限定 Run，不依赖本地临时进度。 */
export function listGenerationJobs(accessToken, { projectId, runId, conversationId, beforeId, shotId, mediaType }) {
  const params = new URLSearchParams({ projectId: String(projectId) })
  if (runId) params.set('runId', runId)
  if (conversationId) params.set('conversationId', conversationId)
  if (beforeId) params.set('beforeId', String(beforeId))
  if (shotId) params.set('shotId', String(shotId))
  if (mediaType) params.set('mediaType', mediaType)
  return authorizedRequest(accessToken, `${ENDPOINT}?${params}`)
}
/** 取消结果采用权，不承诺供应商停止计费。 */
export function cancelGenerationJob(accessToken, jobId) {
  return authorizedRequest(accessToken, `${ENDPOINT}/${encodeURIComponent(jobId)}/cancel`, { method: 'POST' })
}
/** 用户明确核对后关闭未知作业，只释放平台等待，不重提。 */
export function acknowledgeUnknownJob(accessToken, jobId, note) {
  return authorizedRequest(accessToken, `${ENDPOINT}/${encodeURIComponent(jobId)}/acknowledge-unknown`, {
    method: 'POST', body: JSON.stringify({ checkedSupplier: true, note }),
  })
}
