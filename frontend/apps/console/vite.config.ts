import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig(({ mode }) => ({
  plugins: [vue()],
  server: {
    port: 4174,
    proxy: { '/api': { target: loadEnv(mode, '.', 'ISM_').ISM_CONSOLE_API_TARGET || 'http://127.0.0.1:18080', changeOrigin: true } },
  },
}));
