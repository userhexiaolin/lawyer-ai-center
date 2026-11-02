import {checkDownloadAuth} from '@/utils/authExpiry';
import { lawyerRequest, type CaseFile } from './index';
import { useUserStore } from '@/stores';
const path = (id: string) => '/lawyer/case/' + encodeURIComponent(id) + '/files';
export async function caseFiles(id: string) { return (await lawyerRequest<{ data: CaseFile[] }>(path(id))).data; }
export async function caseFile(id: string, ossId: string) { return (await lawyerRequest<{ data: CaseFile }>(path(id) + '/' + encodeURIComponent(ossId))).data; }
export async function readCaseFileBlob(id: string, file: CaseFile) {
  const user = useUserStore(), token = user.token;
  if (!token) throw Error('请先登录');
  const response = await fetch(import.meta.env.VITE_API_URL + path(id) + '/' + encodeURIComponent(file.ossId) + '/download', {
    headers: { Authorization: `Bearer ${token}`, clientid: import.meta.env.VITE_CLIENT_ID }, signal: AbortSignal.timeout(65000),
  });
  await checkDownloadAuth(response,token);
  const blob = await response.blob();
  if (!response.ok || /application\/json/i.test(blob.type)) {
    let message = '材料下载失败，请稍后重试';
    try { message = JSON.parse(await blob.text()).msg || message; } catch { /* non-JSON error */ }
    throw Error(message);
  }
  if (user.token !== token) throw Error('登录用户已变化，请重试');
  return blob;
}
export async function downloadCaseFile(id: string, file: CaseFile) {
  const blob = await readCaseFileBlob(id, file);
  const url = URL.createObjectURL(blob), a = document.createElement('a');
  a.href = url; a.download = (file.originalName || file.fileName || '案件材料').replace(/[<>:"/\\|?*\x00-\x1f]/g, '_');
  document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 30000);
}
