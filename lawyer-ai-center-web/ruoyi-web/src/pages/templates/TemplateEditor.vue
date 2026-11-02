<script setup lang="ts">
import type { FormInstance } from 'element-plus';
import type { ManagedTemplate, TemplateCategoryOption } from '@/api/document/templateManagement';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onScopeDispose, ref, watch } from 'vue';
import { saveManagedTemplate, saveTemplateMetadata } from '@/api/document/templateManagement';
import { useUserStore } from '@/stores';
import TemplateFields from './TemplateFields.vue';
import { useTemplateSource } from './useTemplateSource';
import { useUnsavedEditor } from './useUnsavedEditor';
import { failureMessage, hasTemplatePermission, inputError, templateState } from './workflow';

const props = defineProps<{
  initial: Partial<ManagedTemplate>;
  mode: 'metadata' | 'source';
  types: TemplateCategoryOption[];
  permissions: string[];
}>();
const emit = defineEmits<{ saved: [] }>();
const open = defineModel<boolean>({ required: true });
const user = useUserStore();
const form = ref<ManagedTemplate>({
  name: '',
  documentType: props.types.find(t => !t.disabled)?.value || '',
  content: '',
  sortOrder: 0,
  status: '0',
  remark: '',
  ...props.initial,
  fields: props.initial.blocks?.some(b => b.fields != null)
    ? props.initial.blocks.flatMap(b => b.fields || [])
    : undefined,
  editableBlocks:
    props.initial.editableBlocks
    || props.initial.blocks?.filter(b => b.editable).map(b => b.id)
    || [],
});
const editableBlocks = computed({
  get: () => form.value.editableBlocks || [],
  set: (value: string[]) => {
    form.value.editableBlocks = value;
  },
});
const formRef = ref<FormInstance>();
const saving = ref(false);
const confirming = ref(false);
const error = ref('');
const source = props.mode === 'source';
const canWrite = computed(() =>
  hasTemplatePermission(props.permissions, 'template', form.value.id ? 'edit' : 'add'),
);
const {
  working,
  error: sourceError,
  previewLoading,
  previewError,
  previewUrl,
  selectFile,
  parseExisting,
  preview,
} = useTemplateSource(form);
const submitting = computed(() => saving.value || confirming.value);
const busy = computed(() => submitting.value || working.value);
const { close, saved, dirty } = useUnsavedEditor(open, submitting, () =>
  JSON.stringify(form.value));
let alive = true;
onScopeDispose(() => {
  alive = false;
  if (confirming.value)
    ElMessageBox.close();
});
const title = computed(() =>
  !canWrite.value
    ? '模板详情'
    : source
      ? form.value.id
        ? '核对字段 / 替换原件'
        : '上传模板'
      : form.value.id
        ? '编辑模板'
        : '新增正文模板',
);
const categoryEnabled = computed(() =>
  props.types.some(t => t.value === form.value.documentType && !t.disabled),
);
const oldCategory = props.initial.documentType;
watch(
  [() => form.value.fields, () => form.value.editableBlocks],
  () => {
    form.value.fieldsConfirmed = false;
  },
  { deep: true, flush: 'sync' },
);
const fileInput = ref<HTMLInputElement>();
async function choose(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || busy.value || !canWrite.value || !categoryEnabled.value)
    return;
  const token = user.token;
  if (form.value.sourceVersion) {
    confirming.value = true;
    try {
      await ElMessageBox.confirm(
        '新文件将建立新的工作版本，当前未保存的字段配置会被替换。保存前，已保存模板与历史文书仍使用原版本。',
        '替换模板原文件？',
        { confirmButtonText: '选择新文件', cancelButtonText: '取消', type: 'warning' },
      );
    }
    catch {
      return;
    }
    finally {
      confirming.value = false;
    }
  }
  if (alive && open.value && user.token === token)
    await selectFile(file);
}
async function save(draft = false) {
  if (busy.value || !canWrite.value)
    return;
  const token = user.token;
  if (!(await formRef.value?.validate().catch(() => false)))
    return;
  if (busy.value)
    return;
  error.value = inputError(
    { ...form.value, saveDraft: draft },
    !source && !!form.value.id && !!form.value.sourceVersion,
  );
  if (error.value)
    return;
  if (
    !categoryEnabled.value
    && (!form.value.id
      || oldCategory !== form.value.documentType
      || (props.initial.status === '1' && form.value.status === '0'))
  ) {
    error.value = '请选择启用的模板分类；分类可能已停用，请刷新后核对。';
    return;
  }
  if (source) {
    if (!form.value.sourceVersion) {
      error.value = '请先上传有效的模板文件';
      return;
    }
    if (
      !draft
      && (!form.value.fieldsConfirmed
        || form.value.parseStatus === 'uploaded'
        || !editableBlocks.value.length
        || (form.value.fields !== undefined
          && !form.value.fields.some(f => f.enabled && editableBlocks.value.includes(f.blockId))))
    ) {
      error.value
        = '请解析并核对至少一个允许填写的字段或段落，勾选确认后保存；也可先保存待核模板。';
      return;
    }
  }
  // Confirm any change of activation intent, including when made inside a form.
  if (form.value.id && props.initial.status !== form.value.status) {
    confirming.value = true;
    try {
      await ElMessageBox.confirm(
        form.value.status === '1'
          ? '停用后，新起草不再选择该模板，已有文书及其版本保留。'
          : '启用后，仍需分类启用且字段已确认，才会用于新起草。',
        '确认修改模板使用状态？',
        { confirmButtonText: '确认保存', cancelButtonText: '继续编辑', type: 'warning' },
      );
    }
    catch {
      return;
    }
    finally {
      confirming.value = false;
    }
  }
  if (!alive || user.token !== token || busy.value || !open.value || !canWrite.value)
    return;
  saving.value = true;
  try {
    const payload = {
      ...form.value,
      name: form.value.name.trim(),
      saveDraft: source && draft,
      ...(source && draft ? { fieldsConfirmed: false } : {}),
    };
    await (!source && payload.id && payload.sourceVersion
      ? saveTemplateMetadata(payload)
      : saveManagedTemplate(payload));
    if (user.token !== token || !open.value)
      return;
    saved();
    ElMessage.success(draft ? '待核模板已保存，完成字段确认后才可用于起草' : '模板已保存');
    emit('saved');
  }
  catch (reason) {
    if (user.token === token)
      error.value = `${failureMessage(reason)} 本次修改尚未保存，输入已保留。若版本已变化，请先保留输入，再重新打开最新模板核对。`;
  }
  finally {
    saving.value = false;
  }
}
</script>

