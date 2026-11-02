<script setup lang="ts">
import type { DocTemplate, DocumentTypeOption } from '#/api/legal/doc-template';

import { Alert, Button, Modal, Select, Space, Table } from 'ant-design-vue';

import TemplateAiIcon from './template-ai-icon.vue';
import { useTemplateBulkActions } from './use-template-bulk-actions';

const props = defineProps<{
  reload: () => Promise<void>;
  types: DocumentTypeOption[];
}>();
const bulk = useTemplateBulkActions(() => props.types, props.reload);
const {
  open,
  mode,
  rows,
  saving,
  recognizing,
  deleteConfirmed,
  pending,
  saved,
  start,
  close,
  select,
  recognizeRow,
  confirm,
} = bulk;
function choose(id: string, value: string) {
  const row = rows.value.find((item) => item.id === id);
  if (row) select(row, value);
}
function retry(id: string) {
  const row = rows.value.find((item) => item.id === id);
  if (row) void recognizeRow(row);
}
defineExpose({
  start: (selected: DocTemplate[], action: 'delete' | 'type') =>
    start(selected, action),
});
const columns = [
  { title: '模板名称', dataIndex: 'name', width: 180 },
  { title: '原文书类型', dataIndex: 'original', width: 140 },
  { title: '当前选择（待保存）', key: 'choice', width: 210 },
  { title: '处理结果', key: 'result', width: 260 },
];
</script>
<template>
  <Modal
    :open="open"
    :title="mode === 'type' ? 'AI识别类型' : '批量删除模板'"
    width="960px"
    :style="{ top: '24px' }"
    :body-style="{ maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' }"
    :mask-closable="false"
    :closable="!saving"
    :keyboard="!saving"
    @cancel="close"
  >
    <Alert
      :type="mode === 'delete' ? 'warning' : 'info'"
      show-icon
      :message="
        mode === 'delete'
          ? `确认删除以下 ${rows.length} 份模板？删除后不能再选择这些模板生成文书；已有文书保留。此操作不可撤销。`
          : '识别结果仅替换下方选择，点击确定才保存。可手动修改；后台识别不会覆盖较新的手动选择。'
      "
    />
    <p v-if="mode === 'type'">
      使用已授权内网 Qwen 模型，每份最多发送6000字正文摘要。识别失败保留原类型。
    </p>
    <Table
      :columns="
        mode === 'type'
          ? columns
          : columns.filter((column) => column.key !== 'choice')
      "
      :data-source="rows"
      :pagination="false"
      :scroll="{ x: 790, y: 360 }"
      row-key="id"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <Space
          v-if="column.key === 'choice'"
          direction="vertical"
          style="width: 100%"
        >
          <Select
            :value="record.documentType"
            :options="types"
            :disabled="saving || record.state === 'saved'"
            :aria-label="`${record.name}的文书类型`"
            style="width: 100%"
            @update:value="choose(record.id, String($event))"
          />
          <Button
            :loading="record.recognizing"
            :disabled="saving || recognizing || record.state === 'saved'"
            @click="retry(record.id)"
          >
            <template #icon>
              <TemplateAiIcon
                :disabled="saving || recognizing || record.state === 'saved'"
              />
            </template>
            AI识别类型
          </Button>
        </Space>
        <Alert
          v-else-if="column.key === 'result' && record.notice"
          :message="record.notice"
          :type="
            record.state === 'failed'
              ? 'error'
              : record.state === 'saved'
                ? 'success'
                : 'info'
          "
          show-icon
        />
      </template>
    </Table>
    <template #footer>
      <Space wrap>
        <span
          v-text="
            `${saved} / ${rows.length} 项处理完成${recognizing ? ' · 后台识别中，可直接确定' : ''}`
          "
        ></span>
        <Button :disabled="saving" @click="close">
          {{ pending.length > 0 ? '取消' : '完成' }}
        </Button>
        <Button
          v-if="pending.length > 0"
          :danger="mode === 'delete'"
          :type="mode === 'delete' ? 'default' : 'primary'"
          :loading="saving"
          @click="confirm"
        >
          {{
            mode === 'delete'
              ? deleteConfirmed
                ? '重试失败项删除'
                : '确认删除'
              : '确定'
          }}
        </Button>
      </Space>
    </template>
  </Modal>
</template>
