import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  // Relative assets allow deployment behind a separately configured application entry.
  base: process.env.VITE_PUBLIC_BASE || './',
  server: { host: '127.0.0.1', port: 4207, strictPort: true, proxy: {
    '/api': { target: process.env.LECTUS_API_ORIGIN || 'http://127.0.0.1:8080', changeOrigin: true },
  } },
});
