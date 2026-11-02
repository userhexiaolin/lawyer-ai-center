import { createApp } from 'vue';

import { ConfigProvider, theme } from 'ant-design-vue';
import zhCN from 'ant-design-vue/es/locale/zh_CN';

import TemplatePage from '../../apps/web-antd/src/views/legal/doc-template/index.vue';
import { useAccess } from './fixture-access';
import FixtureShell from './shell.vue';

import '../../packages/@core/base/design/src/design-tokens/default.css';
import '../../packages/@core/base/design/src/design-tokens/dark.css';
import 'ant-design-vue/dist/reset.css';
import '../../packages/styles/src/antd/index.css';
import './shell.css';

const dark = new URLSearchParams(location.search).get('theme') === 'dark';
document.documentElement.classList.toggle('dark', dark);
const app = createApp(FixtureShell, {
  page: TemplatePage,
  provider: ConfigProvider,
  locale: zhCN,
  theme: {
    algorithm: dark ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: { borderRadius: 6 },
  },
});
app.directive('access', {
  mounted(element, binding) {
    if (!useAccess().hasAccessByCodes(binding.value)) element.remove();
  },
});
app.mount('#app');
