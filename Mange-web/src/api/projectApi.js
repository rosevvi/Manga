import { authorizedFormRequest, authorizedRequest } from './authorizedApi'
import { AUTH_CONFIG } from '../constants/auth'
import {
  PROJECT_ENDPOINTS,
  projectEndpoint,
  projectScriptEndpoint,
  projectScriptGenerateEndpoint,
  projectScriptImportEndpoint,
  projectWorkflowStageEndpoint,
  projectWorkspaceEndpoint,
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

export function getProjectWorkspace(accessToken, projectId) {
  return authorizedRequest(accessToken, projectWorkspaceEndpoint(projectId))
}

export function getProjectScript(accessToken, projectId) {
  return authorizedRequest(accessToken, projectScriptEndpoint(projectId))
}

export function saveProjectScript(accessToken, projectId, script) {
  return authorizedRequest(accessToken, projectScriptEndpoint(projectId), {
    method: 'PUT',
    body: JSON.stringify(script),
  })
}

export function importProjectScript(accessToken, projectId, rawContent, sourceType) {
  return authorizedRequest(accessToken, projectScriptImportEndpoint(projectId), {
    method: 'POST',
    body: JSON.stringify({ rawContent, sourceType }),
  })
}

export function updateProjectWorkflowStage(accessToken, projectId, stage) {
  return authorizedRequest(accessToken, projectWorkflowStageEndpoint(projectId), {
    method: 'PUT',
    body: JSON.stringify({ stage }),
  })
}

export async function generateProjectScript(accessToken, projectId, prompt, onEvent, signal) {
  const response = await fetch(`${AUTH_CONFIG.apiBaseUrl}${projectScriptGenerateEndpoint(projectId)}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: `${AUTH_CONFIG.bearerType} ${accessToken}`,
    },
    body: JSON.stringify({ prompt }),
    signal,
  })
  if (!response.ok) {
    const payload = await response.json().catch(() => null)
    throw new Error(payload?.message ?? 'Unable to generate project script')
  }
  if (!response.body) throw new Error('Script generation stream is unavailable')

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = 'message'
  let eventData = []
  const dispatch = () => {
    if (eventData.length === 0) return
    const event = { event: eventName, data: eventData.join('\n') }
    onEvent?.(event)
    eventName = 'message'
    eventData = []
  }
  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done })
    const lines = buffer.split(/\r?\n/)
    buffer = lines.pop() ?? ''
    lines.forEach((line) => {
      if (!line) return dispatch()
      if (line.startsWith('event:')) eventName = line.slice(6).trim()
      if (line.startsWith('data:')) eventData.push(line.slice(5).trimStart())
    })
    if (done) {
      dispatch()
      break
    }
  }
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
