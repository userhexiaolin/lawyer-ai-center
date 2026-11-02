import { normalizeSessionId } from './session'
const json = { 'content-type': 'application/json' }
function validRows(rows, message) {
  if (!Array.isArray(rows) || rows.some(row => !row || !normalizeSessionId(row.id) || typeof row.name !== 'string')) throw new Error(message)
  return rows.map(row => ({ ...row, id: normalizeSessionId(row.id) }))
}
export function documentRequest(request, path, method = 'GET', data = {}) {
  return request(path, method, data, json).then(result => {
    if (!result || Number(result.code) !== 200) throw Object.assign(new Error(result && result.msg || '操作失败，请重试'), { code: result && result.code })
    return result
  })
}
export async function documentPermissions(request) {
  const result = await documentRequest(request, '/system/user/getInfo')
  const data = result.data || result
  return Array.isArray(data.permissions) ? data.permissions : []
}
export function canManage(permissions, area, action) {
  const prefix = area === 'category' ? 'legal:doc-template-type' : 'legal:doc-template'
  return Array.isArray(permissions) && permissions.some(value => value === '*:*:*' || value === prefix + ':' + action)
}
export async function templateList(request, params) {
  const result = await documentRequest(request, '/legal/doc-template/list', 'GET', params), data = result.data
  if (!data || !Number.isSafeInteger(Number(data.total)) || Number(data.total) < 0) throw new Error('模板总数格式异常，请重试')
  return { ...data, rows: validRows(data.rows, '模板列表格式异常，请重试'), total: Number(data.total) }
}
export async function templateTypes(request, management = false) {
  const result = await documentRequest(request, management ? '/legal/doc-template-type/options' : '/legal/doc-template/types')
  if (!Array.isArray(result.data) || result.data.some(row => !row || typeof row.value !== 'string' || !row.value || typeof row.label !== 'string')) throw new Error('模板分类格式异常，请重试')
  return result.data
}
export async function templateDetail(request, id) {
  const key = normalizeSessionId(id)
  if (!key) throw new Error('模板编号无效')
  const data = (await documentRequest(request, '/legal/doc-template/' + key)).data
  if (!data || normalizeSessionId(data.id) !== key || typeof data.name !== 'string' || typeof data.content !== 'string') throw new Error('模板详情与当前记录不一致，请重试')
  return { ...data, id: key }
}
export async function saveTemplate(request, form, metadata = false) {
  const id = normalizeSessionId(form.id), path = '/legal/doc-template' + (id ? '/' + id : '') + (id && metadata ? '/metadata' : '')
  if (form.id && !id) throw new Error('模板编号无效，当前编辑已保留')
  const data = metadata ? { name: form.name, documentType: form.documentType, sortOrder: form.sortOrder, status: form.status, remark: form.remark } : form
  return documentRequest(request, path, id ? 'PUT' : 'POST', data)
}
export async function categoryList(request, name = '') {
  const data = (await documentRequest(request, '/legal/doc-template-type/list', 'GET', { name })).data
  return validRows(data, '模板分类列表格式异常，请重试')
}
export function saveCategory(request, form) {
  const id = normalizeSessionId(form.id)
  if (form.id && !id) return Promise.reject(new Error('分类编号无效，当前编辑已保留'))
  return documentRequest(request, '/legal/doc-template-type' + (id ? '/' + id : ''), id ? 'PUT' : 'POST', form)
}
export function removeManaged(request, area, id) {
  const key = normalizeSessionId(id)
  if (!key) return Promise.reject(new Error('记录编号无效'))
  return documentRequest(request, (area === 'category' ? '/legal/doc-template-type/' : '/legal/doc-template/') + key, 'DELETE')
}
export async function enabledTemplates(request, type) {
  const data = (await documentRequest(request, '/legal/doc-template/enabled', 'GET', { type })).data
  const rows = validRows(data, '可用模板格式异常，请重新加载')
  if (rows.some(row => row.documentType !== type)) throw new Error('返回模板分类与当前选择不一致，请重新加载')
  return rows
}
export async function templateSchema(request, type, id) {
  const key = normalizeSessionId(id)
  if (!key) throw new Error('模板编号无效')
  const data = (await documentRequest(request, '/legal/doc-template/form', 'GET', { type, templateId: key })).data
  if (!data || normalizeSessionId(data.templateId) !== key || typeof data.version !== 'string' || !data.version || typeof data.templateName !== 'string' || !Array.isArray(data.fields) || data.fields.some(field => !field || typeof field.id !== 'string' || !field.id || typeof field.label !== 'string') || new Set(data.fields.map(field => field.id)).size !== data.fields.length) throw new Error('模板字段或版本与当前选择不一致，请重新加载')
  return { ...data, templateId: key }
}
export async function composeDraft(request, sessionId, form) {
  const key = normalizeSessionId(sessionId)
  if (!key) throw new Error('文书会话无效')
  const data = (await documentRequest(request, '/legal/document/compose', 'POST', { sessionId: key, form })).data
  if (!data || normalizeSessionId(data.sessionId) !== key || !normalizeSessionId(data.messageId) || typeof data.content !== 'string') throw new Error('合成返回的文书与当前会话不一致，请恢复当前文书核对')
  return { ...data, sessionId: key, messageId: normalizeSessionId(data.messageId) }
}
export async function loadDraft(request, id) {
  const key = normalizeSessionId(id)
  if (!key) throw new Error('文书会话无效')
  const draft = (await documentRequest(request, '/legal/document/draft/' + key)).data
  if (!draft || !normalizeSessionId(draft.messageId) || normalizeSessionId(draft.sessionId) !== key || typeof draft.content !== 'string') throw new Error('返回草稿与当前会话不一致，请重试')
  return { ...draft, sessionId: key, messageId: normalizeSessionId(draft.messageId) }
}
export async function saveDraft(request, id, draft, content) {
  const key = normalizeSessionId(id)
  if (!key || !normalizeSessionId(draft.messageId)) throw new Error('请先恢复当前草稿')
  await documentRequest(request, '/legal/document/draft/' + key, 'PUT', { messageId: draft.messageId, content })
  try { return await loadDraft(request, key) }
  catch (error) { throw new Error('保存请求已完成，但尚未回读确认：' + error.message + '。当前编辑已保留，请重试。') }
}
