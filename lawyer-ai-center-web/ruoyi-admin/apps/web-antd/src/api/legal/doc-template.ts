import { requestClient } from '#/api/request';

export interface DocumentTypeOption {
  value: string;
  label: string;
  disabled?: boolean;
}
export interface DocTemplate {
  id?: string;
  expectedRevision?: string;
  fieldAnalysis?: TemplateFieldTask;
  name: string;
  documentType: string;
  content: string;
  sortOrder: number;
  status: '0' | '1';
  remark: string;
  sourceVersion?: string;
  sourceFormat?: string;
  sourceFilename?: string;
  parseStatus?: 'confirmed' | 'parsed' | 'rules' | 'uploaded';
  analysisWarning?: string;
  saveDraft?: boolean;
  blocks?: {
    editable: boolean;
    fields?: TemplateField[];
    id: string;
    text: string;
  }[];
  fields?: TemplateField[];
  fieldsConfirmed?: boolean;
  configureExisting?: boolean;
  editableBlocks?: string[];
  layoutConfirmed?: boolean;
}
export interface TemplateFieldTask {
  taskId: string;
  state: 'failed' | 'partial' | 'ready' | 'reviewed' | 'running' | 'stale';
  phase: string;
  warning: string;
  resultVersion?: string;
  startedAt: string;
  expiresAt: string;
}
export const startTemplateFieldAnalysis = (
  id: string,
  replacePending = false,
) =>
  requestClient.post<TemplateFieldTask>(
    `${base}/${id}/field-analysis`,
    { replacePending },
    {
      timeout: 660_000,
      errorMessageMode: 'none',
    },
  );
export const templateFieldAnalysisStatus = (id: string) =>
  requestClient.get<null | TemplateFieldTask>(`${base}/${id}/field-analysis`, {
    errorMessageMode: 'none',
  });
export const templateFieldAnalysisResult = (id: string) =>
  requestClient.get<DocTemplate>(`${base}/${id}/field-analysis/result`, {
    errorMessageMode: 'none',
  });
export interface TemplateField {
  id: string;
  blockId: string;
  start: number;
  end: number;
  original: string;
  label: string;
  kind: 'date' | 'money' | 'number' | 'text';
  unit: string;
  presentation: 'text' | 'underline';
  enabled: boolean;
}
export interface TemplateTypeAnalysis {
  id: string;
  previousType: string;
  revision: string;
  documentType: null | string;
  warning: string;
}
export const classifyExistingTemplateType = (
  id: string,
  signal?: AbortSignal,
) =>
  requestClient.post<TemplateTypeAnalysis>(
    `${base}/${id}/classify`,
    undefined,
    {
      errorMessageMode: 'none',
      signal,
      timeout: 660_000,
    },
  );
export const applyTemplateType = (
  id: string,
  documentType: string,
  expectedRevision: string,
) =>
  requestClient.put<void>(
    `${base}/${id}/type`,
    { documentType, expectedRevision },
    { errorMessageMode: 'none' },
  );
export const removeTemplateQuietly = (id: string) =>
  requestClient.delete<void>(`${base}/${id}`, { errorMessageMode: 'none' });
export const templateTypeVersion = (id: string) =>
  requestClient.get<{ documentType: string; revision: string }>(
    `${base}/${id}/type`,
    { errorMessageMode: 'none' },
  );
export function classifyTemplateType(file: File, signal?: AbortSignal) {
  const data = new FormData();
  data.append('file', file);
  return requestClient.post<{ documentType: null | string; warning: string }>(
    `${base}/classify`,
    data,
    { errorMessageMode: 'none', signal, timeout: 660_000 },
  );
}
export const analyzeTemplateFields = (version: string) =>
  requestClient.post<{
    fields: TemplateField[];
    method: string;
    warning: string;
  }>(`${base}/fields/${encodeURIComponent(version)}`, undefined, {
    timeout: 110_000,
  });
export const parseTemplateVersion = (version: string, type: string) =>
  requestClient.post<DocTemplate>(
    `${base}/parse/${encodeURIComponent(version)}`,
    undefined,
    { params: { type }, timeout: 660_000 },
  );
export const reidentifyTemplate = (id: string) =>
  requestClient.post<
    Pick<
      DocTemplate,
      'blocks' | 'content' | 'name' | 'sourceFormat' | 'sourceVersion'
    >
  >(`${base}/${id}/reidentify`);
const base = '/legal/doc-template';
export const documentTypes = () =>
  requestClient.get<DocumentTypeOption[]>('/legal/doc-template-type/options');
export const templateList = (params: Record<string, unknown>) =>
  requestClient.get<{
    processing?: string;
    rows: DocTemplate[];
    total: number;
  }>(`${base}/list`, {
    params,
  });
export const templateDetail = (id: string) =>
  requestClient.get<DocTemplate>(`${base}/${id}`);
export const saveTemplate = (value: DocTemplate) =>
  value.id
    ? requestClient.putWithMsg<void>(`${base}/${value.id}`, value)
    : requestClient.postWithMsg<string>(base, value);
export const saveTemplateMetadata = (value: DocTemplate) =>
  requestClient.putWithMsg<void>(`${base}/${value.id}/metadata`, {
    name: value.name,
    documentType: value.documentType,
    sortOrder: value.sortOrder,
    status: value.status,
    remark: value.remark,
  });
export const removeTemplate = (id: string) =>
  requestClient.deleteWithMsg<void>(`${base}/${id}`);
export function uploadTemplate(file: File, type: string, parse = false) {
  const data = new FormData();
  data.append('file', file);
  data.append('type', type);
  data.append('parse', String(parse));
  return requestClient.post<
    Pick<
      DocTemplate,
      | 'analysisWarning'
      | 'blocks'
      | 'content'
      | 'documentType'
      | 'name'
      | 'parseStatus'
      | 'sourceFilename'
      | 'sourceFormat'
      | 'sourceVersion'
    >
  >(`${base}/upload`, data, { timeout: 660_000 });
}
export const templatePreview = (version: string) =>
  requestClient.get<Blob>(`${base}/preview/${encodeURIComponent(version)}`, {
    responseType: 'blob',
    isTransformResponse: false,
    timeout: 660_000,
  });
