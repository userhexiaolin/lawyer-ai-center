import type { Ref } from 'vue';
import type { ManagedTemplate } from '@/api/document/templateManagement';
import { onScopeDispose, ref, watch } from 'vue';
import {
  parseTemplateSource,
  previewTemplateSource,
  uploadTemplateSource,
} from '@/api/document/templateManagement';
import { failureMessage } from './workflow';

export function useTemplateSource(form: Ref<ManagedTemplate>) {
  const working = ref(false);
  const error = ref('');
  const previewLoading = ref(false);
  const previewError = ref('');
  const previewUrl = ref('');
  let version = 0;
  let previewVersion = 0;
  let controller: AbortController | undefined;
  let previewController: AbortController | undefined;
  let automaticName = '';
  function clearPreview() {
    previewVersion++;
    previewController?.abort();
    if (previewUrl.value)
      URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = '';
    previewError.value = '';
    previewLoading.value = false;
  }
  onScopeDispose(() => {
    version++;
    controller?.abort();
    clearPreview();
  });
  watch(() => form.value.sourceVersion, clearPreview);
  function apply(result: ManagedTemplate) {
    if (!result.sourceVersion || !result.content?.trim())
      throw new Error('未读取到模板正文，请重新选择可编辑的文件');
    const fields = result.blocks?.some(b => b.fields !== undefined && b.fields !== null)
      ? result.blocks.flatMap(b => b.fields || [])
      : undefined;
    Object.assign(form.value, {
      content: result.content,
      sourceVersion: result.sourceVersion,
      sourceFilename: result.sourceFilename,
      sourceFormat: result.sourceFormat,
      parseStatus: result.parseStatus,
      analysisWarning: result.analysisWarning,
      blocks: result.blocks || [],
      fields,
      editableBlocks: result.blocks?.map(b => b.id) || [],
      fieldsConfirmed: false,
    });
  }
  async function selectFile(file: File) {
    if (working.value)
      return;
    error.value = '';
    if (
      !/\.(?:docx|doc|rtf|odt|pdf|txt|md)$/i.test(file.name)
      || !file.size
      || file.size > 10 * 1024 * 1024
    ) {
      error.value = '请选择非空且不超过10MB的 DOCX、DOC、RTF、ODT、文字PDF、TXT 或 Markdown 文件';
      return;
    }
    if (!form.value.documentType) {
      error.value = '请先选择启用的模板分类';
      return;
    }
    const current = ++version;
    controller?.abort();
    controller = new AbortController();
    working.value = true;
    try {
      const result = await uploadTemplateSource(file, form.value.documentType, controller.signal);
      if (current !== version)
        return;
      apply(result);
      const name = file.name.replace(/\.[^.]+$/, '').slice(0, 100);
      if (!form.value.name.trim() || form.value.name === automaticName)
        form.value.name = name;
      automaticName = name;
    }
    catch (reason) {
      if (current === version)
        error.value = `${failureMessage(reason)} 原有内容未被替换，可重新选择文件。`;
    }
    finally {
      if (current === version)
        working.value = false;
    }
  }
  async function parseExisting() {
    if (!form.value.sourceVersion || working.value)
      return;
    const current = ++version;
    working.value = true;
    error.value = '';
    try {
      const result = await parseTemplateSource(form.value.sourceVersion, form.value.documentType);
      if (current === version)
        apply(result);
    }
    catch (reason) {
      if (current === version)
        error.value = failureMessage(reason);
    }
    finally {
      if (current === version)
        working.value = false;
    }
  }
  async function preview() {
    if (!form.value.sourceVersion || previewLoading.value)
      return;
    clearPreview();
    const current = previewVersion;
    previewController = new AbortController();
    previewLoading.value = true;
    try {
      const blob = await previewTemplateSource(form.value.sourceVersion, previewController.signal);
      if (current === previewVersion)
        previewUrl.value = URL.createObjectURL(blob);
    }
    catch (reason) {
      if (current === previewVersion)
        previewError.value = failureMessage(reason);
    }
    finally {
      if (current === previewVersion)
        previewLoading.value = false;
    }
  }
  return {
    working,
    error,
    previewLoading,
    previewError,
    previewUrl,
    selectFile,
    parseExisting,
    preview,
  };
}
