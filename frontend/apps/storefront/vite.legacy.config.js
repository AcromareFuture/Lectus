import { defineConfig } from 'vite';
export default defineConfig({base:'./',build:{outDir:'dist'},server:{proxy:{'/api':{target:process.env.LECTUS_API_ORIGIN || 'http://127.0.0.1:8080',changeOrigin:true,rewrite:path=>path.replace(/^\/api/,'/api/legacy')}}}});
