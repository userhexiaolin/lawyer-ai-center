import type { ManagedTemplate, TemplateCategoryOption } from '@/api/document/templateManagement';
import { ElMessage, ElMessageBox } from 'element-plus';
import { onScopeDispose, reactive, ref } from 'vue';
import {
  categoryOptions,
  managedTemplates,
  removeManagedTemplate,
  saveTemplateMetadata,
  templateDetail,
} from '@/api/document/templateManagement';
import { useUserStore } from '@/stores';
import { failureMessage } from './workflow';

export function useTemplateLibrary(allowed: (action: string) => boolean) {
  const user = useUserStore();
  const filters = reactive({ name: '', type: '' });
  const applied = ref({ ...filters });
  const rows = ref<ManagedTemplate[]>([]);
  const types = ref<TemplateCategoryOption[]>([]);
  const page = ref(1);
  const total = ref(0);
  const loading = ref(false);
  const error = ref('');
  const typeError = ref('');
  const actionError = ref('');
  const pending = ref('');
  const open = ref(false);
  const mode = ref<'metadata' | 'source'>('metadata');
  const initial = ref<Partial<ManagedTemplate>>({});
  let generation = 0;
  let optionGeneration = 0;
  let alive = true;
  let confirming = false;
  let optionTask: Promise<void> | undefined;
  onScopeDispose(() => {
    generation++;
    optionGeneration++;
    alive = false;
    if (confirming)
      ElMessageBox.close();
  });
  function loadTypes() {
    if (optionTask)
      return optionTask;
    optionTask = readTypes().finally(() => {
      optionTask = undefined;
    });
    return optionTask;
  }
  async function readTypes() {
    const request = ++optionGeneration;
    const token = user.token;
    typeError.value = '';
    try {
      const result = await categoryOptions();
      if (alive && request === optionGeneration && token === user.token)
        types.value = result;
    }
    catch (reason) {
      if (alive && request === optionGeneration && token === user.token) {
        types.value = [];
        typeError.value = failureMessage(reason);
      }
    }
  }
  async function load() {
    if (!allowed('list'))
      return;
    const request = ++generation;
    const token = user.token;
    loading.value = true;
    error.value = '';
    try {
      const result = await managedTemplates({
        ...applied.value,
        pageNum: page.value,
        pageSize: 20,
      });
      if (!alive || request !== generation || token !== user.token)
        return;
      const max = Math.max(1, Math.ceil(result.total / 20));
      if (page.value > max) {
        page.value = max;
        return await load();
      }
      rows.value = result.rows;
      total.value = result.total;
    }
    catch (reason) {
      if (alive && request === generation && token === user.token) {
        rows.value = [];
        total.value = 0;
        error.value = failureMessage(reason);
      }
    }
    finally {
      if (request === generation)
        loading.value = false;
    }
  }
  function query(reset = false) {
    if (reset)
      Object.assign(filters, { name: '', type: '' });
    applied.value = { name: filters.name.trim(), type: filters.type };
    page.value = 1;
    void load();
  }
  function changePage(value: number) {
    if (value === page.value || loading.value || pending.value)
      return;
    page.value = value;
    void load();
  }
  async function edit(row?: ManagedTemplate, source = false) {
    if (
      pending.value
      || loading.value
      || (row ? !allowed('query') : !allowed('add'))
      || (source && row && !allowed('edit'))
    ) {
      return;
    }
    pending.value = row?.id || 'new';
    actionError.value = '';
    const token = user.token;
    try {
      // Reload after category changes in the other tab, including disabled history categories.
      const [result] = await Promise.all([row?.id ? templateDetail(row.id) : Promise.resolve({}), loadTypes()]);
      if (!alive || user.token !== token)
        return;
      initial.value = result;
      mode.value = source ? 'source' : 'metadata';
      open.value = true;
    }
    catch (reason) {
      if (alive && user.token === token)
        actionError.value = failureMessage(reason);
    }
    finally {
      pending.value = '';
    }
  }
  async function mutate(row: ManagedTemplate, remove = false) {
    if (!row.id || pending.value || loading.value || !allowed(remove ? 'remove' : 'edit'))
      return;
    pending.value = row.id;
    const token = user.token;
    const label = remove ? '删除' : row.status === '0' ? '停用' : '启用';
    actionError.value = '';
    try {
      confirming = true;
      await ElMessageBox.confirm(
        remove
          ? '删除后，该模板不再供新起草选择，操作不可撤销。已绑定的历史文书版本保留。'
          : row.status === '0'
            ? '停用后，该模板不再供新起草选择，已有文书及其原文件版本保留。'
            : '启用后，仅分类启用且填写字段已确认的模板可用于新的文书起草。',
        `${label}“${row.name}”？`,
        { confirmButtonText: `确认${label}`, cancelButtonText: '取消', type: 'warning' },
      );
      if (!alive || user.token !== token || !allowed(remove ? 'remove' : 'edit'))
        return;
      await (remove
        ? removeManagedTemplate(row.id)
        : saveTemplateMetadata({ ...row, status: row.status === '0' ? '1' : '0' }));
      if (!alive || user.token !== token)
        return;
      ElMessage.success(`模板已${label}`);
      await load();
    }
    catch (reason) {
      if (reason !== 'cancel' && reason !== 'close' && alive && user.token === token)
        actionError.value = failureMessage(reason);
    }
    finally {
      confirming = false;
      pending.value = '';
    }
  }
  return {
    filters,
    applied,
    rows,
    types,
    page,
    total,
    loading,
    error,
    typeError,
    actionError,
    pending,
    open,
    mode,
    initial,
    load,
    loadTypes,
    query,
    changePage,
    edit,
    mutate,
  };
}