<template>
  <el-drawer
    :model-value="open"
    :title="title"
    :size="source ? 'min(1120px, 100vw)' : 'min(760px, 100vw)'"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    :before-close="close"
  >
    <div class="template-editor">
      <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
      <el-alert
        v-if="!types.length"
        title="暂无可用分类，请先在“模板分类”中新增或启用分类；如果分类加载失败，请返回列表重试。"
        type="warning"
        :closable="false"
      />
      <el-form
        ref="formRef"
        :model="form"
        label-position="top"
        :disabled="busy || !canWrite"
        @submit.prevent="save()"
      >
        <div class="metadata-grid">
          <el-form-item
            label="模板名称"
            prop="name"
            :rules="[
              { required: true, whitespace: true, message: '请输入模板名称', trigger: 'blur' },
            ]"
          >
            <el-input v-model="form.name" :maxlength="100" autofocus />
          </el-form-item>
          <el-form-item
            label="模板分类"
            prop="documentType"
            :rules="[{ required: true, message: '请选择模板分类', trigger: 'change' }]"
          >
            <el-select v-model="form.documentType" placeholder="选择文书分类" class="full-width">
              <el-option
                v-for="item in types"
                :key="item.value"
                :value="item.value"
                :label="item.label + (item.disabled ? '（停用）' : '')"
                :disabled="!!item.disabled && item.value !== oldCategory"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="排序号">
            <el-input-number v-model="form.sortOrder" :min="0" :max="999999" :precision="0" />
          </el-form-item>
          <el-form-item label="使用状态">
            <el-radio-group v-model="form.status">
              <el-radio value="0">
                启用
              </el-radio><el-radio value="1">
                停用
              </el-radio>
            </el-radio-group>
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            :maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <template v-if="source">
          <section class="source-section" aria-label="模板原文件">
            <h3>模板原文件</h3>
            <p class="yl-safety-note">
              支持 DOCX、DOC、RTF、ODT、文字
              PDF、TXT、Markdown，单文件不超过10MB，PDF不超过100页，正文不超过40000字。上传后使用本地规则解析，候选需要人工核对。
            </p>
            <input
              ref="fileInput"
              type="file"
              accept=".docx,.doc,.rtf,.odt,.pdf,.txt,.md"
              class="file-input"
              aria-label="选择模板原文件"
              :disabled="busy || !canWrite || !categoryEnabled"
              @change="choose"
            >
            <el-button
              :loading="working"
              :disabled="busy || !canWrite || !categoryEnabled"
              @click="fileInput?.click()"
            >
              {{ form.sourceVersion ? "替换原文件" : "选择模板文件" }}
            </el-button>
            <span v-if="working" role="status">正在上传并解析，请稍候…</span>
            <el-alert
              v-if="sourceError"
              :title="sourceError"
              type="error"
              :closable="false"
              role="alert"
            />
          </section>
        </template>
        <section v-if="form.sourceVersion" class="source-context" aria-label="原文件版本信息">
          <strong>{{ form.sourceFilename || "已保存原文件" }}</strong>
          <p>
            版本 {{ form.sourceVersion }} · {{ form.sourceFormat?.toUpperCase() }} ·
            {{ templateState(form) }}
          </p>
          <p v-if="form.analysisWarning && !form.fieldsConfirmed" class="yl-safety-note">
            {{ form.analysisWarning }}
          </p>
          <p v-if="!source" class="yl-safety-note">
            此处仅修改基础信息。正文与字段请从“核对字段”维护；修改原文请替换原文件。
          </p>
          <template v-if="source">
            <el-button
              v-if="form.parseStatus === 'uploaded'"
              :loading="working"
              @click="parseExisting"
            >
              解析原文件
            </el-button>
            <el-button v-else :loading="previewLoading" @click="preview">
              预览 PDF 版式
            </el-button>
          </template>
        </section>
        <el-form-item
          v-if="!source"
          label="模板正文"
          prop="content"
          :rules="[
            {
              required: !form.sourceVersion,
              whitespace: true,
              message: '请输入正文',
              trigger: 'blur',
            },
          ]"
        >
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="14"
            :maxlength="40000"
            show-word-limit
            :readonly="!!form.sourceVersion"
          />
          <p v-if="!form.sourceVersion" class="yl-safety-note">
            仅正文模板不保留 Word 版式。严格按原模板填写时，需要补传原文件并核对字段。
          </p>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="previewError"
        :title="previewError"
        type="error"
        :closable="false"
        role="alert"
      >
        <el-button text @click="preview">
          重试预览
        </el-button>
      </el-alert>
      <iframe
        v-if="previewUrl"
        :src="previewUrl"
        title="当前模板版本 PDF 版式预览"
        class="pdf-preview"
      />
      <template v-if="source && form.sourceVersion && form.parseStatus !== 'uploaded'">
        <TemplateFields
          v-model:fields="form.fields"
          v-model:editable="editableBlocks"
          :blocks="form.blocks || []"
          :disabled="busy || !canWrite"
        />
        <el-checkbox
          v-model="form.fieldsConfirmed"
          :disabled="busy || !canWrite"
          class="confirmation"
        >
          我已核对允许填写的段落、字段含义、类型和原件单位；仅填写原有空白。
        </el-checkbox>
      </template>
    </div>
    <template #footer>
      <div class="editor-footer">
        <span v-if="dirty" role="status">有未保存的修改</span>
        <el-button :disabled="saving" @click="close">
          {{ working ? "取消上传并关闭" : canWrite ? "取消" : "关闭" }}
        </el-button>
        <el-button
          v-if="source && canWrite"
          :disabled="busy || !form.sourceVersion"
          @click="save(true)"
        >
          保存待核模板
        </el-button>
        <el-button
          v-if="canWrite"
          type="primary"
          :loading="saving"
          :disabled="working"
          @click="save()"
        >
          {{ source ? "确认字段并保存" : "保存模板" }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.template-editor {
  display: grid;
  gap: var(--yl-space-4);
}
.metadata-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--yl-space-5);
}
.full-width {
  width: 100%;
}
.source-section {
  margin-bottom: var(--yl-space-4);
}
.source-section h3 {
  margin: 0;
  font-size: 16px;
}
.file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}
.source-context {
  padding: var(--yl-space-4);
  margin-bottom: var(--yl-space-4);
  overflow-wrap: anywhere;
  background: var(--el-fill-color-light);
  border: 1px solid var(--yl-color-border);
  border-radius: var(--yl-radius-panel);
}
.source-context p {
  margin: var(--yl-space-2) 0;
}
.pdf-preview {
  width: 100%;
  height: 480px;
  border: 1px solid var(--yl-color-border);
}
.confirmation {
  height: auto;
  white-space: normal;
}
.editor-footer {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yl-space-2);
  align-items: center;
  justify-content: flex-end;
}
.editor-footer > span {
  margin-right: auto;
  font-size: var(--yl-text-sm);
  color: var(--yl-color-text-muted);
}

@media (width <= 767px) {
  .metadata-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .editor-footer > span {
    width: 100%;
  }
  .editor-footer .el-button {
    min-height: 40px;
    margin: 0;
  }
}
</style>
