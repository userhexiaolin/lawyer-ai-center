<script setup lang="ts">
import { watch } from 'vue';
import { useRoute } from 'vue-router';
import AppPageHeader from '@/components/AppPageHeader.vue';
import AppState from '@/components/AppState.vue';
import { useScreenStore } from '@/hooks/useScreen';
import TemplateCategories from './TemplateCategories.vue';
import TemplateLibrary from './TemplateLibrary.vue';
import { useTemplateAccess } from './useTemplateAccess';

const route = useRoute();
const { isMobile } = useScreenStore();
const { user, error, verify } = useTemplateAccess();
watch(() => user.token, verify, { immediate: true });
</script>

<template>
  <main class="yl-page template-workspace">
    <AppPageHeader
      title="模板管理"
      eyebrow="文书工作台"
      :description="
        isMobile
          ? '维护模板、核对字段，再用于文书起草。'
          : '维护常用文书模板与分类，核对填写字段后用于文书起草。'
      "
    >
      <template v-if="!isMobile" #actions>
        <router-link to="/drafting" class="draft-link">
          返回文书起草
        </router-link>
      </template>
    </AppPageHeader>
    <nav class="template-tabs" aria-label="模板管理功能">
      <router-link
        :to="{ name: 'templateManagement' }"
        :aria-current="route.name === 'templateManagement' ? 'page' : undefined"
      >
        模板管理
      </router-link>
      <router-link
        :to="{ name: 'templateCategories' }"
        :aria-current="route.name === 'templateCategories' ? 'page' : undefined"
      >
        模板分类
      </router-link>
    </nav>
    <AppState v-if="error" kind="error" title="暂时无法核对账号权限" :description="error">
      <el-button @click="verify">
        重新加载权限
      </el-button>
    </AppState>
    <AppState v-else-if="!user.identityReady" kind="loading" title="正在核对账号权限" />
    <template v-else>
      <TemplateLibrary
        v-show="route.name === 'templateManagement'"
        :key="`templates-${user.token}`"
        :active="route.name === 'templateManagement'"
        :permissions="user.userInfo?.menuPermission || []"
      />
      <TemplateCategories
        v-show="route.name === 'templateCategories'"
        :key="`categories-${user.token}`"
        :active="route.name === 'templateCategories'"
        :permissions="user.userInfo?.menuPermission || []"
      />
    </template>
  </main>
</template>

<style scoped>
.template-workspace {
  min-width: 0;
}
.template-tabs {
  display: flex;
  gap: var(--yl-space-6);
  margin-bottom: var(--yl-space-6);
  border-bottom: 1px solid var(--yl-color-border);
}
.template-tabs a {
  padding: var(--yl-space-3) var(--yl-space-1);
  color: var(--yl-color-text-muted);
  text-decoration: none;
  border-bottom: 2px solid transparent;
}
.template-tabs a[aria-current] {
  font-weight: 600;
  color: var(--yl-color-brand-strong);
  border-bottom-color: var(--el-color-primary);
}
.draft-link {
  padding: var(--yl-space-2);
}
</style>
