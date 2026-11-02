import type { LoginUser } from '@/api/auth/types';
import { ElMessage } from 'element-plus';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';

export const useUserStore = defineStore(
  'user',
  () => {
    const token = ref<string>();
    const userInfo = ref<LoginUser>();
    const verifiedIdentityToken = ref<string>();
    const identityLoading = ref(false);
    const identityError = ref('');
    let identityRequest: { token: string; promise: Promise<LoginUser> } | undefined;
    const router = useRouter();
    const loginRedirectPath = ref<string>('');

    const setToken = (value: string) => {
      if (token.value !== value) {
        userInfo.value = undefined;
        verifiedIdentityToken.value = undefined;
        identityError.value = '';
      }
      token.value = value;
    };
    const clearToken = () => {
      token.value = void 0;
      userInfo.value = undefined;
      verifiedIdentityToken.value = undefined;
      identityError.value = '';
    };

    const setUserInfo = (value: LoginUser) => {
      userInfo.value = value;
    };
    const clearUserInfo = () => {
      userInfo.value = void 0;
      verifiedIdentityToken.value = undefined;
    };
    const identityReady = computed(() => !!token.value && verifiedIdentityToken.value === token.value && !!userInfo.value);
    async function ensureIdentity(): Promise<LoginUser> {
      const requestToken = token.value;
      if (!requestToken)
        throw new Error('请先登录');
      if (identityReady.value)
        return userInfo.value!;
      if (identityRequest?.token === requestToken)
        return identityRequest.promise;
      identityLoading.value = true;
      identityError.value = '';
      const promise = (async () => {
        try {
          const { fetchSessionIdentity } = await import('@/utils/sessionIdentity');
          const identity = await fetchSessionIdentity(requestToken, () => token.value);
          if (token.value !== requestToken)
            throw new Error('登录用户已变化，请重试');
          verifiedIdentityToken.value = requestToken;
          userInfo.value = identity;
          return identity;
        }
        catch (e) {
          if (token.value === requestToken)
            identityError.value = e instanceof Error ? e.message : '暂时无法核对账号身份，请重试';
          throw e;
        }
        finally {
          if (token.value === requestToken)
            identityLoading.value = false;
        }
      })();
      identityRequest = { token: requestToken, promise };
      try {
        return await promise;
      }
      finally {
        if (identityRequest?.promise === promise)
          identityRequest = undefined;
      }
    }

    const logout = async () => {
      // 如果需要调用接口，可以在这里调用
      clearToken();
      clearUserInfo();
      router.replace({ name: 'chat' });
    };

    const setLoginRedirectPath = (path?: string) => {
      loginRedirectPath.value = path || '';
    };

    const consumeLoginRedirectPath = () => {
      const path = loginRedirectPath.value;
      loginRedirectPath.value = '';
      return path;
    };

    const isAuthExpiredHandling = ref(false);

    // 登录弹框状态
    const isLoginDialogVisible = ref(false);
    const openLoginDialog = () => {
      isLoginDialogVisible.value = true;
    };
    const closeLoginDialog = () => {
      isLoginDialogVisible.value = false;
    };

    const ensureLogin = (path?: string, message: string = '登录后即可继续使用完整功能') => {
      setLoginRedirectPath(path);
      openLoginDialog();
      ElMessage.info(message);
    };

    const handleAuthExpired = (path?: string, message: string = '登录状态已失效，请重新登录') => {
      if (isAuthExpiredHandling.value)
        return;

      isAuthExpiredHandling.value = true;

      clearToken();
      clearUserInfo();
      setLoginRedirectPath(path);
      openLoginDialog();
      ElMessage.info(message);
      if (!router.currentRoute.value.meta.stayOnAuthExpired) {
        router.replace({ name: 'chat' });
      }
    };

    const resetAuthExpiredHandling = () => {
      isAuthExpiredHandling.value = false;
    };

    return {
      token,
      setToken,
      clearToken,
      userInfo,
      identityReady,
      identityLoading,
      identityError,
      ensureIdentity,
      setUserInfo,
      clearUserInfo,
      logout,
      loginRedirectPath,
      setLoginRedirectPath,
      consumeLoginRedirectPath,
      ensureLogin,
      handleAuthExpired,
      resetAuthExpiredHandling,
      isLoginDialogVisible,
      openLoginDialog,
      closeLoginDialog,
    };
  },
  {
    persist: true,
  },
);
