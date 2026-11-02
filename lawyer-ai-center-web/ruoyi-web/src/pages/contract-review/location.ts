export interface TextSegment { text: string; highlighted: boolean }
export function locateClause(source: string, clause: string): { segments: TextSegment[]; found: boolean; repeated: boolean } {
  const offsets: number[] = []; let compact = '';
  for (let i = 0; i < source.length; i++) {
    if (/\s/.test(source[i] || '')) continue;
    compact += source[i]; offsets.push(i);
  }
  const needle = clause.replace(/\s/g, '');
  const index = needle ? compact.indexOf(needle) : -1;
  if (index < 0) return { segments: [{ text: source, highlighted: false }], found: false, repeated: false };
  const start = offsets[index]!; const end = offsets[index + needle.length - 1]! + 1;
  return { segments: [
    { text: source.slice(0, start), highlighted: false },
    { text: source.slice(start, end), highlighted: true },
    { text: source.slice(end), highlighted: false },
  ], found: true, repeated: compact.indexOf(needle, index + needle.length) >= 0 };
}

/** Only recover the source delimited by our own review workflow, never guess from an AI reply. */
export function extractContractText(message: string): string {
  const begin = '\n---合同正文开始---\n'; const end = '\n---合同正文结束---';
  const start = message.indexOf(begin); const finish = message.lastIndexOf(end);
  return start >= 0 && finish > start ? message.slice(start + begin.length, finish) : '';
}
