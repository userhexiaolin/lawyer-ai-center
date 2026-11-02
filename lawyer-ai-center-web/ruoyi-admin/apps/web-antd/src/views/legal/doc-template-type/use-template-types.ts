import type { DocTemplateType } from '#/api/legal/doc-template-type';

import { computed, onScopeDispose, reactive, ref } from 'vue';

import {
  removeTemplateType,
  saveTemplateType,
  templateTypeList,
} from '#/api/legal/doc-template-type';

export function useTemplateTypes() {
  const filters = reactive({
    name: '',
    status: undefined as string | undefined,
    source: undefined as string | undefined,
  });
  const applied = ref({ ...filters });
  const rows = ref<DocTemplateType[]>([]);
  const loading = ref(false);
  const error = ref('');
  const actionError = ref('');
  const pendingId = ref('');
  const page = ref(1);
  const filtered = computed(
    () =>
      !!(applied.value.name || applied.value.status || applied.value.source),
  );
  const visibleRows = computed(() =>
    rows.value.filter(
      (row) =>
        (!applied.value.status || row.status === applied.value.status) &&
        (!applied.value.source ||
          (row.builtin ? 'builtin' : 'custom') === applied.value.source),
    ),
  );
  let generation = 0;
  onScopeDispose(() => {
    generation++;
  });
  const failure = (reason: unknown, fallback: string) => {
    const value = reason as Error;
    return /timeout|exceeded/i.test(value?.message || '')
      ? '请求超时，请检查服务连接后重试。'
      : value?.message || fallback;
  };
  async function load() {
    const request = ++generation;
    loading.value = true;
    error.value = '';
    try {
      const result = await templateTypeList(applied.value.name || undefined);
      if (request !== generation) return;
      rows.value = result;
      page.value = Math.min(
        page.value,
        Math.max(1, Math.ceil(visibleRows.value.length / 20)),
      );
    } catch (error_) {
      if (request !== generation) return;
      rows.value = [];
      error.value = failure(error_, '模板类型加载失败，请重试。');
    } finally {
      if (request === generation) loading.value = false;
    }
  }
  function query() {
    applied.value = { ...filters, name: filters.name.trim() };
    page.value = 1;
    return load();
  }
  function reset() {
    Object.assign(filters, { name: '', status: undefined, source: undefined });
    return query();
  }
  async function mutate(row: DocTemplateType, action: 'delete' | 'toggle') {
    if (
      !row.id ||
      loading.value ||
      pendingId.value ||
      (action === 'delete' && row.builtin)
    )
      return;
    pendingId.value = row.id;
    actionError.value = '';
    try {
      await (action === 'delete'
        ? removeTemplateType(row.id)
        : saveTemplateType({
            ...row,
            status: row.status === '0' ? '1' : '0',
          }));
      await load();
    } catch (error_) {
      actionError.value = failure(
        error_,
        '操作未完成，请重试；列表状态未更改。',
      );
    } finally {
      pendingId.value = '';
    }
  }
  return {
    filters,
    rows,
    visibleRows,
    loading,
    error,
    actionError,
    pendingId,
    page,
    filtered,
    load,
    query,
    reset,
    mutate,
  };
}
