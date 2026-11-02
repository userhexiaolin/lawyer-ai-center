const keys = new Set([
  'type',
  'lawName',
  'articleNo',
  'status',
  'caseNo',
  'title',
  'court',
  'sourceUrl',
  'fragmentId',
  'law_name',
  'article_no',
  'validityStatus',
  'validity_status',
  'case_no',
  'source_url',
  'fragment_id',
  'url',
]);
function record(value: unknown): boolean {
  if (!value || typeof value !== 'object' || Array.isArray(value))
    return false;
  const row = value as Record<string, unknown>;
  const text = (key: string) => typeof row[key] === 'string' && String(row[key]).trim().length > 0;
  return (
    Object.keys(row).every(key => keys.has(key))
    && (((text('lawName') || text('law_name')) && (text('articleNo') || text('article_no')))
      || text('caseNo')
      || text('case_no'))
  );
}
function metadata(raw: string) {
  try {
    const value: unknown = JSON.parse(raw);
    return Array.isArray(value) ? value.length > 0 && value.every(record) : record(value);
  }
  catch {
    return false;
  }
}
/** Remove only complete citation records; document protocols and unrelated JSON remain intact. */
export function stripCitationMetadata(text: string) {
  let removed = false;
  function plain(value: string) {
    let output = '';
    let position = 0;
    let budget = Math.max(100000, value.length * 8);
    for (let start = 0; start < value.length; start++) {
      if (value[start] !== '{' && value[start] !== '[')
        continue;
      const stack: string[] = [];
      let quoted = false;
      let escaped = false;
      let end = -1;
      for (let i = start; i < value.length; i++) {
        if (--budget < 0)
          return output + value.slice(position);
        const c = value[i];
        if (quoted) {
          if (escaped)
            escaped = false;
          else if (c === '\\')
            escaped = true;
          else if (c === '"')
            quoted = false;
          continue;
        }
        if (c === '"') {
          quoted = true;
          continue;
        }
        if (c === '{' || c === '[')
          stack.push(c);
        if (c === '}' || c === ']') {
          if (stack.pop() !== (c === '}' ? '{' : '['))
            break;
          if (!stack.length) {
            end = i + 1;
            break;
          }
        }
      }
      if (end < 0)
        continue;
      const raw = value.slice(start, end);
      output += value.slice(position, start);
      if (metadata(raw))
        removed = true;
      else output += raw;
      position = end;
      start = end - 1;
    }
    return output + value.slice(position);
  }
  let output = '';
  let position = 0;
  const fences = /```[^\n]*\n([\s\S]*?)```/g;
  for (let match = fences.exec(text); match !== null; match = fences.exec(text)) {
    output += plain(text.slice(position, match.index));
    if (metadata(match[1]))
      removed = true;
    else output += match[0];
    position = match.index + match[0].length;
  }
  output = (output + plain(text.slice(position))).trim();
  return removed ? output.replace(/\n\s*---\s*$/, '').trim() : output;
}

