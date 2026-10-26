import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  outputDir: process.env.PLAYWRIGHT_OUTPUT_DIR || 'test-results',
  fullyParallel: false,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 4173 --strictPort',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: false,
    env: { VITE_API_PROXY_TARGET: 'http://127.0.0.1:8080' },
    timeout: 30_000,
  },
})
