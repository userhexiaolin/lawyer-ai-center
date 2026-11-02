export type RiskLevel = 'high' | 'medium' | 'low' | 'unknown';
export interface ContractRisk {
  id: string | number;
  riskLevel: RiskLevel;
  clauseTitle: string;
  quote: string;
  location: { page?: number; paragraph?: number; positionText?: string };
  problem: string;
  lawRefs: unknown[];
  caseRefs: unknown[];
  qualityWarnings: string[];
  /** Read-only compatibility fields for existing source locators and historical clients. */
  level: RiskLevel;
  title: string;
  clause: string;
  position: string;
  analysis: string;
  suggestion: string;
  legalBasis: string[];
  citations: unknown[];
}
export interface ContractReviewResult {
  contractType: string;
  issues: ContractRisk[];
  summary: string;
  riskLevel: RiskLevel;
  basicInfo: { title: string; parties: string[]; signDate: string };
  risks: ContractRisk[];
  missingClauses: string[];
  overallSuggestion: string;
}
export const textModeMessage = '结构化解析失败，已切换为文本模式';
export const riskPresentation = {
  high: { label: '高风险', type: 'danger', order: 0 },
  medium: { label: '中风险', type: 'warning', order: 1 },
  low: { label: '低风险', type: 'primary', order: 2 },
  unknown: { label: '风险待确认', type: 'info', order: 3 },
} as const;
const record = (value: unknown): value is Record<string, unknown> => Boolean(value && typeof value === 'object' && !Array.isArray(value));
const text = (value: unknown) => typeof value === 'string' ? value.trim() : '';
function textList(value: unknown): string[] {
  if (typeof value === 'string') return value.trim() ? [value.trim()] : [];
  return Array.isArray(value) ? value.map(text).filter(Boolean) : [];
}
function level(value: unknown): RiskLevel {
  const normalized = text(value).toLowerCase();
  return normalized === 'high' || normalized === 'medium' || normalized === 'low' ? normalized : 'unknown';
}
function normalize(value: unknown): ContractReviewResult | null {
  if (!record(value) || !text(value.summary)) return null;
  // An explicitly supplied issues array is authoritative, including an empty array.
  const modern = 'issues' in value;
  const entries = modern ? value.issues : 'risks' in value ? value.risks : 'riskLevel' in value ? [] : null;
  if (!Array.isArray(entries)) return null;
  const risks: ContractRisk[] = [];
  const refs = (v: unknown): unknown[] => Array.isArray(v) ? v.filter(v => text(v) || record(v)) : textList(v);
  for (const [index, risk] of entries.entries()) {
    // Do not silently drop malformed risk entries and understate the review.
    if (!record(risk) || !text(modern ? risk.clauseTitle ?? risk.clause_title : risk.title)) return null;
    const riskLevel = level(modern ? risk.riskLevel ?? risk.risk_level : risk.level);
    const clauseTitle = text(modern ? risk.clauseTitle ?? risk.clause_title : risk.title);
    const quote = text(modern ? risk.quote : risk.clause);
    const problem = text(modern ? risk.problem : risk.analysis);
    const rawLocation = record(risk.location) ? risk.location : {};
    const location = {
      ...(Number.isInteger(rawLocation.page) && Number(rawLocation.page) > 0 ? { page: Number(rawLocation.page) } : {}),
      ...(Number.isInteger(rawLocation.paragraph) && Number(rawLocation.paragraph) > 0 ? { paragraph: Number(rawLocation.paragraph) } : {}),
      ...(text(rawLocation.positionText ?? rawLocation.position_text ?? (!modern ? risk.position : '')) ? { positionText: text(rawLocation.positionText ?? rawLocation.position_text ?? risk.position) } : {}),
    };
    const legacyRefs = modern ? [] : refs(risk.legalBasis);
    const lawRefs = modern ? refs(risk.lawRefs ?? risk.law_refs) : legacyRefs.filter(v => !record(v) || v.type !== 'case');
    const caseRefs = [...refs(risk.caseRefs ?? risk.case_refs), ...legacyRefs.filter(v => record(v) && v.type === 'case')];
    const citations = [...lawRefs.filter(record).map(v => ({ ...v, type: 'law', lawName: v.lawName ?? v.law_name ?? v.name, articleNo: v.articleNo ?? v.article_no ?? v.article, sourceUrl: v.sourceUrl ?? v.source_url ?? v.url })),
      ...caseRefs.filter(record).map(v => ({ ...v, type: 'case', caseNo: v.caseNo ?? v.case_no, sourceUrl: v.sourceUrl ?? v.source_url ?? v.url })),
      ...(Array.isArray(risk.citations) ? risk.citations : [])];
    risks.push({
      id: typeof risk.id === 'number' || typeof risk.id === 'string' ? risk.id : index + 1,
      riskLevel, clauseTitle, quote, location, problem, lawRefs, caseRefs, qualityWarnings: textList(risk.qualityWarnings),
      level: riskLevel, title: clauseTitle, clause: quote,
      position: [location.positionText, location.page ? `第${location.page}页` : '', location.paragraph ? `第${location.paragraph}段` : ''].filter(Boolean).join(' / '),
      analysis: problem, suggestion: text(risk.suggestion),
      legalBasis: textList(lawRefs), citations,
    });
  }
  const basic = record(value.basicInfo) ? value.basicInfo : {};
  risks.sort((a, b) => riskPresentation[a.riskLevel].order - riskPresentation[b.riskLevel].order);
  return {
    contractType: text(value.contractType ?? value.contract_type), issues: risks,
    summary: text(value.summary), riskLevel: 'riskLevel' in value ? level(value.riskLevel) : modern ? risks[0]?.riskLevel || 'unknown' : 'unknown',
    basicInfo: { title: text(basic.title), parties: textList(basic.parties), signDate: text(basic.signDate) },
    risks,
    missingClauses: textList(value.missingClauses), overallSuggestion: text(value.overallSuggestion),
  };
}

