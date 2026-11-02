export const providerOptions = [
  { label: 'OpenAI', value: 'openai' },
  { label: '深度求索', value: 'deepseek' },
  { label: '智谱AI', value: 'zhipu' },
  { label: '小米MiMo', value: 'xiaomi' },
  { label: '阿里云百炼', value: 'qianwen' },
  { label: 'PPIO', value: 'ppio' },
  { label: 'Atlas Cloud', value: 'atlas' },
  { label: 'MiniMax', value: 'minimax' },
  { label: 'Dify', value: 'dify' },
  { label: 'Coze / 扣子', value: 'coze' },
  { label: 'Ollama', value: 'ollama' },
  { label: 'Hugging Face TEI（向量 / 重排）', value: 'tei' },
  { label: 'vLLM', value: 'vllm' },
  { label: '自定义 OpenAI', value: 'custom_api' },
  { label: '自定义 Anthropic', value: 'custom_anthropic' },
] as const;

export function getCustomProviderConfig(providerCode: unknown) {
  if (providerCode === 'tei') {
    return {
      protocol: 'TEI Embedding / Rerank',
      apiHostPlaceholder: 'http://模型服务器地址:8081（重排模型使用8082）',
    };
  }
  if (providerCode === 'vllm') {
    return {
      protocol: 'OpenAI Chat Completions',
      apiHostPlaceholder: 'http://模型服务器地址:8000/v1',
    };
  }
  if (providerCode === 'custom_api') {
    return {
      protocol: 'OpenAI Chat Completions',
      apiHostPlaceholder: 'https://服务商地址/v1',
    };
  }
  if (providerCode === 'custom_anthropic') {
    return {
      protocol: 'Anthropic Messages',
      apiHostPlaceholder: 'https://服务商地址/v1',
    };
  }
  return null;
}

export const providerStatusOptions = [
  { label: '启用', value: '0' },
  { label: '停用', value: '1' },
];
