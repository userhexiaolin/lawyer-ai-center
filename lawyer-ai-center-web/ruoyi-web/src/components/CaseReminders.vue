<script setup lang="ts">
import { onMounted, onScopeDispose, ref, watch } from 'vue';
import { lawyerRequest } from '@/api/lawyer';
import { useUserStore } from '@/stores';

const rows = ref<
  { caseId: string; title: string; kind: string; date: string; days: number; level: string }[]
>([]);
const error = ref('');
const loading = ref(false);
let disposed = false;
const user = useUserStore();
watch(
  () => user.token,
  () => {
    rows.value = [];
    error.value = '';
    if (user.token)
      void load();
  },
);
async function load() {
  if (loading.value || !user.token)
    return;
  loading.value = true;
  error.value = '';
  try {
    const r = await lawyerRequest<{ data: typeof rows.value }>('/lawyer/case/upcoming');
    if (!disposed)
      rows.value = r.data;
  }
  catch (e) {
    if (!disposed)
      error.value = (e as Error).message;
  }
  finally {
    loading.value = false;
  }
}
onMounted(() => {
  if (user.token)
    void load();
});
onScopeDispose(() => {
  disposed = true;
});
</script>

<template>
  <section v-if="user.token" class="reminders">
    <b>今日 / 本周待办</b><el-button text :loading="loading" @click="load">
      刷新
    </el-button>
    <p v-if="!rows.length && !loading && !error">
      未来7天暂无开庭或时效待办
    </p>
    <p v-if="error">
      {{ error }} <el-button text @click="load">
        重试
      </el-button>
    </p>
    <p v-for="r in rows" :key="r.caseId + r.kind">
      <router-link :to="{ path: '/my-cases', query: { id: r.caseId } }" :class="r.level">
        {{ r.date }} · {{ r.title }} · {{ r.kind }}（{{ r.days === 0 ? '今天' : `${r.days}天后` }}）
      </router-link>
    </p>
  </section>
</template>

<style scoped>
.reminders {
  background: var(--yl-color-surface);
  width: 100%;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--yl-radius-panel);
  margin: 12px 0;
  font-size: 14px;
  box-sizing: border-box;
}
.reminders p {
  margin: 8px 0;
}
.danger {
  color: var(--yl-color-danger);
}
.warning {
  color: var(--yl-color-warning);
}
</style>
