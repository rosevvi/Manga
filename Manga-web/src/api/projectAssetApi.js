import { authorizedRequest } from './authorizedApi'
import { projectEndpoint, projectScriptChapterEndpoint } from '../constants/project'

const assetsPath = (projectId) => `${projectEndpoint(projectId)}/assets`
const chapterAssetsPath = (projectId, chapterId) => `${projectScriptChapterEndpoint(projectId, chapterId)}/assets`
const json = (method, body) => ({ method, body: JSON.stringify(body) })

/** 访问项目目录，与按章节读取图片的接口分开。 */
export function listProjectAssets(token, projectId) {
  return authorizedRequest(token, assetsPath(projectId))
}

export function listChapterAssets(token, projectId, chapterId) {
  return authorizedRequest(token, chapterAssetsPath(projectId, chapterId))
}

/** 获取导入章节时自动创建的识别任务。 */
export function getChapterExtraction(token, projectId, chapterId) {
  return authorizedRequest(token, `${chapterAssetsPath(projectId, chapterId)}/extraction`)
}

export function createProjectAsset(token, projectId, asset) {
  return authorizedRequest(token, assetsPath(projectId), json('POST', asset))
}

export function updateProjectAsset(token, projectId, assetId, fields) {
  return authorizedRequest(token, `${assetsPath(projectId)}/${assetId}`, json('PUT', fields))
}

export function setChapterAsset(token, projectId, chapterId, assetId, attached) {
  return authorizedRequest(token, `${chapterAssetsPath(projectId, chapterId)}/${assetId}`, { method: attached ? 'PUT' : 'DELETE' })
}

export function saveAssetVisual(token, projectId, assetId, visualId, visual) {
  const path = `${assetsPath(projectId)}/${assetId}/visuals`
  return authorizedRequest(token, visualId ? `${path}/${visualId}` : path, json(visualId ? 'PUT' : 'POST', visual))
}

export function setAssetPrimaryVisual(token, projectId, assetId, visualId) {
  return authorizedRequest(token, `${assetsPath(projectId)}/${assetId}/primary-visual/${visualId}`, { method: 'PUT' })
}

export function getShotAssetImage(token, projectId, shotId) {
  return authorizedRequest(token, `${projectEndpoint(projectId)}/storyboard-shots/${shotId}/asset-image`)
}

export function adoptShotAssetImage(token, projectId, shotId, image) {
  return authorizedRequest(token, `${projectEndpoint(projectId)}/storyboard-shots/${shotId}/asset-image`, json('PUT', image))
}
