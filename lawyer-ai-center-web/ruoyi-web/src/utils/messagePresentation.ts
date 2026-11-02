/** Display only. Never pass this text to extraction, persistence, composition or export. */
export type MessageKind = 'plain' | 'extraction' | 'elements' | 'document' | 'form' | 'review' | 'internal';
export interface MessageView {
  text: string;
  markdown: string;
  kind: MessageKind;
  structured: boolean;
  pending: boolean;
}
export interface MessageViewOptions { role?: 'user' | 'assistant'; streaming?: boolean }
type Row = Record<string, unknown>;
const row = (value: unknown): value is Row => !!value && typeof value === 'object' && !Array.isArray(value);
const text = (value: unknown) => typeof value === 'string' ? value.trim() : '';
const scalar = (value: unknown) => text(value).replace(/\s+/g, ' ');
const escapeMarkdown = (value: string) => value.replace(/[\\`*_[\]<>#|]/g, '\\$&');
const sentence = (value: string) => /[。！？!?]$/.test(value) ? value : `${value}。`;
function view(value: string, kind: MessageKind = 'plain', pending = false): MessageView {
  return { text: value, markdown: kind === 'plain' ? value : escapeMarkdown(value), kind, structured: kind !== 'plain', pending };
}
function kind(value: unknown): MessageKind {
  if (!row(value))
    return 'plain';
  if ('fieldValues' in value)
    return 'extraction';
  if (('partyA' in value && 'partyB' in value && ('facts' in value || 'amount' in value)) || ('plaintiff' in value && 'defendant' in value && 'facts' in value) || ('parties' in value && 'facts' in value && 'purpose' in value))
    return 'elements';
  if ('documentTemplate' in value)
    return 'document';
  if ('templateId' in value && 'values' in value)
    return 'form';
  if ('summary' in value && ('issues' in value || 'risks' in value || 'riskLevel' in value))
    return 'review';
  return 'plain';
}
function clue(value: string): MessageKind {
  if (/"fieldValues"\s*:/.test(value))
    return 'extraction';
  if (/^\s*\{\s*"(?:partyA|plaintiff|parties)"\s*:/.test(value))
    return 'elements';
  if (/"documentTemplate"\s*:/.test(value))
    return 'document';
  if (/"templateId"\s*:/.test(value) && /"values"\s*:/.test(value))
    return 'form';
  if (/"summary"\s*:/.test(value) && /"(?:issues|risks|riskLevel)"\s*:/.test(value))
    return 'review';
  // A historical title can end before fieldValues; do not expose the version UUID.
  if (/^\s*\{\s*"version"\s*:/.test(value))
    return 'extraction';
  return 'internal';
}
function unfinished(value: MessageKind, streaming = false): MessageView {
  const purpose = value === 'extraction' ? '填写信息' : value === 'document' ? '文书正文' : value === 'review' ? '审查结果' : '回复';
  return view(streaming ? `正在整理${purpose}…` : `这条${purpose}尚未完整生成，请重新生成或返回相应业务页面核对。`, value === 'plain' ? 'internal' : value, streaming);
}
function extraction(value: Row): MessageView {
  if (!Array.isArray(value.fieldValues) || value.fieldValues.length > 1000 || value.fieldValues.some(item => !row(item) || typeof item.value !== 'string' || typeof item.quote !== 'string'))
    return unfinished('extraction');
  const fields = value.fieldValues as Row[];
  const facts: string[] = [];
  const missing: string[] = [];
  const identifiers = [text(value.version), ...fields.map(item => text(item.fieldId))].filter(Boolean);
  const reason = (raw: unknown) => {
    let result = scalar(raw);
    for (const id of identifiers) result = result.split(id).join('对应填写项');
    return result;
  };
  let missingCount = 0;
  for (const field of fields) {
    const filled = scalar(field.value);
    const quote = scalar(field.quote);
    const explanation = reason(field.explanation);
    if (!filled) {
      missingCount++;
      if (explanation)
        missing.push(sentence(explanation));
      continue;
    }
    const label = scalar(field.label);
    let fact = label && !identifiers.includes(label)
      ? `${label}：${filled}`
      : quote && quote.length <= 500 && quote.includes(filled)
        ? quote
        : explanation && explanation.includes(filled) ? explanation : `提取建议为“${filled}”`;
    if (!quote)
      fact += '（尚缺可核对的原文依据）';
    facts.push(sentence(fact));
  }
  const found = fields.length - missingCount;
  const paragraphs = [found ? `根据本次提取结果，整理出以下填写建议：${Array.from(new Set(facts)).join('')}` : '本次尚未提取到可用的填写信息。'];
  if (missingCount)
    paragraphs.push(`还有${missingCount}处填写内容需要补充或核对。${Array.from(new Set(missing)).join('') || '现有材料未提供足够依据，请补充材料或人工填写。'}`);
  paragraphs.push('以上为提取建议，尚未经过人工确认；请对照原材料进行人工核对后再用于起草。');
  return view(paragraphs.join('\n\n'), 'extraction');
}
function document(value: Row): MessageView {
  if (!Array.isArray(value.blocks) || value.blocks.some(block => !row(block) || typeof block.text !== 'string'))
    return unfinished('document');
  const body = value.blocks.map(block => (block as Row).text as string).join('\n\n').replace(/\{\{[^{}\r\n]{1,200}\}\}/g, '【待填写】');
  const report = row(value.fillReport) ? value.fillReport : null;
  const warning = report?.status === 'partial'
    ? '部分内容尚未填写，请补充并对照原件核对。'
    : report && ['invalid', 'configuration_required', 'stale'].includes(String(report.status)) ? '模板填写配置或版本需要重新核对。' : '';
  const stale = Array.isArray(value.calculationResults) && value.calculationResults.some(item => row(item) && ['stale', 'invalid'].includes(String(item.status)));
  return view([warning, stale ? '计算结果尚待重新校验，请勿直接作为已核金额使用。' : '', body || '当前文书尚无正文。'].filter(Boolean).join('\n\n'), 'document');
}
function elements(value: Row, request = false): MessageView {
  const labels: Record<string, string> = { partyA: '合同甲方', partyB: '合同乙方', parties: '当事人', facts: '事实与事由', deadline: '期限与要求', purpose: '起草目的与相关主体', address: '合同涉及的地址', amount: '合同计价金额', amountUnit: '计价单位', period: '合同期限或计费周期', signDate: '签署日期', contractNo: '合同编号', plaintiff: '原告', defendant: '被告', claims: '诉讼请求', evidence: '证据材料', court: '拟提交法院' };
  const details: string[] = [];
  const missing: string[] = [];
  for (const [key, label] of Object.entries(labels)) {
    if (!(key in value))
      continue;
    if (typeof value[key] !== 'string')
      return unfinished('elements');
    if (text(value[key]))
      details.push(`${label}：${text(value[key])}`);
    else missing.push(label);
  }
  if (row(value.other) && text(value.other.rawClauses))
    details.push(`关键原文：${text(value.other.rawClauses)}`);
  return view([
    request ? `请根据以下要素起草${scalar(value.type) || '文书'}，未提供的信息保留待补充。` : details.length ? '根据本次整理结果，得到以下文书要素，仍需人工核对。' : '本次尚未整理出可用的文书要素，请补充材料或人工填写。',
    ...details,
    missing.length ? `尚待补充或核对：${missing.join('、')}。` : '',
    'contractType' in value && text(value.amount) ? '合同计价金额不等于本次欠款总额，不能据此推定实际欠款。' : '',
  ].filter(Boolean).join('\n\n'), 'elements');
}
function form(value: Row): MessageView {
  if (!Array.isArray(value.values))
    return unfinished('form');
  const filled = value.values.filter(item => row(item) && text(item.value)).length;
  const type = scalar(value.type) || '所选文书';
  return view(`请按${type}模板起草文书，使用已填写的${filled}项要素。未填写的内容保留待补充，生成后仍需人工核对。${text(value.sourceName) ? `\n\n参考材料：${text(value.sourceName)}` : ''}`, 'form');
}
function review(value: Row): MessageView {
  if (!text(value.summary))
    return unfinished('review');
  const entries = 'issues' in value ? value.issues : value.risks;
  if (entries !== undefined && (!Array.isArray(entries) || entries.some(item => !row(item))))
    return unfinished('review');
  const paragraphs = [sentence(text(value.summary))];
  for (const entry of (entries || []) as Row[]) {
    const description = [scalar(entry.clauseTitle ?? entry.title), scalar(entry.problem ?? entry.analysis), scalar(entry.suggestion)].filter(Boolean).join('；');
    if (description)
      paragraphs.push(sentence(description));
  }
  if (text(value.overallSuggestion))
    paragraphs.push(sentence(text(value.overallSuggestion)));
  paragraphs.push('以上为审查建议，请结合合同原文人工核对。');
  return view(paragraphs.join('\n\n'), 'review');
}
function formatted(value: unknown, depth = 0): MessageView | null {
  if (depth > 8)
    return null;
  if (typeof value === 'string') {
    try {
      return formatted(JSON.parse(value), depth + 1);
    }
    catch { return null; }
  }
  if (!row(value))
    return null;
  switch (kind(value)) {
    case 'extraction': return extraction(value);
    case 'elements': return elements(value);
    case 'document': return document(value);
    case 'form': return form(value);
    case 'review': return review(value);
    default: return 'data' in value && Object.keys(value).every(key => ['code', 'msg', 'data'].includes(key)) ? formatted(value.data, depth + 1) : null;
  }
}
function jsonEnd(source: string, start: number): number {
  const stack: string[] = [];
  let quoted = false;
  let escaped = false;
  for (let i = start; i < source.length; i++) {
    const char = source[i];
    if (quoted) {
      if (escaped)
        escaped = false;
      else if (char === '\\')
        escaped = true;
      else if (char === '"')
        quoted = false;
      continue;
    }
    if (char === '"') {
      quoted = true;
    }
    else if (char === '{' || char === '[') {
      stack.push(char);
    }
    else if (char === '}' || char === ']') {
      if (stack.pop() !== (char === '}' ? '{' : '['))
        return -1;
      if (!stack.length)
        return i + 1;
    }
  }
  return -1;
}
function internalRequest(source: string): MessageView | null {
  if (source.startsWith('通读本次合同全文及全部附件')) {
    let material = '';
    try {
      const marker = source.lastIndexOf('合同原文JSON：');
      const value: unknown = marker < 0 ? '' : JSON.parse(source.slice(marker + '合同原文JSON：'.length).trim());
      material = typeof value === 'string' ? value : '';
    }
    catch { /* Keep the request readable even when the historical title is truncated. */ }
    return view(`请整理合同中明确记载的主体、事实、期限和起草目的，缺项留空并由人工核对。${material ? `\n\n提供的材料：\n${material}` : ''}`, 'form');
  }
  if (source.startsWith('本轮仅执行合同字段提取')) {
    const material = source.match(/---合同资料开始---([\s\S]*?)(?:---合同资料结束---|$)/)?.[1].trim();
    return view(`请提取合同中明确记载的主体、计价条款和期限等信息，不推算实际欠款；缺项留空并由人工核对。${material ? `\n\n提供的材料：\n${material}` : ''}`, 'form');
  }
  if (source.includes('你是合同字段抽取器') && source.includes('模板字段JSON：') && source.includes('合同原文JSON：')) {
    const start = source.indexOf('模板字段JSON：') + '模板字段JSON：'.length;
    const end = source.indexOf('合同原文JSON：', start);
    try {
      const schema = JSON.parse(source.slice(start, end).trim());
      const material = JSON.parse(source.slice(end + '合同原文JSON：'.length).trim());
      const name = row(schema) ? scalar(schema.templateName) : '';
      return view(`请根据提供的合同提取${name ? `《${name}》` : '所选模板'}所需的填写信息，缺少依据的内容留空并说明。${typeof material === 'string' ? `\n\n提供的材料：\n${material}` : ''}`, 'form');
    }
    catch {
      return view('请根据已提供的合同，提取所选模板需要的填写信息；缺少依据的内容留空，稍后由人工核对。', 'form');
    }
  }
  if (source.trimStart().startsWith('你是合同字段抽取器'))
    return view('请根据提供的材料，提取所选模板需要的填写信息，缺项留空并由人工核对。', 'form');
  const snapshot = source.match(/---(?:模板填写要素|要素)JSON开始---([\s\S]*?)(?:---(?:模板填写要素|要素)JSON结束---|$)/);
  if (snapshot) {
    try {
      const value: unknown = JSON.parse(snapshot[1]);
      return row(value) ? kind(value) === 'elements' ? elements(value, true) : form(value) : unfinished('form');
    }
    catch { return unfinished('form'); }
  }
  const elementStart = source.indexOf('---起草要素---');
  if (elementStart >= 0 && source.startsWith('请起草')) {
    try {
      const value: unknown = JSON.parse(source.slice(elementStart + '---起草要素---'.length).trim());
      if (row(value))
        return view([source.match(/^请起草([^，。\n]+)/)?.[0] || '请根据以下要素起草文书', ...Object.entries({ parties: '当事人', facts: '事实与事由', purpose: '目的与诉求', deadline: '期限与要求' }).flatMap(([key, label]) => text(value[key]) ? [`${label}：${text(value[key])}`] : [])].join('\n\n'), 'form');
    }
    catch { return unfinished('form'); }
  }
  return null;
}
export function presentMessage(content: string, options: MessageViewOptions = {}): MessageView {
  if (typeof content !== 'string' || !content)
    return view('');
  const request = internalRequest(content);
  if (request)
    return request;
  const citation = content.indexOf('```legal-citations');
  if (citation >= 0)
    return view(content.slice(0, citation).trim() + (options.streaming ? '\n\n正在整理引用信息…' : '\n\n引用信息尚未完整显示，请核对来源。'), 'internal', options.streaming);
  const trimmed = content.trim();
  if (trimmed.length > 300000 && /fieldValues|documentTemplate|模板填写要素JSON/.test(trimmed))
    return unfinished(clue(trimmed), options.streaming);
  const unfenced = trimmed.replace(/^```(?:json)?[ \t]*\r?\n([\s\S]*?)```$/i, '$1');
  try {
    const result = formatted(JSON.parse(unfenced));
    if (result)
      return result;
  }
  catch { /* Scan complete embedded protocol objects without repairing their data. */ }
  let cursor = 0;
  let output = '';
  let found: MessageKind = 'plain';
  let pending = false;
  for (let start = 0; start < content.length; start++) {
    if (content[start] !== '{' && content[start] !== '[')
      continue;
    const end = jsonEnd(content, start);
    const tail = content.slice(start);
    if (end < 0) {
      const protocol = clue(tail);
      if (protocol !== 'internal' || options.streaming || (options.role !== 'user' && /^\s*(?:```json\s*)?[[{]/.test(trimmed))) {
        const result = unfinished(protocol, options.streaming);
        output += content.slice(cursor, start).replace(/```(?:json)?\s*$/i, '') + result.text;
        return view(output, result.kind, result.pending);
      }
      break;
    }
    let result: MessageView | null = null;
    try {
      result = formatted(JSON.parse(content.slice(start, end)));
    }
    catch {
      if (clue(tail) !== 'internal')
        result = unfinished(clue(tail), options.streaming);
    }
    if (result) {
      output += content.slice(cursor, start).replace(/```(?:json)?\s*$/i, '') + result.text;
      cursor = end;
      const fence = content.slice(cursor).match(/^\s*```/);
      if (fence)
        cursor += fence[0].length;
      found = result.kind;
      pending ||= result.pending;
    }
    start = end - 1;
  }
  if (found !== 'plain')
    return view(output + content.slice(cursor), found, pending);
  if (options.streaming && /^\s*```(?:j(?:s(?:on?)?)?)?\s*$/i.test(content))
    return unfinished('internal', true);
  return view(content);
}
export function messagePreview(content: string, role: 'assistant' | 'user' = 'assistant'): string {
  return presentMessage(content, { role }).text.replace(/\s+/g, ' ').trim().slice(0, 80);
}
export function messageTitle(content: string): string {
  const result = presentMessage(content);
  if (!result.structured)
    return content;
  return result.kind === 'extraction' ? '模板信息提取' : result.kind === 'elements' ? '文书要素整理' : result.kind === 'document' ? '文书草稿' : result.kind === 'form' ? '模板文书起草' : result.kind === 'review' ? '合同审查' : '会话记录';
}
