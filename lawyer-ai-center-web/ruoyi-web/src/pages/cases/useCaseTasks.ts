import { computed, onScopeDispose, ref, watch } from 'vue';
import { caseTaskApi, type CaseTask } from '@/api/lawyer/tasks';

export function useCaseTasks(caseId: () => string, token: () => string | undefined, disabled: () => boolean,
  notify: (message: string, error?: boolean) => void, setBusy: (busy: boolean) => void, api = caseTaskApi) {
  const tasks = ref<CaseTask[]>([]), loading = ref(false), busy = ref(false), error = ref('');
  const totals = computed(() => ({ done: tasks.value.filter(t => t.status === 'done').length, total: tasks.value.length,
    checked: tasks.value.reduce((n, t) => n + t.checklist.filter(c => c.checked).length, 0), checks: tasks.value.reduce((n, t) => n + t.checklist.length, 0),
    minutes: tasks.value.reduce((n, t) => n + t.workMinutes, 0) }));
  let version = 0, disposed = false;
  onScopeDispose(() => { disposed = true; version++; setBusy(false); });
  async function load() {
    const v = ++version, id = caseId(), owner = token();
    tasks.value = []; error.value = ''; loading.value = !!owner;
    if (!owner || !id) { loading.value = false; return; }
    try { const result = await api.list(id); if (!disposed && version === v && owner === token()) tasks.value = result.tasks; }
    catch (e) { if (!disposed && version === v && owner === token()) error.value = (e as Error).message; }
    finally { if (!disposed && version === v) loading.value = false; }
  }
  async function run(action: (check: () => void) => Promise<void>) {
    if (busy.value || loading.value || disabled() || disposed || !token()) return;
    const v = version, owner = token(); busy.value = true; setBusy(true); error.value = '';
    const active = () => !disposed && v === version && owner === token();
    const check = () => { if (!active()) throw Error('操作已取消'); };
    try { await action(check); }
    catch (e) { if (active()) { error.value = (e as Error).message; notify(error.value, true); } }
    finally { if (active()) { busy.value = false; setBusy(false); } }
  }
  function replace(task: CaseTask) { tasks.value = tasks.value.map(t => t.id === task.id ? task : t); }
  const create = (title: string, dueAt: string, done: () => void) => run(async check => {
    if (!title.trim()) throw Error('请填写任务标题');
    const task = await api.create(caseId(), title.trim(), dueAt); check(); tasks.value.push(task); done(); notify('任务已创建');
  });
  const status = (id: string, value: CaseTask['status']) => run(async check => { const task = await api.status(id, value); check(); replace(task); });
  const addCheck = (taskId: string, text: string, done: () => void) => run(async check => {
    if (!text.trim()) throw Error('请填写检查项内容');
    const item = await api.addCheck(taskId, text.trim()); check();
    tasks.value = tasks.value.map(t => t.id === taskId ? { ...t, checklist: [...t.checklist, item] } : t); done(); notify('检查项已添加');
  });
  const toggle = (taskId: string, itemId: string, checked: boolean) => run(async check => {
    const item = await api.toggle(taskId, itemId, checked); check();
    tasks.value = tasks.value.map(t => t.id === taskId ? { ...t, checklist: t.checklist.map(c => c.id === itemId ? item : c) } : t);
  });
  const time = (id: string, minutes: number, note: string, requestId: string, done: () => void) => run(async check => {
    if (!Number.isInteger(minutes) || minutes < 1 || minutes > 1440) throw Error('工时须为1至1440的整数分钟');
    const task = await api.time(id, minutes, note, requestId); check(); replace(task); done(); notify('工时已登记');
  });
  watch(() => [caseId(), token()], () => { busy.value = false; setBusy(false); void load(); }, { immediate: true, flush: 'sync' });
  return { tasks, totals, loading, busy, error, load, create, status, addCheck, toggle, time };
}
