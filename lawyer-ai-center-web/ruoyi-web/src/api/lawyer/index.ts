import { useUserStore } from '@/stores';
import {checkAuthExpiry} from '@/utils/authExpiry';
export interface LawyerCase {
  id: string; title: string; caseType?: string; clientName?: string; oppositeParty?: string;
  status: '进行中' | '已结案' | '中止'; court?: string; filingDate?: string | null;
  nextHearingDate?: string | null; limitationDate?: string | null; description?: string;
}
export type CaseForm = Omit<LawyerCase, 'id'> & { id?: string };
export interface CaseSession { id: string; sessionTitle?: string; caseId?: string }
export interface CaseFile { ossId: string; originalName?: string; fileName?: string; fileSuffix?: string; url?: string | null; uploadedAt?: string | null; linkedAt?: string | null; downloadUrl?: string }
export interface CaseDetail { caseInfo: LawyerCase; sessions: CaseSession[]; files: CaseFile[] }
export interface CasePage { code: number; rows: LawyerCase[]; total: number }
export async function lawyerRequest<T>(path: string, method = 'GET', data?: unknown, timeoutMs = 65000): Promise<T> {
  const user = useUserStore(); const token = user.token;
  if (!token) throw new Error('请先登录');
  const response = await fetch(import.meta.env.VITE_API_URL + path, { method, headers: { Authorization: `Bearer ${token}`, clientid: import.meta.env.VITE_CLIENT_ID, 'content-type': 'application/json' }, body: data === undefined ? undefined : JSON.stringify(data), signal: AbortSignal.timeout(timeoutMs) });
  if(user.token!==token)throw Error('登录用户已变化，请重试');
  checkAuthExpiry(response.status);
  const body = await response.json();
  if (user.token !== token) throw new Error('登录用户已变化，请重试');
  checkAuthExpiry(response.status,body);
  if (!response.ok || body.code !== 200) throw new Error(body.msg || '操作失败，请重试');
  return body;
}
export function caseList(params: Record<string, string | number | undefined> = {}) {
  const query = new URLSearchParams(); Object.entries(params).forEach(([key,value]) => { if (value != null && value !== '') query.set(key,String(value)); });
  return lawyerRequest<CasePage>('/lawyer/case/list?' + query);
}
export function caseDetail(id: string) { return lawyerRequest<{ data: CaseDetail }>('/lawyer/case/' + id); }
export function saveCase(input: CaseForm) { return lawyerRequest<{ data: string }>('/lawyer/case', input.id ? 'PUT' : 'POST', input); }
export const emptyCase = (): CaseForm => ({ title: '', caseType: '', clientName: '', oppositeParty: '', status: '进行中', court: '', filingDate: null, nextHearingDate: null, limitationDate: null, description: '' });
