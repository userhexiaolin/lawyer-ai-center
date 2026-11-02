import { requestClient } from '#/api/request';

export interface DocTemplateType {
  id?: string;
  name: string;
  sortOrder: number;
  status: '0' | '1';
  builtin: boolean;
  remark: string;
}
const base = '/legal/doc-template-type';
export const templateTypeList = (name?: string) =>
  requestClient.get<DocTemplateType[]>(`${base}/list`, {
    params: { name },
    errorMessageMode: 'none',
  });
export const saveTemplateType = (value: DocTemplateType) =>
  value.id
    ? requestClient.putWithMsg<void>(`${base}/${value.id}`, value, {
        errorMessageMode: 'none',
      })
    : requestClient.postWithMsg<string>(base, value, {
        errorMessageMode: 'none',
      });
export const removeTemplateType = (id: string) =>
  requestClient.deleteWithMsg<void>(`${base}/${id}`, {
    errorMessageMode: 'none',
  });