export function documentText(value: string) {
  const text = value
    .trim()
    .replace(/^```(?:markdown|md|text|plaintext)?[ \t]*\r?\n([\s\S]*?)```$/i, '$1')
    .trim();
  return stripCitationMetadata(text);
}
export function documentDeliveryIssues(value: string) {
  const fillState = templateFillState(value);
  // Missing values are advisory. The server still checks ownership, version and immutable text.
  return fillState?.status === 'invalid' ? [fillState.description] : [];
}
const blankMarker = /\{\{[^{}\r\n]{1,200}\}\}|【[^【】\r\n]{0,80}(?:待补充|待填写|待确认|待核实|未提供)[^【】\r\n]{0,80}】/g;
export interface TemplatePresentationField { start: number; end: number; original: string; label: string; presentation: 'text' | 'underline'; blank?: string }
function presentationRanges(raw: string, fields: TemplatePresentationField[] = []) {
  return Array.from(raw.matchAll(blankMarker)).map((match) => {
    const field = fields.find(field => field.start === match.index && field.end === match.index + match[0].length);
    let blank = ' '.repeat(match[0].length);
    if (field) {
      const native = field.blank ?? field.original;
      blank = /^\{\{[^{}]+\}\}$/.test(native) ? blank : native;
      if (field.presentation === 'underline' && /^\s*$/.test(blank))
        blank = '_'.repeat(Math.max(1, blank.length));
    }
    return { rawStart: match.index, rawEnd: match.index + match[0].length, blank };
  });
}
/** Presentation only: never call this on stored document JSON or an extraction prompt. */
export function documentPresentation(value: string, fields: TemplatePresentationField[] = []) {
  let result = value;
  for (const range of presentationRanges(value, fields).reverse()) result = result.slice(0, range.rawStart) + range.blank + result.slice(range.rawEnd);
  return result;
}
/** Map a visible text edit back to the raw text, preserving all untouched marker identities. */
export function editDocumentPresentation(raw: string, next: string, fields: TemplatePresentationField[] = []) {
  const before = documentPresentation(raw, fields);
  if (before === next)
    return raw;
  let start = 0;
  while (start < before.length && start < next.length && before[start] === next[start]) start++;
  let end = before.length;
  let nextEnd = next.length;
  while (end > start && nextEnd > start && before[end - 1] === next[nextEnd - 1]) {
    end--;
    nextEnd--;
  }
  const ranges: { rawStart: number; rawEnd: number; start: number; end: number }[] = [];
  let delta = 0;
  for (const range of presentationRanges(raw, fields)) {
    const position = range.rawStart - delta;
    ranges.push({ rawStart: range.rawStart, rawEnd: range.rawEnd, start: position, end: position + range.blank.length });
    delta += range.rawEnd - range.rawStart - range.blank.length;
  }
  function offset(position: number, right: boolean) {
    let shift = 0;
    for (const range of ranges) {
      if (position <= range.start)
        return position + shift;
      if (position < range.end)
        return right ? range.rawEnd : range.rawStart;
      shift += range.rawEnd - range.rawStart - (range.end - range.start);
    }
    return position + shift;
  }
  let rawStart = offset(start, false);
  let rawEnd = offset(end, true);
  for (const range of ranges) {
    if (start === end ? start >= range.start && start <= range.end : start < range.end && end > range.start) {
      rawStart = Math.min(rawStart, range.rawStart);
      rawEnd = Math.max(rawEnd, range.rawEnd);
    }
  }
  let replacement = next.slice(start, nextEnd);
  if (ranges.some(range => range.rawStart === rawStart && range.rawEnd === rawEnd) && /^[\s_＿]*$/.test(replacement))
    replacement = raw.slice(rawStart, rawEnd);
  return raw.slice(0, rawStart) + replacement + raw.slice(rawEnd);
}
export function deliveryText(value: string, warnings: string[] = []) {
  return (
    (warnings.length ? `> 待人工核实：${warnings.join('；')}。\n\n` : '') + documentText(value)
  );
}
export interface TemplateDocument {
  documentTemplate: string;
  blocks: { id: string; text: string; editable: boolean; fields?: TemplatePresentationField[] }[];
  calculations?: unknown[];
  calculationResults?: CalculationResult[];
  fillReport?: TemplateFillReport;
}
export interface TemplateFillIssue {
  fieldId: string;
  blockId: string;
  label: string;
  reason: 'unfilled' | 'template_configuration';
}
export interface TemplateFillReport {
  status: 'complete' | 'partial' | 'configuration_required' | 'stale';
  totalFields: number;
  filledFields: number;
  issues: TemplateFillIssue[];
}
export interface TemplateFillState {
  status: TemplateFillReport['status'] | 'unverified' | 'invalid';
  title: string;
  description: string;
  issues: TemplateFillIssue[];
  totalFields?: number;
  filledFields?: number;
}
function validFillReport(report: unknown): report is TemplateFillReport {
  if (!report || typeof report !== 'object')
    return false;
  const value = report as TemplateFillReport;
  if (!['complete', 'partial', 'configuration_required', 'stale'].includes(value.status)
    || !Number.isSafeInteger(value.totalFields) || value.totalFields < 0 || value.totalFields > 500
    || !Number.isSafeInteger(value.filledFields) || value.filledFields < 0 || value.filledFields > value.totalFields
    || !Array.isArray(value.issues) || value.issues.length > 500
    || !value.issues.every(issue => issue && typeof issue.fieldId === 'string'
      && typeof issue.blockId === 'string' && typeof issue.label === 'string'
      && ['unfilled', 'template_configuration'].includes(issue.reason))) {
    return false;
  }
  return value.status !== 'complete' || (value.filledFields === value.totalFields && value.issues.length === 0);
}
export function templateFillState(value: string): TemplateFillState | null {
  let raw: unknown;
  try {
    raw = JSON.parse(value);
  }
  catch {
    return null;
  }
  if (!raw || typeof raw !== 'object' || !('documentTemplate' in raw))
    return null;
  const data = templateDocument(value);
  if (!data) {
    return { status: 'invalid', title: '填充报告无效', description: '模板草稿或填充报告格式无效，请重新生成；当前内容暂不可交付。', issues: [] };
  }
  const report = data.fillReport;
  if (!report) {
    return { status: 'unverified', title: '填充状态未验证', description: '此历史草稿没有字段填充报告，内容仍待核对；保存或下载时会重新校验，未填写处沿用原模板的空白或下划线。', issues: [] };
  }
  const descriptions = {
    complete: ['字段已填写 · 待人工核对', '模板字段已填写，内容仍为 AI 草稿；请核对主体、金额、日期与合同依据。'],
    partial: ['字段待补充 · 可下载草稿', '部分字段来源尚未匹配或资料待补充。未填写处沿用原模板的空白或下划线，仍可保存和下载草稿，后续补充填写；内容待人工核对。'],
    configuration_required: ['模板填写位需核对', '模板存在未配置的填写位或字段配置问题，请核对模板；生成过程不会绕过模板字段配置。草稿仍可下载，未填写处沿用原模板空白或下划线。'],
    stale: ['修改后待校验', '正文已修改，字段填充报告已失效。保存或下载时会由服务端重新校验，缺项不影响下载；内容仍待人工核对。'],
  };
  const [title, description] = descriptions[report.status];
  return { ...report, title, description };
}
export interface CalculationResult {
  id: string;
  blockId: string;
  slot: number;
  label: string;
  status: 'calculated' | 'pending' | 'stale';
  value: string | null;
  unit: string;
  formula: string;
  sources: string[];
  message: string;
}
export function templateDocument(value: string): TemplateDocument | null {
  try {
    const data = JSON.parse(value);
    if (
      typeof data.documentTemplate === 'string'
      && Array.isArray(data.blocks)
      && data.blocks.every(
        (block: unknown) =>
          block
          && typeof block === 'object'
          && 'id' in block
          && typeof block.id === 'string'
          && 'text' in block
          && typeof block.text === 'string'
          && 'editable' in block
          && typeof block.editable === 'boolean',
      )
    ) {
      if (data.calculationResults !== undefined && (!Array.isArray(data.calculationResults)
        || data.calculationResults.length > 500 || !data.calculationResults.every((result: CalculationResult) =>
        result && typeof result.id === 'string' && typeof result.blockId === 'string'
        && typeof result.slot === 'number' && typeof result.label === 'string'
        && ['calculated', 'pending', 'stale'].includes(result.status)
        && (result.value === null || typeof result.value === 'string') && typeof result.unit === 'string'
        && typeof result.formula === 'string' && typeof result.message === 'string'
        && Array.isArray(result.sources) && result.sources.every(source => typeof source === 'string')))) {
        return null;
      }
      if (data.fillReport !== undefined && !validFillReport(data.fillReport))
        return null;
      return data;
    }
  }
  catch {
    /* Standard documents use Markdown. */
  }
  return null;
}
export function templateDocumentText(value: string) {
  const data = templateDocument(value);
  return data ? data.blocks.map(block => block.text).join('\n\n') : documentText(value);
}
export function editTemplateBlock(value: string, id: string, text: string) {
  const data = templateDocument(value);
  if (!data)
    throw new Error('模板草稿格式无效');
  const block = data.blocks.find(block => block.id === id);
  if (!block?.editable)
    throw new Error('固定模板区域不能修改');
  if (block.text === text)
    return value;
  const before = block.text;
  let start = 0;
  while (start < before.length && start < text.length && before[start] === text[start]) start++;
  let end = before.length;
  let nextEnd = text.length;
  while (end > start && nextEnd > start && before[end - 1] === text[nextEnd - 1]) {
    end--;
    nextEnd--;
  }
  const change = text.length - before.length;
  block.fields = block.fields?.flatMap((field) => {
    if (field.end <= start)
      return [field];
    if (field.start >= end)
      return [{ ...field, start: field.start + change, end: field.end + change }];
    if (field.start <= start && field.end >= end)
      return [{ ...field, end: field.end + change }];
    return [];
  });
  block.text = text;
  if (data.fillReport)
    data.fillReport.status = 'stale';
  for (const result of data.calculationResults || []) {
    if (result.blockId === id && result.status === 'calculated') {
      result.status = 'stale';
      result.message = '所在段落已编辑；请保存草稿，由服务端重新校验。修改了计算值时需核对要素后重新生成。';
    }
  }
  return JSON.stringify(data);
}
export function generalDraftInput(raw: string) {
  const snapshot = raw.split('---起草要素---\n')[1];
  if (!snapshot)
    throw new Error('历史记录缺少起草要素');
  const value = JSON.parse(
    snapshot.split('\n\n---律所模板开始---')[0].split('\n\n---版式填写JSON开始---')[0],
  );
  const result = { parties: '', facts: '', purpose: '', deadline: '' };
  for (const key of Object.keys(result) as (keyof typeof result)[]) {
    if (typeof value[key] !== 'string')
      throw new Error('历史起草要素格式不完整');
    result[key] = value[key];
  }
  return result;
}
