/** AI 服务配置字段限制。 */
export const AI_CONFIG_LIMITS = Object.freeze({
  name: 80,
  baseUrl: 1024,
  model: 160,
  apiKey: 2048,
  proxyHost: 255,
  proxyUsername: 255,
  proxyPassword: 512,
  remark: 500,
})

/** AI 服务出站代理类型。 */
export const AI_PROXY_OPTIONS = Object.freeze([
  { value: 'NONE', labelKey: 'aiConfig.proxy.none' },
  { value: 'HTTP', labelKey: 'aiConfig.proxy.http' },
  { value: 'SOCKS5', labelKey: 'aiConfig.proxy.socks5' },
])

/** AI 服务配置接口路径。 */
export const AI_PROVIDER_ENDPOINTS = Object.freeze({
  configs: '/ai-provider-configs',
  providers: '/ai-provider-configs/providers',
  testConnection: '/ai-provider-configs/test-connection',
})

/** 可选择的 AI 服务商及其推荐连接默认值。 */
export const AI_PROVIDER_OPTIONS = Object.freeze([
  {
    value: 'OPENAI',
    label: 'OpenAI',
    baseUrl: 'https://api.openai.com',
    model: 'gpt-4o-mini',
    apiKeyRequired: true,
    capabilities: ['TEXT', 'IMAGE', 'VIDEO'],
    description: 'OpenAI 官方模型和生成接口',
  },
  {
    value: 'OPENAI_COMPATIBLE',
    label: 'OpenAI Compatible',
    baseUrl: '',
    model: '',
    apiKeyRequired: true,
    capabilities: ['TEXT'],
    description: '兼容 OpenAI 协议的第三方网关',
  },
  {
    value: 'ANTHROPIC',
    label: 'Anthropic',
    baseUrl: 'https://api.anthropic.com',
    model: 'claude-3-5-sonnet-latest',
    apiKeyRequired: true,
    capabilities: ['TEXT'],
    description: 'Claude 系列文本与多模态理解模型',
  },
  {
    value: 'GEMINI',
    label: 'Gemini',
    baseUrl: 'https://generativelanguage.googleapis.com',
    model: 'gemini-2.0-flash',
    apiKeyRequired: true,
    capabilities: ['TEXT', 'IMAGE'],
    description: 'Google AI Studio / Gemini Developer API',
  },
  {
    value: 'DEEPSEEK',
    label: 'DeepSeek',
    baseUrl: 'https://api.deepseek.com',
    model: 'deepseek-chat',
    apiKeyRequired: true,
    capabilities: ['TEXT'],
    description: 'DeepSeek 官方文本模型',
  },
  {
    value: 'DASHSCOPE',
    label: 'DashScope',
    baseUrl: 'https://dashscope.aliyuncs.com',
    model: 'qwen-plus',
    apiKeyRequired: true,
    capabilities: ['TEXT', 'IMAGE', 'VIDEO'],
    description: '阿里云百炼 / DashScope',
  },
  {
    value: 'OLLAMA',
    label: 'Ollama',
    baseUrl: 'http://localhost:11434',
    model: 'llama3.1',
    apiKeyRequired: false,
    capabilities: ['TEXT'],
    description: '本地部署的开源模型服务',
  },
  {
    value: 'VOLCENGINE',
    label: 'Volcengine',
    baseUrl: 'https://ark.cn-beijing.volces.com',
    model: '',
    apiKeyRequired: true,
    capabilities: ['TEXT', 'IMAGE'],
    description: '火山引擎方舟模型服务',
  },
  {
    value: 'NEWAPI',
    label: 'New API',
    baseUrl: '',
    model: '',
    apiKeyRequired: true,
    capabilities: ['TEXT', 'IMAGE', 'VIDEO'],
    description: 'OpenAI 兼容聚合网关',
  },
  {
    value: 'KLING',
    label: 'Kling',
    baseUrl: 'https://api.klingai.com',
    model: '',
    apiKeyRequired: true,
    capabilities: ['IMAGE', 'VIDEO'],
    description: '可灵图片与视频生成服务',
  },
  {
    value: 'JIMENG',
    label: 'Jimeng',
    baseUrl: '',
    model: '',
    apiKeyRequired: true,
    capabilities: ['IMAGE', 'VIDEO'],
    description: '即梦图片与视频生成服务或兼容网关',
  },
  {
    value: 'COMFYUI',
    label: 'ComfyUI',
    baseUrl: 'http://localhost:8188',
    model: '',
    apiKeyRequired: false,
    capabilities: ['IMAGE', 'VIDEO'],
    description: '本地或远程 ComfyUI 工作流服务',
  },
  {
    value: 'CUSTOM',
    label: 'Custom',
    baseUrl: '',
    model: '',
    apiKeyRequired: false,
    capabilities: ['TEXT', 'IMAGE', 'VIDEO'],
    description: '自定义 AI 服务接入',
  },
])

export const AI_CAPABILITY_LABELS = Object.freeze({
  TEXT: 'Text',
  IMAGE: 'Image',
  VIDEO: 'Video',
})

export function aiProviderConfigEndpoint(configId) {
  return `${AI_PROVIDER_ENDPOINTS.configs}/${encodeURIComponent(configId)}`
}

export function aiProviderDefaultEndpoint(configId) {
  return `${aiProviderConfigEndpoint(configId)}/default`
}

export function aiProviderTestEndpoint(configId) {
  return `${aiProviderConfigEndpoint(configId)}/test-connection`
}
