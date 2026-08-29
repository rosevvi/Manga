/** 项目与分镜接口、状态及表单限制。 */
export const PROJECT_ENDPOINTS = Object.freeze({
  projects: '/projects',
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

export const PROJECT_LIMITS = Object.freeze({
  name: 120,
  description: 1000,
  coverUrl: 1024,
  genre: 64,
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

export function storyboardShotsEndpoint(projectId) {
  return `${projectEndpoint(projectId)}/storyboard-shots`
}

export function storyboardShotEndpoint(projectId, shotId) {
  return `${storyboardShotsEndpoint(projectId)}/${encodeURIComponent(shotId)}`
}

export function storyboardShotOrderEndpoint(projectId) {
  return `${storyboardShotsEndpoint(projectId)}/order`
}
