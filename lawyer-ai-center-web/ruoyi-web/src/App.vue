<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import { watch } from 'vue';
import AppState from '@/components/AppState.vue';
import { useUserStore } from '@/stores';

const user = useUserStore();
async function loadIdentity() {
  if (!user.token)
    return;
  try {
    await user.ensureIdentity();
  }
  catch {
    // The store exposes a retryable error; existing 401 handling restores login.
  }
}
watch(() => user.token, loadIdentity, { immediate: true });
</script>

<template>
  <el-config-provider :locale="zhCn">
    <AppState v-if="user.token && !user.identityReady" :kind="user.identityLoading ? 'loading' : 'error'" :title="user.identityLoading ? '正在核对账号身份' : '暂时无法载入账号工作区'" :description="user.identityLoading ? '核对成功后恢复当前页面。' : user.identityError || '请重新加载账号身份。'">
      <el-button v-if="!user.identityLoading" type="primary" @click="loadIdentity">
        重新加载
      </el-button>
      <el-button v-if="!user.identityLoading" @click="user.logout()">
        退出并重新登录
      </el-button>
    </AppState>
    <router-view v-else :key="user.token" />
  </el-config-provider>
</template>

<style scoped lang="scss"></style>
