import type { DocTemplate } from '#/api/legal/doc-template';

import { effectScope, ref } from 'vue';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  analyzeTemplateFields,
  parseTemplateVersion,
  saveTemplate,
  uploadTemplate,
} from '#/api/legal/doc-template';

import { useTemplateBatchUpload } from '../use-template-batch-upload';
import { useTemplateUpload } from '../use-template-upload';

vi.mock('#/api/legal/doc-template', () => ({
  analyzeTemplateFields: vi.fn(),
  classifyTemplateType: vi
    .fn()
    .mockResolvedValue({ documentType: null, warning: '' }),
  parseTemplateVersion: vi.fn(),
  saveTemplate: vi.fn(),
  uploadTemplate: vi.fn(),
}));
const options = [{ value: '律师函', label: '律师函' }];
const field = {
  id: 'p0-2-6',
  blockId: 'p0',
  start: 2,
  end: 6,
  original: '____',
  label: '人工修正本金',
  kind: 'money' as const,
  unit: '元',
  presentation: 'underline' as const,
  enabled: true,
};
const saved: DocTemplate = {
  id: '19',
  name: '新名称',
  sourceFilename: '02_原文件.docx',
  documentType: '律师函',
  content: '本金____元',
  sourceVersion: 'saved',
  sourceFormat: 'docx',
  status: '0',
  remark: '',
  sortOrder: 0,
  parseStatus: 'confirmed',
  fieldsConfirmed: true,
  editableBlocks: ['p0'],
  blocks: [{ id: 'p0', text: '本金____元', editable: true, fields: [field] }],
  configureExisting: true,
};
function required<T>(value: T | undefined): T {
  if (value === undefined) throw new Error('Missing test value');
  return value;
}
let scopes: ReturnType<typeof effectScope>[] = [];
function setup(initial: Partial<DocTemplate> = saved) {
  const scope = effectScope();
  scopes.push(scope);
  const open = ref(false);
  const result = required(
    scope.run(() =>
      useTemplateUpload(
        open,
        () => options,
        vi.fn(),
        () => structuredClone(initial),
      ),
    ),
  );
  open.value = true;
  return { open, result };
}
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(uploadTemplate).mockReset();
  vi.mocked(analyzeTemplateFields).mockReset();
  vi.mocked(saveTemplate).mockResolvedValue('new-id');
});
afterEach(() => {
  scopes.forEach((scope) => scope.stop());
  scopes = [];
});
describe('cached fields and explicit parsing', () => {
  it('reopens saved data without parsing or invalidating confirmation and keeps current name separate from original filename', async () => {
    const { open, result } = setup();
    for (let index = 0; index < 3; index++) {
      expect(result.form.name).toBe('新名称');
      expect(result.fileName.value).toBe('02_原文件.docx');
      expect(result.form.fields?.[0]?.label).toBe('人工修正本金');
      expect(result.form.fieldsConfirmed).toBe(true);
      expect(result.form.editableBlocks).toEqual(['p0']);
      open.value = false;
      open.value = true;
    }
    expect(analyzeTemplateFields).not.toHaveBeenCalled();
    expect(parseTemplateVersion).not.toHaveBeenCalled();
    expect(uploadTemplate).not.toHaveBeenCalled();
    required(required(result.form.fields)[0]).label = '新的人工修改';
    expect(result.form.fieldsConfirmed).toBe(false);
  });
  it('does not show obsolete pending-review warnings for a confirmed configuration', () => {
    const { result } = setup({ ...saved, analysisWarning: 'AI字段建议待核对' });
    expect(result.statusText.value).toBe('已确认');
    expect(result.analysisWarning.value).toBe('');
    const pending = setup({
      ...saved,
      fieldsConfirmed: false,
      analysisWarning: 'AI字段建议待核对',
    });
    expect(pending.result.analysisWarning.value).toBe('AI字段建议待核对');
  });
  it('shows unparsed as a valid uploaded state and parses only on explicit action', async () => {
    const { result } = setup({
      ...saved,
      sourceVersion: 'raw',
      parseStatus: 'uploaded',
      content: '',
      blocks: [],
      editableBlocks: [],
      fieldsConfirmed: false,
    });
    expect(result.unparsed.value).toBe(true);
    expect(result.statusText.value).toBe('未解析');
    expect(result.ready.value).toBe(true);
    expect(analyzeTemplateFields).not.toHaveBeenCalled();
    vi.mocked(parseTemplateVersion).mockResolvedValue({
      ...saved,
      id: undefined,
      sourceVersion: 'working',
      parseStatus: 'rules',
    });
    vi.mocked(analyzeTemplateFields).mockResolvedValue({
      fields: [field],
      method: 'AI',
      warning: '待核对',
    });
    await result.analyzeFields();
    expect(parseTemplateVersion).toHaveBeenCalledWith('raw', '律师函');
    expect(analyzeTemplateFields).toHaveBeenCalledWith('working');
    expect(result.form.name).toBe('新名称');
    expect(result.form.editableBlocks).toEqual(['p0']);
    expect(result.form.fieldsConfirmed).toBe(false);
  });
  it('defaults to upload-only and saves enabled without fabricating confirmation', async () => {
    const { result } = setup({});
    expect(result.parseOnUpload.value).toBe(false);
    vi.mocked(uploadTemplate).mockResolvedValue({
      name: '原文件',
      documentType: '律师函',
      content: '',
      sourceVersion: 'raw',
      sourceFormat: 'docx',
      parseStatus: 'uploaded',
      blocks: [],
    });
    const file = new File(['bytes'], '原文件.docx');
    await result.selectFile(file);
    await result.submit();
    expect(uploadTemplate).toHaveBeenCalledWith(file, '律师函', false);
    expect(analyzeTemplateFields).not.toHaveBeenCalled();
    expect(saveTemplate).toHaveBeenCalledWith(
      expect.objectContaining({
        status: '0',
        saveDraft: true,
        fieldsConfirmed: false,
        content: '',
      }),
    );
  });
});
describe('batch queue', () => {
  function batch() {
    const scope = effectScope();
    scopes.push(scope);
    const open = ref(false);
    const queue = required(
      scope.run(() => useTemplateBatchUpload(open, () => options, vi.fn())),
    );
    open.value = true;
    return { ...queue, open };
  }
  it('uploads multiple raw files, retains partial success and retries only failed save without upload duplication', async () => {
    const queue = batch();
    expect(queue.parseOnUpload.value).toBe(false);
    vi.mocked(uploadTemplate).mockImplementation(async (file) => ({
      name: file.name,
      documentType: '律师函',
      content: '',
      sourceVersion: file.name,
      parseStatus: 'uploaded',
      blocks: [],
    }));
    vi.mocked(saveTemplate)
      .mockResolvedValueOnce('a')
      .mockRejectedValueOnce(new Error('保存超时'))
      .mockResolvedValueOnce('b');
    queue.addFiles([new File(['a'], '甲.docx'), new File(['b'], '乙.docx')]);
    await queue.start();
    expect(queue.completed.value).toBe(1);
    expect(queue.items.value[1]?.error).toBe('保存超时');
    expect(uploadTemplate).toHaveBeenCalledTimes(2);
    expect(analyzeTemplateFields).not.toHaveBeenCalled();
    await queue.start();
    expect(queue.completed.value).toBe(2);
    expect(uploadTemplate).toHaveBeenCalledTimes(2);
    expect(saveTemplate).toHaveBeenCalledTimes(3);
  });
  it('uses the newly selected parse option after an upload failed before returning a version', async () => {
    const queue = batch();
    const file = new File(['a'], '原件.docx');
    queue.addFiles([file]);
    vi.mocked(uploadTemplate)
      .mockRejectedValueOnce(new Error('上传失败'))
      .mockResolvedValueOnce({
        name: '原件',
        documentType: '律师函',
        content: saved.content,
        sourceVersion: 'new-version',
        blocks: saved.blocks,
      });
    vi.mocked(analyzeTemplateFields).mockResolvedValue({
      fields: [field],
      method: 'AI',
      warning: '待核对',
    });
    await queue.start();
    queue.parseOnUpload.value = true;
    await queue.start();
    expect(vi.mocked(uploadTemplate).mock.calls.map((call) => call[2])).toEqual(
      [false, true],
    );
    expect(analyzeTemplateFields).toHaveBeenCalledWith('new-version');
    expect(queue.completed.value).toBe(1);
  });
  it('stops an obsolete batch after closing and reopening, without parsing, saving or uploading the next file', async () => {
    const queue = batch();
    queue.parseOnUpload.value = true;
    queue.addFiles([new File(['a'], '甲.docx'), new File(['b'], '乙.docx')]);
    let finish:
      | ((value: Awaited<ReturnType<typeof uploadTemplate>>) => void)
      | undefined;
    vi.mocked(uploadTemplate).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    const running = queue.start();
    queue.open.value = false;
    queue.open.value = true;
    required(finish)({
      name: '原件',
      documentType: '律师函',
      content: saved.content,
      sourceVersion: 'old-version',
      blocks: saved.blocks,
    });
    await running;
    expect(uploadTemplate).toHaveBeenCalledTimes(1);
    expect(analyzeTemplateFields).not.toHaveBeenCalled();
    expect(saveTemplate).not.toHaveBeenCalled();
    expect(queue.items.value).toEqual([]);
    expect(queue.busy.value).toBe(false);
  });
  it('saves analyzed suggestions as unconfirmed drafts and preserves user names', async () => {
    const queue = batch();
    queue.parseOnUpload.value = true;
    const file = new File(['a'], '02_原始.docx');
    queue.addFiles([file]);
    required(queue.items.value[0]).name = '新的模板名';
    vi.mocked(uploadTemplate).mockResolvedValue({
      name: '02_原始',
      documentType: '律师函',
      content: saved.content,
      sourceVersion: 'work',
      blocks: saved.blocks,
      parseStatus: 'rules',
    });
    vi.mocked(analyzeTemplateFields).mockResolvedValue({
      fields: [field],
      method: 'AI',
      warning: 'AI建议待核对',
    });
    await queue.start();
    expect(uploadTemplate).toHaveBeenCalledWith(file, '律师函', true);
    expect(analyzeTemplateFields).toHaveBeenCalledTimes(1);
    expect(saveTemplate).toHaveBeenCalledWith(
      expect.objectContaining({
        name: '新的模板名',
        saveDraft: true,
        status: '0',
        fieldsConfirmed: false,
        fields: [field],
      }),
    );
    expect(required(queue.items.value[0]).warning).toBe('AI建议待核对');
  });
});
