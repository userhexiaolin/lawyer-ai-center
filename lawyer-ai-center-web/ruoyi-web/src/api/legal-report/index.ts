import {checkDownloadAuth} from '@/utils/authExpiry';
import { lawyerRequest } from '@/api/lawyer';
import { useUserStore } from '@/stores';
import { readWordResponse, wordFilename } from './download';
import type { ReportDetail, ReportEdit, ReportSnapshot, SearchReport, SearchReportItem } from './types';

const root = '/legal/report';
const path = (id: string) => `${root}/${encodeURIComponent(id)}`;
async function data<T>(url: string, method = 'GET', body?: unknown): Promise<T> {
  return (await lawyerRequest<{ data: T }>(url, method, body)).data;
}
export const reportApi = {
  list: (pageNum = 1, pageSize = 20) => lawyerRequest<{ rows: SearchReport[]; total: number }>(`${root}/list?pageNum=${pageNum}&pageSize=${pageSize}`),
  create: (title: string) => data<SearchReport>(root, 'POST', { title }),
  detail: (id: string) => data<ReportDetail>(path(id)),
  update: (id: string, edit: ReportEdit) => data<SearchReport>(path(id), 'PUT', edit),
  remove: (id: string) => lawyerRequest(path(id), 'DELETE'),
  add: (id: string, item: ReportSnapshot) => data<{ item: SearchReportItem; duplicate: boolean }>(`${path(id)}/items`, 'POST', item),
  order: (id: string, itemIds: string[]) => lawyerRequest(`${path(id)}/items/order`, 'PUT', { itemIds }),
  removeItem: (id: string, itemId: string) => lawyerRequest(`${path(id)}/items/${encodeURIComponent(itemId)}`, 'DELETE'),
  generate: (id: string) => data<{ fileUrl: string; status: 'generated' }>(`${path(id)}/generate`, 'POST'),
  async download(id: string, title: string) {
    const user = useUserStore(), token = user.token;
    if (!token) throw new Error('请先登录');
    // Use our own authenticated endpoint, never forward credentials to a returned external URL.
    const response = await fetch(import.meta.env.VITE_API_URL + path(id) + '/download', {
      headers: { Authorization: `Bearer ${token}`, clientid: import.meta.env.VITE_CLIENT_ID },
      signal: AbortSignal.timeout(65000),
    });
    await checkDownloadAuth(response,token);
    const blob = await readWordResponse(response);
    if (user.token !== token) throw new Error('登录用户已变化，请重试');
    const url = URL.createObjectURL(blob), link = document.createElement('a');
    link.href = url; link.download = wordFilename(title); document.body.appendChild(link); link.click(); link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 30000);
  },
};
