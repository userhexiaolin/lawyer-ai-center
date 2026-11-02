import type { TemplateField } from '#/api/legal/doc-template';

import { effectScope, ref } from 'vue';

import { describe, expect, it } from 'vitest';

import { useTemplateFields } from '../use-template-fields';

function setup(texts: string[], existing: TemplateField[] = []) {
  const scope = effectScope();
  const fields = ref<TemplateField[]>(existing);
  const disabled = ref(false);
  const blocks = texts.map((text, i) => ({
    id: `p${i}`,
    text,
    editable: true,
  }));
  const editor = scope.run(() =>
    useTemplateFields(
      fields,
      () => blocks,
      () => disabled.value,
    ),
  );
  if (!editor) throw new Error('Inactive test scope');
  return { scope, fields, disabled, blocks, editor };
}

describe('continuous template field marking', () => {
  it('adds fifteen fields consecutively across paragraphs, preserves all original text and supports undo', () => {
    const texts = Array.from(
      { length: 3 },
      () => '姓名___本金____元利息___元起日___截止日___',
    );
    const { scope, fields, blocks, editor } = setup(texts);
    expect(editor.available.value).toHaveLength(15);
    for (let i = 0; i < 15; i++) {
      editor.add();
      expect(editor.error.value).toBe('');
      expect(fields.value).toHaveLength(i + 1);
      expect(editor.addedCount.value).toBe(i + 1);
    }
    expect(new Set(fields.value.map((f) => f.id)).size).toBe(15);
    expect(editor.available.value).toHaveLength(0);
    expect(blocks.map((b) => b.text)).toEqual(texts);
    expect(fields.value.filter((f) => f.unit === '元')).toHaveLength(6);
    expect(fields.value.every((f) => f.presentation === 'underline')).toBe(
      true,
    );
    editor.undo();
    expect(fields.value).toHaveLength(14);
    expect(editor.available.value).toHaveLength(1);
    editor.add();
    expect(fields.value).toHaveLength(15);
    scope.stop();
  });

  it('keeps configured, including disabled, positions out of candidates and explains duplicate selection', () => {
    const existing: TemplateField = {
      id: 'old',
      blockId: 'p0',
      start: 2,
      end: 5,
      original: '___',
      label: '本金',
      kind: 'money',
      unit: '元',
      presentation: 'underline',
      enabled: false,
    };
    const { scope, fields, editor } = setup(['本金___元利息___元'], [existing]);
    expect(editor.available.value).toHaveLength(1);
    expect(editor.start.value).toBe(8);
    editor.start.value = 2;
    editor.end.value = 5;
    editor.add();
    expect(fields.value).toHaveLength(1);
    expect(editor.error.value).toContain('已有字段');
    const next = editor.available.value[0];
    if (!next) throw new Error('Missing blank candidate');
    editor.rangeKey.value = next.key;
    editor.add();
    expect(editor.error.value).toBe('');
    expect(fields.value).toHaveLength(2);
    scope.stop();
  });

  it('rejects fixed text, fixed units, overlap and invalid offsets without losing configured fields', () => {
    const { scope, fields, editor } = setup(['本金______元']);
    editor.start.value = 0;
    editor.end.value = 3;
    editor.add();
    expect(editor.error.value).toContain('固定文字');
    editor.start.value = 2;
    editor.end.value = 9;
    editor.add();
    expect(editor.error.value).toContain('单位');
    editor.start.value = 2;
    editor.end.value = 5;
    editor.add();
    expect(fields.value).toHaveLength(1);
    expect(editor.available.value[0]).toMatchObject({ start: 5, end: 8 });
    editor.start.value = 4;
    editor.end.value = 8;
    editor.add();
    expect(editor.error.value).toContain('重叠');
    editor.start.value = -1;
    editor.add();
    expect(editor.error.value).toContain('起止位置');
    expect(fields.value).toHaveLength(1);
    scope.stop();
  });

  it('supports real empty table cells but never invents positions after colon endings', () => {
    const { scope, fields, disabled, editor } = setup([
      '',
      '姓名：',
      '________先生/女士：',
    ]);
    disabled.value = true;
    editor.add();
    expect(fields.value).toHaveLength(0);
    disabled.value = false;
    editor.add();
    editor.add();
    expect(fields.value).toHaveLength(2);
    expect(editor.available.value).toHaveLength(0);
    disabled.value = true;
    editor.undo();
    expect(fields.value).toHaveLength(2);
    disabled.value = false;
    editor.blockId.value = 'p2';
    editor.start.value = 14;
    editor.end.value = 14;
    editor.add();
    expect(editor.error.value).toContain('原有空白');
    expect(fields.value).toHaveLength(2);
    scope.stop();
  });
});
