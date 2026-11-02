import type { DocTemplateType } from '../../apps/web-antd/src/api/legal/doc-template-type';
const scenario = new URLSearchParams(location.search).get('scenario');
let rows: DocTemplateType[] = scenario === 'empty' ? [] : [
  { id: '1', name: '民事起诉状', builtin: true, status: '0', sortOrder: 10, remark: '内置专用起草流程' },
  { id: '2', name: '律师函', builtin: true, status: '0', sortOrder: 20, remark: '' },
  { id: '3', name: '劳动争议文书', builtin: false, status: '0', sortOrder: 30, remark: '合成类型，供主路径验收；不写入真实数据库。' },
  { id: '4', name: '物业服务合同及业主委员会日常管理配套文书分类（长名称合成样例）', builtin: false, status: '1', sortOrder: 40, remark: '覆盖长名称、停用与多行备注的布局。这里的所有分类均为合成测试数据。' },
  { id: '5', name: '有关联模板的分类', builtin: false, status: '0', sortOrder: 50, remark: '删除时返回服务端约束错误' },
];
let loads = 0, saves = 0;
const delay = () => new Promise(resolve => setTimeout(resolve, 100));
export async function templateTypeList(name = '') {
  await delay();
  if (scenario === 'list-error' && ++loads === 1) throw new Error('timeout of 10000ms exceeded');
  return rows.filter(row => row.name.includes(name)).map(row => ({ ...row }));
}
export async function saveTemplateType(value: DocTemplateType) {
  await delay();
  if (scenario === 'save-error' && ++saves === 1) throw new Error('保存请求超时');
  if (scenario === 'conflict') throw new Error('模板类型已变化，请刷新后重试（409）');
  if (rows.some(row => row.id !== value.id && row.name === value.name)) throw new Error('该模板类型名称已存在');
  if (value.id) rows = rows.map(row => row.id === value.id ? { ...value } : row);
  else rows.push({ ...value, id: String(rows.length + 1) });
}
export async function removeTemplateType(id: string) {
  await delay();
  if (id === '5') throw new Error('该类型下仍有模板，请先删除模板或调整模板类型');
  rows = rows.filter(row => row.id !== id);
}
