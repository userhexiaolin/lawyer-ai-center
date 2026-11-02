import { lawyerRequest } from '@/api/lawyer';
import type { InboxApi, InboxPage } from '@/pages/notifications/workflow';
export const inboxApi: InboxApi = {
  async list(beforeId) { return (await lawyerRequest<{ data: InboxPage }>('/system/notice/inbox' + (beforeId ? '?beforeId=' + encodeURIComponent(beforeId) : ''))).data; },
  async read(id) { await lawyerRequest('/system/notice/inbox/' + encodeURIComponent(id) + '/read', 'PUT'); },
};
