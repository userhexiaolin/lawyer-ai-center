import type { DocTemplate, TemplateFieldTask } from '#/api/legal/doc-template';

import { effectScope, ref } from 'vue';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  startTemplateFieldAnalysis,
  templateFieldAnalysisStatus,
} from '#/api/legal/doc-template';

import { useTemplateFieldAnalysis } from '../use-template-field-analysis';

vi.mock('#/api/legal/doc-template', () => ({
  startTemplateFieldAnalysis: vi.fn(),
  templateFieldAnalysisStatus: vi.fn(),
}));
function required<T>(value: T | undefined): T {
  if (value === undefined) throw new Error('Missing test fixture');
  return value;
}
const task: TemplateFieldTask = {
  taskId: 'job1',
  state: 'running',
  phase: '正在 AI 识别字段',
  warning: '',
  startedAt: '',
  expiresAt: '',
};
const makeRow = (): DocTemplate => ({
  id: '1',
  name: '合成模板',
  documentType: '律师函',
  content: '',
  sortOrder: 0,
  status: '0',
  remark: '',
  sourceVersion: 'original',
  parseStatus: 'uploaded',
});
let scope: ReturnType<typeof effectScope>;
beforeEach(() => {
  vi.useFakeTimers();
  vi.resetAllMocks();
  scope = effectScope();
});
afterEach(() => {
  scope.stop();
  vi.useRealTimers();
});
describe('list field parsing', () => {
  it('starts once, polls real phases, and retains source and activation until review', async () => {
    const rows = ref([makeRow()]);
    const actions = required(scope.run(() => useTemplateFieldAnalysis(rows)));
    let finish!: (value: TemplateFieldTask) => void;
    vi.mocked(startTemplateFieldAnalysis).mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    vi.mocked(templateFieldAnalysisStatus).mockResolvedValue(task);
    const request = actions.start(required(rows.value[0]));
    await actions.start(required(rows.value[0]));
    expect(startTemplateFieldAnalysis).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1500);
    expect(required(rows.value[0]).fieldAnalysis?.phase).toBe(
      '正在 AI 识别字段',
    );
    finish({ ...task, state: 'ready', phase: '待人工核对' });
    await request;
    expect(actions.pending(required(rows.value[0]))).toBe(true);
    expect(required(rows.value[0]).sourceVersion).toBe('original');
    expect(required(rows.value[0]).status).toBe('0');
    expect(required(rows.value[0]).fieldsConfirmed).toBeUndefined();
    expect(actions.active(required(rows.value[0]))).toBe(false);
  });
  it('restores running progress after navigation and stops polling on disposal', async () => {
    const rows = ref([{ ...makeRow(), fieldAnalysis: task }]);
    const actions = required(scope.run(() => useTemplateFieldAnalysis(rows)));
    rows.value = [...rows.value];
    await Promise.resolve();
    vi.mocked(templateFieldAnalysisStatus).mockResolvedValue({
      ...task,
      state: 'partial',
      phase: '规则候选待核对',
    });
    await vi.advanceTimersByTimeAsync(1600);
    expect(actions.pending(required(rows.value[0]))).toBe(true);
    expect(startTemplateFieldAnalysis).not.toHaveBeenCalled();
    scope.stop();
    await vi.advanceTimersByTimeAsync(5000);
    expect(templateFieldAnalysisStatus).toHaveBeenCalledTimes(1);
  });
  it('recovers server status after connection loss and never automatically resubmits AI', async () => {
    const rows = ref([makeRow()]);
    const actions = required(scope.run(() => useTemplateFieldAnalysis(rows)));
    vi.mocked(startTemplateFieldAnalysis).mockRejectedValue(
      new Error('连接中断'),
    );
    vi.mocked(templateFieldAnalysisStatus).mockResolvedValue(task);
    await actions.start(required(rows.value[0]));
    expect(actions.active(required(rows.value[0]))).toBe(true);
    await vi.advanceTimersByTimeAsync(1600);
    expect(startTemplateFieldAnalysis).toHaveBeenCalledTimes(1);
    expect(required(rows.value[0]).fieldAnalysis?.phase).toBe(task.phase);
  });
});
