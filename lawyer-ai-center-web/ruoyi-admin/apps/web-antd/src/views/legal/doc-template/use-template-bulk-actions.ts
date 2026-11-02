import type { DocTemplate, DocumentTypeOption } from '#/api/legal/doc-template';

import { computed, onScopeDispose, ref } from 'vue';

import {
  applyTemplateType,
  classifyExistingTemplateType,
  removeTemplateQuietly,
  templateTypeVersion,
} from '#/api/legal/doc-template';

export interface BulkTypeRow {
  id: string;
  name: string;
  original: string;
  documentType: string;
  revision: string;
  intent: number;
  recognizing: boolean;
  state: 'failed' | 'ready' | 'saved' | 'saving';
  notice: string;
}
export function useTemplateBulkActions(
  options: () => DocumentTypeOption[],
  onChanged: () => Promise<void>,
) {
  const open = ref(false);
  const mode = ref<'delete' | 'type'>('type');
  const rows = ref<BulkTypeRow[]>([]);
  const saving = ref(false);
  const recognizing = ref(false);
  const deleteConfirmed = ref(false);
  const saved = computed(
    () => rows.value.filter((row) => row.state === 'saved').length,
  );
  const pending = computed(() =>
    rows.value.filter((row) => row.state !== 'saved'),
  );
  let generation = 0;
  const requests = new Set<AbortController>();
  function cancelRecognition() {
    generation++;
    requests.forEach((request) => request.abort());
    requests.clear();
    recognizing.value = false;
    rows.value.forEach((row) => {
      row.recognizing = false;
    });
  }
  function close() {
    if (saving.value) return;
    cancelRecognition();
    open.value = false;
  }
  onScopeDispose(cancelRecognition);
  async function recognizeRow(row: BulkTypeRow, requestedIntent?: number) {
    if (saving.value || row.state === 'saved' || row.recognizing) return;
    const expectedIntent = requestedIntent ?? ++row.intent;
    const token = generation;
    const controller = new AbortController();
    requests.add(controller);
    row.recognizing = true;
    if (row.intent === expectedIntent) row.notice = '正在识别；不会自动保存';
    try {
      const result = await classifyExistingTemplateType(
        row.id,
        controller.signal,
      );
      if (!open.value || token !== generation) return;
      // Keep the snapshot for an intervening manual choice, but never overwrite that choice.
      row.revision = result.revision;
      row.original = result.previousType;
      if (row.intent !== expectedIntent) return;
      if (
        result.documentType &&
        options().some(
          (type) => type.value === result.documentType && !type.disabled,
        )
      ) {
        row.documentType = result.documentType;
        row.notice = `已切换为“${result.documentType}”，待保存${result.warning ? `；${result.warning}` : ''}`;
      } else {
        row.notice =
          result.warning?.trim() || '未能识别，保留当前类型；可重试或手动选择';
      }
      row.state = 'ready';
    } catch {
      if (token === generation && row.intent === expectedIntent)
        row.notice = '识别未完成，保留当前类型；请重试或取消后在编辑中修改';
    } finally {
      requests.delete(controller);
      if (token === generation) row.recognizing = false;
    }
  }
  function select(row: BulkTypeRow, type: string) {
    if (saving.value || row.state === 'saved') return;
    row.intent++;
    row.documentType = type;
    row.notice = '已手动选择，待保存';
    row.state = 'ready';
  }
  async function start(selected: DocTemplate[], action: 'delete' | 'type') {
    if (saving.value || selected.length === 0 || open.value) return;
    cancelRecognition();
    mode.value = action;
    deleteConfirmed.value = false;
    rows.value = selected
      .filter((row) => row.id)
      .map((row) => ({
        id: String(row.id),
        name: row.name,
        original: row.documentType,
        documentType: row.documentType,
        revision: '',
        intent: 0,
        recognizing: false,
        state: 'ready',
        notice: '',
      }));
    if (rows.value.length === 0) return;
    open.value = true;
    if (action === 'delete') return;
    const token = generation;
    recognizing.value = true;
    for (const row of rows.value) {
      if (token !== generation) break;
      // The click on the batch button predates any manual edits made while queued.
      await recognizeRow(row, 0);
    }
    if (token === generation) recognizing.value = false;
  }
  async function confirm() {
    if (saving.value || !open.value || pending.value.length === 0) return;
    cancelRecognition();
    saving.value = true;
    deleteConfirmed.value = mode.value === 'delete';
    let changed = false;
    try {
      for (const row of pending.value) {
        try {
          row.state = 'saving';
          let unchanged = false;
          if (mode.value === 'delete') {
            await removeTemplateQuietly(row.id);
          } else if (row.documentType === row.original) {
            unchanged = true;
          } else {
            if (!row.revision) {
              // A manual choice can be saved even if optional AI has not returned.
              const current = await templateTypeVersion(row.id);
              if (current.documentType !== row.original)
                throw new Error('模板类型已变化，请重新打开后核对');
              row.revision = current.revision;
            }
            if (
              !options().some(
                (type) => type.value === row.documentType && !type.disabled,
              )
            )
              throw new Error('所选类型已不可用，请重新选择');
            await applyTemplateType(row.id, row.documentType, row.revision);
          }
          row.state = 'saved';
          if (mode.value === 'delete') row.notice = '已删除';
          else if (unchanged) row.notice = '类型未变更，无需保存';
          else row.notice = '已保存';
          changed ||= !unchanged;
        } catch (error) {
          row.state = 'failed';
          row.notice = (error as Error).message || '操作失败，请重试';
        }
      }
    } finally {
      saving.value = false;
      if (changed) await onChanged();
    }
  }
  return {
    open,
    mode,
    rows,
    saving,
    recognizing,
    deleteConfirmed,
    pending,
    saved,
    start,
    close,
    select,
    recognizeRow,
    confirm,
  };
}
