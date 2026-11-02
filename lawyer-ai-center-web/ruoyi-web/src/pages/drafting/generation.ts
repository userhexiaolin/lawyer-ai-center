import { lawyerRequest } from '@/api/lawyer';
export interface DraftingReady { agentId: string; model: string }
export async function prepareDrafting(): Promise<DraftingReady> {
  return (await lawyerRequest<{data:DraftingReady}>('/legal/drafting/prepare')).data;
}
export function generationError(error: unknown): string {
  const value=error as {message?:string;result?:{msg?:string};msg?:string};
  const text=value?.result?.msg||value?.msg||value?.message||'生成请求未完成，请重试';
  return text.replace(/\s+at\s+[\s\S]*/, '').slice(0,300);
}
