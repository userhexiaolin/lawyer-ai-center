export type LawStatus = 'effective' | 'amended' | 'repealed' | 'unknown';
export type ReportItemType = 'case_full' | 'case_section' | 'law' | 'viewpoint' | 'custom_text' | 'chart';
// Snowflake IDs are strings throughout the UI; never convert them to Number.
export interface SearchReport {
  id: string;
  title: string;
  status: 'draft' | 'generated';
  background?: string | null;
  conclusion?: string | null;
  updatedAt: string;
  createdAt: string;
  generatedAt?: string | null;
  fileUrl?: string | null;
}
export interface SearchReportItem {
  id: string;
  reportId: string;
  itemType: ReportItemType;
  snapshotTitle: string;
  snapshotContent: string;
  sortOrder: number;
  lawStatus?: LawStatus | null;
  lawName?: string | null;
  articleNo?: string | null;
  refId?: string | null;
  sourceUrl?: string | null;
}
export interface ReportSnapshot {
  itemType: ReportItemType;
  snapshotTitle?: string;
  snapshotContent?: string;
  lawStatus?: LawStatus;
  lawName?: string;
  articleNo?: string;
  sourceUrl?: string;
  refId?: string;
  sectionKey?: string;
  fragmentId?: string;
}
export interface ReportDetail { report: SearchReport; items: SearchReportItem[] }
export interface ReportEdit { title: string; background: string; conclusion: string }
