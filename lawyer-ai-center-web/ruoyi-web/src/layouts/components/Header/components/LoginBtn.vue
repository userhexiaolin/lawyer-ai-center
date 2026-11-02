<!-- LoginBtn 登录按钮 -->
<script setup lang="ts">
import LoginDialog from '@/components/LoginDialog/index.vue';
import { useUserStore } from '@/stores';

const userStore = useUserStore();
const isLoginDialogVisible = computed(() => userStore.isLoginDialogVisible);

// 点击登录按钮时调用Store方法打开弹框
function handleClickLogin() {
  userStore.openLoginDialog();
}
</script>

<template>
  <div class="login-btn-wrapper">
    <button type="button" class="login-btn" @click="handleClickLogin">
      登录
    </button>

    <!-- 登录弹框 -->
    <LoginDialog v-model:visible="isLoginDialogVisible" />
  </div>
</template>

<style scoped lang="scss">
.login-btn {
  border: 0;
  font: inherit;
  min-height: 36px;
  padding: 8px 16px;
  border-radius: var(--yl-radius-control);
  box-sizing: border-box;
  background: var(--yl-color-brand);
  color: var(--yl-color-text);
  cursor: pointer;
}
.login-btn:hover {
  background: var(--el-color-primary-light-3);
}
</style>
