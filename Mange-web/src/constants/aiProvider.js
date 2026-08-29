/** AI 服务配置字段限制。 */
export const AI_CONFIG_LIMITS = Object.freeze({
  name: 80,
  baseUrl: 1024,
  model: 160,
  apiKey: 2048,
  remark: 500,
})

/** AI 服务配置接口路径。 */
export const AI_PROVIDER_ENDPOINTS = Object.freeze({
  configs: '/ai-provider-configs',
})

/** 可选择的 AI 服务商及其推荐连接默认值。 */
export const AI_PROVIDER_OPTIONS = Object.freeze([
  { value: 'OPENAI', label: 'OpenAI', baseUrl: 'https://api.openai.com', model: '' },
  { value: 'OPENAI_COMPATIBLE', label: 'OpenAI Compatible', baseUrl: '', model: '' },
  { value: 'ANTHROPIC', label: 'Anthropic', baseUrl: 'https://api.anthropic.com', model: '' },
  { value: 'GEMINI', label: 'Gemini', baseUrl: 'https://generativelanguage.googleapis.com', model: '' },
  { value: 'DEEPSEEK', label: 'DeepSeek', baseUrl: 'https://api.deepseek.com', model: '' },
  { value: 'DASHSCOPE', label: 'DashScope', baseUrl: 'https://dashscope.aliyuncs.com', model: '' },
  { value: 'OLLAMA', label: 'Ollama', baseUrl: 'http://localhost:11434', model: '' },
  { value: 'CUSTOM', label: 'Custom', baseUrl: '', model: '' },
])

export function aiProviderConfigEndpoint(configId) {
  return `${AI_PROVIDER_ENDPOINTS.configs}/${encodeURIComponent(configId)}`
}

export function aiProviderDefaultEndpoint(configId) {
  return `${aiProviderConfigEndpoint(configId)}/default`
}
