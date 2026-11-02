<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { documentTypes } from '@/api/document/templates';
import AppPageHeader from '@/components/AppPageHeader.vue';
import { provideDraftWorkspace } from '@/hooks/useDraftWorkspace';
import { useScreenStore } from '@/hooks/useScreen';
import { useUserStore } from '@/stores';
import BatchDraft from './BatchDraft.vue';
import GeneralDraft from './GeneralDraft.vue';
import LitigationDraft from './LitigationDraft.vue';

const typeError = ref('');
const typeLoading = ref(false);
const { isMobile } = useScreenStore();
const user = useUserStore();
provideDraftWorkspace();

const mode = ref('single');
const batchVisited = ref(false);
watch(mode, (value) => {
  if (value === 'batch')
    batchVisited.value = true;
});
const singleWorking = ref(false);
const batchWorking = ref(false);
const working = computed(() => singleWorking.value || batchWorking.value);
const route = useRoute();
const router = useRouter();
const types = ref<string[]>([]);
const type = computed(() =>
  types.value.includes(String(route.query.type)) ? String(route.query.type) : types.value[0] || '',
);
watch(type, () => {
  batchVisited.value = mode.value === 'batch';
});
let typeEpoch = 0;
onScopeDispose(() => {
  typeEpoch++;
});
async function loadTypes() {
  if (typeLoading.value)
    return;
  const epoch = ++typeEpoch;
  const token = user.token;
  typeLoading.value = true;
  typeError.value = '';
  try {
    const result = await documentTypes();
    if (epoch !== typeEpoch || token !== user.token)
      return;
    types.value = result.map(t => t.value);
    if (type.value === '法律意见书')
      void router.replace('/legal-opinion');
  }
  catch (e) {
    if (epoch === typeEpoch && token === user.token)
      typeError.value = (e as Error).message || '文书类型加载失败，请重试';
  }
  finally {
    if (epoch === typeEpoch)
      typeLoading.value = false;
  }
}
watch(() => user.token, () => {
  typeEpoch++;
  typeLoading.value = false;
  types.value = [];
  void loadTypes();
}, { immediate: true, flush: 'sync' });
if (route.query.type === '法律意见书')
  void router.replace('/legal-opinion');
function choose(value: string) {
  if (working.value)
    return;
  if (value === '法律意见书') {
    void router.push('/legal-opinion');
    return;
  }
  void router.replace({ path: '/drafting', query: { type: value } });
}
</script>

<template>
  <main class="drafting yl-page">
    <AppPageHeader
      title="文书起草"
      :description="isMobile ? '选择类型和模板，填写后核对文书草稿。' : '选择文书类型，核对起草要素，生成后核对、编辑、保存和下载草稿。'"
      eyebrow="法律工作台"
    >
      <template v-if="!isMobile" #actions>
        <el-button :disabled="working" @click="router.push('/templates')">
          管理模板
        </el-button>
      </template>
    </AppPageHeader>
    <p v-if="typeLoading" role="status">
      正在加载文书类型…
    </p>
    <el-alert v-if="typeError" :title="typeError" type="error" :closable="false" role="alert">
      <el-button :loading="typeLoading" @click="loadTypes">
        重试文书类型
      </el-button>
    </el-alert>
    <nav class="document-types" aria-label="文书类型">
      <el-button
        v-for="value in types"
        :key="value"
        :disabled="working"
        :type="type === value ? 'primary' : 'default'"
        :aria-pressed="type === value"
        @click="choose(value)"
      >
        {{ value === '自由描述文书' ? '自由描述生成' : value }}
      </el-button>
    </nav>
    <el-radio-group v-if="type" v-model="mode" :disabled="working" aria-label="起草模式">
      <el-radio-button value="single">
        单份起草
      </el-radio-button><el-radio-button value="batch">
        批量起草（总计≤200MB）
      </el-radio-button>
    </el-radio-group><BatchDraft
      v-if="type && batchVisited"
      v-show="mode === 'batch'"
      :key="`batch-${type}`"
      :type="type"
      @busy="batchWorking = $event"
    />
    <div v-show="mode === 'single'">
      <LitigationDraft
        v-if="type === '民事起诉状' || type === '民事答辩状'"
        :key="type"
        @busy="singleWorking = $event"
      /><GeneralDraft v-else-if="type" :key="type" :type="type" @busy="singleWorking = $event" /><el-empty
        v-else-if="!typeError && !typeLoading"
        description="暂无可用文书类型，请联系管理员启用类型"
      />
    </div>
  </main>
</template>

<style scoped>
.drafting {
  min-width: 0;
}
.drafting > p {
  color: var(--el-text-color-secondary);
  line-height: 1.8;
}
.document-types {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin: 24px 0;
}
.document-types .el-button {
  margin: 0;
}
@media (width <= 766px) {
  .document-types {
    flex-wrap: nowrap;
    gap: var(--yl-space-2);
    padding: var(--yl-space-1) 0;
    margin: var(--yl-space-4) 0;
    overflow-x: auto;
  }
  .document-types .el-button {
    flex-shrink: 0;
    min-height: 40px;
  }
}
</style>
