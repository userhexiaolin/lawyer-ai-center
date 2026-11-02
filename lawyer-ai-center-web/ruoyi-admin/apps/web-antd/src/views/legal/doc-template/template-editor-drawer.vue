<script setup lang="ts">
import type { FormInstance } from 'ant-design-vue';

import type { DocTemplate, DocumentTypeOption } from '#/api/legal/doc-template';

import { computed, inject, onScopeDispose, ref, watch } from 'vue';
import { onBeforeRouteLeave, routerKey } from 'vue-router';

import { useEventListener } from '@vueuse/core';
import {
  Alert,
  Button,
  Drawer,
  Form,
  FormItem,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Tag,
} from 'ant-design-vue';

import {
  classifyExistingTemplateType,
  saveTemplate,
  saveTemplateMetadata,
} from '#/api/legal/doc-template';

import TemplateAiIcon from './template-ai-icon.vue';
import { useTemplateTypeChoice } from './use-template-type-choice';

const props = defineProps<{
  canWrite: boolean;
  initial: Partial<DocTemplate>;
  types: DocumentTypeOption[];
}>();
const emit = defineEmits<{ saved: [] }>();
const open = defineModel<boolean>('open', { default: false });
const Textarea = Input.TextArea;
const placeholderHint =
  '可使用 {{ 原告 }}、{{ 被告 }}、{{ 案由 }} 等占位符；生成内容仍需人工核对。';
const formRef = ref<FormInstance>();
const form = ref<DocTemplate>({
  name: '',
  documentType: '',
  content: '',
  sortOrder: 0,
  status: '0',
  remark: '',
});
const saving = ref(false);
const error = ref('');
const original = ref('');
const snapshot = () =>
  JSON.stringify([
    form.value.name,
    form.value.documentType,
    form.value.content,
    form.value.sortOrder,
    form.value.status,
    form.value.remark,
  ]);
const dirty = computed(() => open.value && snapshot() !== original.value);
let returnFocus: HTMLElement | undefined;
function afterOpenChange(value: boolean) {
  if (value) {
    if (props.canWrite)
      document.querySelector<HTMLInputElement>('#template-name')?.focus();
  } else {
    const target = returnFocus?.isConnected
      ? returnFocus
      : document.querySelector<HTMLInputElement>('#filter-template-name');
    target?.focus();
  }
}
const { recognizing, notice, select, recognize, cancel, reset } =
  useTemplateTypeChoice(
    () => props.types,
    (value) => {
      form.value.documentType = value;
    },
    (signal) => classifyExistingTemplateType(form.value.id!, signal),
  );
watch(
  open,
  (value) => {
    reset();
    error.value = '';
    if (!value) return;
    returnFocus =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : undefined;
    form.value = {
      name: '',
      documentType: props.types.find((type) => !type.disabled)?.value || '',
      content: '',
      sortOrder: 0,
      status: '0',
      remark: '',
      ...props.initial,
    };
    original.value = snapshot();
    formRef.value?.clearValidate();
  },
  { immediate: true },
);

