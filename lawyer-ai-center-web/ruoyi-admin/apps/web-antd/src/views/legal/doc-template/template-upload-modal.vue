<script setup lang="ts">
import type { UploadProps } from 'ant-design-vue';

import type { DocTemplate, DocumentTypeOption } from '#/api/legal/doc-template';

import { computed, inject, nextTick, onScopeDispose, ref, watch } from 'vue';
import { onBeforeRouteLeave, routerKey } from 'vue-router';

import { InboxOutlined } from '@ant-design/icons-vue';
import { useEventListener } from '@vueuse/core';
import {
  Alert,
  Button,
  Checkbox,
  CheckboxGroup,
  Drawer,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Select,
  Tag,
  Upload,
} from 'ant-design-vue';

import { templatePreview } from '#/api/legal/doc-template';

import { templateFieldSource } from './template-field-source';
import TemplateFields from './template-fields.vue';
import { useTemplateUpload } from './use-template-upload';

const props = defineProps<{
  initial?: Partial<DocTemplate>;
  returnFocus?: HTMLElement;
  types: DocumentTypeOption[];
}>();
const emit = defineEmits<{ saved: [] }>();
const open = defineModel<boolean>('open', { default: false });
const Textarea = Input.TextArea;
const UploadDragger = Upload.Dragger;
const {
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
} = useTemplateUpload(
  open,
  () => props.types,
  () => emit('saved'),
  () => props.initial || {},
);
const confirmationAnchor = ref<HTMLElement>();
const errorAnchor = ref<HTMLElement>();
const reviewRoot = ref<HTMLElement>();
const original = ref('');
const snapshot = () => JSON.stringify(form);
const dirty = computed(() => open.value && snapshot() !== original.value);
const working = computed(
  () => saving.value || analyzing.value || uploading.value,
);
const source = computed(() =>
  templateFieldSource(form.blocks || [], form.fields || []),
);
let openerElement: HTMLElement | undefined;
watch(
  open,
  (value) => {
    if (!value) return;
    original.value = snapshot();
    openerElement =
      props.returnFocus ||
      (document.activeElement instanceof HTMLElement
        ? document.activeElement
        : undefined);
  },
  { immediate: true, flush: 'sync' },
);
let discardDialog: ReturnType<typeof Modal.confirm> | undefined;
function close() {
  if (working.value || discardDialog) return;
  if (!dirty.value) {
    open.value = false;
    return;
  }
  discardDialog = Modal.confirm({
    title: '放弃本次未保存的字段修改？',
    content: '关闭后本次修改将丢失，已保存的模板及原文件保持不变。',
    okText: '放弃修改',
    cancelText: '继续核对',
    onOk: () => {
      open.value = false;
      discardDialog = undefined;
    },
    onCancel: () => {
      discardDialog = undefined;
    },
  });
}
function afterOpenChange(value: boolean) {
  if (value) reviewRoot.value?.focus();
  else {
    const target =
      openerElement?.isConnected && !openerElement.matches(':disabled')
        ? openerElement
        : document.querySelector<HTMLInputElement>('#filter-template-name');
    target?.focus();
  }
}
function focusField(id: string) {
  const field = reviewRoot.value?.querySelector<HTMLElement>(
    `[data-field-id="${CSS.escape(id)}"]`,
  );
  field?.scrollIntoView({ block: 'center' });
  field
    ?.querySelector<HTMLInputElement>('input:not([type="checkbox"])')
    ?.focus();
}
onScopeDispose(() => discardDialog?.destroy());
useEventListener(window, 'beforeunload', (event) => {
  if (dirty.value) {
    event.preventDefault();
    event.returnValue = '';
  }
});
if (inject(routerKey, undefined)) {
  onBeforeRouteLeave(() => {
    if (working.value) return false;
    if (!dirty.value) return true;
    return new Promise<boolean>((resolve) => {
      discardDialog?.destroy();
      discardDialog = Modal.confirm({
        title: '放弃本次未保存的字段修改并离开？',
        content: '本次修改将丢失，已保存的配置保持不变。',
        okText: '放弃并离开',
        cancelText: '继续核对',
        onOk: () => {
          discardDialog = undefined;
          resolve(true);
        },
        onCancel: () => {
          discardDialog = undefined;
          resolve(false);
        },
      });
    });
  });
}
async function save() {
  await submit();
  if (!open.value || !error.value) return;
  message.error(error.value, 3);
  await nextTick();
  const needsConfirmation =
    error.value === '请核对填写字段或段落，并勾选确认后保存';
  const anchor = needsConfirmation
    ? confirmationAnchor.value
    : errorAnchor.value;
  anchor?.scrollIntoView?.({ block: 'center' });
  if (needsConfirmation) anchor?.querySelector('input')?.focus();
  else anchor?.focus();
}
const previewBusy = ref(false);
const previewUrl = ref('');
const previewError = ref('');
let previewGeneration = 0;
function hidePreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = '';
}
async function preview() {
  if (!form.sourceVersion || previewBusy.value) return;
  const current = ++previewGeneration;
  const version = form.sourceVersion;
  previewBusy.value = true;
  previewError.value = '';
  try {
    const blob = await templatePreview(version);
    if (
      current !== previewGeneration ||
      !open.value ||
      version !== form.sourceVersion
    )
      return;
    const signature = blob instanceof Blob ? await blob.slice(0, 5).text() : '';
    if (
      current !== previewGeneration ||
      !open.value ||
      version !== form.sourceVersion
    )
      return;
    if (!(blob instanceof Blob) || !signature.startsWith('%PDF-'))
      throw new Error('未收到有效PDF预览，请检查转换服务');
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = URL.createObjectURL(blob);
  } catch (error_) {
    if (
      current === previewGeneration &&
      open.value &&
      version === form.sourceVersion
    )
      previewError.value =
        (error_ as Error).message ||
        '模板预览失败，可重试预览或继续核对字段后保存';
  } finally {
    if (current === previewGeneration) previewBusy.value = false;
  }
}
watch(
  () => [open.value, form.sourceVersion],
  () => {
    previewGeneration++;
    previewBusy.value = false;
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = '';
    previewError.value = '';
  },
  { flush: 'sync' },
);
onScopeDispose(() => {
  previewGeneration++;
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
});
const beforeUpload: UploadProps['beforeUpload'] = async (file) => {
  await selectFile(file);
  return Upload.LIST_IGNORE;
};
function onDrop(event: DragEvent) {
  const files = event.dataTransfer?.files;
  if (!files?.length || saving.value || uploading.value || analyzing.value)
    return;
  if (files.length !== 1) {
    removeFile();
    error.value = '一次只能上传一个模板文件';
    return;
  }
  void selectFile(files[0]!);
}
const dropHandlers = {
  onDragover: (event: DragEvent) => {
    if (event.dataTransfer?.types.includes('Files')) event.preventDefault();
  },
  onDropCapture: (event: DragEvent) => {
    if (!event.dataTransfer?.files.length) return;
    event.preventDefault();
    event.stopPropagation();
    onDrop(event);
  },
};
</script>

