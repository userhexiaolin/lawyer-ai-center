<!-- Header 头部 -->
<script setup lang="ts">
import { onKeyStroke } from '@vueuse/core';
import { useRoute } from 'vue-router';
import { SIDE_BAR_WIDTH } from '@/config/index';
import { useDesignStore, useUserStore } from '@/stores';
import { useSessionStore } from '@/stores/modules/session';
import Avatar from './components/Avatar.vue';
import Collapse from './components/Collapse.vue';
import CreateChat from './components/CreateChat.vue';
import LoginBtn from './components/LoginBtn.vue';
import TitleEditing from './components/TitleEditing.vue';

const userStore = useUserStore();
const designStore = useDesignStore();
const sessionStore = useSessionStore();
const route = useRoute();

const currentSession = computed(() => sessionStore.currentSession);

onMounted(() => {
  // 侧栏随窗口收窄，主内容与顶部导航共享同一个宽度。
  document.documentElement.style.setProperty(
    `--sidebar-default-width`,
    `clamp(220px, 18vw, ${SIDE_BAR_WIDTH}px)`,
  );
  if (designStore.isCollapse) {
    document.documentElement.style.setProperty(`--sidebar-left-container-default-width`, ``);
  }
  else {
    document.documentElement.style.setProperty(
      `--sidebar-left-container-default-width`,
      'var(--sidebar-default-width)',
    );
  }
});

// 定义 Ctrl+K 的处理函数
function handleCtrlK(event: KeyboardEvent) {
  event.preventDefault(); // 防止默认行为
  sessionStore.createSessionBtn();
}

// 设置全局的键盘按键监听
onKeyStroke(event => event.ctrlKey && event.key.toLowerCase() === 'k', handleCtrlK, {
  passive: false,
});
</script>

<template>
  <div class="header-container">
    <header class="header-box">
      <div class="header-start">
        <Collapse v-if="designStore.isCollapse" />
        <span class="workspace-label">律师工作空间</span>
        <span class="header-divider" aria-hidden="true">/</span>
        <div class="header-current">
          <TitleEditing v-if="route.name === 'chatWithId' && currentSession" />
          <span v-else>{{ route.meta.title || '工作台' }}</span>
        </div>
        <CreateChat v-if="designStore.isCollapse" class="create-chat-entry" />
      </div>
      <nav class="header-actions" aria-label="账号与帮助">
        <router-link to="/help" class="header-link" aria-label="帮助与数据来源">
          <el-icon aria-hidden="true">
            <QuestionFilled />
          </el-icon><span class="header-link-label">帮助</span>
        </router-link>
        <router-link
          v-if="userStore.token"
          :to="{ name: 'notifications' }"
          class="header-link"
          aria-label="个人通知"
        >
          <el-icon aria-hidden="true">
            <Bell />
          </el-icon>
        </router-link>
        <Avatar v-if="userStore.token" />
        <LoginBtn v-else />
      </nav>
    </header>
  </div>
</template>

<style scoped lang="scss">
.header-container {
  width: 100%;
  height: var(--header-container-default-heigth);
  background: var(--yl-color-surface);
  border-bottom: 1px solid var(--yl-color-border);
  box-sizing: border-box;
}
.header-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  box-sizing: border-box;
  width: calc(100% - var(--sidebar-left-container-default-width, 0px));
  height: 100%;
  margin-left: var(--sidebar-left-container-default-width, 0px);
  padding: 0 var(--yl-page-padding);
}
.header-start {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
}
.workspace-label {
  flex-shrink: 0;
  color: var(--yl-color-text-muted);
  font-size: 13px;
}
.header-divider {
  color: var(--el-border-color);
}
.header-current {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 500;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}
.header-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 32px;
  min-width: 32px;
  color: var(--yl-color-text-muted);
  text-decoration: none;
  border-radius: var(--yl-radius-panel);
}
.header-link:hover {
  background: var(--el-fill-color-light);
  color: var(--yl-color-brand-strong);
}
@media (max-width: 767px) {
  .header-box {
    width: 100%;
    margin-left: 0;
    padding: 0 16px;
  }
  .workspace-label,
  .header-divider,
  .header-link-label,
  .create-chat-entry {
    display: none;
  }
  .header-actions {
    gap: 8px;
  }
}
</style>
