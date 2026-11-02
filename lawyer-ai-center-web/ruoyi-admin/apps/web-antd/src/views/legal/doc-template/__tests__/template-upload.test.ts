import type { EffectScope, Ref } from 'vue';

import { effectScope, ref } from 'vue';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  analyzeTemplateFields,
  parseTemplateVersion,
  saveTemplate,
  uploadTemplate,
} from '#/api/legal/doc-template';

import { useTemplateUpload } from '../use-template-upload';

vi.mock('#/api/legal/doc-template', () => ({
  parseTemplateVersion: vi.fn(),
  saveTemplate: vi.fn(),
  uploadTemplate: vi.fn(),
  analyzeTemplateFields: vi.fn(),
}));

describe('template upload confirmation', () => {
  let open: Ref<boolean>, saved: ReturnType<typeof vi.fn>, scope: EffectScope;
  let upload: ReturnType<typeof useTemplateUpload>;
  const options = [
    { value: '民事起诉状', label: '民事起诉状' },
    { value: '申请书', label: '申请书' },
    { value: '合同文书', label: '合同文书', disabled: true },
  ];
  const file = (name = '示例模板.md') => new File(['# {{原告}}'], name);

  beforeEach(() => {
    vi.mocked(uploadTemplate).mockReset();
    vi.mocked(saveTemplate).mockReset();
    vi.mocked(analyzeTemplateFields).mockReset();
    vi.mocked(uploadTemplate).mockImplementation(async (file, type) => ({
      name: file.name.replace(/\.[^.]+$/, ''),
      documentType: type,
      content: '# {{原告}}',
    }));
    vi.mocked(saveTemplate).mockResolvedValue('19');
    scope = effectScope();
    open = ref(false);
    saved = vi.fn();
    const initialized = scope.run(() =>
      useTemplateUpload(open, () => options, saved),
    );
    if (!initialized) throw new Error('Inactive test scope');
    upload = initialized;
    open.value = true;
    upload.parseOnUpload.value = true;
  });
  afterEach(() => scope.stop());
  it('keeps original text, requires field confirmation, and invalidates confirmation after edits', async () => {
    const field = {
      id: 'p1-2-8',
      blockId: 'p1',
      start: 2,
      end: 8,
      original: '______',
      label: '逾期本金',
      kind: 'money' as const,
      unit: '元',
      presentation: 'underline' as const,
      enabled: true,
    };
    vi.mocked(uploadTemplate).mockResolvedValueOnce({
      name: '原始模板',
      documentType: '民事起诉状',
      content: '本金______元',
      sourceVersion: 'v1',
      blocks: [
        { id: 'p1', text: '本金______元', editable: false, fields: [field] },
      ],
    });
    vi.mocked(analyzeTemplateFields).mockResolvedValueOnce({
      fields: [field],
      method: 'AI',
      warning: '待核对',
    });
    await upload.selectFile(file('原始模板.docx'));
    expect(analyzeTemplateFields).toHaveBeenCalledWith('v1');
    expect(upload.form.content).toBe('本金______元');
    upload.form.layoutConfirmed = false;
    await upload.submit();
    expect(saveTemplate).not.toHaveBeenCalled();
    upload.form.fieldsConfirmed = true;
    const target = upload.form.fields?.[0];
    if (!target) throw new Error('Missing field');
    target.label = '到期本金';
    expect(upload.form.fieldsConfirmed).toBe(false);
    upload.form.fieldsConfirmed = true;
    await upload.submit();
    expect(saveTemplate).toHaveBeenCalledWith(
      expect.objectContaining({
        content: '本金______元',
        fieldsConfirmed: true,
        editableBlocks: ['p1'],
      }),
    );
    expect(vi.mocked(saveTemplate).mock.calls[0]?.[0]).not.toHaveProperty(
      'layoutConfirmed',
    );
  });

  it('uploads and reads the filename without creating a template before confirmation', async () => {
    await upload.selectFile(file('律师函.docx'));
    expect(upload.form.name).toBe('律师函');
    expect(upload.fileName.value).toBe('律师函.docx');
    expect(upload.ready.value).toBe(true);
    expect(saveTemplate).not.toHaveBeenCalled();
    expect(saved).not.toHaveBeenCalled();
  });
  it('selects every mapped paragraph and preserves manual selection through field edits and reanalysis', async () => {
    const field = {
      id: 'p1-2-5',
      blockId: 'p1',
      start: 2,
      end: 5,
      original: '___',
      label: '本金',
      kind: 'money' as const,
      unit: '元',
      presentation: 'underline' as const,
      enabled: true,
    };
    vi.mocked(uploadTemplate).mockResolvedValueOnce({
      name: '原件',
      documentType: '民事起诉状',
      content: '标题\n本金___元\n签名___',
      sourceVersion: 'mapped',
      blocks: [
        { id: 'p0', text: '标题', editable: false, fields: [] },
        { id: 'p1', text: '本金___元', editable: false, fields: [field] },
        { id: 'p2', text: '签名___', editable: false, fields: [] },
      ],
    });
    vi.mocked(analyzeTemplateFields).mockResolvedValue({
      fields: [field],
      method: 'AI',
      warning: '待核对',
    });
    await upload.selectFile(file('原件.docx'));
    expect(upload.form.editableBlocks).toEqual(['p0', 'p1', 'p2']);
    upload.form.editableBlocks = ['p1'];
    const target = upload.form.fields?.[0];
    if (!target) throw new Error('Missing field');
    target.label = '逾期本金';
    expect(upload.form.editableBlocks).toEqual(['p1']);
    vi.mocked(parseTemplateVersion).mockResolvedValueOnce({
      name: '原件',
      documentType: '民事起诉状',
      content: upload.form.content,
      sortOrder: 0,
      status: '1',
      remark: '',
      sourceVersion: 'mapped-next',
      blocks: upload.form.blocks,
    });
    await upload.analyzeFields();
    expect(upload.form.editableBlocks).toEqual(['p1']);
    upload.selectAllBlocks();
    expect(upload.form.editableBlocks).toEqual(['p0', 'p1', 'p2']);
    upload.form.fieldsConfirmed = true;
    upload.form.editableBlocks = ['p1'];
    expect(upload.form.fieldsConfirmed).toBe(false);
  });
  it('requires writable regions and one content confirmation without requiring PDF preview', async () => {
    vi.mocked(uploadTemplate).mockResolvedValueOnce({
      name: '律师函',
      documentType: '民事起诉状',
      content: '正文',
      sourceVersion: 'version-1',
      sourceFormat: 'docx',
      blocks: [
        { id: 'p0', text: '固定标题', editable: false },
        { id: 'p1', text: '正文', editable: false },
      ],
    });
    await upload.selectFile(file('律师函.docx'));
    expect(upload.form.editableBlocks).toEqual(['p0', 'p1']);
    upload.form.editableBlocks = [];
    await upload.submit();
    expect(saveTemplate).not.toHaveBeenCalled();
    upload.form.editableBlocks = ['p1'];
    await upload.submit();
    expect(saveTemplate).not.toHaveBeenCalled();
    upload.form.fieldsConfirmed = true;
    await upload.submit();
    expect(saveTemplate).toHaveBeenCalledWith(
      expect.objectContaining({
        sourceVersion: 'version-1',
        editableBlocks: ['p1'],
        fieldsConfirmed: true,
      }),
    );
    expect(vi.mocked(saveTemplate).mock.calls[0]?.[0]).not.toHaveProperty(
      'layoutConfirmed',
    );
  });

  it('selects all paragraphs by default and restores all after a manual partial selection', async () => {
    vi.mocked(uploadTemplate).mockResolvedValueOnce({
      name: '律师函',
      documentType: '民事起诉状',
      content: '标题\n正文',
      sourceVersion: 'version-1',
      blocks: [
        { id: 'p0', text: '标题', editable: false },
        { id: 'p1', text: '正文', editable: false },
      ],
    });
    await upload.selectFile(file('律师函.docx'));
    expect(upload.form.editableBlocks).toEqual(['p0', 'p1']);
    upload.form.editableBlocks = ['p1'];
    upload.selectAllBlocks();
    expect(upload.form.editableBlocks).toEqual(['p0', 'p1']);
    upload.form.editableBlocks = [];
    upload.saving.value = true;
    upload.selectAllBlocks();
    expect(upload.form.editableBlocks).toEqual([]);
    upload.saving.value = false;
    open.value = false;
    upload.selectAllBlocks();
    expect(upload.form.editableBlocks).toEqual([]);
  });

  it('confirmation creates one template using the edited name and all chosen metadata', async () => {
    await upload.selectFile(file());
    Object.assign(upload.form, {
      name: '  我的申请书  ',
      documentType: '申请书',
      sortOrder: 37,
      status: '1',
      remark: '测试备注',
    });
    await Promise.all([upload.submit(), upload.submit()]);
    expect(saveTemplate).toHaveBeenCalledTimes(1);
    expect(saveTemplate).toHaveBeenCalledWith(
      expect.objectContaining({
        name: '我的申请书',
        documentType: '申请书',
        sortOrder: 37,
        status: '1',
        remark: '测试备注',
        content: '# {{原告}}',
      }),
    );
    expect(open.value).toBe(false);
    expect(saved).toHaveBeenCalledTimes(1);
  });

  it('replacing a file updates an automatic name and preserves a manually edited name', async () => {
    await upload.selectFile(file('第一版.md'));
    await upload.selectFile(file('第二版.md'));
    expect(upload.form.name).toBe('第二版');
    upload.form.name = '自定义名称';
    await upload.selectFile(file('第三版.md'));
    expect(upload.form.name).toBe('自定义名称');
    expect(upload.fileName.value).toBe('第三版.md');
  });

  it('cancelling an in-flight upload discards its late response even after reopening', async () => {
    let finish!: (value: Awaited<ReturnType<typeof uploadTemplate>>) => void;
    vi.mocked(uploadTemplate).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    const pending = upload.selectFile(file('旧文件.md'));
    expect(upload.uploading.value).toBe(true);
    open.value = false;
    open.value = true;
    finish({ name: '旧文件', documentType: '民事起诉状', content: '旧正文' });
    await pending;
    expect(upload.form.name).toBe('');
    expect(upload.form.content).toBe('');
    expect(upload.fileName.value).toBe('');
    expect(upload.ready.value).toBe(false);
    expect(upload.uploading.value).toBe(false);
    expect(saveTemplate).not.toHaveBeenCalled();
  });

  it('cannot confirm until the file has finished parsing', async () => {
    await upload.submit();
    expect(saveTemplate).not.toHaveBeenCalled();
    let finish!: (value: Awaited<ReturnType<typeof uploadTemplate>>) => void;
    vi.mocked(uploadTemplate).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    const pending = upload.selectFile(file());
    await upload.submit();
    expect(saveTemplate).not.toHaveBeenCalled();
    finish({ name: '示例模板', documentType: '民事起诉状', content: '# 正文' });
    await pending;
    expect(upload.ready.value).toBe(true);
  });

  it('a failed replacement cannot accidentally save the previously uploaded file', async () => {
    await upload.selectFile(file('已成功.md'));
    vi.mocked(uploadTemplate).mockRejectedValueOnce(new Error('文件损坏'));
    await upload.selectFile(file('损坏.docx'));
    expect(upload.error.value).toBe('文件损坏');
    expect(upload.ready.value).toBe(false);
    expect(upload.form.content).toBe('');
    await upload.submit();
    expect(saveTemplate).not.toHaveBeenCalled();
  });

  it('rejects unsupported, empty and oversized files before sending them', async () => {
    await upload.selectFile(file('模板.exe'));
    await upload.selectFile(new File([], '空.md'));
    const large = file();
    Object.defineProperty(large, 'size', { value: 10 * 1024 * 1024 + 1 });
    await upload.selectFile(large);
    expect(uploadTemplate).not.toHaveBeenCalled();
    expect(upload.ready.value).toBe(false);
  });

  it('validates name, enabled type and manual sort order before saving', async () => {
    await upload.selectFile(file());
    upload.form.name = ' ';
    await upload.submit();
    upload.form.name = '有效名称';
    upload.form.documentType = '合同文书';
    await upload.submit();
    upload.form.documentType = '申请书';
    upload.form.sortOrder = -1;
    await upload.submit();
    upload.form.sortOrder = 1.5;
    await upload.submit();
    expect(saveTemplate).not.toHaveBeenCalled();
    upload.form.sortOrder = 5;
    await upload.submit();
    expect(saveTemplate).toHaveBeenCalledTimes(1);
  });

  it('a failed confirmation keeps the form and permits retry', async () => {
    await upload.selectFile(file());
    upload.form.remark = '保留备注';
    vi.mocked(saveTemplate).mockRejectedValueOnce(new Error('保存失败'));
    await upload.submit();
    expect(open.value).toBe(true);
    expect(upload.saving.value).toBe(false);
    expect(upload.form.remark).toBe('保留备注');
    expect(upload.ready.value).toBe(true);
    await upload.submit();
    expect(saved).toHaveBeenCalledTimes(1);
  });

  it('removing a file clears only the automatically generated name', async () => {
    await upload.selectFile(file());
    upload.removeFile();
    expect(upload.form.name).toBe('');
    expect(upload.ready.value).toBe(false);
    await upload.selectFile(file());
    upload.form.name = '手动名称';
    upload.removeFile();
    expect(upload.form.name).toBe('手动名称');
    expect(upload.ready.value).toBe(false);
  });
});
