import type { DocTemplate, TemplateField } from '#/api/legal/doc-template';

interface Segment {
  field?: TemplateField;
  number?: number;
  text: string;
}

/** A short, source-derived association; other blanks become ellipses in this preview only. */
export function templateFieldContext(text: string, field: TemplateField) {
  const left = text.slice(0, field.start);
  const boundary = Math.max(
    ...['，', '。', '；', ';', '\n'].map((separator) =>
      left.lastIndexOf(separator),
    ),
  );
  const before = left
    .slice(boundary + 1)
    .replaceAll(/\{\{[^{}]+\}\}|[_＿]+|\s{2,}/gu, '…')
    .slice(-24);
  const right = text.slice(field.end);
  const after = right.match(/^[^_＿\s，。；;]{0,12}/u)?.[0] || '';
  const unit = after.match(/^[年月日元%市路弄号室]/u)?.[0] || '';
  return {
    before,
    blank: field.original || '〔空白表格单元格〕',
    unit,
    after: after.slice(unit.length),
  };
}

/** Group by source location, never by repeated paragraph text or field name. */
export function templateFieldSource(
  blocks: NonNullable<DocTemplate['blocks']>,
  fields: TemplateField[],
) {
  const errors: string[] = [];
  const groups = blocks.flatMap((block) => {
    const items = fields
      .filter((field) => field.blockId === block.id)
      .toSorted((a, b) => a.start - b.start || a.end - b.end);
    if (items.length === 0) return [];
    const segments: Segment[] = [];
    const seen = new Set<string>();
    let position = 0;
    for (const [index, field] of items.entries()) {
      const key = `${field.start}:${field.end}`;
      if (
        seen.has(key) ||
        field.start < position ||
        field.end > block.text.length ||
        field.end < field.start ||
        field.start < 0 ||
        (field.start === field.end && block.text !== '') ||
        block.text.slice(field.start, field.end) !== field.original
      ) {
        errors.push(`${field.id}：位置重复、重叠或与原文不一致`);
        continue;
      }
      seen.add(key);
      if (field.start > position)
        segments.push({ text: block.text.slice(position, field.start) });
      segments.push({ text: field.original, field, number: index + 1 });
      position = field.end;
    }
    if (position < block.text.length)
      segments.push({ text: block.text.slice(position) });
    return [{ blockId: block.id, text: block.text, fields: items, segments }];
  });
  for (const field of fields)
    if (!blocks.some((block) => block.id === field.blockId)) {
      errors.push(`${field.id}：原段落不存在，请重新识别`);
    }
  return { groups, errors };
}
