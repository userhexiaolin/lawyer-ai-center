import type { DocumentTypeOption } from '#/api/legal/doc-template';

const aliases = new Map<string, string[]>([
  ['合同文书', ['合同']],
  ['民事答辩状', ['答辩状']],
  ['民事起诉状', ['起诉状']],
]);
const normalize = (value: string) =>
  value.normalize('NFKC').toLowerCase().replaceAll(/\s+/g, '');

function uniqueType(matches: { keyword: string; type: string }[]) {
  const specific = matches.filter(
    (match) =>
      !matches.some(
        (other) =>
          other.keyword !== match.keyword &&
          other.keyword.includes(match.keyword),
      ),
  );
  const types = new Set(specific.map((match) => match.type));
  return types.size === 1 ? [...types][0] : undefined;
}

export function templateTypeFromFilename(
  filename: string,
  options: DocumentTypeOption[],
): string | undefined {
  const name = normalize(filename.replace(/\.[^.]+$/, ''));
  const enabled = options.filter((option) => !option.disabled);
  const matches = enabled.flatMap((option) =>
    [option.value, option.label]
      .map((keyword) => normalize(keyword))
      .filter((keyword) => keyword && name.includes(keyword))
      .map((keyword) => ({ keyword, type: option.value })),
  );
  if (matches.length > 0) return uniqueType(matches);
  return uniqueType(
    enabled.flatMap((option) =>
      (aliases.get(option.value) || aliases.get(option.label) || [])
        .filter((keyword) => name.includes(normalize(keyword)))
        .map((keyword) => ({ keyword, type: option.value })),
    ),
  );
}
