import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    // Pinned: 5173 and 5174 are used by other local stacks on this machine, and the
    // backend's CORS allowlist is keyed to this exact origin. strictPort makes a clash
    // fail loudly instead of silently drifting to a port CORS will then reject.
    port: 5175,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
