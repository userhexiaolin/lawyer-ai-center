import type { DocTemplate } from '#/api/legal/doc-template';

import { effectScope } from 'vue';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  documentTypes,
  removeTemplate,
  saveTemplateMetadata,
  templateDetail,
  templateList,
} from '#/api/legal/doc-template';

import { useTemplateLibrary } from '../use-template-library';

vi.mock('#/api/legal/doc-template', () => ({
  documentTypes: vi.fn(),
  removeTemplate: vi.fn(),
  saveTemplateMetadata: vi.fn(),
  templateDetail: vi.fn(),
  templateList: vi.fn(),
}));
const row: DocTemplate = {
  id: '1',
  name: '合成模板',
  documentType: '律师函',
  content: '',
  sortOrder: 0,
  status: '1',
  remark: '',
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(documentTypes).mockResolvedValue([]);
  vi.mocked(templateList).mockResolvedValue({ rows: [row], total: 1 });
});
function library() {
  const scope = effectScope();
  const state = scope.run(useTemplateLibrary);
  if (!state) throw new Error('Missing effect scope');
  return { state, stop: () => scope.stop() };
}
describe('template list requests and recovery', () => {
  it('shows an empty result without an error when switching from all templates to a status with no matches', async () => {
    const { state, stop } = library();
    await state.load();
    vi.mocked(templateList).mockResolvedValueOnce({
      processing: 'error',
      rows: [],
      total: 0,
    });
    await state.filterProcessing('error');
    expect(state.error.value).toBe('');
    expect(state.rows.value).toEqual([]);
    expect(state.total.value).toBe(0);
    expect(state.page.value).toBe(1);
    expect(state.busy.value).toBe(false);
    expect(state.filtered.value).toBe(true);
    stop();
  });
  it('filters the whole server result before pagination and keeps submitted search context', async () => {
    vi.mocked(templateList).mockImplementation(async (params) => ({
      rows: [row],
      total: 1,
      processing: String(params.processing || ''),
    }));
    const { state, stop } = library();
    state.filters.name = '已提交';
    state.filters.type = '律师函';
    await state.query();
    state.page.value = 4;
    state.selectedKeys.value = ['1'];
    state.filters.name = '还未提交';
    await state.filterProcessing('review');
    expect(templateList).toHaveBeenLastCalledWith({
      name: '已提交',
      type: '律师函',
      processing: 'review',
      pageNum: 1,
      pageSize: 20,
    });
    expect(state.selectedKeys.value).toEqual([]);
    expect(state.processing.value).toBe('review');
    await state.load();
    expect(templateList).toHaveBeenLastCalledWith(
      expect.objectContaining({ processing: 'review' }),
    );
    await state.reset();
    expect(state.processing.value).toBe('');
    expect(state.filtered.value).toBe(false);
    stop();
  });
  it('does not present an unfiltered page as a successful filter when the backend is older', async () => {
    const { state, stop } = library();
    await state.filterProcessing('error');
    expect(state.rows.value).toEqual([]);
    expect(state.error.value).toContain('筛选服务尚未更新');
    expect(state.processing.value).toBe('error');
    stop();
  });
  it('pagination uses submitted filters instead of unsubmitted typing', async () => {
    const { state, stop } = library();
    state.filters.name = '已提交';
    await state.query();
    state.filters.name = '未提交';
    await state.load();
    expect(templateList).toHaveBeenLastCalledWith(
      expect.objectContaining({ name: '已提交' }),
    );
    stop();
  });
  it('ignores a late response from an older query', async () => {
    let finish!: (value: { rows: DocTemplate[]; total: number }) => void;
    vi.mocked(templateList).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    const { state, stop } = library();
    const older = state.query();
    state.filters.name = '新查询';
    await state.query();
    finish({ rows: [{ ...row, name: '过期结果' }], total: 1 });
    await older;
    expect(state.rows.value[0]?.name).toBe('合成模板');
    expect(state.busy.value).toBe(false);
    stop();
  });
  it('clears stale results on failure, preserves filters and recovers on retry', async () => {
    const { state, stop } = library();
    state.filters.name = '保留的筛选';
    await state.query();
    vi.mocked(templateList).mockRejectedValueOnce(new Error('服务暂不可用'));
    await state.load();
    expect(state.rows.value).toEqual([]);
    expect(state.error.value).toBe('服务暂不可用');
    expect(state.filters.name).toBe('保留的筛选');
    await state.load();
    expect(state.error.value).toBe('');
    expect(state.rows.value).toHaveLength(1);
    stop();
  });
  it('prevents duplicate deletion and preserves the row after a rejected deletion', async () => {
    let reject!: (error: Error) => void;
    vi.mocked(removeTemplate).mockImplementationOnce(
      () =>
        new Promise((_, fail) => {
          reject = fail;
        }),
    );
    const { state, stop } = library();
    await state.load();
    const pending = state.mutate('1', 'delete');
    await state.mutate('1', 'delete');
    expect(removeTemplate).toHaveBeenCalledTimes(1);
    expect(state.pendingId.value).toBe('1');
    reject(new Error('无权删除'));
    await pending;
    expect(state.rows.value).toHaveLength(1);
    expect(state.actionError.value).toBe('无权删除');
    stop();
  });
  it('returns from an empty last page to the last valid page', async () => {
    const { state, stop } = library();
    state.page.value = 2;
    await state.load();
    expect(state.page.value).toBe(1);
    expect(templateList).toHaveBeenLastCalledWith(
      expect.objectContaining({ pageNum: 1 }),
    );
    stop();
  });
  it('does not reverse a newer server status after a stale confirmation', async () => {
    vi.mocked(templateDetail).mockResolvedValue({ ...row, status: '1' });
    const { state, stop } = library();
    await state.mutate('1', 'toggle', '0');
    expect(saveTemplateMetadata).not.toHaveBeenCalled();
    expect(state.actionError.value).toContain('模板状态已变化');
    stop();
  });
});
