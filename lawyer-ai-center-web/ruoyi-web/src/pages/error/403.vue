<script setup lang="ts">
import { useRouter } from 'vue-router';
import AppPageHeader from '@/components/AppPageHeader.vue';
import { HOME_URL } from '@/config/index.ts';

// 路由跳转
const router = useRouter();

function handleHomePage() {
  router.push({ path: HOME_URL });
}
</script>

<template>
  <main class="error-page">
    <section class="error-card yl-panel">
      <p class="error-code" aria-hidden="true">
        403
      </p>
      <AppPageHeader
        title="暂时无法访问"
        description="当前账号无法打开此页面。可以返回工作台，或联系管理员确认所需权限。"
        eyebrow="与律行"
      />
      <div class="error-actions">
        <el-button type="primary" @click="handleHomePage">
          返回工作台
        </el-button><router-link to="/help">
          查看帮助
        </router-link>
      </div>
    </section>
  </main>
</template>

<style scoped>
.error-page {
  display: grid;
  place-items: center;
  box-sizing: border-box;
  min-height: 100dvh;
  padding: 24px;
  background: var(--yl-color-page);
}
.error-card {
  width: min(100%, 560px);
  box-sizing: border-box;
  padding: 40px;
}
.error-code {
  margin: 0 0 24px;
  font-size: 48px;
  font-weight: 600;
  letter-spacing: -0.06em;
  line-height: 1;
  color: var(--yl-color-brand-strong);
}
.error-actions {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}
@media (max-width: 600px) {
  .error-card {
    padding: 24px;
  }
}
</style>
