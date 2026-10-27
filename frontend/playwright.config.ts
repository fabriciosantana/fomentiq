import { defineConfig, devices } from '@playwright/test'

const webPort = process.env.PLAYWRIGHT_WEB_PORT || '14173'
const apiProxyTarget = process.env.PLAYWRIGHT_API_PROXY_TARGET || 'http://127.0.0.1:18080'
const webProtocol = process.env.PLAYWRIGHT_WEB_PROTOCOL || 'http'
if (!/^\d{1,5}$/.test(webPort) || Number(webPort) < 1 || Number(webPort) > 65535) {
  throw new Error('PLAYWRIGHT_WEB_PORT must be a valid TCP port')
}
if (!/^http:\/\/127\.0\.0\.1:\d{1,5}$/.test(apiProxyTarget)) {
  throw new Error('PLAYWRIGHT_API_PROXY_TARGET must be a loopback HTTP origin')
}
if (!['http', 'https'].includes(webProtocol)) throw new Error('PLAYWRIGHT_WEB_PROTOCOL must be http or https')
const webOrigin = `${webProtocol}://127.0.0.1:${webPort}`

export default defineConfig({
  testDir: './e2e',
  outputDir: process.env.PLAYWRIGHT_OUTPUT_DIR || 'test-results',
  fullyParallel: false,
  reporter: 'list',
  use: {
    baseURL: webOrigin,
    ignoreHTTPSErrors: webProtocol === 'https',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${webPort} --strictPort`,
    url: webOrigin,
    ignoreHTTPSErrors: webProtocol === 'https',
    reuseExistingServer: false,
    env: {
      VITE_API_PROXY_TARGET: apiProxyTarget,
      ...(process.env.VITE_DEV_HTTPS_KEY ? { VITE_DEV_HTTPS_KEY: process.env.VITE_DEV_HTTPS_KEY } : {}),
      ...(process.env.VITE_DEV_HTTPS_CERT ? { VITE_DEV_HTTPS_CERT: process.env.VITE_DEV_HTTPS_CERT } : {}),
    },
    timeout: 30_000,
  },
})
