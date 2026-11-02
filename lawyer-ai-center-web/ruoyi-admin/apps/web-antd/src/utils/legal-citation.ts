/** Alpha §16. Never infer a case number or a source from generated prose. */
export type LegalCitation =
  | { type: 'law'; lawName: string; articleNo: string; status?: string; sourceUrl?: string; fragmentId?: string }
  | { type: 'case'; caseNo: string; title?: string; court?: string; sourceUrl?: string; fragmentId?: string };
const text = (v: unknown): string => typeof v === 'string' ? v.trim() : '';
export function normalizeCitation(value: unknown): LegalCitation | null {
  if (!value || typeof value !== 'object') return null;
  const v = value as Record<string, unknown>;
  const common = { sourceUrl: text(v.sourceUrl ?? v.source_url), fragmentId: text(v.fragmentId ?? v.fragment_id) };
  const caseNo = text(v.caseNo ?? v.case_no);
  if (v.type === 'case' || caseNo) return caseNo ? { type: 'case', caseNo, title: text(v.title), court: text(v.court), ...common } : null;
  const lawName = text(v.lawName ?? v.law_name), articleNo = text(v.articleNo ?? v.article_no);
  if (!lawName || !articleNo) return null;
  const raw = text(v.status ?? v.validityStatus ?? v.validity_status);
  const status = ({ '有效': 'effective', '已失效': 'repealed', '已修订': 'amended', '已修改': 'amended' } as Record<string,string>)[raw] || raw;
  return { type: 'law', lawName, articleNo, status, ...common };
}
export function citationLabel(c: LegalCitation): string {
  if (c.type === 'case') return [c.caseNo, c.title, c.court].filter(Boolean).join(' · ');
  const article = /^第/.test(c.articleNo) ? c.articleNo : `第${c.articleNo.replace(/条$/, '')}条`;
  return `《${c.lawName.replace(/[《》]/g, '')}》${article}`;
}
export function citationWarning(c: LegalCitation): string {
  if (c.type !== 'law' || c.status === 'effective') return '';
  return c.status === 'repealed' ? '已失效' : c.status === 'amended' ? '已修改' : '时效待核实';
}
export function citationTarget(c: LegalCitation): { kind: 'fragment' | 'source' | 'missing'; value: string } {
  if (c.fragmentId) return { kind: 'fragment', value: c.fragmentId };
  if (c.sourceUrl && /^https?:\/\//i.test(c.sourceUrl)) return { kind: 'source', value: c.sourceUrl };
  return { kind: 'missing', value: '' };
}
/** Text responses embed a fenced `legal-citations` JSON array. Incomplete streams stay as text. */
export function splitLegalContent(content: string): { markdown: string; citations: LegalCitation[] } {
  const citations: LegalCitation[] = [];
  const markdown = content.replace(/```legal-citations\s*\r?\n([\s\S]*?)```/g, (block, json: string) => {
    try {
      const values: unknown = JSON.parse(json);
      if (!Array.isArray(values)) return block;
      const normalized = values.map(normalizeCitation);
      if (normalized.some(v => !v)) return block;
      citations.push(...normalized as LegalCitation[]);
      return '';
    } catch { return block; }
  });
  return { markdown, citations };
}
