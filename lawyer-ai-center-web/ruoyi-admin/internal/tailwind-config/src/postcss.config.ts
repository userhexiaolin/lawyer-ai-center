import config from '.';
import { createRequire } from 'node:module';
const resolve = createRequire(import.meta.url).resolve;

export default {
  plugins: {
    ...(process.env.NODE_ENV === 'production' ? { [resolve('cssnano')]: {} } : {}),
    // Specifying the config is not necessary in most cases, but it is included
    [resolve('autoprefixer')]: {},
    // 修复 element-plus 和 ant-design-vue 的样式和tailwindcss冲突问题
    [resolve('postcss-antd-fixes')]: { prefixes: ['ant', 'el'] },
    [resolve('postcss-import')]: {},
    [resolve('postcss-preset-env')]: {},
    [resolve('tailwindcss')]: { config },
    [resolve('tailwindcss/nesting')]: {},
  },
};
