import { effectScope } from 'vue';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  removeTemplateType,
  saveTemplateType,
  templateTypeList,
} from '#/api/legal/doc-template-type';

import { useTemplateTypes } from '../../doc-template-type/use-template-types';

vi.mock('#/api/legal/doc-template-type', () => ({
  removeTemplateType: vi.fn(),
  saveTemplateType: vi.fn(),
  templateTypeList: vi.fn(),
}));
const row = {
  id: '1',
  name: '合成分类',
  status: '0' as const,
  builtin: false,
  sortOrder: 10,
  remark: '',
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(templateTypeList).mockResolvedValue([
    row,
    { ...row, id: '2', builtin: true, status: '1' },
  ]);
});
function setup() {
  const scope = effectScope();
  const state = scope.run(useTemplateTypes);
  if (!state) throw new Error('Missing effect scope');
  return { state, stop: () => scope.stop() };
}
describe('template type queries and mutations', () => {
  it('uses submitted filters, filters real status/source and retains drafts on refresh', async () => {
    const { state, stop } = setup();
    state.filters.status = '1';
    state.filters.source = 'builtin';
    state.filters.name = ' 已提交 ';
    await state.query();
    expect(state.visibleRows.value.map((value) => value.id)).toEqual(['2']);
    state.filters.name = '未提交';
    await state.load();
    expect(templateTypeList).toHaveBeenLastCalledWith('已提交');
    stop();
  });
  it('ignores late responses and clears old rows after timeout, then retries', async () => {
    const { state, stop } = setup();
    let finish!: (value: (typeof row)[]) => void;
    vi.mocked(templateTypeList).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    const old = state.load();
    await state.load();
    finish([{ ...row, name: '旧结果' }]);
    await old;
    expect(state.rows.value[0]?.name).toBe('合成分类');
    vi.mocked(templateTypeList).mockRejectedValueOnce(
      new Error('timeout of 10000ms exceeded'),
    );
    await state.load();
    expect(state.error.value).toContain('请求超时');
    expect(state.rows.value).toEqual([]);
    await state.load();
    expect(state.error.value).toBe('');
    expect(state.rows.value).toHaveLength(2);
    stop();
  });
  it('retains a rejected deletion and blocks duplicate requests and builtin deletion', async () => {
    const { state, stop } = setup();
    await state.load();
    let fail!: (error: Error) => void;
    vi.mocked(removeTemplateType).mockImplementationOnce(
      () =>
        new Promise((_, reject) => {
          fail = reject;
        }),
    );
    const pending = state.mutate(row, 'delete');
    await state.mutate(row, 'delete');
    expect(removeTemplateType).toHaveBeenCalledTimes(1);
    fail(new Error('该类型下仍有模板'));
    await pending;
    expect(state.rows.value).toHaveLength(2);
    expect(state.actionError.value).toContain('仍有模板');
    await state.mutate({ ...row, builtin: true }, 'delete');
    expect(removeTemplateType).toHaveBeenCalledTimes(1);
    stop();
  });
  it('does not optimistically change status and returns to valid last page', async () => {
    const { state, stop } = setup();
    await state.load();
    vi.mocked(saveTemplateType).mockRejectedValueOnce(new Error('无权操作'));
    await state.mutate(row, 'toggle');
    expect(state.rows.value[0]?.status).toBe('0');
    expect(state.actionError.value).toBe('无权操作');
    state.page.value = 2;
    await state.load();
    expect(state.page.value).toBe(1);
    stop();
  });
});
