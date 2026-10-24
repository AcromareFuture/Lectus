import { defineConfig } from 'vite';
export default defineConfig({
  base: process.env.VITE_PUBLIC_BASE || (process.env.NODE_ENV === 'production' ? '/lectus/' : '/'),
  publicDir: 'public',
  build: { outDir: 'dist-web', assetsDir: 'desktop-assets', rollupOptions: { input: 'index.html' } },
  server: { host: '0.0.0.0', port: 4206, strictPort: true, proxy: {
    '/api': { target: process.env.LECTUS_API_ORIGIN || 'http://127.0.0.1:8080', changeOrigin: true,
      configure(proxy) { proxy.on('proxyReq', request => request.setHeader('Origin', 'http://127.0.0.1:4206')); },
    },
  } },
});
