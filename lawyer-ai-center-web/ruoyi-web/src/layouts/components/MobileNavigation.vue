<script setup lang="ts">
import { EditPen, Files, Menu } from '@element-plus/icons-vue';
import { useRoute } from 'vue-router';
import { useDesignStore } from '@/stores';
import { mobileCoreNavigation, navigationActive } from './Aside/navigation';

const route = useRoute();
const design = useDesignStore();
function toggleMenu() {
  design.setCollapse(!design.isCollapse);
  design.isSafeAreaHover = false;
  design.hasActivatedHover = false;
  document.documentElement.style.setProperty('--sidebar-left-container-default-width', '0px');
}
</script>

<template>
  <nav class="mobile-navigation" aria-label="移动核心功能">
    <router-link
      v-for="item in mobileCoreNavigation"
      :key="item.name"
      :to="{ name: item.name }"
      :aria-current="navigationActive(item.name, String(route.name)) ? 'page' : undefined"
      class="core-entry"
    >
      <el-icon aria-hidden="true">
        <Files v-if="item.name === 'templateManagement'" /><EditPen v-else />
      </el-icon>
      <span>{{ item.label }}</span>
    </router-link>
    <button
      type="button"
      class="core-entry"
      aria-controls="primary-sidebar"
      :aria-expanded="!design.isCollapse"
      @click="toggleMenu"
    >
      <el-icon aria-hidden="true">
        <Menu />
      </el-icon><span>全部功能</span>
    </button>
  </nav>
</template>

<style scoped>
.mobile-navigation {
  z-index: 20;
  box-sizing: border-box;
  display: grid;
  flex-shrink: 0;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--yl-space-1);
  min-height: calc(64px + env(safe-area-inset-bottom, 0px));
  padding: var(--yl-space-1) var(--yl-space-3)
    calc(var(--yl-space-1) + env(safe-area-inset-bottom, 0px));
  background: var(--yl-color-surface);
  border-top: 1px solid var(--yl-color-border);
}
.core-entry {
  display: flex;
  flex-direction: column;
  gap: var(--yl-space-1);
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 48px;
  padding: var(--yl-space-1);
  font: inherit;
  font-size: 13px;
  line-height: 1.4;
  color: var(--yl-color-text-muted);
  text-decoration: none;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--yl-radius-panel);
}
.core-entry .el-icon {
  font-size: 20px;
}
.core-entry[aria-current],
.core-entry:hover {
  color: var(--yl-color-brand-strong);
  background: var(--el-color-primary-light-9);
}
.core-entry[aria-current] {
  font-weight: 600;
}
</style>
