import { parseReviewResult } from './result';
const cell = (value: unknown): string => String(value ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\|/g, '&#124;').replace(/\r?\n/g, '<br>');
export function reviewFilename(name: string, kind: string, extension: string, now = new Date()) {
  return `${name.replace(/\.[^.]+$/, '').replace(/[\\/:*?"<>|\x00-\x1f]/g, '_').slice(0,80) || '合同'}-${kind}-${new Intl.DateTimeFormat('sv-SE', {timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(now)}.${extension}`;
}
export function revisionMarkdown(content: string, name: string, position: string, focus: string[]) {
  const result = parseReviewResult(content); if (!result) throw new Error('审查结果尚不能导出结构化对照');
  const ref = (v: unknown) => typeof v === 'string' ? v : v && typeof v === 'object' ? Object.entries(v).filter(([k])=>['lawName','law_name','articleNo','article_no','caseNo','case_no','status'].includes(k)).map(([,x])=>x).join(' ') : '';
  return `# ${cell(name)} · 修订对照\n\n审查立场：${cell(position)}；重点范围：${cell(focus.join('、') || '全文')}\n\n${cell(result.summary)}\n\n| 条款位置 | 原文摘录 | 问题 | 建议修改表述 | 依据（需核对） |\n| --- | --- | --- | --- | --- |\n` + result.issues.map(r => `| ${cell(r.position || r.clauseTitle)} | ${cell(r.quote || '未提供摘录')} | ${cell(r.problem)} | ${cell(r.suggestion || '待人工补充')} | ${cell([...r.lawRefs,...r.caseRefs].map(ref).join('；') || '未提供依据')} |`).join('\n') + '\n\n本文件为审查草稿，修改前请结合原文与权威依据复核。\n';
}
export function downloadRevision(content: string, name: string, position: string, focus: string[]) {
  const url=URL.createObjectURL(new Blob([revisionMarkdown(content,name,position,focus)],{type:'text/markdown;charset=utf-8'}));
  const a=document.createElement('a');a.href=url;a.download=reviewFilename(name,'修订对照','md');a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
