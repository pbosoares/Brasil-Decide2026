import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
export default defineConfig({ plugins: [react(), tailwindcss()], server: { port: 5173, strictPort: true, proxy: { '/results': { target: 'http://127.0.0.1:8080', rewrite: path => path.replace(/^\/results/, '/api/v1/results') } } }, preview: { port: 4173, strictPort: true } });
