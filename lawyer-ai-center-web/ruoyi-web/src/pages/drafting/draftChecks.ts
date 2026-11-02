/** Heuristic disclosure, not a completeness or legal accuracy guarantee. */
export function draftWarnings(draft:string,input:string) {
  const warnings:string[]=[];
  for(const word of ['庭审','开庭','多次','微信','公证'])if(draft.includes(word)&&(!input.includes(word)||new RegExp('(?:未提供|没有|未曾|无).{0,8}'+word).test(input)))warnings.push(`草稿出现要素未提供或已否认的“${word}”，请核实或删除`);
  if(/[（(]20\d{2}[)）].{0,15}民.{0,5}\d+号/.test(draft)&&!/[（(]20\d{2}[)）].{0,15}民.{0,5}\d+号/.test(input))warnings.push('草稿出现未提供的案号，请删除并核对');
  return warnings;
}
