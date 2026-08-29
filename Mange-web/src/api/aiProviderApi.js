import { authorizedRequest } from './authorizedApi'
import {
  AI_PROVIDER_ENDPOINTS,
  aiProviderConfigEndpoint,
  aiProviderDefaultEndpoint,
} from '../constants/aiProvider'

export function getAiProviderConfigs(accessToken) {
  return authorizedRequest(accessToken, AI_PROVIDER_ENDPOINTS.configs)
}

export function createAiProviderConfig(accessToken, config) {
  return authorizedRequest(accessToken, AI_PROVIDER_ENDPOINTS.configs, {
    method: 'POST',
    body: JSON.stringify(config),
  })
}

export function updateAiProviderConfig(accessToken, configId, config) {
  return authorizedRequest(accessToken, aiProviderConfigEndpoint(configId), {
    method: 'PUT',
    body: JSON.stringify(config),
  })
}

export function deleteAiProviderConfig(accessToken, configId) {
  return authorizedRequest(accessToken, aiProviderConfigEndpoint(configId), { method: 'DELETE' })
}

export function setDefaultAiProviderConfig(accessToken, configId) {
  return authorizedRequest(accessToken, aiProviderDefaultEndpoint(configId), { method: 'PUT' })
}