<template>
  <Drawer
    :open="open"
    :title="form.configureExisting ? '核对模板字段' : '替换模板文件'"
    :width="form.configureExisting ? 'min(1440px, 100vw)' : 'min(720px, 100vw)'"
    :body-style="{ padding: 0 }"
    :mask-closable="false"
    :closable="!working"
    :keyboard="!working"
    @close="close"
    @after-open-change="afterOpenChange"
  >
    <div
      ref="reviewRoot"
      class="review-root"
      tabindex="-1"
      v-bind="dropHandlers"
    >
      <div
        v-if="error"
        ref="errorAnchor"
        class="review-error"
        tabindex="-1"
        role="alert"
      >
        <Alert :message="error" type="error" show-icon />
      </div>
      <Form layout="vertical" class="review-form" :disabled="working">
        <div
          class="review-layout"
          :class="{ 'review-layout--split': form.configureExisting }"
        >
          <aside
            v-if="form.configureExisting"
            class="source-pane"
            aria-label="当前版本原文"
          >
            <div class="source-heading">
              <div>
                <h2>原文核对</h2>
                <p class="review-muted">{{ fileName }}</p>
              </div>
              <Button
                :loading="previewBusy"
                :disabled="!form.sourceVersion"
                @click="preview"
              >
                查看版式 PDF
              </Button>
            </div>
            <p class="review-muted">
              点击高亮填写位置，可定位右侧对应字段。原文仅供核对，保持原样。
            </p>
            <Alert
              v-if="previewError"
              :message="previewError"
              type="warning"
              show-icon
            />
            <template v-if="previewUrl">
              <Button type="link" @click="hidePreview">返回原文段落</Button>
              <iframe
                :src="previewUrl"
                title="模板版式预览"
                class="template-preview"
              ></iframe>
            </template>
            <div v-else class="source-document">
              <section
                v-for="block in form.blocks || []"
                :key="block.id"
                class="source-paragraph"
              >
                <span class="review-muted">原文段落 {{ block.id }}</span>
                <p>
                  <template
                    v-for="(segment, index) in source.groups.find(
                      (group) => group.blockId === block.id,
                    )?.segments || [{ text: block.text }]"
                    :key="index"
                  >
                    <button
                      v-if="'field' in segment && segment.field"
                      type="button"
                      class="source-marker"
                      :aria-label="`核对${segment.field.label}`"
                      @click="focusField(segment.field.id)"
                    >
                      {{ segment.text || '〔空白表格单元格〕' }}
                    </button>
                    <span v-else>{{ segment.text }}</span>
                  </template>
                </p>
              </section>
              <p v-if="!form.blocks?.length" class="review-muted">
                尚无可核对的原文段落，请先解析字段；也可查看版式 PDF。
              </p>
            </div>
          </aside>
          <section class="review-pane" aria-label="字段配置">
            <h2 v-if="form.configureExisting">字段配置</h2>
            <component
              :is="form.configureExisting ? 'details' : 'div'"
              :class="{ 'review-metadata': form.configureExisting }"
            >
              <summary v-if="form.configureExisting">
                基本信息 · {{ form.name }}
              </summary>
              <FormItem label="模板名称" required>
                <Input
                  v-model:value="form.name"
                  :maxlength="100"
                  placeholder="选择文件后自动读取文件名，可编辑"
                />
              </FormItem>
              <FormItem label="模板类型" required>
                <Select
                  v-model:value="form.documentType"
                  :options="types"
                  placeholder="请选择模板类型"
                />
              </FormItem>
              <div class="upload-fields">
                <FormItem label="列表排序号（越大越靠前）" required>
                  <InputNumber
                    v-model:value="form.sortOrder"
                    :min="0"
                    :max="999999"
                    :precision="0"
                    style="width: 100%"
                  />
                </FormItem>
                <FormItem label="状态" required>
                  <Select
                    v-model:value="form.status"
                    :options="[
                      { value: '0', label: '启用' },
                      { value: '1', label: '停用' },
                    ]"
                  />
                </FormItem>
              </div>
              <FormItem label="备注">
                <Textarea
                  v-model:value="form.remark"
                  :maxlength="500"
                  :rows="2"
                  placeholder="选填"
                />
              </FormItem>
            </component>
            <FormItem v-if="!form.configureExisting" label="模板文件" required>
              <Checkbox
                v-if="!form.configureExisting"
                v-model:checked="parseOnUpload"
              >
                上传并解析（不勾选则仅保存原文件）
              </Checkbox>
              <UploadDragger
                accept=".docx,.pdf,.txt,.md,.doc,.rtf,.odt"
                :show-upload-list="false"
                :multiple="false"
                :before-upload="beforeUpload"
                :disabled="uploading || saving"
              >
                <p class="ant-upload-drag-icon"><InboxOutlined /></p>
                <p class="ant-upload-text">
                  {{
                    uploading
                      ? '正在上传原文件…'
                      : fileName
                        ? '点击选择其他文件，或拖入文件替换'
                        : '点击选择模板文件，或将文件拖到此弹窗'
                  }}
                </p>
                <p class="ant-upload-hint">
                  支持 DOCX、PDF、DOC、RTF、ODT、TXT、Markdown，文件不超过10MB
                </p>
              </UploadDragger>
              <div v-if="fileName" class="selected-file">
                <div class="file-details">
                  <div class="file-name">原始文件：{{ fileName }}</div>
                  <Tag
                    :color="
                      analyzing || uploading
                        ? 'blue'
                        : form.fieldsConfirmed
                          ? 'green'
                          : 'default'
                    "
                  >
                    {{ statusText }}
                  </Tag>
                </div>
                <Button
                  type="link"
                  :disabled="uploading || saving"
                  @click="removeFile"
                >
                  移除
                </Button>
              </div>
              <p class="upload-hint">
                原文件保留。新上传模板默认启用，完成解析和字段核对后才可用于起草。填写仅作用于已确认的空白，固定文字、下划线及单位保持原样。
              </p>
            </FormItem>
            <div v-if="unparsed" class="upload-error">
              <Alert
                message="原文件已上传，尚未解析填写字段。"
                description="点击“解析字段”识别模板中的填写位置；解析结果需要人工核对后才能用于起草。"
                type="info"
                show-icon
              />
              <Button
                type="primary"
                :loading="analyzing"
                :disabled="uploading || saving"
                @click="analyzeFields()"
              >
                解析字段
              </Button>
              <Alert
                v-if="analysisWarning"
                :message="analysisWarning"
                type="warning"
                show-icon
              />
            </div>
            <FormItem
              v-if="!unparsed && form.blocks?.length"
              label="填写字段与段落"
              required
            >
              <details class="block-selection">
                <summary>
                  允许填写的段落 · 已选 {{ form.editableBlocks?.length || 0 }} /
                  {{ form.blocks?.length || 0 }} 段
                </summary>
                <Button
                  class="select-all-blocks"
                  :disabled="uploading || analyzing || saving"
                  @click="selectAllBlocks"
                >
                  全选
                </Button>
                <CheckboxGroup
                  v-model:value="form.editableBlocks"
                  :disabled="uploading || analyzing || saving"
                  :options="
                    form.blocks.map((block) => ({
                      label: block.text,
                      value: block.id,
                    }))
                  "
                  class="block-options"
                />
              </details>
              <template v-if="form.fields !== undefined">
                <Alert
                  v-if="analysisWarning"
                  :message="analysisWarning"
                  type="warning"
                  show-icon
                />
                <Popconfirm
                  title="重新识别会覆盖本次字段修改，原文件保持不变。是否继续？"
                  @confirm="analyzeFields()"
                >
                  <Button :loading="analyzing" :disabled="uploading || saving">
                    重新解析字段
                  </Button>
                </Popconfirm>
                <TemplateFields
                  v-model="form.fields"
                  :source-alongside="form.configureExisting"
                  :blocks="form.blocks || []"
                  :disabled="uploading || analyzing || saving"
                />
              </template>
            </FormItem>
          </section>
        </div>
      </Form>
    </div>
    <template #footer>
      <div
        v-if="!unparsed && form.blocks?.length"
        ref="confirmationAnchor"
        class="field-confirmation"
      >
        <Checkbox
          v-model:checked="form.fieldsConfirmed"
          :aria-invalid="!form.fieldsConfirmed && !!error"
          :aria-describedby="
            !form.fieldsConfirmed && error
              ? 'field-confirmation-error'
              : undefined
          "
          :disabled="uploading || analyzing || saving"
        >
          {{
            form.fields === undefined
              ? '已核对填写段落和模板内容'
              : '已核对启用字段的位置、含义、类型及单位'
          }}
        </Checkbox>
        <p
          v-if="
            !form.fieldsConfirmed &&
            error === '请核对填写字段或段落，并勾选确认后保存'
          "
          id="field-confirmation-error"
          class="confirmation-error"
          role="alert"
        >
          请先核对字段并勾选确认，再保存模板。
        </p>
      </div>
      <div class="review-footer">
        <span class="review-muted" role="status">{{
          saving ? '正在保存…' : dirty ? '有未保存的修改' : '核对字段后确认保存'
        }}</span>
        <div class="review-footer-actions">
          <Button :disabled="working" @click="close">取消</Button>
          <Button
            type="primary"
            :loading="saving"
            :disabled="uploading || analyzing || !ready"
            @click="save"
          >
            {{ form.configureExisting ? '保存字段配置' : '保存替换文件' }}
          </Button>
        </div>
      </div>
    </template>
  </Drawer>
