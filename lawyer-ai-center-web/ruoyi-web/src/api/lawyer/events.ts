import type { ClaimLink, ResolvedSource } from './claims';
import type { ReviewChange } from './review';
import { request } from './dossier';

export type DateKind = 'exact' | 'range' | 'approximate' | 'relative' | 'unknown';
export interface EventRecord {
  id: string; caseId: string; title: string; summary: string; eventType: string;
  participantsJson: string; dateKind: DateKind; dateRaw: string;
  dateStart: string | null; dateEnd: string | null; timezone: string | null;
  reviewStatus: string; sourceStatus: string; sourceErrors: string;
  origin: string; originClaimId: string | null; groupLabel: string; sortOrder: number;
  version: number; reviewerId: string | null;
}
export interface EventDetail {
  event: EventRecord; links: ClaimLink[]; sources: ResolvedSource[];
  history: ReviewChange[]; conflict: boolean; revision: string;
}
export interface EventEdit {
  expectedVersion: number; title: string; summary: string; eventType: string;
  participants: { subjectId: string | null; label: string }[];
  dateKind: DateKind; dateRaw: string; dateStart: string | null; dateEnd: string | null;
  timezone: string | null; sources: ClaimLink[]; reviewStatus: string;
  groupLabel: string; sortOrder: number; reason: string;
}
export interface EventFilter {
  search: string; dateKind: string; status: string; participant: string;
  fileVersionId: string; from: string; to: string; groupLabel: string; datePool: string;
  pageNum: number; pageSize: number;
}
export interface EventListing { rows: EventDetail[]; total: number; unknownCount: number; revision: string }
export interface EventNode { nodeId: string; event: EventRecord; sourceRefs: ClaimLink[]; conflict: boolean }
export interface EventGraph { revision: string; events: EventNode[]; precedence: { id: string; beforeId: string; afterId: string; state: string; version: number; reason: string }[] }
export const dateKinds: Record<DateKind, string> = { exact: '确切日', range: '日期区间', approximate: '约略日期', relative: '相对日期', unknown: '日期未知' };
export function eventRequest<T>(id: string, path: string, method = 'GET', body?: unknown) { return request<T>(id, `/events${path}`, method, body); }
export function eventFilter(): EventFilter { return { search: '', dateKind: '', status: '', participant: '', fileVersionId: '', from: '', to: '', groupLabel: '', datePool: '', pageNum: 1, pageSize: 25 }; }
export function eventQuery(filter: EventFilter) { const query = new URLSearchParams(); for (const [key, value] of Object.entries(filter)) if (value !== '') query.set(key, String(value)); return query.toString(); }
export function eventDate(value: Pick<EventRecord, 'dateKind' | 'dateRaw' | 'dateStart' | 'dateEnd'>) {
  const start = value.dateStart;
  const precision = start?.length === 7 ? '（月，无确切日）' : start?.length === 4 ? '（年，无确切日）' : '';
  const normalized = value.dateKind === 'range' ? `${start || '待核'}～${value.dateEnd || '待核'}` : start ? `${start}${precision}` : '日期待核';
  return `${dateKinds[value.dateKind] || '日期待核'} · ${value.dateRaw || '无原始日期'} · ${normalized}`;
}
export function newEvent(): EventEdit { return { expectedVersion: 1, title: '', summary: '', eventType: 'fact', participants: [], dateKind: 'unknown', dateRaw: '', dateStart: null, dateEnd: null, timezone: null, sources: [], reviewStatus: 'pending_review', groupLabel: '', sortOrder: 0, reason: '' }; }
export function eventEdit(detail: EventDetail): EventEdit {
  const value = detail.event; let participants: EventEdit['participants'] = [];
  try { const data = JSON.parse(value.participantsJson); if (!Array.isArray(data) || data.some(p => !p || typeof p.label !== 'string' || p.subjectId != null && typeof p.subjectId !== 'string')) throw new Error(); participants = data.map(p => ({ subjectId: p.subjectId ?? null, label: p.label })); } catch { throw new Error('参与主体记录损坏，请保留原数据并联系管理员'); }
  return { expectedVersion: value.version, title: value.title, summary: value.summary, eventType: value.eventType, participants, dateKind: value.dateKind, dateRaw: value.dateRaw, dateStart: value.dateStart, dateEnd: value.dateEnd, timezone: value.timezone, sources: detail.links.map(link => ({ ...link })), reviewStatus: value.reviewStatus === 'confirmed' ? 'confirmed' : 'pending_review', groupLabel: value.groupLabel, sortOrder: value.sortOrder, reason: '' };
}
