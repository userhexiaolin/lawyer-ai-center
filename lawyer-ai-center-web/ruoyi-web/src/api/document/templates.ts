import { lawyerRequest } from '@/api/lawyer';

export interface DocumentTypeOption { value: string; label: string }
export interface DocumentTemplateOption { id: string; name: string; documentType: string; sortOrder: number; compatible?: boolean; warning?: string; preservesLayout?: boolean }
export const STANDARD_TEMPLATE = '__standard__';
export interface TemplateFormField { id: string; label: string; kind: 'text' | 'money' | 'number' | 'date'; unit: string; context: string }
export interface TemplateFormSchema { templateId: string; templateName: string; version: string; templateContext?: string; fields: TemplateFormField[] }
export interface TemplateFormValue { fieldId: string; value: string; quote: string; origin: 'manual' | 'extracted'; explanation?: string }
export interface TemplateFormInput { type: string; templateId: string; version: string; values: TemplateFormValue[]; sourceText: string; sourceName: string }
const base = '/legal/doc-template';
export async function templateForm(type: string, templateId: string) {
  return (await lawyerRequest<{ data: TemplateFormSchema }>(`${base}/form?type=${encodeURIComponent(type)}&templateId=${encodeURIComponent(templateId)}`)).data;
}
export async function composeTemplate(sessionId: string, form: TemplateFormInput) {
  return (await lawyerRequest<{ data: { content: string; input: string; sessionId: string } }>('/legal/document/compose', 'POST', { sessionId, form })).data;
}
export async function documentTypes() {
  return (await lawyerRequest<{ data: DocumentTypeOption[] }>(`${base}/types`)).data;
}
export async function enabledTemplates(type: string) {
  return (await lawyerRequest<{ data: DocumentTemplateOption[] }>(`${base}/enabled?type=${encodeURIComponent(type)}`)).data;
}
export async function templatePrompt(type: string, templateId: string | undefined, content: string) {
  const standard = templateId === STANDARD_TEMPLATE;
  return (await lawyerRequest<{ data: { content: string; templateId: string | null; templateName: string | null } }>(`${base}/prompt`, 'POST', { type, templateId: standard ? undefined : templateId || undefined, content, useTemplate: !standard })).data.content;
}
