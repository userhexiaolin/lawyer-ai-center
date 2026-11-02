export interface DossierDocument { ossId: string; name: string; type: string; status: 'success'|'partial'|'failed'; characters: number; text: string; error: string }
export interface DossierBundle { caseTitle: string; documents: DossierDocument[]; totalCharacters: number; maxFiles: number; maxCharacters: number }
export interface DossierAnswer { question: string; answer: string; documents: Pick<DossierDocument,'name'|'status'|'characters'>[] }
const safe=(s:string)=>s.replace(/[<>]/g,c=>c==='<'?'&lt;':'&gt;');
export function dossierMinutes(title:string,answers:DossierAnswer[]) {
  if(!answers.length)throw Error('请先完成一次阅卷问答');
  return '# 阅卷纪要 · '+safe(title)+'\n\n本纪要由案件材料辅助生成，请人工核对来源、金额、日期和证据效力。\n\n'+answers.map((item,i)=>`## 问答${i+1}\n\n### 本次材料\n\n${item.documents.map(d=>`- ${safe(d.name)}：${d.status==='success'?'解析成功':d.status==='partial'?'部分解析，仅成功页纳入':'解析失败，未纳入'}，${d.characters}字`).join('\n')}\n\n### 问题\n\n${safe(item.question)}\n\n### 回答\n\n${item.answer}\n`).join('\n');
}
export function downloadMinutes(title:string,answers:DossierAnswer[]) {
  const url=URL.createObjectURL(new Blob([dossierMinutes(title,answers)],{type:'text/markdown;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download=(title.replace(/[\\/:*?"<>|]/g,'_').slice(0,80)||'案件')+'-阅卷纪要-'+new Date().toISOString().slice(0,10)+'.md';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
