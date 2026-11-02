import type { FormSchemaGetter } from '#/adapter/form';
import type { VxeGridProps } from '#/adapter/vxe-table';

export const querySchema: FormSchemaGetter = () => [
  {
    component: 'Input',
    fieldName: 'name',
    label: '附件名称',
  },
];

/**
 * 表格列配置
 * 如果需要使用 i18n 国际化，请使用 getter 函数形式，否则切换语言时列配置不会刷新
 * 使用方式: export const columns: () => VxeGridProps['columns'] = () => [...]
 */
export const columns: VxeGridProps['columns'] = [
  { title: '法规名称', field: 'lawName', minWidth: 180 },
  { title: '条款号', field: 'articleNo', minWidth: 100 },
  { title: '适用地区', field: 'region', minWidth: 100 },
  { title: '时效状态', field: 'validityStatus', minWidth: 100 },
  { title: '发布日期', field: 'publishDate', minWidth: 110 },
  { title: '失效日期', field: 'expireDate', minWidth: 110 },
  { title: '来源版本', field: 'sourceVersion', minWidth: 120 },
  { type: 'checkbox', width: 60 },
  {
    title: '附件名称',
    field: 'name',
  },
  {
    title: '附件类型',
    field: 'type',
  },
  {
    title: '文档内容',
    field: 'content',
  },
  {
    title: '备注',
    field: 'remark',
  },
  {
    field: 'action',
    fixed: 'right',
    slots: { default: 'action' },
    title: '操作',
    width: 180,
  },
];
