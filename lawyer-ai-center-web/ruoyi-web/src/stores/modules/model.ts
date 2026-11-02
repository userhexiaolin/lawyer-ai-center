import type { GetSessionListVO } from '@/api/model/types';
import { watch } from 'vue';
import { defineStore } from 'pinia';
import { lawyerRequest } from '@/api/lawyer';
import { useUserStore } from './user';

// 模型管理
export const useModelStore = defineStore('model', () => {
  // 当前模型
  const currentModelInfo = ref<GetSessionListVO>({});

  // 设置当前模型
  const setCurrentModelInfo = (modelInfo: GetSessionListVO) => {
    currentModelInfo.value = modelInfo;
  };

  // 模型菜单列表
  const user = useUserStore();
  watch(() => user.token, () => { modelList.value = []; currentModelInfo.value = {}; }, {flush:'sync'});
  const modelList = ref<GetSessionListVO[]>([]);
  // 请求模型菜单列表
  const requestModelList = async () => {
    // 没有 token 时不请求
    const userStore = useUserStore();
    if (!userStore.token) {
      return;
    }

    try {
      const token = userStore.token;
      const res = await lawyerRequest<{data:GetSessionListVO[]}>('/system/model/modelList');
      if (userStore.token === token) modelList.value = Array.isArray(res.data) ? res.data : [];
    }
    catch (error) {
      console.error('requestModelList错误', error);
    }
  };

  return {
    currentModelInfo,
    setCurrentModelInfo,
    modelList,
    requestModelList,
  };
});