let discardDialog: ReturnType<typeof Modal.confirm> | undefined;
function close() {
  if (saving.value || discardDialog) return;
  if (!props.canWrite || !dirty.value) {
    open.value = false;
    return;
  }
  discardDialog = Modal.confirm({
    title: '放弃本次未保存的修改？',
    content: '关闭后本次修改将丢失，已保存的模板不受影响。',
    okText: '放弃修改',
    cancelText: '继续编辑',
    onOk: () => {
      open.value = false;
      discardDialog = undefined;
    },
    onCancel: () => {
      discardDialog = undefined;
    },
  });
}
onScopeDispose(() => discardDialog?.destroy());
if (inject(routerKey, undefined)) {
  onBeforeRouteLeave(() => {
    if (saving.value) return false;
    if (!props.canWrite || !dirty.value) return true;
    return new Promise<boolean>((resolve) => {
      discardDialog?.destroy();
      discardDialog = Modal.confirm({
        title: '放弃本次未保存的修改并离开？',
        content: '离开后本次修改将丢失，已保存的模板不受影响。',
        okText: '放弃并离开',
        cancelText: '继续编辑',
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
useEventListener(window, 'beforeunload', (event) => {
  if (props.canWrite && dirty.value) {
    event.preventDefault();
    event.returnValue = '';
  }
});
async function save() {
  if (!props.canWrite || saving.value) return;
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  // Validation is asynchronous; a second click may have started before it resolved.
  if (saving.value) return;
  saving.value = true;
  error.value = '';
  cancel();
  try {
    const value = { ...form.value, name: form.value.name.trim() };
    await (value.id && value.sourceVersion
      ? saveTemplateMetadata(value)
      : saveTemplate(value));
    original.value = snapshot();
    open.value = false;
    emit('saved');
  } catch (error_) {
    error.value = `${(error_ as Error).message || '保存失败'}。本次修改尚未保存，输入已保留，请核对后重试。`;
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <Drawer
    :open="open"
    :title="canWrite ? (form.id ? '模板基本信息' : '新增模板') : '模板详情'"
    width="min(800px, 100vw)"
    :mask-closable="false"
    :closable="!saving"
    :keyboard="!saving"
    @after-open-change="afterOpenChange"
    @close="close"
  >
    <div class="editor-content">
      <Alert
        v-if="error"
        :message="error"
        type="error"
        show-icon
        role="alert"
      />
      <div v-if="form.sourceVersion" class="source-context">
        <Space wrap>
          <Tag>{{ form.sourceFormat?.toUpperCase() || '原文件' }}</Tag>
          <strong>已保留原文件</strong>
          <span>{{
            form.fieldsConfirmed ? '填写字段已确认' : '填写字段待核对'
          }}</span>
        </Space>
        <p v-if="form.sourceFilename">原文件：{{ form.sourceFilename }}</p>
        <p>
          此处修改基础信息。原文与填写字段请从列表的“核对字段 /
          编辑字段”维护；更换原件请使用“更多 → 替换文件”。
        </p>
      </div>
      <Alert
        v-else
        message="纯文本模板不保留原文件版式。如需沿用 Word 版式，请使用“上传模板”。"
        type="info"
        show-icon
      />
      <Form
        ref="formRef"
        :model="form"
        layout="vertical"
        :disabled="saving || !canWrite"
        scroll-to-first-error
      >
        <div class="editor-grid">
          <FormItem
            label="模板名称"
            html-for="template-name"
            name="name"
            :rules="[
              { required: true, whitespace: true, message: '请输入模板名称' },
            ]"
          >
            <Input
              id="template-name"
              v-model:value="form.name"
              :maxlength="100"
              placeholder="输入便于检索的模板名称"
            />
          </FormItem>
          <FormItem
            label="文书类型"
            html-for="template-document-type"
            name="documentType"
            :rules="[{ required: true, message: '请选择文书类型' }]"
          >
            <Select
              id="template-document-type"
              :value="form.documentType"
              :options="types"
              placeholder="请选择文书类型"
              aria-label="文书类型"
              @update:value="select(String($event))"
            />
            <Button
              v-if="form.id && canWrite"
              :loading="recognizing"
              :disabled="saving"
              class="recognize-action"
              @click="recognize"
            >
              <template #icon>
                <TemplateAiIcon :disabled="saving" />
              </template>
              <span>AI识别类型</span>
            </Button>
          </FormItem>
          <FormItem
            label="排序号"
            html-for="template-sort"
            name="sortOrder"
            extra="管理列表按数值从大到小排列，0 排在最后"
          >
            <InputNumber
              id="template-sort"
              v-model:value="form.sortOrder"
              :min="0"
              :max="999999"
              :precision="0"
              class="full-width"
            />
          </FormItem>
          <FormItem label="使用状态" html-for="template-status" name="status">
            <Select
              id="template-status"
              v-model:value="form.status"
              :options="[
                { value: '0', label: '启用' },
                { value: '1', label: '停用' },
              ]"
              aria-label="使用状态"
            />
          </FormItem>
        </div>
        <p v-if="form.id && canWrite" class="hint">
          AI 使用已保存模板的最多 6000
          字正文摘要识别类型，结果待核对，点击保存后生效。
        </p>
        <Alert v-if="notice" :message="notice" type="info" show-icon />
        <FormItem label="备注" html-for="template-remark" name="remark">
          <Textarea
            id="template-remark"
            v-model:value="form.remark"
            :maxlength="500"
            :rows="2"
            placeholder="可填写适用场景或维护说明"
          />
        </FormItem>
        <FormItem
          html-for="template-content"
          :label="
            form.sourceVersion
              ? '模板正文（只读）'
              : '模板正文（纯文本 / Markdown）'
          "
          name="content"
          :rules="
            form.sourceVersion
              ? []
              : [
                  {
                    required: true,
                    whitespace: true,
                    message: '请输入模板正文',
                  },
                ]
          "
        >
          <Textarea
            id="template-content"
            v-model:value="form.content"
            :readonly="!!form.sourceVersion"
            :rows="12"
            :maxlength="40000"
            show-count
          />
          <p
            v-if="!form.sourceVersion"
            class="hint"
            v-text="placeholderHint"
          ></p>
        </FormItem>
      </Form>
    </div>
    <template #footer>
      <div class="editor-footer">
        <span class="hint" role="status">{{
          saving
            ? '正在保存…'
            : dirty && canWrite
              ? '有未保存的修改'
              : '基础信息维护'
        }}</span>
        <Space>
          <Button :disabled="saving" @click="close">
            {{ canWrite ? '取消' : '关闭' }}
          </Button>
          <Button
            v-if="canWrite"
            type="primary"
            :loading="saving"
            @click="save"
          >
            保存模板
          </Button>
        </Space>
      </div>
    </template>
  </Drawer>
</template>

<style scoped>
.editor-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.source-context {
  padding: 16px;
  background: hsl(var(--muted));
  border: 1px solid hsl(var(--border));
  border-radius: var(--radius);
}

.source-context p {
  margin: 8px 0 0;
  overflow-wrap: anywhere;
}

.editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 0 16px;
}

.full-width {
  width: 100%;
}

.recognize-action {
  margin-top: 8px;
}

.hint {
  margin: 8px 0;
  font-size: 13px;
  line-height: 1.6;
  color: hsl(var(--muted-foreground));
}

.editor-footer {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

@media (max-width: 640px) {
  .editor-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
