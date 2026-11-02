import type { Ref } from 'vue';

import type { DocTemplate, DocumentTypeOption } from '#/api/legal/doc-template';

import { computed, onScopeDispose, reactive, ref, watch } from 'vue';

import {
  analyzeTemplateFields,
  parseTemplateVersion,
  saveTemplate,
  uploadTemplate,
} from '#/api/legal/doc-template';

export function useTemplateUpload(
  open: Ref<boolean>,
  options: () => DocumentTypeOption[],
  onSaved: () => void,
  initial: () => Partial<DocTemplate> = () => ({}),
) {
  const fresh = (): DocTemplate => ({
    name: '',
    documentType: options().find((type) => !type.disabled)?.value || '',
    content: '',
    sortOrder: 0,
    status: '0',
    remark: '',
  });
  const form = reactive(fresh());
  const analysisWarning = ref('');
  const parseOnUpload = ref(false);
  const unparsed = computed(() => form.parseStatus === 'uploaded');
  const statusText = computed(() => {
    if (uploading.value) return '上传中';
    if (analyzing.value) return '解析中';
    if (unparsed.value) return '未解析';
    return form.fieldsConfirmed ? '已确认' : '待核对';
  });
  const analyzing = ref(false);
  const error = ref('');
  const fileName = ref('');
  const saving = ref(false);
  const uploading = ref(false);
  const ready = computed(
    () =>
      !!fileName.value &&
      (!!form.sourceVersion || !!form.content.trim()) &&
      !uploading.value &&
      !analyzing.value,
  );
  let initializing = false;
  let boundVersion: string | undefined;
  let automaticName = '';
  let version = 0;

  watch(
    open,
    (value) => {
      version++;
      uploading.value = false;
      analyzing.value = false;
      analysisWarning.value = '';
      saving.value = false;
      error.value = '';
      if (value) {
        initializing = true;
        parseOnUpload.value = false;
        Object.assign(
          form,
          fresh(),
          {
            id: undefined,
            expectedRevision: undefined,
            fieldAnalysis: undefined,
            sourceVersion: undefined,
            sourceFormat: undefined,
            blocks: [],
            editableBlocks: [],
            fields: undefined,
            fieldsConfirmed: false,
            parseStatus: undefined,
            sourceFilename: undefined,
            configureExisting: false,
            analysisWarning: '',
            saveDraft: false,
          },
          initial(),
        );
        fileName.value = '';
        automaticName = '';
        boundVersion = form.id ? form.sourceVersion : undefined;
        if (form.configureExisting && form.sourceVersion) {
          fileName.value =
            form.sourceFilename ||
            `${form.name}.${form.sourceFormat || 'docx'}`;
          const blocks = form.blocks || [];
          form.fields = blocks.some(
            (b) => b.fields !== undefined && b.fields !== null,
          )
            ? blocks.flatMap((b) => b.fields || [])
            : undefined;
          form.editableBlocks =
            initial().editableBlocks ??
            blocks.filter((b) => b.editable).map((b) => b.id);
          analysisWarning.value = form.fieldsConfirmed
            ? ''
            : form.analysisWarning || '';
        }
        initializing = false;
      } else {
        initializing = false;
      }
    },
    { flush: 'sync', immediate: true },
  );
  watch(
    [() => form.fields, () => form.editableBlocks],
    () => {
      if (!initializing) form.fieldsConfirmed = false;
    },
    { deep: true, flush: 'sync' },
  );
  onScopeDispose(() => {
    version++;
  });

  async function selectFile(file: File) {
    if (!open.value || uploading.value || analyzing.value || saving.value)
      return;
    const current = ++version;
    form.content = '';
    form.parseStatus = undefined;
    analysisWarning.value = '';
    form.sourceVersion = undefined;
    form.blocks = [];
    form.editableBlocks = [];
    form.fields = undefined;
    form.fieldsConfirmed = false;
    fileName.value = '';
    error.value = '';
    if (!/\.(?:docx|pdf|txt|md|doc|rtf|odt)$/i.test(file.name)) {
      error.value = '仅支持 DOCX、PDF、DOC、RTF、ODT、TXT 或 Markdown 文件';
      return;
    }
    if (file.size === 0 || file.size > 10 * 1024 * 1024) {
      error.value = '请选择非空且不超过10MB的模板文件';
      return;
    }
    if (
      !options().some(
        (type) => type.value === form.documentType && !type.disabled,
      )
    ) {
      error.value = '请先选择启用的模板类型';
      return;
    }
    const name = file.name
      .replace(/^.*[\\/]/, '')
      .replace(/\.[^.]+$/, '')
      .slice(0, 100);
    if (!form.name.trim() || form.name === automaticName) form.name = name;
    automaticName = name;
    fileName.value = file.name;
    uploading.value = true;
    try {
      const result = await uploadTemplate(
        file,
        form.documentType,
        parseOnUpload.value,
      );
      if (version !== current || !open.value) return;
      if (result.parseStatus !== 'uploaded' && !result.content.trim())
        throw new Error('未读取到模板正文，请重新选择文件');
      form.parseStatus = result.parseStatus;
      form.sourceFilename = file.name;
      form.content = result.content;
      form.sourceVersion = result.sourceVersion;
      form.sourceFormat = result.sourceFormat;
      form.blocks = result.blocks || [];
      const mapped = form.blocks.some(
        (b) => b.fields !== undefined && b.fields !== null,
      );
      form.fields = mapped
        ? form.blocks.flatMap((b) => b.fields || [])
        : undefined;
      form.editableBlocks = form.blocks.map((block) => block.id);
      if (mapped && parseOnUpload.value) await analyzeFields(false);
    } catch (error_) {
      if (version === current && open.value)
        error.value = (error_ as Error).message || '模板文件上传失败，请重试';
    } finally {
      if (version === current) uploading.value = false;
    }
  }
  async function analyzeFields(reparse = true) {
    if (!open.value || !form.sourceVersion || analyzing.value || saving.value)
      return;
    const current = version;
    let source = form.sourceVersion;
    analyzing.value = true;
    analysisWarning.value = '';
    try {
      if (reparse || unparsed.value || source === boundVersion) {
        const parsed = await parseTemplateVersion(source, form.documentType);
        if (version !== current || !open.value || source !== form.sourceVersion)
          return;
        const oldSelection = form.editableBlocks;
        const wasUnparsed = unparsed.value;
        form.content = parsed.content;
        const nextSource = parsed.sourceVersion;
        if (!nextSource) throw new Error('未收到有效的解析版本');
        form.sourceVersion = source = nextSource;
        form.sourceFormat = parsed.sourceFormat;
        form.blocks = parsed.blocks || [];
        form.fields = form.blocks.flatMap((b) => b.fields || []);
        form.editableBlocks = wasUnparsed
          ? form.blocks.map((b) => b.id)
          : oldSelection || form.blocks.map((b) => b.id);
        form.parseStatus = parsed.parseStatus || 'rules';
        form.fieldsConfirmed = false;
      }
      const result = await analyzeTemplateFields(source);
      if (version !== current || !open.value || source !== form.sourceVersion)
        return;
      form.parseStatus = result.method === 'AI' ? 'parsed' : 'rules';
      form.fields = result.fields;
      form.fieldsConfirmed = false;
      analysisWarning.value = result.warning;
    } catch (error_) {
      if (version === current && open.value)
        analysisWarning.value =
          (error_ as Error).message ||
          'AI识别失败，保留规则候选，可人工核对或重试';
    } finally {
      if (version === current) analyzing.value = false;
    }
  }

  function removeFile() {
    if (uploading.value || analyzing.value || saving.value) return;
    version++;
    if (form.name === automaticName) form.name = '';
    automaticName = '';
    fileName.value = '';
    form.content = '';
    form.sourceVersion = undefined;
    form.blocks = [];
    form.editableBlocks = [];
    form.fields = undefined;
    form.fieldsConfirmed = false;
    form.parseStatus = undefined;
    error.value = '';
  }

  function selectAllBlocks() {
    if (!open.value || uploading.value || analyzing.value || saving.value)
      return;
    form.editableBlocks = (form.blocks || []).map((block) => block.id);
  }

  async function submit() {
    if (!open.value || uploading.value || analyzing.value || saving.value)
      return;
    error.value = '';
    if (!ready.value) {
      error.value = '请选择模板文件并等待处理完成';
      return;
    }
    if (!form.name.trim() || form.name.trim().length > 100) {
      error.value = '请填写不超过100字的模板名称';
      return;
    }
    if (
      !options().some(
        (type) => type.value === form.documentType && !type.disabled,
      )
    ) {
      error.value = '请选择启用的模板类型';
      return;
    }
    if (
      !Number.isInteger(form.sortOrder) ||
      form.sortOrder < 0 ||
      form.sortOrder > 999_999
    ) {
      error.value = '排序号须为0至999999的整数';
      return;
    }
    if (!['0', '1'].includes(form.status)) {
      error.value = '请选择启用或停用状态';
      return;
    }
    if (form.remark.length > 500) {
      error.value = '备注不超过500字';
      return;
    }
    const current = version;
    if (!unparsed.value && form.sourceVersion && !form.editableBlocks?.length) {
      error.value = '请勾选允许按本案内容填写的段落';
      return;
    }
    if (!unparsed.value && form.sourceVersion && !form.fieldsConfirmed) {
      error.value = '请核对填写字段或段落，并勾选确认后保存';
      return;
    }
    if (
      !unparsed.value &&
      form.fields !== undefined &&
      (!form.fieldsConfirmed || !form.fields.some((f) => f.enabled))
    ) {
      error.value =
        '请核对并确认至少一个填写字段；原模板无可填写区域时不能启用保真填写';
      return;
    }
    saving.value = true;
    try {
      const {
        configureExisting: _configureExisting,
        layoutConfirmed: _layoutConfirmed,
        ...payload
      } = form;
      await saveTemplate({
        ...payload,
        name: form.name.trim(),
        ...(unparsed.value ? { saveDraft: true } : {}),
      });
      if (version !== current || !open.value) return;
      open.value = false;
      onSaved();
    } catch (error_) {
      if (version === current && open.value)
        error.value = (error_ as Error).message || '模板保存失败，请重试';
    } finally {
      if (version === current) saving.value = false;
    }
  }

  return {
    form,
    parseOnUpload,
    unparsed,
    statusText,
    fileName,
    uploading,
    analyzing,
    analysisWarning,
    saving,
    error,
    ready,
    selectFile,
    removeFile,
    selectAllBlocks,
    analyzeFields,
    submit,
  };
}
