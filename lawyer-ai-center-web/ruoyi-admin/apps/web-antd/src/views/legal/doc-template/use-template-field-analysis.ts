import type { Ref } from 'vue';

import type { DocTemplate } from '#/api/legal/doc-template';

import { onScopeDispose, ref, watch } from 'vue';

import {
  startTemplateFieldAnalysis,
  templateFieldAnalysisStatus,
} from '#/api/legal/doc-template';

/** Poll real server phases. Never re-submit a model request after a connection failure. */
export function useTemplateFieldAnalysis(rows: Ref<DocTemplate[]>) {
  const starting = ref(new Set<string>());
  const errors = ref<Record<string, string>>({});
  let timer: ReturnType<typeof setTimeout> | undefined;
  let disposed = false;
  let polling = false;
  const active = (row: DocTemplate) =>
    !!row.id &&
    (starting.value.has(row.id) || row.fieldAnalysis?.state === 'running');
  const pending = (row: DocTemplate) =>
    ['partial', 'ready'].includes(row.fieldAnalysis?.state || '');
  function schedule() {
    clearTimeout(timer);
    if (!disposed && rows.value.some((row) => active(row)))
      timer = setTimeout(() => void poll(), 1500);
  }
  async function poll() {
    if (disposed || polling) return;
    polling = true;
    try {
      await Promise.all(
        rows.value
          .filter((row) => active(row))
          .map(async (row) => {
            const id = row.id;
            if (!id) return;
            try {
              const status = await templateFieldAnalysisStatus(id);
              if (disposed || !rows.value.includes(row)) return;
              if (status) row.fieldAnalysis = status;
              errors.value[id] = '';
            } catch {
              if (!disposed && rows.value.includes(row))
                errors.value[id] = '进度暂不可用，正在重新连接；请勿重复解析';
            }
          }),
      );
    } finally {
      polling = false;
      schedule();
    }
  }
  watch(rows, schedule, { immediate: true });
  onScopeDispose(() => {
    disposed = true;
    clearTimeout(timer);
  });
  async function start(row: DocTemplate, replacePending = false) {
    if (!row.id || active(row)) return;
    const id = row.id;
    starting.value.add(id);
    errors.value[id] = '';
    schedule();
    try {
      const result = await startTemplateFieldAnalysis(id, replacePending);
      const current = rows.value.find((item) => item.id === id);
      if (!disposed && current) current.fieldAnalysis = result;
    } catch (error) {
      const current = rows.value.find((item) => item.id === id);
      if (!disposed && current) {
        errors.value[id] =
          (error as Error).message || '解析请求连接中断，请刷新查看服务端状态';
        try {
          const status = await templateFieldAnalysisStatus(id);
          if (!disposed && rows.value.includes(current) && status)
            current.fieldAnalysis = status;
        } catch {
          /* Keep the connection error visible. */
        }
      }
    } finally {
      starting.value.delete(id);
      schedule();
    }
  }
  return { active, pending, start, starting, errors };
}
