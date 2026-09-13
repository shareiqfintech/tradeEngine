import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendTarget = env.VITE_API_BASE_URL || 'http://localhost:8080'

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      // Local-dev convenience: same-origin '/api' calls are proxied straight
      // to the Spring Boot backend, so the browser never needs CORS
      // configured on the backend for day-to-day frontend development.
      // In production the Nginx image performs the equivalent proxy (see
      // nginx.conf) - the frontend code itself always just calls '/api/...'
      // relative to VITE_API_BASE_URL (see src/api/apiClient.ts).
      proxy: {
        '/api': {
          target: backendTarget,
          changeOrigin: true,
        },
        '/actuator': {
          target: backendTarget,
          changeOrigin: true,
        },
      },
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks(id: string) {
            if (id.includes('node_modules')) {
              if (id.includes('react-dom') || id.includes('/react/') || id.includes('react-router')) return 'vendor-react'
              if (id.includes('@tanstack')) return 'vendor-query'
              if (id.includes('recharts') || id.includes('d3-')) return 'vendor-charts'
              if (id.includes('@radix-ui')) return 'vendor-radix'
              return 'vendor'
            }
            return undefined
          },
        },
      },
    },
  }
})
