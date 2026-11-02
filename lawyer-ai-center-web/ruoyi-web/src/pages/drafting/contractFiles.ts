export const MAX_CONTRACT_BYTES = 200 * 1024 * 1024;
export interface ContractFileSize {
  name: string;
  size: number;
}
export function contractBytes(files: readonly ContractFileSize[]) {
  return files.reduce((total, file) => total + file.size, 0);
}
export function validateDraftingFiles(
  current: readonly ContractFileSize[],
  incoming: readonly ContractFileSize[],
) {
  for (const file of incoming) {
    if (!/\.(pdf|docx|jpe?g|png)$/i.test(file.name)) throw Error("请选择PDF、DOCX、JPG或PNG文件");
    if (!Number.isSafeInteger(file.size) || file.size <= 0) throw Error("请选择非空的合同文件");
  }
  if (contractBytes(current) + contractBytes(incoming) > MAX_CONTRACT_BYTES)
    throw Error("本批合同文件总大小不能超过200MB，请减少文件后重新选择");
}
