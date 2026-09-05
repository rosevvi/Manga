/** 项目与分镜接口、状态及表单限制。 */
export const PROJECT_ENDPOINTS = Object.freeze({
  projects: '/projects',
  artStylePresets: '/projects/presets/art-styles',
  imageUploads: '/uploads/images',
})

export const PROJECT_STATUS = Object.freeze({
  DRAFT: 'DRAFT',
  IN_PROGRESS: 'IN_PROGRESS',
  COMPLETED: 'COMPLETED',
  ARCHIVED: 'ARCHIVED',
})

export const STORYBOARD_SHOT_STATUS = Object.freeze({
  DRAFT: 'DRAFT',
  READY: 'READY',
  COMPLETED: 'COMPLETED',
})

export const PROJECT_VISIBILITY_SCOPE = Object.freeze({
  PUBLIC: 'PUBLIC',
  PRIVATE: 'PRIVATE',
  TEAM: 'TEAM',
})

export const PROJECT_TYPES = Object.freeze(['漫剧', '短剧', '动画', '宣传片', 'MV'])

export const PROJECT_ASPECT_RATIOS = Object.freeze([
  { value: '16:9', labelKey: 'projects.aspectRatio.16_9', descriptionKey: 'projects.aspectRatio.landscape' },
  { value: '9:16', labelKey: 'projects.aspectRatio.9_16', descriptionKey: 'projects.aspectRatio.vertical' },
  { value: '1:1', labelKey: 'projects.aspectRatio.1_1', descriptionKey: 'projects.aspectRatio.square' },
  { value: '4:3', labelKey: 'projects.aspectRatio.4_3', descriptionKey: 'projects.aspectRatio.classic' },
])

export const CUSTOM_ART_STYLE = 'custom'

export const PROJECT_LIMITS = Object.freeze({
  name: 120,
  description: 1000,
  coverUrl: 1024,
  genre: 64,
  aspectRatio: 20,
  artStyle: 64,
  artStyleDescription: 2000,
  artStyleImagePrompt: 2000,
  artStyleImageUrl: 1024,
  shotTitle: 120,
  shotSceneName: 120,
  shotType: 32,
  shotCameraMovement: 64,
  shotDurationSeconds: 3600,
  shotContent: 2000,
  shotDialogue: 2000,
  shotSoundEffect: 500,
  shotImageUrl: 1024,
  shotNotes: 2000,
})

export function projectEndpoint(projectId) {
  return `${PROJECT_ENDPOINTS.projects}/${encodeURIComponent(projectId)}`
}

export function projectWorkspaceEndpoint(projectId) {
  return `${projectEndpoint(projectId)}/workspace`
}

export function projectWorkflowStageEndpoint(projectId) {
  return `${projectEndpoint(projectId)}/workflow-stage`
}

export function projectScriptEndpoint(projectId) {
  return `${projectEndpoint(projectId)}/script`
}

export function projectScriptImportEndpoint(projectId) {
  return `${projectScriptEndpoint(projectId)}/import`
}

export function projectScriptGenerateEndpoint(projectId) {
  return `${projectScriptEndpoint(projectId)}/generate`
}

export function projectMembersEndpoint(projectId) {
  return `${projectEndpoint(projectId)}/members`
}

export function storyboardShotsEndpoint(projectId) {
  return `${projectEndpoint(projectId)}/storyboard-shots`
}

export function storyboardShotEndpoint(projectId, shotId) {
  return `${storyboardShotsEndpoint(projectId)}/${encodeURIComponent(shotId)}`
}

export function storyboardShotOrderEndpoint(projectId) {
  return `${storyboardShotsEndpoint(projectId)}/order`
}
