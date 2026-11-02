import type { ManagedTemplate, TemplateCategory } from '@/api/document/templateManagement';

export function hasTemplatePermission(
  permissions: string[] | undefined,
  domain: 'template' | 'category',
  action: string,
) {
  const prefix = domain === 'category' ? 'legal:doc-template-type' : 'legal:doc-template';
  return !!permissions?.some(value => value === '*:*:*' || value === `${prefix}:${action}`);
}
export function templateState(row: ManagedTemplate) {
  if (!row.sourceVersion)
    return '仅正文 · 待补原文件';
  if (row.parseStatus === 'uploaded')
    return '已上传 · 未解析';
  return row.fieldsConfirmed ? '填写字段已确认' : '填写字段待核对';
}
export function canStartDraft(row: ManagedTemplate, types: { value: string; disabled?: boolean }[]) {
  return !!row.id && row.status === '0' && row.documentType !== '法律意见书'
    && (!row.sourceVersion || row.fieldsConfirmed === true)
    && types.some(type => type.value === row.documentType && !type.disabled);
}
export function inputError(value: TemplateCategory | ManagedTemplate, metadataOnly = false) {
  const limit = 'builtin' in value ? 50 : 100;
  if (!value.name.trim() || value.name.trim().length > limit)
    return `请填写不超过${limit}字的名称`;
  if (!Number.isInteger(value.sortOrder) || value.sortOrder < 0 || value.sortOrder > 999999)
    return '排序号须为0至999999的整数';
  if (!['0', '1'].includes(value.status))
    return '请选择启用或停用状态';
  if ((value.remark || '').length > 500)
    return '备注不超过500字';
  if (
    'content' in value
    && (!value.documentType
      || (!metadataOnly
        && (!value.content?.trim() || value.content.length > 40000)
        && !(value.sourceVersion && value.parseStatus === 'uploaded' && value.saveDraft)))
  ) {
    return '请选择模板分类并填写1至40000字的正文，或上传有效原文件';
  }
  return '';
}
export function failureMessage(error: unknown) {
  if (error instanceof Error) {
    if (/timeout|timed out/i.test(error.message) || error.name === 'TimeoutError')
      return '请求超时，请检查连接后重试；未保存的输入已保留。';
    return error.message;
  }
  return '操作未完成，请检查连接后重试。';
}
