import { effectScope, ref } from 'vue';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  analyzeTemplateFields,
  classifyTemplateType,
  saveTemplate,
  uploadTemplate,
} from '#/api/legal/doc-template';

import { useTemplateBatchUpload } from '../use-template-batch-upload';

vi.mock('#/api/legal/doc-template', () => ({
  analyzeTemplateFields: vi.fn(),
  classifyTemplateType: vi.fn(),
  saveTemplate: vi.fn(),
  uploadTemplate: vi.fn(),
}));
const types = [
  { value: '自由描述文书', label: '自由描述文书' },
  { value: '律师函', label: '律师函' },
  { value: '民事起诉状', label: '民事起诉状' },
];
let scope: ReturnType<typeof effectScope>;
function setup() {
  scope = effectScope();
  const open = ref(true);
  const queue = scope.run(() =>
    useTemplateBatchUpload(open, () => types, vi.fn()),
  );
  if (!queue) throw new Error('Inactive scope');
  return queue;
}
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(classifyTemplateType).mockResolvedValue({
    documentType: null,
    warning: '',
  });
  vi.mocked(uploadTemplate).mockImplementation(async (file, type) => ({
    name: file.name,
    documentType: type,
    content: '',
    sourceVersion: file.name,
    parseStatus: 'uploaded',
    blocks: [],
  }));
  vi.mocked(saveTemplate).mockResolvedValue('id');
});
afterEach(() => scope.stop());

describe('non-blocking batch type prefill', () => {
  it('prefills matching files and immediately uploads unmatched and ambiguous files using the default', async () => {
    const queue = setup();
    queue.addFiles([
      new File(['a'], '律师函.docx'),
      new File(['b'], '未知.docx'),
      new File(['c'], '律师函_民事起诉状.docx'),
    ]);
    expect(queue.items.value.map((row) => row.documentType)).toEqual([
      '律师函',
      '自由描述文书',
      '自由描述文书',
    ]);
    expect(uploadTemplate).not.toHaveBeenCalled();
    expect(analyzeTemplateFields).not.toHaveBeenCalled();
    await queue.start();
    expect(queue.completed.value).toBe(3);
    expect(vi.mocked(uploadTemplate).mock.calls.map((call) => call[1])).toEqual(
      ['律师函', '自由描述文书', '自由描述文书'],
    );
    expect(analyzeTemplateFields).not.toHaveBeenCalled();
    expect(saveTemplate).toHaveBeenCalledWith(
      expect.objectContaining({
        status: '0',
        fieldsConfirmed: false,
        saveDraft: true,
      }),
    );
  });

  it('preserves manual choices and applies a changed default only to newly added unmatched files', async () => {
    const queue = setup();
    queue.addFiles([
      new File(['a'], '律师函.docx'),
      new File(['b'], '未知一.docx'),
    ]);
    const manual = queue.items.value[0];
    if (!manual) throw new Error('Missing row');
    manual.documentType = '民事起诉状';
    queue.defaultType.value = '律师函';
    queue.addFiles([new File(['c'], '未知二.docx')]);
    expect(queue.items.value.map((row) => row.documentType)).toEqual([
      '民事起诉状',
      '自由描述文书',
      '律师函',
    ]);
    await queue.start();
    expect(queue.completed.value).toBe(3);
    expect(vi.mocked(uploadTemplate).mock.calls[0]?.[1]).toBe('民事起诉状');
  });
});
