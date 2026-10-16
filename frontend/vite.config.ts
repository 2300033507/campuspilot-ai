import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // The backend runs on a different origin in development. Proxying
    // through Vite means the browser sees one origin, so there are no
    // CORS preflights in development at all. In production the two are
    // genuinely different origins, which is why SecurityConfig still
    // declares them explicitly.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});