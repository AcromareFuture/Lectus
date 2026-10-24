import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  // Relative assets allow deployment behind a separately configured application entry.
  base: process.env.VITE_PUBLIC_BASE || './',
  build: { rollupOptions: { output: { manualChunks(id) {
    if (id.includes('/node_modules/zrender/')) return 'chart-renderer';
    if (id.includes('/node_modules/echarts/')) return 'chart-engine';
  } } } },
  server: { host: '127.0.0.1', port: 4208, strictPort: true, proxy: {
    '/api': { target: process.env.LECTUS_API_ORIGIN || 'http://127.0.0.1:8080', changeOrigin: true },
  } },
});
