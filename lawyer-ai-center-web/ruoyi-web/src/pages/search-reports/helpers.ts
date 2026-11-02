import type { LawStatus, SearchReportItem } from '@/api/legal-report/types';

export const itemTypeLabels = { case_full: '案例全文', case_section: '案例片段', law: '法规', viewpoint: '观点', custom_text: '自定义文本', chart: '图表说明' };
export const lawStatusLabels: Record<LawStatus, string> = { effective: '现行有效', amended: '已修改', repealed: '已失效', unknown: '时效待核实' };
export function lawStatusLabel(status?: LawStatus | null) { return lawStatusLabels[status || 'unknown'] || '时效待核实'; }
export function movedItemIds(items: SearchReportItem[], index: number, delta: -1 | 1): string[] {
  const ids = items.map(item => item.id), next = index + delta;
  if (index >= 0 && index < ids.length && next >= 0 && next < ids.length) [ids[index], ids[next]] = [ids[next], ids[index]];
  return ids;
}
// Session-only default selection, isolated by login token; backend still checks ownership.
let selected: { token: string; id: string } | undefined;
export function currentReport(token?: string) { return token && selected?.token === token ? selected.id : ''; }
export function selectReport(token: string | undefined, id: string) { selected = token && id ? { token, id } : undefined; }
