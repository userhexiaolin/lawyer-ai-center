import { normalizeCitation, citationLabel, citationWarning, type LegalCitation } from '@/utils/legalCitation';

export const INPUT_MARKER = '【法律意见书案情输入 v1】';
export const keys = ['timeline', 'issues', 'analysis', 'risks', 'strategy', 'references'] as const;
export const titles = ['案件事实时间线', '争议焦点', '法律分析', '风险与可行性', '策略建议', '引用清单'];
export interface OpinionInput { facts: string; focus: string; materialName: string; materialText: string }
export interface OpinionSection { key: string; title: string; content: string; citations: LegalCitation[] }
export interface OpinionDocument { title: string; sections: OpinionSection[] }
export function parseOpinion(raw: string): OpinionDocument {
  if (!raw || raw.length > 200000) throw new Error('意见书为空或内容过长');
  let root: any;
  try { root = JSON.parse(raw.trim().replace(/^```(?:json)?\s*\n([\s\S]*?)\n?```$/, '$1')); }
  catch { throw new Error('意见书不是完整 JSON，请重新生成'); }
  if (!root || !Array.isArray(root.sections) || root.sections.length !== 6) throw new Error('意见书必须完整包含六个章节');
  const sections = new Map<string, OpinionSection>(), all = new Map<string, LegalCitation>();
  for (const section of root.sections) {
    const index = keys.indexOf(section?.key);
    if (index < 0 || sections.has(section.key) || typeof section.content !== 'string' || !section.content.trim() || section.content.length > 60000) throw new Error('意见书章节缺失、重复或正文无效');
    if (section.citations !== undefined && (!Array.isArray(section.citations) || section.citations.length > 100)) throw new Error('引用清单必须是数组，且每章不超过100条');
    const citations: LegalCitation[] = (section.citations || []).map((value: any) => {
      const ref = normalizeCitation(value);
      if (!ref || value.type !== ref.type || (value.type === 'law' && (!value.lawName?.trim() || !value.articleNo?.trim())) || (value.type === 'case' && !value.caseNo?.trim()) || (value.sourceUrl && !/^https?:\/\/[^\s]+$/i.test(value.sourceUrl)) || Object.values(value).some(v => typeof v === 'string' && v.length > 2000)) throw new Error('引用元数据不完整或来源地址无效');
      all.set(JSON.stringify(ref), ref); return ref;
    });
    sections.set(section.key, { key: section.key, title: titles[index], content: section.content.trim(), citations });
  }
  const result = keys.map(key => sections.get(key)!);
  result[5].citations = [...all.values()];
  if (!all.size && !result[5].content.includes('建议进一步检索')) result[5].content += '\n\n知识库暂无匹配，建议进一步检索。';
  if (root.title !== undefined && typeof root.title !== 'string') throw new Error('意见书标题无效');
  if ((root.title || '').length > 200) throw new Error('意见书标题过长');
  return { title: root.title?.trim() || '法律意见书', sections: result };
}
export function opinionMarkdown(document: OpinionDocument): string {
  return `# ${document.title}\n\n` + document.sections.map((s, i) => `## ${i + 1}. ${s.title}\n\n${s.content}\n\n` + s.citations.map(r => `- ${citationLabel(r)}${citationWarning(r) ? `（${citationWarning(r)}）` : ''}${r.sourceUrl ? ` · 来源：${r.sourceUrl}` : ''}${r.fragmentId ? ` · 站内片段：${r.fragmentId}` : ''}`).join('\n')).join('\n\n');
}
export function validateInput(input: OpinionInput) {
  if (!input.facts.trim()) throw new Error('请输入案情');
  if (input.facts.length > 30000 || input.materialText.length > 30000 || input.focus.length > 2000) throw new Error('案情和材料各限30000字，分析重点限2000字');
}
export function inputMessage(input: OpinionInput, now = new Date()) {
  validateInput(input);
  const analysisDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
  return INPUT_MARKER + '\n' + JSON.stringify({ ...input, analysisDate, temporalRule: '以analysisDate为分析基准日，逐项比较履行期限；早于基准日的日期不得表述为尚未到期。用户另行指定历史分析日时应明确说明。' });
}
export function readInput(raw: string): OpinionInput {
  if (!raw.startsWith(INPUT_MARKER)) throw new Error('历史记录缺少案情快照');
  // Templates are appended after the original JSON snapshot by DocTemplateService.
  const input = JSON.parse(raw.slice(INPUT_MARKER.length).split('\n\n---律所模板开始---')[0]);
  for (const key of ['facts', 'focus', 'materialName', 'materialText']) if (typeof input[key] !== 'string') throw new Error('历史案情格式不完整');
  validateInput(input); return { facts: input.facts, focus: input.focus, materialName: input.materialName, materialText: input.materialText };
}
