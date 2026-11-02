import { postFile } from '@/api/oss';
import { useUserStore } from '@/stores';
import { checkDownloadAuth } from '@/utils/authExpiry';
import { lawyerRequest } from './index';

export interface FileVersion {
  id: string;
  materialId: string;
  fileName: string;
  versionNo: number;
  sha256: string;
  fileBytes: number;
  mime: string;
  uploadStatus: string;
  parseStatus: string;
  pageCount?: number | null;
  parsedPages: number;
  failedPages: number;
  parserVersion: string;
  lastJobId: string | null;
  errorCode: string | null;
  errorMessage: string | null;
}
export interface Material { material: { id: string; displayName: string; currentVersionId: string | null }; current: FileVersion | null }
export interface MaterialPage {
  id: string;
  pageIndex: number;
  extractStatus: string;
  ocrStatus: string;
  previewStatus: string;
  extractionMethod: string;
  charCount: number;
  errorCode: string | null;
  errorMessage: string | null;
  emptyPageConfirmed: boolean;
  version: number;
  lastJobId: string;
}
export interface ParseJob { id: string; fileVersionId: string; jobType?: string; state: string; progressDone: number; progressTotal: number | null; errorMessage: string | null; attempts: number }
export interface Overview { caseTitle: string; files: number; success: number; partial: number; failed: number; parsedPages: number; knownPages: number; unknownPageFiles: number; issues: number }
export interface UploadTicket { id: string; state: string; fileVersionId: string | null }
const base = (id: string) => `/lawyer/case/${encodeURIComponent(id)}/dossier`;
export async function request<T>(caseId: string, path: string, method = 'GET', data?: unknown): Promise<T> {
  return (await lawyerRequest<{ data: T }>(base(caseId) + path, method, data)).data;
}
export const statusText: Record<string, string> = { issues: '待处理异常', not_started: '尚未解析', queued: '排队解析', running: '解析中', success: '解析成功', partial: '部分解析', failed: '解析失败', cancelled: '已取消', not_needed: '无需OCR', initialized: '待上传', uploading: '上传中', stored: '原件已存', completed: '已归档' };
export const activeJob = (job: ParseJob) => job.state === 'queued' || job.state === 'running';
export function coverage(version: FileVersion | null) {
  return version?.pageCount == null ? `${version?.parsedPages || 0}/总页未知` : `${version.parsedPages}/${version.pageCount}页`;
}
export async function fingerprint(file: File) {
  if (!file.size || file.size > 10 * 1024 * 1024)
    throw new Error('请选择非空且不超过10MB的材料');
  if (!/\.(?:pdf|docx|png|jpe?g|csv|xlsx)$/i.test(file.name))
    throw new Error('支持PDF、DOCX、PNG、JPG、CSV、XLSX；ZIP请在本地安全解压后逐份上传');
  if (!globalThis.crypto?.subtle)
    throw new Error('当前连接无法计算原件指纹，请使用HTTPS或localhost访问');
  const bytes = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
  return Array.from(new Uint8Array(bytes), b => b.toString(16).padStart(2, '0')).join('');
}
export function uploadContent(caseId: string, ticket: string, file: File, onProgress: (value: number) => void, signal: AbortSignal) {
  return postFile<UploadTicket>(`${base(caseId)}/uploads/${encodeURIComponent(ticket)}/content`, file, { onProgress, signal });
}
export async function authenticatedBlob(caseId: string, versionId: string, page?: number) {
  const user = useUserStore();
  const token = user.token;
  if (!token)
    throw new Error('请先登录');
  const path = `${base(caseId)}/files/${encodeURIComponent(versionId)}/${page !== undefined ? `preview?page=${page}` : 'download'}`;
  const response = await fetch(import.meta.env.VITE_API_URL + path, {
    headers: { Authorization: `Bearer ${token}`, clientid: import.meta.env.VITE_CLIENT_ID },
    signal: AbortSignal.timeout(65000),
    cache: 'no-store',
  });
  await checkDownloadAuth(response, token);
  if (!response.ok || /json/i.test(response.headers.get('content-type') || '')) {
    let message = '原件暂不可读取，请稍后重试';
    try {
      message = (await response.json()).msg || message;
    }
    catch { /* keep safe operational error */ }
    throw new Error(message);
  }
  const blob = await response.blob();
  if (user.token !== token)
    throw new Error('登录用户已变化，请重试');
  return blob;
}
export async function downloadVersion(caseId: string, version: Pick<FileVersion, 'id' | 'fileName'>) {
  const blob = await authenticatedBlob(caseId, version.id);
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = Array.from(version.fileName, char => char.charCodeAt(0) < 32 || /[<>:"/\\|?*]/.test(char) ? '_' : char).join('');
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}
