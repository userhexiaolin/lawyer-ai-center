import { useHookFetch } from 'hook-fetch/vue';
import { computed, onScopeDispose, reactive } from 'vue';
import type { AgentVO } from '@/api/agent/types';
import { getAgentList } from '@/api/agent';
import { send } from '@/api/chat';
import { readMediaApiResult } from '@/api/media/response';
import { postFile, uploadFile } from '@/api/oss';
import { create_session } from '@/api/session';
import { useUserStore } from '@/stores';
import { createReviewWorkflow, initialReviewState, isBusy } from './workflow';

export function useContractReview(caseId: () => string = () => '') {
  const user = useUserStore();
  const state = reactive(initialReviewState());
  const { stream, cancel } = useHookFetch({ request: send });
  const workflow = createReviewWorkflow(state, {
    ensureLogin: () => {
      if (user.token) return true;
      user.ensureLogin('/contract-review', '登录后即可使用合同审查');
      return false;
    },
    upload: (file, signal, onProgress) => uploadFile(file, { signal, onProgress }),
    createSession: async (title) => {
      const response = await readMediaApiResult<unknown>(create_session({ sessionTitle: title, caseId: caseId() || undefined, reviewSession: true }));
      return response.data;
    },
    readContent: async (file, signal) => {
      try {
        const result = await postFile<{ text: string }>('/system/contract-review/content', file, { signal, timeout: 180000 });
        if (typeof result?.text !== 'string') throw new Error('正文接口未返回有效文字');
        return result.text;
      }
      catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') throw error;
        throw new Error('合同正文读取失败，请检查文件是否加密、损坏或图片是否清晰；也可在原文区填写正文后继续审查');
      }
    },
    getAgent: async () => {
      const response = await readMediaApiResult<AgentVO[]>(getAgentList());
      const agent = response.data.find(a => a.agentName === '合同审查官' && a.status === '0');
      if (!agent?.id) throw new Error('未找到启用的合同审查官，请联系管理员');
      return { id: agent.id, modelName: agent.modelName };
    },
    stream: payload => stream(payload),
    cancelStream: cancel,
  });
  const busy = computed(() => isBusy(state.stage));
  const canReview = computed(() => !busy.value && Boolean(state.sessionId && state.fileUrl && state.text.trim()));
  const stageLabel = computed(() => ({ idle: '待上传', uploading: '上传中', 'creating-session': '正在创建会话', reading: '正在读取正文', ready: '待审查', reviewing: '审查中', complete: '审查完成', error: '处理失败' })[state.stage]);
  onScopeDispose(workflow.dispose);
  return { state, busy, canReview, stageLabel, prepare: workflow.prepare, review: workflow.review };
}
