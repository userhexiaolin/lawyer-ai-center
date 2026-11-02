import { fileURLToPath } from 'node:url';

import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vite';

// Isolated UI fixture only. No production API, router or authentication is replaced.
const local = (path: string) => fileURLToPath(new URL(path, import.meta.url));
export default defineConfig({
  root: local('./'),
  plugins: [vue()],
  css: { postcss: local('../../apps/web-antd') },
  resolve: {
    alias: [
      {
        find: 'ant-design-vue',
        replacement: local('../../apps/web-antd/node_modules/ant-design-vue'),
      },
      {
        find: '#/api/legal/doc-template',
        replacement: local('./fixture-api.ts'),
      },
      { find: '@vben/access', replacement: local('./fixture-access.ts') },
      { find: '@vben/common-ui', replacement: local('./fixture-page.ts') },
      { find: '#', replacement: local('../../apps/web-antd/src') },
    ],
  },
  server: { host: '127.0.0.1', port: 5198, strictPort: true },
});
