<script setup lang="ts">
import { computed } from 'vue';
import { useTimeGreeting } from '@/hooks/useTimeGreeting';
import { useUserStore } from '@/stores';

const greeting = useTimeGreeting();
const userStore = useUserStore();
const username = computed(() => userStore.userInfo?.nickName || '');
</script>

<template>
  <header class="welcome-text">
    <p>
      {{ greeting }}好<span v-if="username">，{{ username }}</span>
    </p>
    <h1>让法律工作，更进一步。</h1>
    <p class="welcome-description">
      从一个问题开始，或选择一项办案任务。AI 建议请结合原始材料核对。
    </p>
  </header>
</template>

<style scoped>
.welcome-text {
  width: 100%;
  margin: 0 0 28px;
  text-align: left;
}
.welcome-text > p {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--yl-color-brand-strong);
}
.welcome-text h1 {
  margin: 0;
  font-size: clamp(24px, 2.5vw, 32px);
  line-height: 1.4;
  font-weight: 600;
  letter-spacing: -0.04em;
  color: var(--yl-color-text);
}
.welcome-text .welcome-description {
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--yl-color-text-muted);
}
</style>
