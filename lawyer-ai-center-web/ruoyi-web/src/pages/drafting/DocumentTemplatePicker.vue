<script setup lang="ts">
import type { DocumentTemplateOption } from '@/api/document/templates';
import { computed, onScopeDispose, ref, watch } from 'vue';
import { enabledTemplates, STANDARD_TEMPLATE } from '@/api/document/templates';
import { useDraftNavigation } from '@/hooks/useDraftWorkspace';
import { useUserStore } from '@/stores';

const props = defineProps<{ type: string; disabled?: boolean; requestedId?: string }>();
const emit = defineEmits<{ blocked: [value: boolean] }>();
const selected = defineModel<string>({ default: '' });
const user = useUserStore();
const navigation = useDraftNavigation();
const rows = ref<DocumentTemplateOption[]>([]);
const loading = ref(false);
const changing = ref(false);
const unavailable = ref<DocumentTemplateOption>();
const error = ref('');
let version = 0;
let disposed = false;
let scopeType = '';
let scopeToken: string | undefined;
let scopeRequested: string | undefined;
let requested = '';

const selectedTemplate = computed(() => rows.value.find(row => row.id === selected.value) || unavailable.value);
const legacySelected = computed(() => selectedTemplate.value?.preservesLayout === false);
const selectedWarning = computed(() => selectedTemplate.value?.warning || '');

async function refresh() {
  const v = ++version;
  const token = user.token;
  if (scopeType !== props.type || scopeToken !== token || scopeRequested !== props.requestedId)
    requested = props.requestedId || '';
  if (scopeType !== props.type || scopeToken !== token) {
    rows.value = [];
    selected.value = '';
    unavailable.value = undefined;
    emit('blocked', false);
  }
  scopeType = props.type;
  scopeToken = token;
  scopeRequested = props.requestedId;
  if (requested)
    emit('blocked', true);
  error.value = '';
  loading.value = false;
  if (!token)
    return;
  loading.value = true;
  try {
    const values = await enabledTemplates(props.type);
    if (disposed || version !== v || token !== user.token)
      return;
    const previous = selectedTemplate.value;
    rows.value = values;
    if (requested) {
      const match = values.find(row => row.id === requested);
      selected.value = match?.id || '';
      emit('blocked', !match);
      if (!match)
        error.value = '所选模板已停用、删除或移至其他分类。请刷新模板重试，或重新选择模板后再起草。';
      else
        requested = '';
      return;
    }
    if (previous && !values.some(row => row.id === previous.id) && navigation.hasUnsaved()) {
      unavailable.value = previous;
      error.value = '所选模板已停用或不可用，当前填写内容已保留。请先保留输入，再选择可用模板。';
    }
    else {
      unavailable.value = undefined;
      selected.value = selected.value === STANDARD_TEMPLATE || values.some(row => row.id === selected.value) ? selected.value : values[0]?.id || STANDARD_TEMPLATE;
    }
  }
  catch (e) {
    if (!disposed && version === v && token === user.token)
      error.value = (e as Error).message;
  }
  finally {
    if (!disposed && version === v)
      loading.value = false;
  }
}
async function choose(value: string) {
  if (value === selected.value || props.disabled || loading.value || changing.value)
    return;
  const epoch = version;
  const token = user.token;
  changing.value = true;
  try {
    if (!await navigation.confirmDiscard() || disposed || epoch !== version || user.token !== token)
      return;
    selected.value = value;
    requested = '';
    emit('blocked', false);
    error.value = '';
    unavailable.value = undefined;
  }
  finally {
    changing.value = false;
  }
}

watch(() => [props.type, user.token, props.requestedId], () => {
  if (scopeType === props.type && scopeToken === user.token && !props.requestedId) {
    scopeRequested = props.requestedId;
    return;
  }
  void refresh();
}, { immediate: true });
onScopeDispose(() => {
  disposed = true;
  version++;
});
</script>

<template>
  <div class="template-picker">
    <label>文书模板</label>
    <el-select
      :model-value="selected"
      :disabled="disabled || loading || changing"
      :loading="loading"
      placeholder="请选择模板"
      aria-label="文书模板"
      @update:model-value="choose"
    >
      <el-option :value="STANDARD_TEMPLATE" label="标准起草（不使用上传模板）" />
      <el-option v-if="unavailable" :value="unavailable.id" :label="`${unavailable.name}（已不可用）`" disabled />
      <el-option
        v-for="item in rows"
        :key="item.id"
        :label="item.name + (item.preservesLayout === false ? '（仅文字模板）' : '')"
        :value="item.id"
      />
    </el-select>
    <el-button :disabled="disabled || loading || changing" text @click="refresh">
      刷新模板
    </el-button>
    <p v-if="error" role="alert" class="template-error">
      {{ error }}
    </p>
    <el-alert
      v-if="legacySelected"
      title="此模板未保存原始文件，仅能参考文字结构。原模板的字体、字号和排版需在模板管理中补传一次原文件后才能保留；当前文书仍可按标准版式下载。"
      type="warning"
      :closable="false"
    />
    <p v-if="selectedWarning" role="status" class="template-advice">
      当前模板“{{ selectedTemplate?.name }}”：{{ selectedWarning }}
    </p>
    <p v-if="!loading && selected === STANDARD_TEMPLATE">
      使用标准文书格式，依据本次填写的要素起草。
    </p>
    <p v-else-if="rows.length">
      默认使用人工设置为当前类型且排序最小的启用模板，可改选其他模板或标准起草。
    </p>
  </div>
</template>

<style scoped>
.template-picker {
  margin: 18px 0;
}
.template-picker label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
}
.el-select {
  width: min(100%, 420px);
}
.template-picker p {
  margin: 6px 0;
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}
.template-picker .template-error {
  color: var(--el-color-danger);
}
.template-picker .template-advice {
  overflow-wrap: anywhere;
}
</style>
