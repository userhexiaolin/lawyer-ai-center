import type { DocumentDraft } from '@/api/document';
import type { LawyerCase } from '@/api/lawyer';

export function generalCaseFields(value: LawyerCase) {
  const parties = value.clientName?.trim() || value.oppositeParty?.trim()
    ? `本方：${value.clientName?.trim() || '待补充'}\n对方：${value.oppositeParty?.trim() || '待补充'}`
    : '';
  return {
    parties,
    facts: [value.caseType?.trim(), value.description?.trim(), value.court?.trim() ? `法院：${value.court.trim()}` : ''].filter(Boolean).join('\n'),
  };
}
export function litigationCaseFields(value: LawyerCase, type: string) {
  const complaint = type === '民事起诉状';
  return {
    plaintiff: (complaint ? value.clientName : value.oppositeParty)?.trim() || '',
    defendant: (complaint ? value.oppositeParty : value.clientName)?.trim() || '',
    facts: value.description?.trim() || '',
    court: value.court?.trim() || '',
  };
}
/** Only source-backed, non-empty fields participate; unrelated fields are never touched. */
export function mergeCaseFields<K extends string>(target: Record<K, string>, incoming: Partial<Record<K, string>>, replace = false) {
  let applied = 0;
  let kept = 0;
  for (const key of Object.keys(incoming) as K[]) {
    const value = incoming[key];
    if (typeof value !== 'string' || !value.trim())
      continue;
    if (!replace && target[key].trim()) {
      kept++;
    }
    else if (target[key] !== value) {
      target[key] = value;
      applied++;
    }
  }
  return { applied, kept };
}

interface DraftSession { id: string; sessionTitle?: string; caseId?: string }
interface RecoveryClient {
  request: <T>(path: string) => Promise<T>;
  load: (id: string) => Promise<DocumentDraft>;
}
export async function loadDraftSnapshot(type: string, requestedId: string, client: RecoveryClient, current: () => boolean = () => true) {
  let id = requestedId;
  if (!id) {
    const query = new URLSearchParams({ sessionTitle: `文书起草 - ${type}`, pageNum: '1', pageSize: '100', orderByColumn: 'id', isAsc: 'desc' });
    const result = await client.request<{ rows: DraftSession[] }>(`/system/session/list?${query}`);
    if (!current())
      throw new Error('起草上下文已变化，请重新恢复');
    id = result.rows.find(row => row.sessionTitle === `文书起草 - ${type}`)?.id || '';
  }
  if (!id)
    throw new Error('最近100条匹配会话中暂无同类型草稿，请先生成');
  if (!current())
    throw new Error('起草上下文已变化，请重新恢复');
  const [detail, draft] = await Promise.all([
    client.request<{ data: DraftSession }>(`/system/session/${encodeURIComponent(id)}`),
    client.load(id),
  ]);
  if (!current())
    throw new Error('起草上下文已变化，请重新恢复');
  if ((detail.data.id && detail.data.id !== id) || (draft.sessionId && draft.sessionId !== id))
    throw new Error('返回的草稿与所选会话不一致，请重新加载');
  return { session: { ...detail.data, id }, draft };
}
