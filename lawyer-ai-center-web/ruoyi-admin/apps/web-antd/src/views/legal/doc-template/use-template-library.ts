import type { TemplateProcessing } from './template-processing';

import type { DocTemplate, DocumentTypeOption } from '#/api/legal/doc-template';

import { computed, onScopeDispose, reactive, ref } from 'vue';

import {
  documentTypes,
  removeTemplate,
  saveTemplateMetadata,
  templateDetail,
  templateList,
} from '#/api/legal/doc-template';

/** List requests use submitted filters; typing never silently changes pagination. */
export function useTemplateLibrary() {
  const filters = reactive({
    name: '',
    type: undefined as string | undefined,
    processing: '' as '' | TemplateProcessing,
  });
  const applied = ref({ ...filters });
  const page = ref(1);
  const pageSize = 20;
  const rows = ref<DocTemplate[]>([]);
  const types = ref<DocumentTypeOption[]>([]);
  const total = ref(0);
  const busy = ref(false);
  const error = ref('');
  const actionError = ref('');
  const pendingId = ref('');
  const selectedKeys = ref<(number | string)[]>([]);
  const selectedRows = computed(() =>
    rows.value.filter((row) => row.id && selectedKeys.value.includes(row.id)),
  );
  const filtered = computed(
    () =>
      !!(applied.value.name || applied.value.type || applied.value.processing),
  );
  const processing = computed(() => applied.value.processing);
  let generation = 0;
  let detailGeneration = 0;
  onScopeDispose(() => {
    generation++;
    detailGeneration++;
  });

  async function load() {
    const request = ++generation;
    busy.value = true;
    error.value = '';
    selectedKeys.value = [];
    const params = { ...applied.value, pageNum: page.value, pageSize };
    try {
      const [options, result] = await Promise.all([
        documentTypes(),
        templateList(params),
      ]);
      if (request !== generation) return;
      if (params.processing && result.processing !== params.processing)
        throw new Error('处理状态筛选服务尚未更新，请更新管理端服务后重试。');
      types.value = options;
      const lastPage = Math.max(1, Math.ceil(result.total / pageSize));
      if (page.value > lastPage) {
        page.value = lastPage;
        await load();
        return;
      }
      rows.value = result.rows;
      total.value = result.total;
    } catch (error_) {
      if (request !== generation) return;
      rows.value = [];
      total.value = 0;
      const detail = (error_ as Error).message;
      error.value = /timeout|exceeded/i.test(detail || '')
        ? '模板列表加载超时，请重试；若持续出现，请检查后端模板接口响应。'
        : detail || '模板加载失败，请重试';
    } finally {
      if (request === generation) busy.value = false;
    }
  }
  function query() {
    applied.value = { ...filters, name: filters.name.trim() };
    page.value = 1;
    return load();
  }
  function reset() {
    filters.name = '';
    filters.type = undefined;
    filters.processing = '';
    return query();
  }
  function filterProcessing(value: '' | TemplateProcessing) {
    filters.processing = value;
    applied.value = { ...applied.value, processing: value };
    page.value = 1;
    return load();
  }
  async function detail(id: string) {
    if (pendingId.value) return;
    const request = ++detailGeneration;
    pendingId.value = id;
    actionError.value = '';
    try {
      const result = await templateDetail(id);
      if (request === detailGeneration) return result;
    } catch (error_) {
      if (request === detailGeneration)
        actionError.value = (error_ as Error).message || '模板读取失败，请重试';
    } finally {
      if (request === detailGeneration) pendingId.value = '';
    }
  }
  async function mutate(
    id: string,
    action: 'delete' | 'toggle',
    expectedStatus?: DocTemplate['status'],
  ) {
    if (pendingId.value || busy.value) return;
    pendingId.value = id;
    actionError.value = '';
    try {
      if (action === 'delete') await removeTemplate(id);
      else {
        const current = await templateDetail(id);
        if (expectedStatus !== undefined && current.status !== expectedStatus)
          throw new Error('模板状态已变化，请刷新列表并核对后再操作');
        await saveTemplateMetadata({
          ...current,
          status: current.status === '0' ? '1' : '0',
        });
      }
      await load();
    } catch (error_) {
      actionError.value =
        (error_ as Error).message || '操作未完成，请重试；列表状态未更改';
    } finally {
      pendingId.value = '';
    }
  }
  return {
    processing,
    filterProcessing,
    filters,
    page,
    pageSize,
    rows,
    types,
    total,
    busy,
    error,
    actionError,
    pendingId,
    selectedKeys,
    selectedRows,
    filtered,
    load,
    query,
    reset,
    detail,
    mutate,
  };
}
