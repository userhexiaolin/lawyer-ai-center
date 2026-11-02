import { computed, onScopeDispose, reactive, ref, watch } from 'vue';
import { reportApi } from '@/api/legal-report';
import type { ReportEdit, SearchReport, SearchReportItem } from '@/api/legal-report/types';
import { movedItemIds, selectReport } from './helpers';

/** Keeps unsaved prose across item mutations; saves it before generating the snapshot document. */
export function useWorkbench(id: string, token: () => string | undefined, message: (text: string, error?: boolean) => void, api = reportApi) {
  const report = ref<SearchReport>(), items = ref<SearchReportItem[]>([]), busy = ref(''), error = ref('');
  const edit = reactive<ReportEdit>({ title: '', background: '', conclusion: '' });
  const dirty = computed(() => !!report.value && (edit.title !== report.value.title || edit.background !== (report.value.background || '') || edit.conclusion !== (report.value.conclusion || '')));
  let epoch = 0, disposed = false;
  onScopeDispose(() => { disposed = true; epoch++; });
  async function run(kind: string, task: (check: () => void) => Promise<void>) {
    if (busy.value || !token()) return;
    const version = epoch, account = token(); busy.value = kind; error.value = '';
    const active = () => !disposed && version === epoch && account === token();
    const check = () => { if (!active()) throw new Error('操作已取消'); };
    try { await task(check); }
    catch (e) { if (active()) { error.value = (e as Error).message || '操作失败，请重试'; message(error.value, true); } }
    finally { if (active()) busy.value = ''; }
  }
  async function refresh(check: () => void, reset = false) {
    const detail = await api.detail(id); check(); report.value = detail.report; items.value = detail.items;
    if (reset) Object.assign(edit, { title: detail.report.title, background: detail.report.background || '', conclusion: detail.report.conclusion || '' });
    selectReport(token(), id);
  }
  async function saveEdit(check: () => void) {
    if (!edit.title.trim()) throw new Error('请输入报告标题');
    if (dirty.value) { const saved = await api.update(id, { ...edit, title: edit.title.trim() }); check(); report.value = saved; edit.title = saved.title; }
  }
  function invalidateDownload() { if (report.value) report.value = { ...report.value, status: 'draft', fileUrl: null }; }
  const load = () => run('load', check => refresh(check, true));
  const save = () => run('save', async check => { await saveEdit(check); message('已保存'); });
  const move = (index: number, delta: -1 | 1) => run('order', async check => {
    await api.order(id, movedItemIds(items.value, index, delta)); check(); invalidateDownload(); await refresh(check);
  });
  const remove = (itemId: string) => run('remove', async check => { await api.removeItem(id, itemId); check(); invalidateDownload(); await refresh(check); message('条目已删除'); });
  const add = (title: string, content: string, done: () => void) => run('add', async check => {
    if (!title.trim() || !content.trim()) throw new Error('请填写条目标题和正文');
    await api.add(id, { itemType: 'custom_text', snapshotTitle: title.trim(), snapshotContent: content }); check(); invalidateDownload(); done();
    await refresh(check); message('条目已加入报告');
  });
  const download = () => run('download', async check => { await api.download(id, report.value!.title); check(); message('下载已开始'); });
  const generate = () => run('generate', async check => {
    if (!items.value.length) throw new Error('请先添加条目');
    await saveEdit(check);
    await api.generate(id); check(); await refresh(check);
    message('Word 已生成');
    await api.download(id, report.value!.title); check();
  });
  watch(token, () => {
    epoch++; busy.value = ''; report.value = undefined; items.value = []; error.value = '';
    Object.assign(edit, { title: '', background: '', conclusion: '' });
    void load();
  }, { immediate: true, flush: 'sync' });
  return { report, items, busy, error, edit, dirty, load, save, move, remove, add, generate, download };
}
