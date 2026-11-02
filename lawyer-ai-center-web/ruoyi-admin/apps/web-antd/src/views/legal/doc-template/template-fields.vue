<script setup lang="ts">
import type { DocTemplate, TemplateField } from '#/api/legal/doc-template';

import { computed } from 'vue';

import {
  Alert,
  Button,
  Checkbox,
  Input,
  InputNumber,
  Select,
} from 'ant-design-vue';

import {
  templateFieldContext,
  templateFieldSource,
} from './template-field-source';
import { useTemplateFields } from './use-template-fields';

const props = defineProps<{
  blocks: NonNullable<DocTemplate['blocks']>;
  disabled?: boolean;
  sourceAlongside?: boolean;
}>();
const fields = defineModel<TemplateField[]>({ required: true });
const {
  add,
  addedCount,
  available,
  blockId,
  end,
  error,
  rangeKey,
  selected,
  start,
  status,
  undo,
} = useTemplateFields(
  fields,
  () => props.blocks,
  () => !!props.disabled,
);
const kinds = [
  { value: 'text', label: '文字' },
  { value: 'money', label: '金额（元）' },
  { value: 'date', label: '完整日期' },
  { value: 'number', label: '数值 / 年月日分项' },
];
const source = computed(() => templateFieldSource(props.blocks, fields.value));
function context(field: TemplateField) {
  return templateFieldContext(
    props.blocks.find((block) => block.id === field.blockId)?.text || '',
    field,
  );
}
</script>

<template>
  <div :class="{ 'fields-alongside': sourceAlongside }">
    <Alert
      message="字段是独立配置，不修改原件。下划线按原填写区域保留显示；固定文字和单位不会交给AI重写。请核对字段含义，取消误识别的缩进、装饰线或签名线。"
      type="info"
      show-icon
    />
    <p v-if="fields.length === 0" role="status">
      未识别到填写区域，可补标已有空白；扫描图像或绘制线无法仅靠文字位置填写。
    </p>
    <p v-if="fields.length > 0" role="status">
      共 {{ source.groups.length }} 个段落、{{ fields.length }}
      个填写位置。每段原文只展示一次，原文编号与下方字段编号对应。
    </p>
    <Alert
      v-if="source.errors.length > 0"
      message="字段位置与原文不一致，请重新识别后核对；不要直接保存。"
      :description="source.errors.join('；')"
      type="error"
      show-icon
    />
    <div class="fields-list">
      <section
        v-for="group in source.groups"
        :key="group.blockId"
        class="field-group"
        :data-block-id="group.blockId"
      >
        <p>
          原文段落 {{ group.blockId }} · 本段
          {{ group.fields.length }} 个填写位置
        </p>
        <p v-if="!sourceAlongside" class="field-source">
          <span>原文：</span>
          <template
            v-for="(segment, segmentIndex) in group.segments"
            :key="segmentIndex"
          >
            <mark
              v-if="segment.field"
              class="field-highlight"
              :aria-label="`${segment.field.id}原文填写位置`"
              v-text="segment.text || '〔空白表格单元格〕'"
            ></mark>
            <span v-else v-text="segment.text"></span>
            <sup
              v-if="segment.field"
              class="field-number"
              v-text="`[${segment.number}]`"
            ></sup>
          </template>
        </p>
        <div
          v-for="(field, fieldIndex) in group.fields"
          :key="field.id"
          class="field-row"
          :data-field-id="field.id"
        >
          <Checkbox v-model:checked="field.enabled" :disabled="disabled">
            字段 {{ fieldIndex + 1 }} · 启用
          </Checkbox>
          <p class="field-association">
            <span>关联原文：</span>
            <span v-text="context(field).before"></span>
            <mark class="field-highlight" v-text="context(field).blank"></mark>
            <strong v-text="context(field).unit"></strong>
            <span v-text="context(field).after"></span>
          </p>
          <div class="field-inputs">
            <Input
              v-model:value="field.label"
              :maxlength="80"
              :disabled="disabled || !field.enabled"
              :aria-label="`${field.id}字段含义`"
              placeholder="字段含义，如逾期本金"
            />
            <Select
              v-model:value="field.kind"
              :options="kinds"
              :disabled="disabled || !field.enabled"
              :aria-label="`${field.id}数据类型`"
            />
          </div>
        </div>
      </section>
    </div>
    <details>
      <summary>补充遗漏的填写字段（可连续添加）</summary>
      <p>
        选择待补标空白，每添加一项自动切换下一处。也可修改起止位置（从0开始，结束位置不包含在内），只能选择原有空白。
      </p>
      <p role="status">
        本次已补标 {{ addedCount }} 项；剩余
        {{ available.length }} 处空白候选（含可能的缩进、装饰线，请逐项核对）。
      </p>
      <Select
        v-model:value="rangeKey"
        :options="
          available.map((range) => ({
            value: range.key,
            label: `${range.blockId}：位置 ${range.start}—${range.end} · ${blocks.find((b) => b.id === range.blockId)?.text || '空白表格单元格'}`,
          }))
        "
        :disabled="disabled || available.length === 0"
        aria-label="待补标空白"
        placeholder="选择下一处空白，或在下方手动指定位置"
        class="block-select"
      />
      <Select
        v-model:value="blockId"
        :options="
          blocks.map((b) => ({
            value: b.id,
            label: `${b.id}：${b.text || '空白表格单元格'}`,
          }))
        "
        :disabled="disabled"
        aria-label="补标段落"
        class="block-select"
      />
      <pre v-if="selected" class="field-source">{{ selected.text }}</pre>
      <div class="manual-inputs">
        <InputNumber
          v-model:value="start"
          :min="0"
          :precision="0"
          :disabled="disabled"
          aria-label="空白起始位置"
        />
        <InputNumber
          v-model:value="end"
          :min="0"
          :precision="0"
          :disabled="disabled"
          aria-label="空白结束位置"
        />
        <Button :disabled="disabled" @click="add">添加并继续</Button>
        <Button :disabled="disabled || addedCount === 0" @click="undo">
          <span>撤销最近一次补标</span>
        </Button>
      </div>
      <p v-if="status" role="status">{{ status }}</p>
      <Alert
        v-if="error"
        :message="error"
        type="error"
        role="alert"
        show-icon
      />
    </details>
  </div>
</template>

<style scoped>
.fields-list {
  max-height: 420px;
  margin-block: 12px;
  overflow: auto;
}

.fields-alongside .fields-list {
  max-height: none;
  overflow: visible;
}

.field-group {
  padding-block: 12px;
  border-bottom: 1px solid var(--ant-color-border);
}

.field-number {
  margin-inline: 4px;
  color: var(--ant-color-text-secondary);
}

.field-row {
  padding-block: 12px;
  border-bottom: 1px solid var(--ant-color-border);
}

.field-source {
  margin-block: 8px;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.field-highlight {
  color: inherit;
  white-space: pre-wrap;
  text-decoration: underline;
  outline: 1px solid var(--ant-color-warning-border);
  background: var(--ant-color-warning-bg);
}

.field-inputs {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 8px;
}

.manual-inputs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-block: 8px;
}

.block-select {
  width: 100%;
}

@media (max-width: 480px) {
  .field-inputs {
    grid-template-columns: 1fr;
  }
}
</style>
