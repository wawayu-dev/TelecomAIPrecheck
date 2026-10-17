import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    server: {
      host: '127.0.0.1',
      proxy: {
        '/api': { target: env.BACKEND_URL || 'http://127.0.0.1:8080' },
        '/actuator': { target: env.BACKEND_URL || 'http://127.0.0.1:8080' },
      },
    },
    test: { environment: 'jsdom', clearMocks: true },
  }
})
