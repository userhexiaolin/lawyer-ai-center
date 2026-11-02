import { computed, ref } from 'vue';
import { useUserStore } from '@/stores';
import { failureMessage, hasTemplatePermission } from './workflow';

export function useTemplateAccess() {
  const user = useUserStore();
  const error = ref('');
  const permissions = computed(() =>
    user.identityReady ? user.userInfo?.menuPermission : undefined,
  );
  const allowed = (domain: 'template' | 'category', action: string) =>
    hasTemplatePermission(permissions.value, domain, action);
  async function verify() {
    error.value = '';
    try {
      await user.ensureIdentity();
    }
    catch (reason) {
      error.value = failureMessage(reason);
    }
  }
  return { user, allowed, error, verify };
}
