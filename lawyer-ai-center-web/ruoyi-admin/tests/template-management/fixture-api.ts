import { templateProcessing } from '../../apps/web-antd/src/views/legal/doc-template/template-processing';
import type { DocTemplate } from '../../apps/web-antd/src/api/legal/doc-template';

const scenario = new URLSearchParams(location.search).get('scenario');
let listFailed = false;
let saveFailed = false;
const rows: DocTemplate[] = [
  {
    id: '1',
    name: '物业服务费催缴律师函',
    documentType: '律师函',
    content: '致：{{ 收函人 }}\n请核对本模板空白字段。\n（合成测试数据）',
    sortOrder: 1,
    status: '0',
    remark: '适用于物业服务费催缴；发出前核对主体、期间与金额。',
    sourceFormat: 'docx',
    sourceVersion: 'synthetic-v1-20261024',
    fieldsConfirmed: true,
    parseStatus: 'confirmed',
  },
  {
    id: '2',
    name: '民事起诉状（借款合同纠纷）',
    documentType: '民事起诉状',
    content: '原告：____\n被告：____\n诉讼请求：____',
    sortOrder: 2,
    status: '1',
    remark: '解析结果待人工核对，核对完成后再启用。',
    sourceFormat: 'docx',
    sourceVersion: 'synthetic-v2-20261024',
    parseStatus: 'parsed',
  },
  {
    id: '3',
    name: '合同补充协议模板',
    documentType: '合同文书',
    content: '',
    sortOrder: 3,
    status: '1',
    remark: '仅上传原文件，尚未解析。',
    sourceFormat: 'pdf',
    sourceVersion: 'synthetic-v3-20261024',
    parseStatus: 'uploaded',
  },
  {
    id: '4',
    name: '法律意见书通用文字模板',
    documentType: '法律意见书',
    content: '# 法律意见书\n委托事项：{{ 委托事项 }}',
    sortOrder: 4,
    status: '1',
    remark: '历史文字模板；如需原版式请补传原文件。',
  },
  {
    id: '5',
    name: '这是用于验证长中文模板名称换行与固定操作列的一份合成文书模板（不含真实客户材料）',
    documentType: '律师函',
    content: '合成模板正文',
    sortOrder: 5,
    status: '1',
    remark: '长备注与来源版本不应导致浏览器整页横向溢出。',
    sourceFormat: 'docx',
    sourceVersion: 'synthetic-long-source-version-20261024-0000000000000005',
    fieldsConfirmed: true,
    parseStatus: 'confirmed',
  },
];
for (const row of rows)
  if (row.sourceVersion) row.sourceFilename = `${row.name}.${row.sourceFormat}`;
