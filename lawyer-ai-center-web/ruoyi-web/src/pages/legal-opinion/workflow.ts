import { decodeReviewEvents, sessionIdFrom } from '@/pages/contract-review/workflow';
import { inputMessage, parseOpinion, type OpinionDocument, type OpinionInput } from './document';

export interface OpinionState { busy: boolean; progress: string; error: string; raw: string; document: OpinionDocument | null; sessionId: string; fingerprint: string }
export const initialOpinionState = (): OpinionState => ({ busy: false, progress: '', error: '', raw: '', document: null, sessionId: '', fingerprint: '' });
export interface OpinionDependencies {
  agent: () => Promise<{ id: string | number; modelName?: string }>;
  create: (title: string) => Promise<unknown>;
  stream: (payload: { model: string; agentId: string | number; sessionId: string; content: string; enableThinking: boolean }) => AsyncIterable<unknown>;
  cancel: () => void;
  decorate?: (content:string) => Promise<string>;
}
export function createOpinionWorkflow(state: OpinionState, deps: OpinionDependencies) {
  let epoch = 0, disposed = false;
  async function generate(input: OpinionInput) {
    if (disposed || state.busy) return;
    const version = ++epoch, active = () => !disposed && epoch === version;
    state.busy = true; state.error = ''; state.raw = ''; state.document = null;
    try {
      const originalContent = inputMessage(input), fingerprint = JSON.stringify(input);
      const content=deps.decorate?await deps.decorate(originalContent):originalContent;if(!active())return;
      state.progress = '正在准备法律意见书助手';
      const agent = await deps.agent(); if (!active()) return;
      if (!state.sessionId || state.fingerprint !== fingerprint) {
        state.progress = '正在保存案情';
        const id = await deps.create('法律意见书 - ' + input.facts.trim().slice(0, 60));
        if (!active()) return;
        state.sessionId = sessionIdFrom(id); state.fingerprint = fingerprint;
      }
      for (let attempt = 0; attempt < 2; attempt++) {
        state.progress = attempt ? '正在补全六章结构（自动重试1次）' : '正在分析案情并生成六个章节';
        state.raw = ''; let done = false;
        for await (const chunk of deps.stream({ model: agent.modelName || '', agentId: agent.id, sessionId: state.sessionId, content: attempt ? '上一份意见书未通过六章结构校验。请依据原案情重新输出完整 JSON，包含 timeline、issues、analysis、risks、strategy、references 六章；每章有非空 content 和 citations 数组。禁止编造案号，无依据写建议进一步检索。' : content, enableThinking: false })) {
          if (!active()) return;
          for (const event of decodeReviewEvents(chunk)) {
            if (event.event === 'error' || ('code' in event && event.code !== 200)) throw new Error(String(event.error || event.msg || '意见书生成失败'));
            if (event.event === 'content' && typeof event.content === 'string') state.raw += event.content;
            if (event.event === 'done' || event.done === true) done = true;
          }
          if (done) break;
        }
        if (!active()) return;
        if (!done) throw new Error('连接已中断，未收到完整意见书，请重试');
        try { state.document = parseOpinion(state.raw); break; }
        catch (error) { if (attempt) throw error; }
      }
      state.progress = '六章生成完成，已保存至我的历史';
    } catch (e) { if (active()) { deps.cancel(); state.error = (e as Error).message; state.progress = '生成未完成'; } }
    finally { if (active()) state.busy = false; }
  }
  function reset() { epoch++; deps.cancel(); Object.assign(state, initialOpinionState()); }
  function dispose() { reset(); disposed = true; }
  return { generate, reset, dispose };
}
