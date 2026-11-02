import { effectScope } from 'vue';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  applyTemplateType,
  classifyExistingTemplateType,
  removeTemplateQuietly,
  templateTypeVersion,
} from '#/api/legal/doc-template';

import { useTemplateBulkActions } from '../use-template-bulk-actions';

vi.mock('#/api/legal/doc-template', () => ({
  applyTemplateType: vi.fn(),
  classifyExistingTemplateType: vi.fn(),
  removeTemplateQuietly: vi.fn(),
  templateTypeVersion: vi.fn(),
}));
const scopes: ReturnType<typeof effectScope>[] = [];
const templates = ['1', '2'].map((id) => ({
  id,
  name: `模板${id}`,
  documentType: '原类型',
  content: '正文',
  sortOrder: 0,
  status: '1' as const,
  remark: '',
}));
const options = ['原类型', '人工类型', 'AI类型'].map((value) => ({
  value,
  label: value,
}));
function setup() {
  const scope = effectScope();
  scopes.push(scope);
  const changed = vi.fn().mockResolvedValue(undefined);
  const bulk = scope.run(() => useTemplateBulkActions(() => options, changed));
  if (!bulk) throw new Error('Inactive scope');
  return {
    ...bulk,
    changed,
  };
}
function firstRow(bulk: ReturnType<typeof setup>) {
  const row = bulk.rows.value[0];
  if (!row) throw new Error('Missing selected template');
  return row;
}
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(classifyExistingTemplateType).mockImplementation(async (id) => ({
    id,
    previousType: '原类型',
    revision: `v${id}`,
    documentType: 'AI类型',
    warning: '',
  }));
  vi.mocked(applyTemplateType).mockResolvedValue();
  vi.mocked(removeTemplateQuietly).mockResolvedValue();
  vi.mocked(templateTypeVersion).mockResolvedValue({
    documentType: '原类型',
    revision: 'fresh',
  });
});
afterEach(() => scopes.splice(0).forEach((scope) => scope.stop()));
describe('template selection operations', () => {
  it('shows server initialization failures once and does not change or save row types', async () => {
    const warning =
      '类型识别服务尚未初始化，请联系管理员完成数据库升级；当前类型已保留';
    vi.mocked(classifyExistingTemplateType).mockResolvedValue({
      id: '1',
      previousType: '原类型',
      revision: 'v1',
      documentType: null,
      warning,
    });
    const bulk = setup();
    await bulk.start(templates.slice(0, 1), 'type');
    expect(firstRow(bulk).notice).toBe(warning);
    expect(firstRow(bulk).documentType).toBe('原类型');
    expect(applyTemplateType).not.toHaveBeenCalled();
  });
  it('saves a manual choice without waiting for optional AI to return', async () => {
    const bulk = setup();
    vi.mocked(classifyExistingTemplateType).mockImplementationOnce(
      () => new Promise(() => {}),
    );
    void bulk.start(templates.slice(0, 1), 'type');
    bulk.select(firstRow(bulk), '人工类型');
    await bulk.confirm();
    expect(applyTemplateType).toHaveBeenCalledWith('1', '人工类型', 'fresh');
    expect(bulk.saved.value).toBe(1);
  });
  it('does nothing with no selection and never saves before confirm', async () => {
    const bulk = setup();
    await bulk.start([], 'type');
    expect(bulk.open.value).toBe(false);
    await bulk.start(templates, 'type');
    expect(applyTemplateType).not.toHaveBeenCalled();
    expect(bulk.rows.value[0]?.notice).toContain('待保存');
    bulk.select(firstRow(bulk), '人工类型');
    await bulk.confirm();
    expect(applyTemplateType).toHaveBeenCalledWith('1', '人工类型', 'v1');
    expect(bulk.saved.value).toBe(2);
  });
  it('lets explicit AI replace manual choice, while newer manual intent wins pending AI', async () => {
    const bulk = setup();
    await bulk.start(templates.slice(0, 1), 'type');
    const row = firstRow(bulk);
    bulk.select(row, '人工类型');
    await bulk.recognizeRow(row);
    expect(row.documentType).toBe('AI类型');
    let finish!: (
      value: Awaited<ReturnType<typeof classifyExistingTemplateType>>,
    ) => void;
    vi.mocked(classifyExistingTemplateType).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    const pending = bulk.recognizeRow(row);
    bulk.select(row, '人工类型');
    finish({
      id: row.id,
      previousType: '原类型',
      revision: 'latest',
      documentType: 'AI类型',
      warning: '',
    });
    await pending;
    expect(row.documentType).toBe('人工类型');
    await bulk.confirm();
    expect(applyTemplateType).toHaveBeenCalledWith('1', '人工类型', 'latest');
  });
  it('keeps successful items and exposes conflict failures for fresh classification', async () => {
    const bulk = setup();
    await bulk.start(templates, 'type');
    vi.mocked(applyTemplateType).mockRejectedValueOnce(
      new Error('模板版本已变化，请重新识别'),
    );
    await bulk.confirm();
    expect(bulk.saved.value).toBe(1);
    expect(bulk.rows.value[0]?.state).toBe('failed');
    await bulk.recognizeRow(firstRow(bulk));
    await bulk.confirm();
    expect(
      vi.mocked(applyTemplateType).mock.calls.filter(([id]) => id === '2'),
    ).toHaveLength(1);
    expect(bulk.saved.value).toBe(2);
  });
  it('confirms deletion and retries only failed rows', async () => {
    const bulk = setup();
    await bulk.start(templates, 'delete');
    expect(removeTemplateQuietly).not.toHaveBeenCalled();
    vi.mocked(removeTemplateQuietly).mockRejectedValueOnce(new Error('无权限'));
    await bulk.confirm();
    expect(bulk.saved.value).toBe(1);
    expect(bulk.deleteConfirmed.value).toBe(true);
    await bulk.confirm();
    expect(removeTemplateQuietly).toHaveBeenCalledTimes(3);
    expect(bulk.saved.value).toBe(2);
  });
  it('closes without persistence and discards late recognition', async () => {
    const bulk = setup();
    let finish!: (
      value: Awaited<ReturnType<typeof classifyExistingTemplateType>>,
    ) => void;
    vi.mocked(classifyExistingTemplateType).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    const pending = bulk.start(templates, 'type');
    bulk.close();
    finish({
      id: '1',
      previousType: '原类型',
      revision: 'v1',
      documentType: 'AI类型',
      warning: '',
    });
    await pending;
    expect(bulk.rows.value[0]?.documentType).toBe('原类型');
    expect(applyTemplateType).not.toHaveBeenCalled();
    expect(classifyExistingTemplateType).toHaveBeenCalledTimes(1);
  });
});