if (scenario === 'diagnostics' || scenario === 'diagnostics-offline') {
  const task = (
    id: string,
    state: 'failed' | 'partial' | 'ready' | 'running' | 'stale',
    phase: string,
    warning: string,
  ) => ({
    taskId: `synthetic-task-${id}-20261027-0000000000000000000000000000`,
    state,
    phase,
    warning,
    startedAt: '',
    expiresAt: '',
  });
  rows[1]!.fieldAnalysis = task(
    '2',
    'ready',
    '识别完成，待核对',
    '请核对识别出的填写字段。',
  );
  rows[2]!.fieldAnalysis =
    scenario === 'diagnostics-offline'
      ? task('3', 'running', '正在解析原文件', '')
      : task(
          '3',
          'failed',
          '解析失败',
          '合成故障：转换服务不可用，可重试解析；已保存字段保留。',
        );
  rows[4]!.fieldAnalysis = task(
    '5',
    'stale',
    '解析结果已过期',
    '合成故障：文件或字段配置已更新，旧解析结果不能覆盖当前配置，请重新解析。',
  );
  rows.push({
    ...rows[1]!,
    id: '6',
    name: '部分解析的合成模板',
    sortOrder: 6,
    sourceVersion: 'synthetic-partial-source-20261027',
    sourceFilename: '部分解析的合成模板.docx',
    fieldAnalysis: task(
      '6',
      'partial',
      '部分解析，待核对',
      '合成提示：模型暂不可用，保留规则候选供人工核对。',
    ),
  });
}
export async function documentTypes() {
  return ['律师函', '民事起诉状', '合同文书', '法律意见书'].map((value) => ({
    value,
    label: value,
  }));
}
export async function templateList(params: Record<string, unknown>) {
  if (scenario === 'list-error' && !listFailed) {
    listFailed = true;
    throw new Error('合成故障：网络请求超时，请重试');
  }
  const result =
    scenario === 'empty'
      ? []
      : rows.filter(
          (row) =>
            (!params.name || row.name.includes(String(params.name))) &&
            (!params.type || params.type === row.documentType) &&
            (!params.processing ||
              params.processing === templateProcessing(row)),
        );
  const offset =
    (Number(params.pageNum || 1) - 1) * Number(params.pageSize || 20);
  return {
    processing: params.processing || '',
    rows: result
      .slice(offset, offset + Number(params.pageSize || 20))
      .map((row) => ({ ...row })),
    total: result.length,
  };
}
export async function templateDetail(id: string) {
  return { ...rows.find((row) => row.id === id)! };
}
export async function saveTemplateMetadata(value: DocTemplate) {
  if (scenario === 'save-error' && !saveFailed) {
    saveFailed = true;
    throw new Error('合成故障：保存超时');
  }
  Object.assign(rows.find((row) => row.id === value.id)!, value);
}
export async function saveTemplate(value: DocTemplate) {
  if (value.id) {
    await saveTemplateMetadata(value);
    const row = rows.find((item) => item.id === value.id)!;
    if (value.fieldsConfirmed) {
      row.parseStatus = 'confirmed';
      row.fieldsConfirmed = true;
      if (row.fieldAnalysis) row.fieldAnalysis.state = 'reviewed';
    }
  } else rows.push({ ...value, id: String(rows.length + 1) });
}
export async function removeTemplate(id: string) {
  rows.splice(
    rows.findIndex((row) => row.id === id),
    1,
  );
}
export const removeTemplateQuietly = removeTemplate;
export async function classifyExistingTemplateType(id: string) {
  return {
    id,
    previousType: '律师函',
    documentType: '律师函',
    revision: 'synthetic',
    warning: '',
  };
}
export async function templateTypeVersion(id: string) {
  return {
    documentType: (await templateDetail(id)).documentType,
    revision: 'synthetic',
  };
}
export async function applyTemplateType(id: string, documentType: string) {
  Object.assign(rows.find((row) => row.id === id)!, { documentType });
}
// Upload and source conversion need the real service and are explicitly excluded from this UI harness.
const unsupported = async () => {
  throw new Error('隔离 UI 验收环境不连接上传、转换或模型服务');
};
export const classifyTemplateType = unsupported;
export const analyzeTemplateFields = unsupported;
export const parseTemplateVersion = unsupported;
export const reidentifyTemplate = unsupported;
export const uploadTemplate = unsupported;
export const templatePreview = unsupported;

// Deterministic UI-only field workflow fixtures. Real parser/model verified separately through the authenticated API.
const sampleBlocks = Array.from({ length: 18 }, (_, index) => ({
  id: `p${index}`,
  text: `第${index + 1}项：姓名________，金额________元。`,
  editable: true,
  fields: [
    {
      id: `f${index}`,
      blockId: `p${index}`,
      start: `第${index + 1}项：姓名`.length,
      end: `第${index + 1}项：姓名`.length + 8,
      original: '________',
      label: `合成填写项${index + 1}`,
      kind: 'text' as const,
      unit: '',
      presentation: 'underline' as const,
      enabled: true,
    },
  ],
}));
export async function startTemplateFieldAnalysis(id: string) {
  const row = rows.find((item) => item.id === id)!;
  row.fieldAnalysis = {
    taskId: `fixture-${id}`,
    state: 'running',
    phase: '正在解析原文件',
    warning: '',
    startedAt: '',
    expiresAt: '',
  };
  await new Promise((resolve) => setTimeout(resolve, 1800));
  row.fieldAnalysis.phase = '正在 AI 识别字段';
  await new Promise((resolve) => setTimeout(resolve, 1800));
  row.fieldAnalysis = {
    ...row.fieldAnalysis,
    state: scenario === 'parse-error' ? 'failed' : 'partial',
    phase: scenario === 'parse-error' ? '解析失败' : '规则候选待核对',
    warning:
      scenario === 'parse-error'
        ? '合成故障：转换服务不可用，原字段保留，可重试'
        : '合成模型降级：保留规则候选，待人工核对',
    resultVersion: `fixture-result-${id}`,
  };
  return { ...row.fieldAnalysis };
}
export async function templateFieldAnalysisStatus(id: string) {
  if (scenario === 'diagnostics-offline' && id === '3')
    throw new Error('合成故障：进度连接中断');
  return rows.find((item) => item.id === id)?.fieldAnalysis || null;
}
export async function templateFieldAnalysisResult(id: string) {
  if (scenario === 'field-conflict')
    throw new Error('模板版本已变化，请刷新后重试，原字段未被覆盖');
  const row = rows.find((item) => item.id === id)!;
  return {
    ...row,
    expectedRevision: 'fixture-revision',
    sourceVersion: row.fieldAnalysis?.resultVersion || row.sourceVersion,
    parseStatus: 'rules' as const,
    content: sampleBlocks.map((block) => block.text).join('\n'),
    blocks: sampleBlocks,
    editableBlocks: sampleBlocks.map((block) => block.id),
    fieldsConfirmed: false,
  };
}
