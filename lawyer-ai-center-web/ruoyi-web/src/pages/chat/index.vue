<script setup lang="ts">
import { useRoute } from 'vue-router';
import ChatDefaul from '@/pages/chat/layouts/chatDefaul/index.vue';
import ChatWithId from '@/pages/chat/layouts/chatWithId/index.vue';

const route = useRoute();
const sessionId = computed(() => route.params?.id);
</script>

<template>
  <div class="chat-container">
    <!-- 默认聊天页面 -->
    <ChatDefaul v-if="!sessionId" />
    <!-- 带id的聊天页面 -->
    <ChatWithId v-else />
  </div>
</template>

<style lang="scss" scoped>
.chat-container {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  height: 100%;
  padding: 0;
  overflow-anchor: none;
}
</style>