/** Remove only trailing delimiters outside strings. Never infer missing keys, quotes or brackets. */
function readJson(source: string): unknown {
  let clean = ''; let quoted = false; let escaped = false;
  for (let i = 0; i < source.length; i++) {
    const char = source[i];
    if (quoted) {
      clean += char;
      if (escaped) escaped = false;
      else if (char === '\\') escaped = true;
      else if (char === '"') quoted = false;
      continue;
    }
    if (char === '"') quoted = true;
    if (char === ',') {
      let next = i + 1;
      while (/\s/.test(source[next] || '') && next < source.length) next++;
      let previous = i - 1;
      while (previous >= 0 && /\s/.test(source[previous] || '')) previous--;
      if ((source[next] === ']' || source[next] === '}') && previous >= 0 && !'[{:,'.includes(source[previous] || '')) continue;
    }
    clean += char;
  }
  return JSON.parse(clean);
}

/** Accept complete JSON, fences, prose and trailing commas; preserve incomplete output for text mode. */
export function parseReviewResult(content: string): ContractReviewResult | null {
  if (typeof content !== 'string') return null;
  const source = content.trim();
  if (!source || source.length > 300000) return null;
  try { return normalize(readJson(source)); }
  catch { /* Look for complete outer objects, respecting braces inside JSON strings. */ }
  let start = -1;
  let depth = 0;
  let quoted = false;
  let escaped = false;
  const matches: ContractReviewResult[] = [];
  for (let i = 0; i < source.length; i++) {
    const char = source[i];
    if (depth === 0) {
      if (char === '{') { start = i; depth = 1; quoted = false; escaped = false; }
      continue;
    }
    if (quoted) {
      if (escaped) escaped = false;
      else if (char === '\\') escaped = true;
      else if (char === '"') quoted = false;
      continue;
    }
    if (char === '"') quoted = true;
    else if (char === '{') depth++;
    else if (char === '}' && --depth === 0) {
      try {
        const result = normalize(readJson(source.slice(start, i + 1)));
        if (result) matches.push(result);
      } catch { /* Preserve malformed output for the text fallback. */ }
    }
  }
  return matches.length === 1 && depth === 0 ? matches[0] : null;
}
