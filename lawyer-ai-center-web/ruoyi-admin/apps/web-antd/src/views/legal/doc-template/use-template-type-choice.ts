import type { DocumentTypeOption } from '#/api/legal/doc-template';

import { onScopeDispose, ref } from 'vue';

/** Local form intent wins over older asynchronous results. No persistence here. */
export function useTemplateTypeChoice(
  options: () => DocumentTypeOption[],
  setType: (type: string) => void,
  classify: (
    signal: AbortSignal,
  ) => Promise<{ documentType: null | string; warning?: string }>,
) {
  const recognizing = ref(false);
  const notice = ref('');
  let intent = 0;
  let controller: AbortController | undefined;
  function cancel() {
    intent++;
    controller?.abort();
    controller = undefined;
    recognizing.value = false;
  }
  function reset() {
    cancel();
    notice.value = '';
  }
  function select(type: string) {
    cancel();
    setType(type);
    notice.value = '已手动选择，待保存';
  }
  async function recognize() {
    cancel();
    const current = intent;
    controller = new AbortController();
    recognizing.value = true;
    notice.value = '正在识别，可继续手动选择';
    try {
      const result = await classify(controller.signal);
      if (current !== intent) return;
      if (
        result.documentType &&
        options().some(
          (type) => type.value === result.documentType && !type.disabled,
        )
      ) {
        setType(result.documentType);
        notice.value = `已切换为“${result.documentType}”，待保存；确认保存后生效${result.warning ? `；${result.warning}` : ''}`;
      } else {
        notice.value =
          result.warning?.trim() || '未能识别，保留当前类型；可重试或手动选择';
      }
    } catch {
      if (current === intent)
        notice.value = '识别未完成，保留当前类型；可重试或手动选择';
    } finally {
      if (current === intent) {
        recognizing.value = false;
        controller = undefined;
      }
    }
  }
  onScopeDispose(cancel);
  return { recognizing, notice, select, recognize, cancel, reset };
}
