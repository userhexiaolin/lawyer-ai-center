import { fileURLToPath } from 'node:url';

import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '#': fileURLToPath(new URL('apps/web-antd/src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    include: ['apps/web-antd/src/views/legal/doc-template/__tests__/*.test.ts'],
    clearMocks: true,
  },
});
