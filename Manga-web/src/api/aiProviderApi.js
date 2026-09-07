import { authorizedRequest } from './authorizedApi'
import {
  AI_PROVIDER_ENDPOINTS,
  aiProviderConfigEndpoint,
  aiProviderDefaultEndpoint,
  aiProviderTestEndpoint,
} from '../constants/aiProvider'

export function getAiProviderOptions(accessToken) {
  return authorizedRequest(accessToken, AI_PROVIDER_ENDPOINTS.providers)
}

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

export function testAiProviderConnection(accessToken, configId) {
  return authorizedRequest(accessToken, aiProviderTestEndpoint(configId), { method: 'POST' })
}

export function testAiProviderDraftConnection(accessToken, config) {
  return authorizedRequest(accessToken, AI_PROVIDER_ENDPOINTS.testConnection, {
    method: 'POST',
    body: JSON.stringify(config),
  })
}
