import { useUserStore } from '@/stores';
import { extractContractText } from './location';

export interface ReviewHistory { id: string; sessionTitle: string; caseId?: string }
export async function reviewRequest<T>(path: string): Promise<T> {
  const user = useUserStore();const token=user.token;
  const response = await fetch(import.meta.env.VITE_API_URL + path, { headers: {
    Authorization: `Bearer ${user.token}`, clientid: import.meta.env.VITE_CLIENT_ID,
  } });
  const body = await response.json();
  if(user.token!==token)throw new Error('登录用户已变化，请重试');
  if (!response.ok || body.code !== 200) throw new Error(body.msg || '历史加载失败，请重试');
  return body;
}

export async function loadReviewHistory(pageNum = 1) {
  return reviewRequest<{ rows: ReviewHistory[]; total: number }>(`/system/session/list?reviewOnly=true&pageNum=${pageNum}&pageSize=20&orderByColumn=id&isAsc=desc`);
}

export async function loadReviewMessages(sessionId: string) {
  return (await loadReviewDetail(sessionId)).content;
}
export async function loadReviewDetail(sessionId: string) {
  if (!/^[1-9]\d*$/.test(sessionId)) throw new Error('会话ID无效');
  const response = await reviewRequest<{ rows: { role: string; content: string }[] }>(`/system/message/list?sessionId=${sessionId}&pageNum=1&pageSize=100&orderByColumn=id&isAsc=desc`);
  const result = response.rows.find(message => message.role === 'assistant');
  if (!result?.content) throw new Error('此会话尚无审查结果，请在对话中继续审查');
  const older = response.rows.slice(response.rows.indexOf(result) + 1);
  const source = older.filter(message => message.role === 'user').map(message => extractContractText(message.content)).find(Boolean) || '';
  const userContent = older.find(message => message.role === 'user' && extractContractText(message.content))?.content || '';
  let settings: { position?: string; focusClauses?: string[] } = {};
  try { settings = JSON.parse(userContent.match(/【审查设置 v1】([^\n]+)/)?.[1] || '{}'); } catch { /* Old history uses defaults. */ }
  return { content: result.content, sourceText: source, position: typeof settings.position === 'string' ? settings.position : '中立', focusClauses: Array.isArray(settings.focusClauses) ? settings.focusClauses.filter(v => typeof v === 'string') : [], fileName: userContent.match(/文件名：([^\n]+)/)?.[1] || '历史合同' };
}
