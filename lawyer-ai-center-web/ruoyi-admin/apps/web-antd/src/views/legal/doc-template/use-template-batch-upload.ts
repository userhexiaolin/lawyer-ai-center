import type { Ref } from 'vue';

import type { DocTemplate, DocumentTypeOption } from '#/api/legal/doc-template';

import { computed, onScopeDispose, ref, watch } from 'vue';

import {
  analyzeTemplateFields,
  classifyTemplateType,
  saveTemplate,
  uploadTemplate,
} from '#/api/legal/doc-template';

import { templateTypeFromFilename } from './template-filename-type';

export interface UploadQueueItem {
  key: number;
  file: File;
  name: string;
  documentType: string;
  state: 'analyzing' | 'failed' | 'queued' | 'saved' | 'saving' | 'uploading';
  error: string;
  warning: string;
  filenameType?: string;
  typeOrigin: 'AI' | 'default' | 'filename' | 'manual';
  typeRecognizing: boolean;
  typeQueued: boolean;
  typeRevision: number;
  typeWarning: string;
  staged?: Awaited<ReturnType<typeof uploadTemplate>>;
  analyzed?: boolean;
  parse?: boolean;
}
export function useTemplateBatchUpload(
  open: Ref<boolean>,
  options: () => DocumentTypeOption[],
  onSaved: () => void,
) {
  const items = ref<UploadQueueItem[]>([]);
  const parseOnUpload = ref(false);
  const busy = ref(false);
  const stopRequested = ref(false);
  const error = ref('');
  const defaultType = ref('');
  const typeRecognitionEnabled = ref(true);
  const completed = computed(
    () => items.value.filter((row) => row.state === 'saved').length,
  );
  let key = 0;
  let generation = 0;
  const typeQueue: { explicit: boolean; key: number; revision: number }[] = [];
  let typeRequest:
    | undefined
    | {
        controller: AbortController;
        generation: number;
        key: number;
        revision: number;
      };

  function cancelTypeRecognition() {
    typeQueue.length = 0;
    typeRequest?.controller.abort();
    typeRequest = undefined;
    for (const row of items.value) {
      row.typeRecognizing = false;
      row.typeQueued = false;
    }
  }
  onScopeDispose(() => {
    generation++;
    stopRequested.value = true;
    cancelTypeRecognition();
  });
  watch(
    open,
    (value) => {
      generation++;
      cancelTypeRecognition();
      if (!value) {
        stopRequested.value = true;
        return;
      }
      items.value = [];
      parseOnUpload.value = false;
      typeRecognitionEnabled.value = true;
      error.value = '';
      defaultType.value =
        options().find((option) => !option.disabled)?.value || '';
      stopRequested.value = false;
    },
    { immediate: true, flush: 'sync' },
  );
  watch(typeRecognitionEnabled, (enabled) => {
    if (!enabled) {
      cancelTypeRecognition();
      return;
    }
    for (const row of items.value) {
      if (
        !row.filenameType &&
        row.typeOrigin === 'default' &&
        !row.staged &&
        typeRequest?.key !== row.key &&
        !typeQueue.some((item) => item.key === row.key)
      ) {
        row.typeQueued = true;
        typeQueue.push({
          key: row.key,
          revision: row.typeRevision,
          explicit: false,
        });
      }
    }
    void recognizeNextType();
  });

  async function recognizeNextType() {
    if (typeRequest || busy.value || !open.value) return;
    const intent = typeQueue.shift();
    if (!intent) return;
    const { key } = intent;
    const row = items.value.find((item) => item.key === key);
    if (row) row.typeQueued = false;
    if (
      !row ||
      row.staged ||
      row.typeRevision !== intent.revision ||
      (!intent.explicit && row.typeOrigin === 'manual')
    ) {
      void recognizeNextType();
      return;
    }
    const request = {
      controller: new AbortController(),
      generation,
      key,
      revision: row.typeRevision,
    };
    typeRequest = request;
    row.typeRecognizing = true;
    const current = () =>
      typeRequest === request &&
      request.generation === generation &&
      open.value &&
      !busy.value &&
      items.value.includes(row) &&
      !row.staged &&
      row.typeRevision === request.revision;
    try {
      const result = await classifyTemplateType(
        row.file,
        request.controller.signal,
      );
      if (!current()) return;
      if (
        result.documentType &&
        options().some(
          (type) => type.value === result.documentType && !type.disabled,
        )
      ) {
        row.documentType = result.documentType;
        row.typeOrigin = 'AI';
        row.typeWarning = `已切换为“${result.documentType}”，上传后保存；可继续手动修改${result.warning ? `；${result.warning}` : ''}`;
      } else {
        row.typeWarning =
          result.warning?.trim() ||
          '未能识别类型，保持当前类型；可重试、手动选择或直接上传';
      }
    } catch {
      if (current())
        row.typeWarning =
          '后台类型识别暂未完成，保持当前类型；可直接上传或重试';
    } finally {
      if (typeRequest === request) {
        typeRequest = undefined;
        row.typeRecognizing = false;
        void recognizeNextType();
      }
    }
  }

  function retryType(key: number) {
    const row = items.value.find((item) => item.key === key);
    if (!row || busy.value || !open.value || row.staged || row.typeRecognizing)
      return;
    row.typeRevision++;
    const queued = typeQueue.findIndex((item) => item.key === key);
    if (queued !== -1) typeQueue.splice(queued, 1);
    row.typeWarning = '';
    row.typeQueued = true;
    typeQueue.unshift({ key, revision: row.typeRevision, explicit: true });
    void recognizeNextType();
  }

  function selectType(key: number, value: string) {
    const row = items.value.find((item) => item.key === key);
    if (!row || busy.value || row.staged) return;
    row.documentType = value;
    row.typeRevision++;
    row.typeOrigin = 'manual';
    row.typeWarning = '';
    const queued = typeQueue.findIndex((item) => item.key === key);
    if (queued !== -1) typeQueue.splice(queued, 1);
    row.typeQueued = false;
    if (typeRequest?.key === key) {
      typeRequest.controller.abort();
      typeRequest = undefined;
      row.typeRecognizing = false;
      void recognizeNextType();
    }
  }
  function addFiles(files: File[]) {
    if (busy.value) return;
    error.value = '';
    for (const file of files) {
      if (
        items.value.some(
          (row) =>
            row.file.name === file.name &&
            row.file.size === file.size &&
            row.file.lastModified === file.lastModified,
        )
      )
        continue;
      if (items.value.length >= 20) {
        error.value = '每批最多20份文件，请完成本批后继续上传';
        break;
      }
      if (
        !/\.(?:docx|pdf|txt|md|doc|rtf|odt)$/i.test(file.name) ||
        file.size === 0 ||
        file.size > 10 * 1024 * 1024
      ) {
        error.value = `“${file.name}”格式不支持、为空或超过10MB，请重新选择`;
        continue;
      }
      const filenameType = templateTypeFromFilename(file.name, options());
      const row: UploadQueueItem = {
        key: ++key,
        file,
        name: file.name.replace(/\.[^.]+$/, '').slice(0, 100),
        documentType: filenameType || defaultType.value,
        filenameType,
        typeOrigin: filenameType ? 'filename' : 'default',
        typeRecognizing: false,
        typeQueued: !filenameType && typeRecognitionEnabled.value,
        typeRevision: 0,
        typeWarning: '',
        state: 'queued',
        error: '',
        warning: '',
      };
      items.value.push(row);
      if (!filenameType && typeRecognitionEnabled.value)
        typeQueue.push({
          key: row.key,
          revision: row.typeRevision,
          explicit: false,
        });
    }
    void recognizeNextType();
  }
  function remove(key: number) {
    if (busy.value) return;
    if (typeRequest?.key === key) {
      typeRequest.controller.abort();
      typeRequest = undefined;
    }
    items.value = items.value.filter((row) => row.key !== key);
    void recognizeNextType();
  }
  async function start() {
    if (busy.value || !open.value) return;
    // Classification is optional: upload never waits for it and late responses cannot change the payload.
    cancelTypeRecognition();
    const pending = items.value.filter((row) => row.state !== 'saved');
    if (pending.length === 0) return;
    if (
      pending.some(
        (row) =>
          !row.name.trim() ||
          row.name.trim().length > 100 ||
          !options().some(
            (option) => option.value === row.documentType && !option.disabled,
          ),
      )
    ) {
      error.value = '请填写每份模板的名称并选择启用的模板类型';
      return;
    }
    const current = generation;
    busy.value = true;
    stopRequested.value = false;
    error.value = '';
    let savedAny = false;
    try {
      for (const row of pending) {
        if (stopRequested.value || current !== generation || !open.value) break;
        row.error = '';
        // Retry continues the existing staged version; successful templates are skipped.
        if (!row.staged) row.parse = parseOnUpload.value;
        try {
          if (!row.staged) {
            row.state = 'uploading';
            row.staged = await uploadTemplate(
              row.file,
              row.documentType,
              row.parse,
            );
          }
          if (current !== generation || !open.value) break;
          if (row.parse && !row.analyzed) {
            row.state = 'analyzing';
            if (!row.staged.sourceVersion)
              throw new Error('未收到有效的上传版本');
            const analysis = await analyzeTemplateFields(
              row.staged.sourceVersion,
            );
            row.staged = {
              ...row.staged,
              parseStatus: analysis.method === 'AI' ? 'parsed' : 'rules',
              analysisWarning: analysis.warning,
              blocks: row.staged.blocks?.map((block) => ({
                ...block,
                fields: analysis.fields.filter(
                  (field) => field.blockId === block.id,
                ),
              })),
            };
            row.warning = analysis.warning;
            row.analyzed = true;
          }
          if (current !== generation || !open.value) break;
          row.state = 'saving';
          const payload: DocTemplate = {
            ...row.staged,
            name: row.name.trim(),
            documentType: row.documentType,
            sortOrder: 0,
            status: '0',
            remark: '',
            saveDraft: true,
            fieldsConfirmed: false,
            editableBlocks: row.staged.blocks?.map((block) => block.id) || [],
            fields:
              row.staged.blocks?.flatMap((block) => block.fields || []) || [],
          };
          await saveTemplate(payload);
          row.state = 'saved';
          row.typeWarning = '';
          savedAny = true;
        } catch (error_) {
          row.state = 'failed';
          row.error = (error_ as Error).message || '处理失败，请重试';
        }
      }
    } finally {
      busy.value = false;
      if (savedAny && current === generation) onSaved();
    }
  }
  return {
    items,
    parseOnUpload,
    busy,
    stopRequested,
    error,
    defaultType,
    typeRecognitionEnabled,
    completed,
    addFiles,
    remove,
    selectType,
    retryType,
    start,
  };
}
