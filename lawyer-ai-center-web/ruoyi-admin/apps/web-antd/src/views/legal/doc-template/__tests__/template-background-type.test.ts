import { effectScope, nextTick, ref } from 'vue';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
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
  { value: '合同文书', label: '合同文书' },
];
type Result = Awaited<ReturnType<typeof classifyTemplateType>>;
const scopes: ReturnType<typeof effectScope>[] = [];
function setup() {
  const scope = effectScope();
  scopes.push(scope);
  const open = ref(true);
  const queue = scope.run(() =>
    useTemplateBatchUpload(open, () => types, vi.fn()),
  );
  if (!queue) throw new Error('Inactive scope');
  return { ...queue, open };
}
const file = (name = '模板01.docx') => new File(['synthetic template'], name);
async function settle() {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
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
afterEach(() => {
  scopes.splice(0).forEach((scope) => scope.stop());
});

describe('automatic background classification never blocks upload', () => {
  it('shows initialization details once and lets the file upload with its existing type', async () => {
    const warning =
      '类型识别服务尚未初始化，请联系管理员完成数据库升级；当前类型已保留';
    vi.mocked(classifyTemplateType).mockResolvedValueOnce({
      documentType: null,
      warning,
    });
    const queue = setup();
    queue.addFiles([file()]);
    await settle();
    expect(queue.items.value[0]?.typeWarning).toBe(warning);
    expect(queue.items.value[0]?.documentType).toBe('自由描述文书');
    await queue.start();
    expect(queue.completed.value).toBe(1);
  });
  it('explicit retry replaces an earlier manual choice even when automatic AI is disabled', async () => {
    const queue = setup();
    queue.typeRecognitionEnabled.value = false;
    await nextTick();
    queue.addFiles([file('律师函.docx')]);
    const row = queue.items.value[0];
    if (!row) throw new Error('Missing queued file');
    queue.selectType(row.key, '合同文书');
    vi.mocked(classifyTemplateType).mockResolvedValueOnce({
      documentType: '律师函',
      warning: '',
    });
    queue.retryType(row.key);
    await settle();
    expect(row.documentType).toBe('律师函');
    expect(row.typeWarning).toContain('上传后保存');
    queue.selectType(row.key, '合同文书');
    await queue.start();
    expect(vi.mocked(uploadTemplate).mock.calls[0]?.[1]).toBe('合同文书');
  });
  it('offers a non-blocking explicit retry after failure and prevents duplicate retries', async () => {
    const queue = setup();
    vi.mocked(classifyTemplateType).mockRejectedValueOnce(
      new Error('first attempt failed'),
    );
    queue.addFiles([file()]);
    await settle();
    const row = queue.items.value[0];
    if (!row) throw new Error('Missing queued file');
    let finish: ((value: Result) => void) | undefined;
    vi.mocked(classifyTemplateType).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    queue.retryType(row.key);
    queue.retryType(row.key);
    expect(classifyTemplateType).toHaveBeenCalledTimes(2);
    expect(row.typeRecognizing).toBe(true);
    await queue.start();
    expect(queue.completed.value).toBe(1);
    if (!finish) throw new Error('Retry did not start');
    finish({ documentType: '律师函', warning: '' });
    await settle();
    expect(row.documentType).toBe('自由描述文书');
  });

  it('skips AI for filename matches and automatically prefills the next unknown file', async () => {
    const queue = setup();
    vi.mocked(classifyTemplateType).mockResolvedValueOnce({
      documentType: '律师函',
      warning: '待核对',
    });
    queue.addFiles([file('律师函.docx'), file()]);
    await settle();
    expect(classifyTemplateType).toHaveBeenCalledTimes(1);
    expect(vi.mocked(classifyTemplateType).mock.calls[0]?.[0].name).toBe(
      '模板01.docx',
    );
    expect(queue.items.value[1]?.documentType).toBe('律师函');
    expect(queue.items.value[1]?.typeOrigin).toBe('AI');
    expect(uploadTemplate).not.toHaveBeenCalled();
  });

  it.each(['timeout', 'forbidden', 'network'])(
    'keeps the default and uploads after a %s failure',
    async (reason) => {
      const queue = setup();
      vi.mocked(classifyTemplateType).mockRejectedValueOnce(new Error(reason));
      queue.addFiles([file()]);
      await settle();
      expect(queue.items.value[0]?.documentType).toBe('自由描述文书');
      expect(queue.items.value[0]?.typeWarning).toContain('可直接上传');
      await queue.start();
      expect(queue.completed.value).toBe(1);
      expect(vi.mocked(uploadTemplate).mock.calls[0]?.[1]).toBe('自由描述文书');
    },
  );

  it('uploads immediately without waiting, cancels the queue and ignores the late result', async () => {
    const queue = setup();
    let finish: ((value: Result) => void) | undefined;
    vi.mocked(classifyTemplateType).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    queue.addFiles([file(), file('模板02.docx')]);
    expect(queue.items.value[0]?.typeRecognizing).toBe(true);
    const signal = vi.mocked(classifyTemplateType).mock.calls[0]?.[1];
    await queue.start();
    expect(queue.completed.value).toBe(2);
    expect(signal?.aborted).toBe(true);
    expect(classifyTemplateType).toHaveBeenCalledTimes(1);
    if (!finish) throw new Error('Missing pending request');
    finish({ documentType: '律师函', warning: '' });
    await settle();
    expect(queue.items.value.map((row) => row.documentType)).toEqual([
      '自由描述文书',
      '自由描述文书',
    ]);
  });

  it('keeps a manual selection when an older AI request returns', async () => {
    const queue = setup();
    let finish: ((value: Result) => void) | undefined;
    vi.mocked(classifyTemplateType).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    queue.addFiles([file()]);
    const row = queue.items.value[0];
    if (!row || !finish) throw new Error('Missing queued file');
    queue.selectType(row.key, '合同文书');
    finish({ documentType: '律师函', warning: '' });
    await settle();
    expect(row.documentType).toBe('合同文书');
    expect(row.typeOrigin).toBe('manual');
    await queue.start();
    expect(vi.mocked(uploadTemplate).mock.calls[0]?.[1]).toBe('合同文书');
  });

  it.each([null, '不存在的类型'])(
    'retains the default for an unrecognized or stale suggestion %s',
    async (documentType) => {
      const queue = setup();
      vi.mocked(classifyTemplateType).mockResolvedValueOnce({
        documentType,
        warning: '',
      });
      queue.addFiles([file()]);
      await settle();
      expect(queue.items.value[0]?.documentType).toBe('自由描述文书');
      await queue.start();
      expect(queue.completed.value).toBe(1);
    },
  );

  it('does not run AI when disabled and discards a response from a closed queue', async () => {
    const queue = setup();
    queue.typeRecognitionEnabled.value = false;
    await nextTick();
    queue.addFiles([file()]);
    expect(classifyTemplateType).not.toHaveBeenCalled();
    let finish: ((value: Result) => void) | undefined;
    vi.mocked(classifyTemplateType).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    queue.typeRecognitionEnabled.value = true;
    await nextTick();
    queue.open.value = false;
    queue.open.value = true;
    if (!finish) throw new Error('Missing pending request');
    finish({ documentType: '律师函', warning: '' });
    await settle();
    expect(queue.items.value).toEqual([]);
  });
});
