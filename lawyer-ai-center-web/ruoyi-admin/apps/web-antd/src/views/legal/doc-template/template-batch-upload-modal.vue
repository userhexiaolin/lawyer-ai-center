<script setup lang="ts">
import type { DocumentTypeOption } from '#/api/legal/doc-template';

import { InboxOutlined } from '@ant-design/icons-vue';
import {
  Alert,
  Button,
  Checkbox,
  Form,
  FormItem,
  Input,
  Modal,
  Select,
  Space,
  Tag,
  Upload,
} from 'ant-design-vue';

import TemplateAiIcon from './template-ai-icon.vue';
import { useTemplateBatchUpload } from './use-template-batch-upload';

const props = defineProps<{ types: DocumentTypeOption[] }>();
const emit = defineEmits<{ saved: [] }>();
const open = defineModel<boolean>('open', { default: false });
const UploadDragger = Upload.Dragger;
const {
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
} = useTemplateBatchUpload(
  open,
  () => props.types,
  () => emit('saved'),
);
function beforeUpload(file: File) {
  addFiles([file]);
  return Upload.LIST_IGNORE;
}
const labels = {
  queued: '等待上传',
  uploading: '上传中',
  analyzing: '解析中',
  saving: '保存中',
  saved: '已保存',
  failed: '失败，可重试',
};
const typeOrigins = {
  AI: 'AI类型建议，待核对',
  default: '使用默认类型',
  filename: '按文件名匹配',
  manual: '手动选择',
};
</script>
<template>
  <Modal
    v-model:open="open"
    title="上传模板"
    width="900px"
    :style="{ top: '24px' }"
    :body-style="{ maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' }"
    :mask-closable="false"
    :closable="!busy"
    :keyboard="!busy"
  >
    <Alert
      class="batch-notice"
      message="批量上传默认启用；解析并人工核对字段后，模板才可用于起草。"
      type="info"
      show-icon
    />
    <Alert v-if="error" :message="error" type="error" show-icon />
    <Form layout="vertical" :disabled="busy" class="batch-form">
      <FormItem
        label="新加入文件的默认类型"
        extra="加入文件时优先按文件名匹配启用的模板类型；无法唯一匹配时使用此默认类型，可逐份手动修改。"
      >
        <Select v-model:value="defaultType" :options="types" />
      </FormItem>
      <FormItem>
        <Checkbox v-model:checked="typeRecognitionEnabled" :disabled="busy">
          后台 AI 识别未匹配的模板类型（不影响上传）
        </Checkbox>
        <p>
          文件名无法判断时，自动将最多6000字正文摘要交给内网 Qwen 模型识别。
          无法识别时保留默认类型；可随时手动选择或直接上传，无需等待。
        </p>
      </FormItem>
      <FormItem>
        <Checkbox v-model:checked="parseOnUpload" :disabled="busy">
          上传并解析
        </Checkbox>
        <p>默认仅上传原文件；勾选后逐份识别填写字段并保存待核对建议。</p>
      </FormItem>
      <UploadDragger
        :multiple="true"
        :show-upload-list="false"
        accept=".docx,.pdf,.txt,.md,.doc,.rtf,.odt"
        :before-upload="beforeUpload"
        :disabled="busy"
      >
        <p class="ant-upload-drag-icon"><InboxOutlined /></p>
        <p class="ant-upload-text">点击选择或拖入模板文件</p>
        <p class="ant-upload-hint">
          DOCX、DOC、RTF、ODT、文字 PDF、TXT、Markdown
        </p>
        <p class="ant-upload-hint">
          每批最多 20 份，每份不超过 10MB；扫描件需先转为可编辑文字。
        </p>
      </UploadDragger>
      <div v-for="row in items" :key="row.key" class="queue-row">
        <div class="queue-fields">
          <FormItem label="模板名称">
            <Input
              v-model:value="row.name"
              :maxlength="100"
              :disabled="busy || row.state === 'saved'"
            />
          </FormItem>
          <FormItem label="模板类型">
            <Select
              :value="row.documentType"
              :options="types"
              :disabled="busy || !!row.staged"
              @update:value="selectType(row.key, String($event))"
            />
          </FormItem>
        </div>
        <p class="source-name">原始文件：{{ row.file.name }}</p>
        <Space wrap>
          <Tag>
            {{
              row.typeRecognizing
                ? '后台识别类型中，可直接上传'
                : row.typeQueued
                  ? '等待后台识别，可直接上传'
                  : typeOrigins[row.typeOrigin]
            }}
          </Tag>
          <Button
            v-if="!row.staged"
            type="link"
            :disabled="busy || row.typeRecognizing"
            @click="retryType(row.key)"
          >
            <template #icon>
              <TemplateAiIcon :disabled="busy || row.typeRecognizing" />
            </template>
            AI识别类型
          </Button>
          <Tag :color="row.state === 'failed' ? 'red' : 'default'">
            <span
              v-text="
                labels[row.state] +
                (row.state === 'saved'
                  ? row.parse
                    ? ' · 待核对'
                    : ' · 未解析'
                  : '')
              "
            ></span>
          </Tag>
          <Button :disabled="busy" @click="remove(row.key)">
            <span v-text="row.state === 'saved' ? '从队列移除' : '移除'"></span>
          </Button>
        </Space>
        <Alert
          v-if="row.typeWarning"
          :message="row.typeWarning"
          type="info"
          show-icon
        />
        <Alert v-if="row.error" :message="row.error" type="error" show-icon />
        <Alert
          v-if="row.warning"
          :message="row.warning"
          type="warning"
          show-icon
        />
      </div>
    </Form>
    <template #footer>
      <Space wrap class="queue-actions">
        <span
          role="status"
          v-text="`已保存 ${completed} / ${items.length} 份`"
        ></span>
        <Button
          v-if="busy"
          :disabled="stopRequested"
          @click="stopRequested = true"
        >
          {{ stopRequested ? '等待当前文件完成…' : '完成当前文件后暂停' }}
        </Button>
        <Button
          v-else
          type="primary"
          :disabled="items.length === 0 || completed === items.length"
          @click="start"
        >
          {{
            items.some((row) => row.state === 'failed')
              ? '重试失败项并继续'
              : parseOnUpload
                ? '上传并解析'
                : '上传'
          }}
        </Button>
        <Button :disabled="busy" @click="open = false">
          {{ completed ? '完成' : '关闭' }}
        </Button>
      </Space>
    </template>
  </Modal>
</template>
<style scoped>
.batch-notice {
  margin-bottom: 16px;
}

.batch-form {
  margin-top: 16px;
}

.batch-form p {
  font-size: 13px;
  line-height: 1.6;
  color: hsl(var(--muted-foreground));
}

.queue-row {
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid hsl(var(--border));
}

.queue-fields {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 16px;
}

.source-name {
  margin-bottom: 8px;
  overflow-wrap: anywhere;
}

.queue-actions {
  margin-top: 16px;
}

@media (max-width: 640px) {
  .queue-fields {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }
}
</style>
