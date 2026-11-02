import {checkAuthExpiry} from '@/utils/authExpiry';
import { lawyerRequest } from '@/api/lawyer';
import { useUserStore } from '@/stores';
import type { SearchInput, SearchResult } from '@/pages/fact-search/workflow';
export const factSearchApi = {
  libraries: () => lawyerRequest<{ data: { id: string; name: string }[] }>('/legal/fact-search/libraries'),
  async search(input: SearchInput & { sessionId: string }, signal: AbortSignal): Promise<SearchResult> {
    const user = useUserStore(), token = user.token;
    if (!token) throw new Error('请先登录');
    const response = await fetch(import.meta.env.VITE_API_URL + '/legal/fact-search', {
      method: 'POST', headers: { Authorization: `Bearer ${token}`, clientid: import.meta.env.VITE_CLIENT_ID, 'content-type': 'application/json' },
      body: JSON.stringify(input), signal: AbortSignal.any([signal, AbortSignal.timeout(65000)]),
    });
    if(user.token!==token)throw Error('登录用户已变化，请重试');
    checkAuthExpiry(response.status);
    const body = await response.json();
    if (user.token !== token) throw new Error('登录用户已变化，请重试');
    checkAuthExpiry(response.status,body);
    if (!response.ok || body.code !== 200) throw new Error(body.msg || '检索失败，请稍后重试');
    return body.data;
  },
};
