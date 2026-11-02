import type { TemplateField } from '#/api/legal/doc-template';

import { describe, expect, it } from 'vitest';

import {
  templateFieldContext,
  templateFieldSource,
} from '../template-field-source';
import propertyParagraphs from './fixtures/property-paragraphs.json';

const field: TemplateField = {
  id: 'a',
  blockId: 'p0',
  start: 2,
  end: 6,
  original: '____',
  label: '金额',
  kind: 'money',
  unit: '元',
  presentation: 'underline',
  enabled: true,
};
describe('source grouping and integrity', () => {
  it('associates each property blank with its own unit and source clause without looking up coordinates', () => {
    const block = propertyParagraphs.find((b) => b.id === 'p4');
    if (!block) throw new Error('Missing property fixture');
    const contexts = block.fields.map((f) =>
      templateFieldContext(block.text, f as TemplateField),
    );
    expect(contexts.map((c) => c.unit)).toEqual([
      '年',
      '月',
      '日',
      '市',
      '路',
      '弄',
      '号',
      '室',
      '元',
      '',
      '年',
      '月',
      '年',
      '月',
    ]);
    expect(contexts.slice(0, 3).every((c) => c.before.includes('截至'))).toBe(
      true,
    );
    expect(contexts.slice(10).every((c) => c.before.includes('欠费期间'))).toBe(
      true,
    );
    expect(contexts[12]?.before).toContain('至');
    expect(contexts[9]?.before).toContain('大写');
  });
  it('keeps separate real locations even if their paragraphs and labels are identical', () => {
    const blocks = [
      { id: 'p0', text: '金额____元', editable: true },
      { id: 'p1', text: '金额____元', editable: true },
    ];
    const result = templateFieldSource(blocks, [
      field,
      { ...field, id: 'b', blockId: 'p1' },
    ]);
    expect(result.errors).toEqual([]);
    expect(result.groups).toHaveLength(2);
    expect(
      result.groups.map((g) => g.segments.map((s) => s.text).join('')),
    ).toEqual(blocks.map((b) => b.text));
  });
  it('reports duplicate spans, wrong fragments and missing paragraphs instead of quietly hiding them', () => {
    const blocks = [{ id: 'p0', text: '金额____元', editable: true }];
    expect(
      templateFieldSource(blocks, [field, { ...field, id: 'duplicate' }])
        .errors,
    ).toHaveLength(1);
    expect(
      templateFieldSource(blocks, [{ ...field, original: '___' }]).errors,
    ).toHaveLength(1);
    expect(
      templateFieldSource(blocks, [{ ...field, blockId: 'missing' }]).errors,
    ).toHaveLength(1);
    expect(
      templateFieldSource(blocks, [
        { ...field, start: 7, end: 7, original: '' },
      ]).errors,
    ).toHaveLength(1);
  });
});
