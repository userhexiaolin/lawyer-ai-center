import { lawyerRequest } from '@/api/lawyer';
import { useUserStore } from '@/stores';
import { checkAuthExpiry } from '@/utils/authExpiry';

export interface TemplateCategory {
  id?: string;
  name: string;
  sortOrder: number;
  status: '0' | '1';
  builtin: boolean;
  remark: string;
}
export interface TemplateCategoryOption {
  value: string;
  label: string;
  disabled?: boolean;
}
export interface TemplateField {
  id: string;
  blockId: string;
  start: number;
  end: number;
  original: string;
  label: string;
  kind: 'text' | 'money' | 'number' | 'date';
  unit: string;
  presentation: 'text' | 'underline';
  enabled: boolean;
}
export interface TemplateBlock {
  id: string;
  text: string;
  editable: boolean;
  fields?: TemplateField[];
}
export interface ManagedTemplate {
  id?: string;
  expectedRevision?: string;
  name: string;
  documentType: string;
  content: string;
  sortOrder: number;
  status: '0' | '1';
  remark: string;
  sourceVersion?: string;
  sourceFormat?: string;
  sourceFilename?: string;
  parseStatus?: 'uploaded' | 'rules' | 'parsed' | 'confirmed';
  analysisWarning?: string;
  blocks?: TemplateBlock[];
  fields?: TemplateField[];
  editableBlocks?: string[];
  fieldsConfirmed?: boolean;
  saveDraft?: boolean;
}
export interface TemplatePage {
  rows: ManagedTemplate[];
  total: number;
}
const base = '/legal/doc-template';
const categories = '/legal/doc-template-type';
const key = encodeURIComponent;
export async function categoryOptions() {
  return (await lawyerRequest<{ data: TemplateCategoryOption[] }>(`${categories}/options`)).data;
}
export async function categoryList(name = '') {
  return (await lawyerRequest<{ data: TemplateCategory[] }>(`${categories}/list?name=${key(name)}`)).data;
}
export function saveCategory(value: TemplateCategory) {
  return lawyerRequest(
    value.id ? `${categories}/${key(value.id)}` : categories,
    value.id ? 'PUT' : 'POST',
    value,
  );
}
export const removeCategory = (id: string) => lawyerRequest(`${categories}/${key(id)}`, 'DELETE');
export async function managedTemplates(params: {
  name: string;
  type: string;
  pageNum: number;
  pageSize: number;
}) {
  const query = new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)]));
  return (await lawyerRequest<{ data: TemplatePage }>(`${base}/list?${query}`)).data;
}
export async function templateDetail(id: string) {
  return (await lawyerRequest<{ data: ManagedTemplate }>(`${base}/${key(id)}`)).data;
}
export function saveManagedTemplate(value: ManagedTemplate) {
  return lawyerRequest(value.id ? `${base}/${key(value.id)}` : base, value.id ? 'PUT' : 'POST', value);
}
export function saveTemplateMetadata(value: ManagedTemplate) {
  return lawyerRequest(`${base}/${key(value.id!)}/metadata`, 'PUT', {
    name: value.name,
    documentType: value.documentType,
    sortOrder: value.sortOrder,
    status: value.status,
    remark: value.remark,
  });
}
export const removeManagedTemplate = (id: string) => lawyerRequest(`${base}/${key(id)}`, 'DELETE');
export async function parseTemplateSource(version: string, type: string) {
  return (
    await lawyerRequest<{ data: ManagedTemplate }>(
      `${base}/parse/${key(version)}?type=${key(type)}`,
      'POST',
      undefined,
      660000,
    )
  ).data;
}

// Use the existing login/auth-expiry contract for multipart and private binary responses.
async function sourceRequest(path: string, signal: AbortSignal, body?: FormData) {
  const user = useUserStore();
  const token = user.token;
  if (!token)
    throw new Error('请先登录');
  const controller = new AbortController();
  const abort = () => controller.abort(signal.reason);
  if (signal.aborted)
    abort();
  else
    signal.addEventListener('abort', abort, { once: true });
  const timer = setTimeout(() => controller.abort(new DOMException('请求超时', 'TimeoutError')), 660000);
  const dispose = () => {
    clearTimeout(timer);
    signal.removeEventListener('abort', abort);
  };
  try {
    const response = await fetch(import.meta.env.VITE_API_URL + path, {
      method: body ? 'POST' : 'GET',
      headers: { Authorization: `Bearer ${token}`, clientid: import.meta.env.VITE_CLIENT_ID },
      body,
      signal: controller.signal,
      cache: 'no-store',
    });
    if (user.token !== token)
      throw new Error('登录用户已变化，请重试');
    checkAuthExpiry(response.status);
    return { response, current: () => user.token === token, dispose };
  }
  catch (error) {
    dispose();
    throw error;
  }
}
export async function uploadTemplateSource(file: File, type: string, signal: AbortSignal) {
  const data = new FormData();
  data.append('file', file);
  data.append('type', type);
  data.append('parse', 'true'); // Local conversion and rule candidates only; no model request.
  const { response, current, dispose } = await sourceRequest(`${base}/upload`, signal, data);
  try {
    const body = await response.json();
    if (!current())
      throw new Error('登录用户已变化，请重试');
    checkAuthExpiry(response.status, body);
    if (!response.ok || body.code !== 200)
      throw new Error(body.msg || '文件上传或解析失败，请重新选择文件');
    return body.data as ManagedTemplate;
  }
  finally {
    dispose();
  }
}
export async function previewTemplateSource(version: string, signal: AbortSignal) {
  const { response, current, dispose } = await sourceRequest(`${base}/preview/${key(version)}`, signal);
  try {
    const blob = await response.blob();
    if (!current())
      throw new Error('登录用户已变化，请重试');
    if (!response.ok || (await blob.slice(0, 5).text()) !== '%PDF-') {
      let message = '预览失败，未收到有效 PDF，请重试';
      if (blob.size < 65536) {
        try {
          const body = JSON.parse(await blob.text());
          checkAuthExpiry(response.status, body);
          message = body.msg || message;
        }
        catch (error) {
          if (error instanceof Error && !(error instanceof SyntaxError))
            throw error;
        }
      }
      throw new Error(message);
    }
    return blob;
  }
  finally {
    dispose();
  }
}
