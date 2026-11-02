import { request } from './dossier';

export interface Region {
  x: number;
  y: number;
  width: number;
  height: number;
}
export interface SourceAnchor {
  id: string;
  fileVersionId: string;
  pageIndex: number;
  kind: string;
  fragmentStart: number | null;
  fragmentEnd: number | null;
  fragmentText: string;
  geometryJson: string | null;
  pinnedHistory: boolean;
  fingerprint: string;
}
export interface ResolvedSource {
  anchor: SourceAnchor;
  fileName: string;
  pageLabel: string | null;
  current: boolean;
  verified: boolean;
  error: string | null;
  pageText: string;
}
export interface ClaimLink {
  anchorId: string;
  relation: string;
}
export interface ClaimValues {
  dateRaw?: string | null;
  amountRaw?: string | null;
  name?: string | null;
  currency?: string | null;
  datePrecision?: string | null;
  dateStart?: string | null;
  dateEnd?: string | null;
}
export interface Claim {
  id: string;
  kind: string;
  claimText: string;
  originalText: string;
  valuesJson: string;
  risk: string;
  reviewStatus: string;
  sourceStatus: string;
  sourceErrors: string;
  version: number;
  reviewerId: string | null;
  createTime: string;
}
export interface Suggestion {
  id: string;
  runId: string;
  proposedJson: string;
  state: string;
  version: number;
  createTime: string;
}
export interface Change {
  id: string;
  action: string;
  beforeJson: string;
  afterJson: string;
  reason: string;
  operatorId: string;
  createTime: string;
}
export interface ClaimDetail {
  claim: Claim;
  sources: ResolvedSource[];
  links: ClaimLink[];
  suggestions: Suggestion[];
  history: Change[];
}
export interface ClaimEdit {
  expectedVersion: number;
  kind: string;
  text: string;
  values: ClaimValues;
  sources: ClaimLink[];
  reviewStatus: string;
  reason: string;
}
export interface ClaimRun {
  id: string;
  jobId: string;
  modelId: string;
  modelName: string;
  promptVersion: string;
  state: string;
  readablePages: number;
  totalPages: number | null;
  chunkCount: number;
  successfulChunks: number;
  failedChunks: number;
  reservedTokens: number;
  actualTokens: number | null;
  errorCode: string | null;
  errorMessage: string | null;
}
export interface ClaimFilter {
  search: string;
  kind: string;
  status: string;
  group: string;
  fileVersionId: string;
  reviewerId: string;
  from: string;
  to: string;
  pageNum: number;
  pageSize: number;
}
export interface ClaimListing {
  rows: Claim[];
  total: number;
  groups: Record<string, number>;
}
export const claimKinds: Record<string, string> = { excerpt: '摘录', subject: '人物/主体', date: '日期', payment: '金额/付款', clause: '合同条款', relation: '事实关系', contradiction: '疑似矛盾', other: '其他' };
export const claimStates: Record<string, string> = { pending_review: '待核', confirmed: '已核对', excluded: '不采用', stale: '需重新核对', source_unverified: '出处未验证', verified: '出处有效', high_risk: '高风险待核', pending: '普通待核', queued: '排队中', running: '处理中', success: '本次范围处理成功', partial: '处理有缺口', failed: '处理失败', cancelled: '已取消' };
export function emptyClaimFilter(): ClaimFilter {
  return { search: '', kind: '', status: '', group: '', fileVersionId: '', reviewerId: '', from: '', to: '', pageNum: 1, pageSize: 25 };
}
export function claimQuery(filter: ClaimFilter) {
  const query = new URLSearchParams();
  Object.entries(filter).forEach(([key, value]) => {
    if (value !== '' && value !== null && value !== undefined)
      query.set(key, String(value));
  });
  return query.toString();
}
export function claimRequest<T>(id: string, path = '', method = 'GET', body?: unknown) {
  return request<T>(id, `/claims${path}`, method, body);
}
export function claimValues(claim?: Claim): ClaimValues {
  try {
    const values = JSON.parse(claim?.valuesJson || '{}');
    if (typeof values !== 'object' || values === null || Array.isArray(values))
      return {};
    const result: ClaimValues = {};
    for (const key of ['dateRaw', 'amountRaw', 'name', 'currency', 'datePrecision', 'dateStart', 'dateEnd'] as const) {
      if (values[key] === null || typeof values[key] === 'string')
        result[key] = values[key];
    }
    return result;
  }
  catch {
    return {};
  }
}
/** Textareas expose LF-normalized UTF-16 offsets; source hashes retain original line endings. */
export function textareaSourceOffsets(text: string, start: number, end: number) {
  function sourceOffset(offset: number) {
    let raw = 0;
    for (let displayed = 0; displayed < offset && raw < text.length; displayed++)
      raw += text[raw] === '\r' && text[raw + 1] === '\n' ? 2 : 1;
    return raw;
  }
  return { start: sourceOffset(start), end: sourceOffset(end) };
}
export function geometry(raw: string | null | undefined): Region | undefined {
  try {
    const r = JSON.parse(raw || 'null');
    if (r && ['x', 'y', 'width', 'height'].every(k => typeof r[k] === 'number' && Number.isFinite(r[k])) && r.x >= 0 && r.y >= 0 && r.width > 0 && r.height > 0 && r.x + r.width <= 1 && r.y + r.height <= 1)
      return r;
  }
  catch {
  }
}
