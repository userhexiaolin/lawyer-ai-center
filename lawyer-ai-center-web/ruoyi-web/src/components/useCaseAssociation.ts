import type { LawyerCase } from '@/api/lawyer';
import { computed, onScopeDispose, ref, watch } from 'vue';
import { caseDetail, caseList, emptyCase, lawyerRequest, saveCase } from '@/api/lawyer';
import { useUserStore } from '@/stores';

interface AssociationProps { modelValue: string; sessionId: string; disabled: boolean }

export function useCaseAssociation(props: AssociationProps, update: (value: string) => void) {
  const user = useUserStore();
  const selected = ref(props.modelValue);
  const linked = ref('');
  const rows = ref<LawyerCase[]>([]);
  const page = ref(0);
  const more = ref(false);
  const loading = ref(false);
  const restoring = ref(false);
  const saving = ref(false);
  const listError = ref('');
  const associationError = ref('');
  const associationFailure = ref<'restore' | 'save' | 'create' | ''>('');
  const newTitle = ref('');
  const creating = ref(false);
  const busy = computed(() => loading.value || restoring.value || saving.value);
  const error = computed(() => associationError.value || listError.value);
  const retryLabel = computed(() => associationFailure.value === 'save' ? '重试关联' : associationFailure.value === 'restore' ? '重新加载关联' : associationFailure.value === 'create' ? '重试创建案件' : '重新加载案件');
  let generation = 0;
  let listGeneration = 0;
  let disposed = false;
  function context() {
    const version = generation;
    const token = user.token;
    const session = props.sessionId;
    return () => !disposed && version === generation && token === user.token && session === props.sessionId;
  }
  function mergeRows(incoming: LawyerCase[], append = false) {
    const retained = append ? rows.value : rows.value.filter(row => row.id === selected.value);
    rows.value = [...new Map([...retained, ...incoming].map(row => [row.id, row])).values()];
  }
  async function load(append = false) {
    if (loading.value || !user.token)
      return;
    const active = context();
    const request = ++listGeneration;
    const nextPage = append ? page.value + 1 : 1;
    loading.value = true;
    listError.value = '';
    try {
      const result = await caseList({ pageNum: nextPage, pageSize: 20 });
      if (!active() || request !== listGeneration)
        return;
      mergeRows(result.rows, append);
      page.value = nextPage;
      more.value = nextPage * 20 < result.total;
    }
    catch (reason) {
      if (active() && request === listGeneration)
        listError.value = (reason as Error).message;
    }
    finally {
      if (active() && request === listGeneration)
        loading.value = false;
    }
  }
  async function restore() {
    if (!user.token || restoring.value || saving.value)
      return;
    const active = context();
    const session = props.sessionId;
    associationError.value = '';
    associationFailure.value = '';
    if (!session) {
      selected.value = props.modelValue;
      linked.value = selected.value;
      return;
    }
    restoring.value = true;
    try {
      const result = await lawyerRequest<{ data: { caseId?: string } }>(`/system/session/${encodeURIComponent(session)}`);
      if (!active())
        return;
      linked.value = selected.value = result.data.caseId ? String(result.data.caseId) : '';
      update(selected.value);
      if (selected.value && !rows.value.some(row => row.id === selected.value)) {
        const detail = await caseDetail(selected.value);
        if (active())
          mergeRows([detail.data.caseInfo], true);
      }
    }
    catch (reason) {
      if (active()) {
        associationFailure.value = 'restore';
        associationError.value = (reason as Error).message;
      }
    }
    finally {
      if (active())
        restoring.value = false;
    }
  }
  async function persistSelection(active: () => boolean, session: string, value: string) {
    if (session) {
      if (value)
        await lawyerRequest(`/lawyer/case/${encodeURIComponent(value)}/sessions`, 'POST', { sessionId: session });
      else if (linked.value)
        await lawyerRequest(`/lawyer/case/${encodeURIComponent(linked.value)}/sessions/${encodeURIComponent(session)}`, 'DELETE');
    }
    if (active()) {
      linked.value = value;
      associationFailure.value = '';
      update(value);
    }
  }
  function failedSave(reason: unknown) {
    associationFailure.value = 'save';
    associationError.value = `关联尚未保存：${(reason as Error).message}。当前选择已保留，可重试。`;
  }
  async function apply() {
    if (busy.value || props.disabled || !user.token || associationFailure.value === 'restore')
      return;
    const active = context();
    const session = props.sessionId;
    const value = selected.value;
    saving.value = true;
    associationError.value = '';
    try {
      await persistSelection(active, session, value);
    }
    catch (reason) {
      if (active())
        failedSave(reason);
    }
    finally {
      if (active())
        saving.value = false;
    }
  }
  async function create() {
    if (busy.value || props.disabled || !user.token || !newTitle.value.trim() || associationFailure.value === 'restore')
      return;
    const active = context();
    const session = props.sessionId;
    const input = { ...emptyCase(), title: newTitle.value.trim() };
    let created = false;
    saving.value = true;
    associationError.value = '';
    try {
      const result = await saveCase(input);
      if (!active())
        return;
      created = true;
      selected.value = String(result.data);
      mergeRows([{ ...input, id: selected.value }], true);
      creating.value = false;
      newTitle.value = '';
      await persistSelection(active, session, selected.value);
    }
    catch (reason) {
      if (active()) {
        if (created) {
          failedSave(reason);
        }
        else {
          associationFailure.value = 'create';
          associationError.value = `案件创建未完成：${(reason as Error).message}。标题已保留。`;
        }
      }
    }
    finally {
      if (active())
        saving.value = false;
    }
  }
  function retry() {
    if (associationFailure.value === 'save')
      void apply();
    else if (associationFailure.value === 'restore')
      void restore();
    else if (associationFailure.value === 'create')
      void create();
    else
      void load();
  }
  watch(() => props.modelValue, (value) => {
    if (!saving.value && !restoring.value && associationFailure.value !== 'save')
      selected.value = value;
  });
  watch([() => props.sessionId, () => user.token], (value, previous) => {
    const accountChanged = previous.length > 0 && previous[1] !== value[1];
    generation++;
    listGeneration++;
    loading.value = restoring.value = saving.value = false;
    listError.value = associationError.value = '';
    associationFailure.value = '';
    if (previous[1] !== value[1]) {
      rows.value = [];
      selected.value = linked.value = '';
      newTitle.value = '';
      creating.value = false;
      if (previous.length)
        update('');
    }
    page.value = 0;
    more.value = false;
    if (user.token) {
      void load();
      if (props.sessionId || !accountChanged)
        void restore();
    }
  }, { immediate: true, flush: 'sync' });
  onScopeDispose(() => {
    disposed = true;
    generation++;
    listGeneration++;
  });
  return { selected, linked, rows, more, busy, loading, restoring, saving, error, associationFailure, retryLabel, newTitle, creating, load, restore, apply, create, retry };
}
