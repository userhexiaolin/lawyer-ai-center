import type { DraftingReady } from './generation';
import type { TemplateFormSchema } from '@/api/document/templates';
import { useHookFetch } from 'hook-fetch/vue';
import { onScopeDispose, watch } from 'vue';
import { send } from '@/api/chat';
import { readMediaApiResult } from '@/api/media/response';
import { postFile } from '@/api/oss';
import { create_session } from '@/api/session';
import { decodeReviewEvents, sessionIdFrom } from '@/pages/contract-review/workflow';
import { useUserStore } from '@/stores';
import { extractionPrompt, parseElements } from './contractElements';
import { validateDraftingFiles } from './contractFiles';
import { documentText } from './documentText';
import { prepareDrafting } from './generation';
import { parseTemplateExtraction, templateExtractionPrompt } from './templateForm';

export function useContractElements() {
  const user = useUserStore();
  const { stream, cancel } = useHookFetch({ request: send });
  let epoch = 0;
  let controller = new AbortController();
  function stop() {
    epoch++;
    controller.abort();
    controller = new AbortController();
    cancel();
  }
  function context() {
    const id = epoch;
    const token = user.token;
    return () => id === epoch && token === user.token && !controller.signal.aborted;
  }
  async function complete(
    content: string,
    title: string,
    ready: DraftingReady,
    active: () => boolean,
  ) {
    if (!active())
      throw new Error('已停止');
    const response = await readMediaApiResult<unknown>(create_session({ sessionTitle: title }));
    if (!active())
      throw new Error('已停止');
    const sessionId = sessionIdFrom(response.data);
    let text = '';
    let done = false;
    for await (const chunk of stream({ ...ready, sessionId, content, enableThinking: false })) {
      if (!active())
        throw new Error('已停止');
      for (const event of decodeReviewEvents(chunk)) {
        if (event.error || event.event === 'error' || ('code' in event && event.code !== 200))
          throw new Error(String(event.error || event.msg || '生成失败'));
        if (typeof event.content === 'string')
          text += event.content;
        if (event.done || event.event === 'done')
          done = true;
      }
      if (text.length > 200000)
        throw new Error('返回内容超过长度限制');
    }
    if (!active())
      throw new Error('已停止');
    if (!done || !text.trim())
      throw new Error('未收到完整结果，请重试');
    return { sessionId, content: documentText(text) };
  }
  async function extract(file: File, active: () => boolean) {
    validateDraftingFiles([], [file]);
    const ready = await prepareDrafting();
    if (!active())
      throw new Error('已停止');
    const result = await postFile<{ text: string }>(
      '/system/contract-review/drafting-content',
      file,
      {
        signal: controller.signal,
        timeout: 180000,
      },
    );
    if (!active())
      throw new Error('已停止');
    const answer = await complete(
      extractionPrompt(result.text),
      `合同要素提取 - ${file.name}`,
      ready,
      active,
    );
    return parseElements(answer.content, result.text);
  }
  async function extractTemplate(file: File, schema: TemplateFormSchema, active: () => boolean) {
    validateDraftingFiles([], [file]);
    const ready = await prepareDrafting();
    if (!active())
      throw new Error('已停止');
    const result = await postFile<{ text: string }>('/system/contract-review/drafting-content', file, {
      signal: controller.signal,
      timeout: 180000,
    });
    if (!active())
      throw new Error('已停止');
    const answer = await complete(templateExtractionPrompt(schema, result.text), `模板字段提取 - ${file.name}`, ready, active);
    if (!active())
      throw new Error('已停止');
    return { sourceText: result.text, values: parseTemplateExtraction(answer.content, schema, result.text) };
  }
  watch(() => user.token, stop, { flush: 'sync' });
  onScopeDispose(stop);
  return { stop, context, extract, extractTemplate, complete };
}
