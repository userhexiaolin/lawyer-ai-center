import type { ClaimLink, ResolvedSource } from './claims';
import { request } from './dossier';

export interface ReviewField {
  key: string;
  label: string;
  value?: string | null;
  original?: string | null;
  type: string;
  sensitive: boolean;
}
export interface FieldChange {
  field: string;
  oldValue: string;
  newValue: string;
  sensitive: boolean;
  protectedRef: string;
}
export interface ReviewChange {
  id: string;
  action: string;
  operatorId: string;
  createTime: string;
  reason: string;
  beforeFingerprint: string;
  afterFingerprint: string;
  fields: FieldChange[];
}
export interface ReviewView {
  objectKind: string;
  objectId: string;
  version: number | null;
  reviewStatus: string;
  fields: ReviewField[];
  sources: ResolvedSource[];
  links: ClaimLink[];
  history: ReviewChange[];
  initialNote: string;
}
export interface ReviewPointer {
  objectKind: string;
  objectId?: string;
  fileVersionId?: string;
  physicalPageIndex?: string;
  anchorId?: string;
}
export function validId(value: unknown): value is string {
  return typeof value === 'string' && /^[1-9]\d{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n;
}
export function pointer(query: Record<string, unknown>): ReviewPointer {
  const kind = typeof query.objectKind === 'string' ? query.objectKind : 'claim';
  if (!['claim', 'bank', 'source', 'event', 'evidence', 'relation'].includes(kind))
    throw new Error('复核对象类型无效');
  const result: ReviewPointer = { objectKind: kind };
  for (const key of ['objectId', 'fileVersionId', 'anchorId'] as const) {
    if (query[key] !== undefined && query[key] !== '') {
      if (!validId(query[key]))
        throw new Error('来源或对象标识无效');
      result[key] = query[key];
    }
  }
  if (query.physicalPageIndex !== undefined) {
    if (typeof query.physicalPageIndex !== 'string' || !/^[1-9]\d{0,4}$/.test(query.physicalPageIndex))
      throw new Error('物理页号无效');
    result.physicalPageIndex = query.physicalPageIndex;
  }
  if (kind === 'source' ? !result.anchorId : !result.objectId)
    throw new Error('缺少复核对象或真实来源标识');
  return result;
}
export function reviewQuery(value: ReviewPointer) {
  const query = new URLSearchParams();
  Object.entries(value).forEach(([key, item]) => {
    if (item !== undefined)
      query.set(key, item);
  });
  return query.toString();
}
export function reviewLink(caseId: string, kind: string, objectId: string, source?: ResolvedSource) {
  return { path: '/my-cases/review', query: { id: caseId, objectKind: kind, objectId, ...(source ? { anchorId: source.anchor.id, fileVersionId: source.anchor.fileVersionId, physicalPageIndex: String(source.anchor.pageIndex) } : {}) } };
}
export function reviewRequest<T>(id: string, path: string, method = 'GET', body?: unknown) {
  return request<T>(id, `/review${path}`, method, body);
}
export function editValues(fields: ReviewField[]) {
  return Object.fromEntries(fields.map(field => [field.key, field.sensitive ? '' : field.value || '']));
}
export function changedValues(fields: ReviewField[], edits: Record<string, string>) {
  const result: Record<string, string> = {};
  for (const field of fields) {
    const value = edits[field.key] ?? '';
    if (field.sensitive ? value !== '' : value !== (field.value || ''))
      result[field.key] = value;
  }
  return result;
}
export function reviewKey(key: string, ctrl: boolean, meta: boolean, editing: boolean) {
  if ((ctrl || meta) && key.toLowerCase() === 's')
    return 'save';
  if (ctrl || meta || editing)
    return undefined;
  return key.toLowerCase() === 'j' ? 'next' : key.toLowerCase() === 'k' ? 'previous' : undefined;
}
export function maskReviewValue(field: ReviewField, value: string | null | undefined) {
  if (value == null)
    return '未留存/未知';
  return field.sensitive ? value.length < 3 ? '*'.repeat(value.length) : `${value.slice(0, 1)}***${value.slice(-1)}` : value;
}
export function rememberReviewQueue(tenant: string, user: string, caseId: string, kind: string, ids: string[]) {
  if (!tenant || !user || !validId(caseId))
    return;
  const key = `review-queue:${tenant}:${user}:${caseId}:${kind}`;
  let autoNext = false;
  try {
    autoNext = JSON.parse(sessionStorage.getItem(key) || '{}').autoNext === true;
  }
  catch {
  }
  sessionStorage.setItem(key, JSON.stringify({ ids: [...new Set(ids.filter(validId))].slice(0, 100), autoNext }));
}
export function storedReviewQueue(raw: string | null): { ids: string[]; autoNext: boolean } | undefined {
  try {
    const value = JSON.parse(raw || 'null');
    if (value && Array.isArray(value.ids))
      return { ids: [...new Set<string>(value.ids.filter(validId))].slice(0, 100), autoNext: value.autoNext === true };
  }
  catch {
  }
}
