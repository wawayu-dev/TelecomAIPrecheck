import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './e2e', timeout: 60000, workers: 1, retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:5174', browserName: 'chromium' },
  // 不录制网络 trace，避免保存 Basic 凭据或 CSRF Token。
  webServer: { command: 'npm run dev -- --port 5174 --strictPort', url: 'http://127.0.0.1:5174', reuseExistingServer: !process.env.CI },
})
