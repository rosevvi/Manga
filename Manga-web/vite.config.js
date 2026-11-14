import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import { HttpsProxyAgent } from 'https-proxy-agent'

/** 开发环境由 Vite 转发 API 与本地媒体路径，浏览器只请求当前站点。 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080'
  // Node 不会自动采用浏览器系统代理；仅开发配置显式选择代理，保持 TLS 证书校验。
  const createProxyAgent = () => env.DEV_API_PROXY_URL && backendTarget.startsWith('https:')
    ? new HttpsProxyAgent(env.DEV_API_PROXY_URL, { keepAlive: true }) : undefined
  const backendProxy = {
    target: backendTarget,
    changeOrigin: true,
    agent: createProxyAgent(),
  }

  return {
    plugins: [react()],
    server: {
      proxy: {
        // 二维码的短超时使用独立连接池，避免影响复用连接上的流式请求。
        '/api/v1/auth/wechat/qr': {
          ...backendProxy,
          agent: createProxyAgent(),
          proxyTimeout: 15_000,
          timeout: 20_000,
        },
        '/api/v1': backendProxy,
        '/uploads': backendProxy,
      },
    },
  }
})
