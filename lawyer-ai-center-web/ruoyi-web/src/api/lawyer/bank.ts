import { useUserStore } from '@/stores';
import { checkDownloadAuth } from '@/utils/authExpiry';
import { request } from './dossier';

export interface BankFile {
  id: string;
  displayName: string;
  category: string;
  currentVersionId: string | null;
  parseStatus: string;
  parsedPages: number;
  pageCount: number | null;
  suspected: boolean;
}
export interface BankRow {
  id: string;
  fileVersionId: string;
  pageId: string;
  pageIndex: number;
  tableRow: number;
  fragmentStart: number;
  fragmentEnd: number;
  tradeTimeRaw: string | null;
  tradeAt: string | null;
  amountRaw: string | null;
  amount: string | null;
  direction: string;
  currency: string | null;
  balanceRaw: string | null;
  balance: string | null;
  account: string | null;
  counterAccount: string | null;
  counterparty: string | null;
  transactionRef: string | null;
  summary: string | null;
  reasonsJson: string;
  anomalyJson: string;
  reviewStatus: string;
  included: boolean;
  version: number;
  suggestionJson: string | null;
}
export interface BankFilter {
  fileVersionId: string;
  search: string;
  from: string;
  to: string;
  direction: string;
  status: string;
  anomaly: string;
  counterparty: string;
  pageNum: number;
  pageSize: number;
}
export interface BankTotals {
  income: string;
  expense: string;
  net: string;
  confirmed: number;
  pending: number;
  excludedFromTotals: number;
  method: string;
  currencies: Record<string, { income: string; expense: string; net: string }>;
}
export interface BankDuplicate {
  id: string;
  leftRowId: string;
  rightRowId: string;
  decision: string;
  reason: string;
  version: number;
}
export interface BankRun {
  id: string;
  fileVersionId: string;
  jobId: string;
  state: string;
  readablePages: number;
  totalPages: number | null;
  rowCount: number;
  unparsedCount: number;
  errorMessage: string | null;
}
export interface BankListing {
  rows: BankRow[];
  total: number;
  totals: BankTotals;
  duplicates: BankDuplicate[];
  runs: BankRun[];
}
export interface BankSource {
  fileVersionId: string;
  pageIndex: number;
  pageLabel: string | null;
  fileName: string;
  start: number;
  end: number;
  fragment: string;
  current: boolean;
  verified: boolean;
}
export interface BankEdit {
  account: string | null;
  expectedVersion: number;
  tradeTimeRaw: string;
  amountRaw: string;
  direction: string;
  currency: string;
  balanceRaw: string;
  counterAccount: string | null;
  counterparty: string | null;
  transactionRef: string;
  summary: string;
  reviewStatus: string;
  included: boolean;
  reason: string;
}
export interface BankManual {
  fileVersionId: string;
  pageIndex: number;
  start: number;
  end: number;
}
export interface BankUnread {
  fileVersionId: string;
  pageIndex: number;
  rowCount: number;
  unparsedJson: string;
  warningsJson: string;
  lines?: { start: number; end: number; text: string }[];
}
export interface BankExport {
  id: string;
  jobId: string;
  snapshotHash: string;
  rowCount: number;
  pendingCount: number;
  format: string;
  state?: string;
  errorMessage?: string;
}
export const bankLabels: Record<string, string> = {
  pending_review: '待核',
  confirmed: '已核',
  stale: '需重新核对',
  cannot_determine: '暂无法判断',
  rejected: '不采用',
  income: '收入',
  expense: '支出',
  unknown: '未知',
  same: '同一笔重复材料',
  different: '不同笔',
  pending: '待判定',
  BALANCE_MISMATCH: '连续余额不一致',
  CURRENCY_MISMATCH: '币种不一致',
  COLUMN_MISALIGNMENT: '列错位',
  DIRECTION_CONFLICT: '收支正负冲突',
  DATE_OUT_OF_RANGE: '日期越界',
  DUPLICATE_SUSPECTED: '疑似重复',
  FORMULA_LIKE_TEXT: '公式形态文本',
  DATE_UNPARSED: '时间未解析',
  AMOUNT_UNPARSED: '金额未解析',
  BALANCE_UNPARSED: '余额未解析',
  DIRECTION_UNKNOWN: '方向未知',
  CURRENCY_UNKNOWN: '币种未知',
  HEADER_UNRECOGNIZED: '未识别表头',
};
export const label = (state: string) => bankLabels[state] || state;
export function flags(value: string | null) {
  try {
    const parsed: unknown = JSON.parse(value || '[]');
    return Array.isArray(parsed)
      ? parsed.filter((v): v is string => typeof v === 'string')
      : ['状态不可读'];
  }
  catch {
    return ['状态不可读'];
  }
}
export function bankRequest<T>(caseId: string, path = '', method = 'GET', data?: unknown) {
  return request<T>(caseId, `/bank-rows${path}`, method, data);
}
export function bankQuery(filter: BankFilter) {
  return new URLSearchParams(
    Object.entries(filter).map(([key, value]) => [key, String(value)]),
  ).toString();
}
export function emptyBankFilter(): BankFilter {
  return {
    fileVersionId: '',
    search: '',
    from: '',
    to: '',
    direction: '',
    status: '',
    anomaly: '',
    counterparty: '',
    pageNum: 1,
    pageSize: 30,
  };
}
export const exportReady = (value: BankExport) => value.state === 'success';
export async function downloadBank(caseId: string, value: BankExport) {
  if (!exportReady(value))
    throw new Error('导出尚未就绪，请等待任务完成');
  const user = useUserStore();
  const token = user.token;
  if (!token)
    throw new Error('请先登录');
  const response = await fetch(
    `${import.meta.env.VITE_API_URL}/lawyer/case/${encodeURIComponent(caseId)}/dossier/bank-rows/exports/${encodeURIComponent(value.id)}/download`,
    {
      headers: { Authorization: `Bearer ${token}`, clientid: import.meta.env.VITE_CLIENT_ID },
      cache: 'no-store',
      signal: AbortSignal.timeout(65000),
    },
  );
  await checkDownloadAuth(response, token);
  if (!response.ok || /json/i.test(response.headers.get('content-type') || '')) {
    let message = '导出下载失败';
    try {
      message = (await response.json()).msg || message;
    }
    catch {}
    throw new Error(message);
  }
  const blob = await response.blob();
  if (user.token !== token)
    throw new Error('登录用户已变化，请重试');
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `流水核对_${value.id}.${value.format}`;
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}
