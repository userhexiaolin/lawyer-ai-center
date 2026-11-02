<script setup lang="ts">
import type { FormInstance } from 'ant-design-vue';

import type { DocTemplateType } from '#/api/legal/doc-template-type';

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

import { saveTemplateType } from '#/api/legal/doc-template-type';

const props = defineProps<{
  canWrite: boolean;
  initial: Partial<DocTemplateType>;
}>();
const emit = defineEmits<{ saved: [] }>();
const open = defineModel<boolean>('open', { default: false });
const Textarea = Input.TextArea;
const fresh = (): DocTemplateType => ({
  name: '',
  sortOrder: 0,
  status: '0',
  builtin: false,
  remark: '',
});
const form = ref(fresh());
const formRef = ref<FormInstance>();
const saving = ref(false);
const error = ref('');
const original = ref('');
const snapshot = () => JSON.stringify(form.value);
const dirty = computed(() => open.value && snapshot() !== original.value);
let returnFocus: HTMLElement | undefined;
let discard: ReturnType<typeof Modal.confirm> | undefined;
watch(open, (value) => {
  error.value = '';
  if (!value) return;
  returnFocus =
    document.activeElement instanceof HTMLElement
      ? document.activeElement
      : undefined;
  form.value = { ...fresh(), ...props.initial };
  original.value = snapshot();
  formRef.value?.clearValidate();
});
function afterOpenChange(value: boolean) {
  if (value)
    document
      .querySelector<HTMLInputElement>(
        form.value.builtin ? '#type-sort' : '#type-name',
      )
      ?.focus();
  else
    (returnFocus?.isConnected
      ? returnFocus
      : document.querySelector<HTMLInputElement>('#filter-type-name')
    )?.focus();
}
function discardChanges(leaving = false): Promise<boolean> {
  if (saving.value || discard) return Promise.resolve(false);
  if (!dirty.value) return Promise.resolve(true);
  return new Promise((resolve) => {
    discard = Modal.confirm({
      title: leaving ? '放弃未保存的修改并离开？' : '放弃本次未保存的修改？',
      content: '本次修改尚未保存，放弃后无法恢复。',
      okText: '放弃修改',
      cancelText: '继续编辑',
      onOk: () => {
        discard = undefined;
        resolve(true);
      },
      onCancel: () => {
        discard = undefined;
        resolve(false);
      },
    });
  });
}
async function close() {
  if (await discardChanges()) open.value = false;
}
onScopeDispose(() => discard?.destroy());
if (inject(routerKey, undefined))
  onBeforeRouteLeave(() => discardChanges(true));
useEventListener(window, 'beforeunload', (event) => {
  if (dirty.value || saving.value) {
    event.preventDefault();
    event.returnValue = '';
  }
});
async function save() {
  if (saving.value || !props.canWrite) return;
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  if (saving.value || !props.canWrite) return;
  saving.value = true;
  error.value = '';
  try {
    await saveTemplateType({ ...form.value, name: form.value.name.trim() });
    original.value = snapshot();
    open.value = false;
    emit('saved');
  } catch (error_) {
    const detail = (error_ as Error).message || '保存失败';
    error.value = `${/timeout|exceeded/i.test(detail) ? '请求超时' : detail}。本次修改尚未保存，输入已保留，请核对后重试。`;
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <Drawer
    :open="open"
    :title="form.id ? '编辑模板类型' : '新增模板类型'"
    width="min(560px, 100vw)"
    :mask-closable="false"
    :closable="!saving"
    :keyboard="!saving"
    @close="close"
    @after-open-change="afterOpenChange"
  >
    <div class="type-editor">
      <p class="editor-context">
        设置文书模板的分类名称及可用状态。保存后，模板管理和文书起草将使用最新配置。
      </p>
      <Alert
        v-if="error"
        message="类型尚未保存"
        :description="error"
        type="error"
        show-icon
        role="alert"
      />
      <Form
        ref="formRef"
        :model="form"
        layout="vertical"
        :disabled="saving || !canWrite"
        @finish="save"
      >
        <FormItem
          label="类型名称"
          name="name"
          html-for="type-name"
          :rules="[
            { required: true, whitespace: true, message: '请填写类型名称' },
            { max: 50, message: '类型名称不超过50字' },
          ]"
        >
          <Input
            id="type-name"
            v-model:value="form.name"
            placeholder="例如：劳动争议文书"
            :maxlength="50"
            :disabled="form.builtin || !canWrite || saving"
            show-count
          />
        </FormItem>
        <Alert
          v-if="form.builtin"
          message="内置类型名称不可修改"
          description="内置类型用于已有专用起草流程，可调整排序、使用状态与备注；不支持删除。"
          type="info"
          show-icon
        />
        <Alert
          v-else-if="form.id"
          message="修改名称将同步更新关联模板的归类"
          description="请保持分类含义准确，避免影响后续模板选择。"
          type="info"
          show-icon
        />
        <FormItem
          label="排序号"
          name="sortOrder"
          html-for="type-sort"
          :rules="[
            {
              required: true,
              type: 'integer',
              min: 0,
              max: 999999,
              message: '请输入0至999999的整数',
            },
          ]"
          extra="数字越小越靠前。"
        >
          <InputNumber
            id="type-sort"
            v-model:value="form.sortOrder"
            :min="0"
            :max="999999"
            :precision="0"
            class="sort-input"
          />
        </FormItem>
        <FormItem
          label="使用状态"
          name="status"
          html-for="type-status"
          :rules="[{ required: true, message: '请选择使用状态' }]"
          extra="停用后不再供新增模板和新的文书起草选择，已有模板和文书保留。"
        >
          <Select
            id="type-status"
            v-model:value="form.status"
            :options="[
              { value: '0', label: '启用' },
              { value: '1', label: '停用' },
            ]"
          />
        </FormItem>
        <FormItem
          label="备注"
          name="remark"
          html-for="type-remark"
          :rules="[{ max: 500, message: '备注不超过500字' }]"
        >
          <Textarea
            id="type-remark"
            v-model:value="form.remark"
            :rows="4"
            :maxlength="500"
            show-count
            placeholder="说明此类型适用的文书范围（选填）"
          />
        </FormItem>
      </Form>
    </div>
    <template #footer>
      <div class="editor-footer">
        <Tag v-if="dirty">
          <template #default> 未保存 </template>
        </Tag>
        <p v-else class="editor-hint">
          {{ form.id ? '正在编辑类型配置' : '填写后保存即可使用' }}
        </p>
        <Space>
          <Button :disabled="saving" @click="close">
            <template #default> 取消 </template>
          </Button>

          <Button
            v-if="canWrite"
            type="primary"
            :loading="saving"
            @click="save"
          >
            <template #default> 保存类型 </template>
          </Button>
        </Space>
      </div>
    </template>
  </Drawer>
</template>

<style scoped>
.type-editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
  color: hsl(var(--foreground));
}

.editor-context {
  margin: 0;
  line-height: 1.6;
  color: hsl(var(--muted-foreground));
}

.type-editor :deep(.ant-alert) {
  margin-bottom: 16px;
}

.sort-input {
  width: 100%;
}

.editor-footer {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.editor-hint {
  margin: 0;
  font-size: 13px;
  color: hsl(var(--muted-foreground));
}
</style>
