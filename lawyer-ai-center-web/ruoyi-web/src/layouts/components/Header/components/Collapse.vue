<!-- 侧边栏折叠按钮 -->
<script setup lang="ts">
import SvgIcon from '@/components/SvgIcon/index.vue';
import { useCollapseToggle } from '@/hooks/useCollapseToggle';
import { useScreenStore } from '@/hooks/useScreen';
import { useDesignStore } from '@/stores';

const { changeCollapse } = useCollapseToggle();
const designStore = useDesignStore();
const { isMobile } = useScreenStore();

function handleChangeCollapse() {
  if (isMobile.value)
    designStore.setCollapse(!designStore.isCollapse);
  else changeCollapse();
  // 每次切换折叠状态，重置安全区状态
  designStore.isSafeAreaHover = false;
  // 重置首次激活悬停状态
  designStore.hasActivatedHover = false;
  if (!designStore.isCollapse) {
    document.documentElement.style.setProperty(
      `--sidebar-left-container-default-width`,
      'var(--sidebar-default-width)',
    );
  }
  else {
    document.documentElement.style.setProperty(`--sidebar-left-container-default-width`, ``);
  }
}
</script>

<template>
  <button
    type="button"
    class="collapse-container btn-icon-btn"
    :aria-label="designStore.isCollapse ? '展开导航' : '收起导航'"
    :aria-expanded="!designStore.isCollapse"
    aria-controls="primary-sidebar"
    @click="handleChangeCollapse"
  >
    <SvgIcon v-if="!designStore.isCollapse" name="ms-left-panel-close-outline" size="24" />
    <SvgIcon v-if="designStore.isCollapse" name="ms-left-panel-open-outline" size="24" />
  </button>
</template>

<style lang="scss" scoped>
.collapse-container {
  border: 0;
  background: transparent;
  color: var(--yl-color-text-muted);
}
</style>