</template>

<style scoped>
.field-confirmation {
  margin: 16px 0;
  scroll-margin-block: 24px;
}

.field-confirmation:focus-within {
  outline: 2px solid hsl(var(--ring));
  outline-offset: 4px;
}

.confirmation-error {
  margin-top: 8px;
  color: hsl(var(--destructive));
}

.upload-error {
  margin-bottom: 16px;
}

.block-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 350px;
  margin-bottom: 16px;
  overflow: auto;
}

.select-all-blocks {
  margin-bottom: 12px;
}

.upload-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.selected-file {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.file-details {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.file-name {
  overflow-wrap: anywhere;
}

.upload-hint {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.8;
  color: hsl(var(--muted-foreground));
}

.template-preview {
  width: 100%;
  height: calc(100vh - 300px);
  min-height: 320px;
  border: 1px solid hsl(var(--border));
}

@media (max-width: 480px) {
  .upload-fields {
    grid-template-columns: 1fr;
    gap: 0;
  }
}

.review-root {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  outline: none;
}

.review-error {
  padding: 16px 24px 0;
}

.review-form {
  flex: 1;
  min-height: 0;
}

.review-layout {
  height: 100%;
  min-height: 0;
}

.review-layout--split {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.source-pane,
.review-pane {
  min-width: 0;
  height: 100%;
  padding: 24px;
  overflow: auto;
}

.source-pane {
  background: hsl(var(--muted));
  border-right: 1px solid hsl(var(--border));
}

.source-heading,
.review-footer,
.review-footer-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.source-heading {
  align-items: flex-start;
}

.source-heading h2,
.review-pane h2 {
  margin: 0 0 12px;
  font-size: 18px;
  font-weight: 600;
}

.review-muted {
  font-size: 13px;
  line-height: 1.6;
  color: hsl(var(--muted-foreground));
}

.source-document {
  padding: 20px;
  margin-top: 16px;
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: var(--radius);
}

.source-paragraph {
  margin-bottom: 20px;
}

.source-paragraph p {
  line-height: 1.8;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.source-marker {
  padding: 0;
  font: inherit;
  color: inherit;
  white-space: pre-wrap;
  cursor: pointer;
  outline: 1px solid hsl(var(--primary) / 30%);
  background: hsl(var(--primary) / 8%);
  border: 0;
}

.source-marker:focus-visible {
  outline: 2px solid hsl(var(--ring));
  outline-offset: 2px;
}

.review-metadata,
.block-selection {
  padding: 12px;
  margin-bottom: 16px;
  border: 1px solid hsl(var(--border));
  border-radius: var(--radius);
}

.review-metadata summary,
.block-selection summary {
  font-weight: 500;
  cursor: pointer;
}

.review-metadata[open] summary,
.block-selection[open] summary {
  margin-bottom: 16px;
}

.review-footer {
  flex-wrap: wrap;
}

@media (max-width: 960px) {
  .review-layout--split {
    display: block;
  }

  .source-pane,
  .review-pane {
    height: auto;
    overflow: visible;
  }

  .review-root {
    height: auto;
  }

  .source-pane {
    border-right: 0;
    border-bottom: 1px solid hsl(var(--border));
  }

  .source-document {
    max-height: 360px;
    overflow: auto;
  }
}
</style>
