import type { LegalCitation } from '@/utils/legalCitation';
export interface Elements { causeHints: string[]; keywords: string[]; disputes: string[]; parties: string[]; lawsHints: string[] }
export interface Hit { id: string; knowledgeId: string; sourceName?: string; content: string; truncated: boolean; reference?: LegalCitation; legalMetadata?: { lawName?: string; articleNo?: string; region?: string; validityStatus?: string; publishDate?: string } }
export interface SearchInput { text: string; knowledgeIds: string[]; type: 'all' | 'law' | 'case' | 'other'; status: 'all' | 'effective' | 'amended' | 'repealed' | 'unknown'; keyword?: string; dateFrom?: string; dateTo?: string; region?: string; topic?: string }
export interface SearchResult { sessionId: string; elements: Elements; extractionStatus: 'model' | 'fallback'; retrievalMode: 'hybrid' | 'keyword'; query: string; attemptedLibraries: number; hits: Hit[]; warnings: string[]; message: string }
export interface SearchState { busy: boolean; progress: string; error: string; sessionId: string; fingerprint: string; result: SearchResult | null }
export const initialSearchState = (): SearchState => ({ busy: false, progress: '', error: '', sessionId: '', fingerprint: '', result: null });
export function validateResult(value: SearchResult): SearchResult {
  if (!value || !Array.isArray(value.hits) || !Array.isArray(value.warnings) || !value.elements || !['model', 'fallback'].includes(value.extractionStatus)) throw new Error('检索结果格式不完整，请重试');
  for (const key of ['causeHints', 'keywords', 'disputes', 'parties', 'lawsHints'] as const) if (!Array.isArray(value.elements[key]) || value.elements[key].some(s => typeof s !== 'string')) throw new Error('要素摘要格式不完整，请重试');
  if (value.hits.some(h => !h || typeof h.id !== 'string' || typeof h.content !== 'string')) throw new Error('知识库片段格式不完整，请重试');
  return value;
}
export function createSearchWorkflow(state: SearchState, api: { create: (title: string) => Promise<unknown>; search: (input: SearchInput & { sessionId: string }, signal: AbortSignal) => Promise<SearchResult> }) {
  let epoch = 0, disposed = false, controller: AbortController | undefined;
  async function search(input: SearchInput) {
    if (disposed || state.busy) return;
    const version = ++epoch, active = () => !disposed && epoch === version;
    state.error = ''; state.result = null; state.busy = true;
    controller = new AbortController();
    try {
      if (!input.text.trim() || input.text.length > 12000) throw new Error('请输入不超过12000字的案情');
      if (input.knowledgeIds.length > 4) throw new Error('最多选择4个知识库');
      if (input.dateFrom && input.dateTo && input.dateFrom > input.dateTo) throw new Error('开始日期不能晚于结束日期');
      const fingerprint = JSON.stringify(input);
      if (!state.sessionId || state.fingerprint !== fingerprint) {
        state.progress = '正在创建检索会话';
        const id = await api.create('案情检索 - ' + input.text.trim().slice(0, 60));
        if (!active()) return;
        if ((typeof id === 'number' && !Number.isSafeInteger(id)) || !/^[1-9]\d*$/.test(String(id))) throw new Error('未能创建检索会话，请重试');
        state.sessionId = String(id); state.fingerprint = fingerprint;
      }
      state.progress = '正在抽取要素并检索知识库，抽取失败将自动使用关键词检索';
      const result = await api.search({ ...input, sessionId: state.sessionId }, controller.signal);
      if (!active()) return;
      state.result = validateResult(result); state.progress = '检索完成，结果已保存至会话';
    } catch (e) { if (active()) { state.error = e instanceof Error ? e.message : '检索失败，请重试'; state.progress = '检索未完成'; } }
    finally { if (active()) state.busy = false; }
  }
  function reset() { epoch++; controller?.abort(); Object.assign(state, initialSearchState()); }
  function dispose() { reset(); disposed = true; }
  return { search, reset, dispose };
}
