import type { DocTemplate } from '#/api/legal/doc-template';

export type TemplateProcessing =
  | 'confirmed'
  | 'error'
  | 'review'
  | 'running'
  | 'text'
  | 'unparsed';

export const processingOptions: {
  label: string;
  value: '' | TemplateProcessing;
}[] = [
  { label: '全部模板', value: '' },
  { label: '未解析', value: 'unparsed' },
  { label: '解析中', value: 'running' },
  { label: '待核对', value: 'review' },
  { label: '字段已确认', value: 'confirmed' },
  { label: '解析异常', value: 'error' },
  { label: '纯文本', value: 'text' },
];

/** Same precedence as the server's pre-pagination processing filter. */
export function templateProcessing(row: DocTemplate): TemplateProcessing {
  const state = row.fieldAnalysis?.state;
  if (state === 'running') return 'running';
  if (state === 'failed' || state === 'stale') return 'error';
  if (state === 'ready' || state === 'partial') return 'review';
  if (!row.sourceVersion) return 'text';
  if (row.parseStatus === 'uploaded') return 'unparsed';
  return row.fieldsConfirmed ? 'confirmed' : 'review';
}
