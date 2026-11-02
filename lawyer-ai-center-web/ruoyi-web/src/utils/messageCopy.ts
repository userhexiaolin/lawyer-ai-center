import { citationLabel, citationTarget, citationWarning, splitLegalContent } from './legalCitation';
import { presentMessage } from './messagePresentation';

/** Copy the readable answer and source labels, preserving the stored model protocol. */
export function messageCopyText(content: string, role: 'user' | 'assistant' = 'assistant'): string {
  const source = splitLegalContent(content);
  const display = presentMessage(source.markdown, { role });
  if (!source.citations.length)
    return display.text;
  const citations = source.citations.map((citation) => {
    const target = citationTarget(citation);
    return [
      citationLabel(citation),
      citationWarning(citation),
      target.kind === 'source' ? target.value : target.kind === 'fragment' ? '请在原会话打开来源核对' : '未提供可访问来源',
    ].filter(Boolean).join('；');
  });
  return `${display.text}\n\n引用来源（请核对）：\n${citations.map((citation, index) => `${index + 1}. ${citation}`).join('\n')}`;
}
