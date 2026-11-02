import type { AgentVO } from '@/api/agent/types';
import { readMediaApiResult } from '@/api/media/response';
import { watch } from 'vue';
import { defineStore } from 'pinia';
import { getAgentList } from '@/api';
import { useUserStore } from './user';

// 智能体管理
export const useAgentStore = defineStore('agent', () => {
  // 当前智能体
  const currentAgentInfo = ref<AgentVO>({});

  // 设置当前智能体
  const setCurrentAgentInfo = (agentInfo: AgentVO) => {
    currentAgentInfo.value = agentInfo;
  };

  const clearCurrentAgentInfo = () => {
    currentAgentInfo.value = {};
  };

  // 智能体列表
  const user = useUserStore();
  watch(() => user.token, () => { agentList.value = []; currentAgentInfo.value = {}; }, {flush:'sync'});
  const agentList = ref<AgentVO[]>([]);
  // 请求智能体列表
  const requestAgentList = async () => {
    const userStore = useUserStore();
    if (!userStore.token) {
      return;
    }

    try {
      const token = userStore.token;
      const res = await readMediaApiResult<AgentVO[]>(getAgentList());
      if (userStore.token === token) agentList.value = Array.isArray(res.data) ? res.data : [];
    }
    catch (error) {
      console.error('requestAgentList错误', error);
    }
  };

  return {
    currentAgentInfo,
    setCurrentAgentInfo,
    clearCurrentAgentInfo,
    agentList,
    requestAgentList,
  };
});
