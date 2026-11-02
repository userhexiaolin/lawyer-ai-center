export interface PersonalNotice { id: string; title: string; content: string; caseId?: string | null; createdAt: string; readAt?: string | null }
export interface InboxPage { items: PersonalNotice[]; nextId?: string | null; unread: number }
export const initialInboxState = () => ({ items: [] as PersonalNotice[], nextId: null as string | null, unread: 0, busy: false, reading: [] as string[], error: '' });
export type InboxState = ReturnType<typeof initialInboxState>;
export interface InboxApi { list(beforeId?: string): Promise<InboxPage>; read(id: string): Promise<void> }
const id = (value: unknown) => typeof value === 'string' && /^\d{1,20}$/.test(value);
function validate(page: InboxPage) {
  if (!page || !Array.isArray(page.items) || !Number.isSafeInteger(page.unread) || page.unread < 0 || (page.nextId != null && !id(page.nextId)) || page.items.some(n => !n || !id(n.id) || typeof n.title !== 'string' || typeof n.content !== 'string' || typeof n.createdAt !== 'string' || (n.caseId != null && !id(n.caseId)))) throw new Error('通知数据异常，请稍后重试');
  return page;
}
export function createInboxWorkflow(state: InboxState, api: InboxApi) {
  let version = 0;
  async function load(more = false) {
    if (state.busy || state.reading.length || (more && !state.nextId)) return;
    const current = version, cursor = more ? state.nextId! : undefined; state.busy = true; state.error = '';
    try {
      const page = validate(await api.list(cursor)); if (current !== version) return;
      state.items = more ? [...new Map([...state.items, ...page.items].map(n => [n.id, n])).values()] : page.items;
      state.nextId = page.nextId ?? null; state.unread = page.unread;
    } catch (e) { if (current === version) state.error = (e as Error).message || '通知加载失败'; }
    finally { if (current === version) state.busy = false; }
  }
  async function read(notice: PersonalNotice) {
    if (state.busy || notice.readAt || state.reading.includes(notice.id)) return;
    const current = version; state.reading.push(notice.id); state.error = '';
    try {
      await api.read(notice.id); if (current !== version) return;
      const row = state.items.find(n => n.id === notice.id);
      if (row && !row.readAt) { row.readAt = new Date().toISOString(); state.unread = Math.max(0, state.unread - 1); }
    } catch (e) { if (current === version) state.error = (e as Error).message || '已读状态保存失败'; }
    finally { if (current === version) state.reading = state.reading.filter(n => n !== notice.id); }
  }
  function reset() { version++; Object.assign(state, initialInboxState()); }
  return { load, read, reset };
}
