import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Frontend runs on :5173 inside the container. API and SSE traffic is
// proxied to the SentinelX API Gateway via the docker network.
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.VITE_API_BASE || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/actuator': {
        target: process.env.VITE_API_BASE || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
});