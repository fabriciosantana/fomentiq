import react from '@vitejs/plugin-react'
import { readFileSync } from 'node:fs'
import { loadEnv } from 'vite'
import { configDefaults, defineConfig } from 'vitest/config'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const httpsKeyPath = env.VITE_DEV_HTTPS_KEY
  const httpsCertPath = env.VITE_DEV_HTTPS_CERT
  const https = httpsKeyPath && httpsCertPath
    ? { key: readFileSync(httpsKeyPath), cert: readFileSync(httpsCertPath) }
    : undefined

  return {
    plugins: [react()],
    server: {
      https,
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      restoreMocks: true,
      exclude: [...configDefaults.exclude, 'e2e/**'],
    },
  }
})
