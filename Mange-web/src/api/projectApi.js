import { authorizedFormRequest, authorizedRequest } from './authorizedApi'
import {
  PROJECT_ENDPOINTS,
  projectEndpoint,
  storyboardShotEndpoint,
  storyboardShotOrderEndpoint,
  storyboardShotsEndpoint,
} from '../constants/project'

export function getProjects(accessToken) {
  return authorizedRequest(accessToken, PROJECT_ENDPOINTS.projects)
}

export function getArtStylePresets(accessToken) {
  return authorizedRequest(accessToken, PROJECT_ENDPOINTS.artStylePresets)
}

export function uploadProjectImage(accessToken, file, subDir = 'images') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('subDir', subDir)
  return authorizedFormRequest(accessToken, PROJECT_ENDPOINTS.imageUploads, formData)
}

export function createProject(accessToken, project) {
  return authorizedRequest(accessToken, PROJECT_ENDPOINTS.projects, {
    method: 'POST',
    body: JSON.stringify(project),
  })
}

export function updateProject(accessToken, projectId, project) {
  return authorizedRequest(accessToken, projectEndpoint(projectId), {
    method: 'PUT',
    body: JSON.stringify(project),
  })
}

export function deleteProject(accessToken, projectId) {
  return authorizedRequest(accessToken, projectEndpoint(projectId), { method: 'DELETE' })
}

export function getStoryboardShots(accessToken, projectId) {
  return authorizedRequest(accessToken, storyboardShotsEndpoint(projectId))
}

export function createStoryboardShot(accessToken, projectId, shot) {
  return authorizedRequest(accessToken, storyboardShotsEndpoint(projectId), {
    method: 'POST',
    body: JSON.stringify(shot),
  })
}

export function updateStoryboardShot(accessToken, projectId, shotId, shot) {
  return authorizedRequest(accessToken, storyboardShotEndpoint(projectId, shotId), {
    method: 'PUT',
    body: JSON.stringify(shot),
  })
}

export function deleteStoryboardShot(accessToken, projectId, shotId) {
  return authorizedRequest(accessToken, storyboardShotEndpoint(projectId, shotId), { method: 'DELETE' })
}

export function reorderStoryboardShots(accessToken, projectId, shotIds) {
  return authorizedRequest(accessToken, storyboardShotOrderEndpoint(projectId), {
    method: 'PUT',
    body: JSON.stringify({ shotIds }),
  })
}
