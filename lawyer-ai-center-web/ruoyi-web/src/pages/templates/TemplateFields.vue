<script setup lang="ts">
import type { TemplateBlock, TemplateField } from '@/api/document/templateManagement';

defineProps<{ blocks: TemplateBlock[]; disabled: boolean }>();
const fields = defineModel<TemplateField[] | undefined>('fields');
const editable = defineModel<string[]>('editable', { required: true });
</script>

<template>
  <section class="field-review" aria-label="填写字段核对">
    <div class="review-heading">
      <h3>原文与填写字段</h3>
      <el-button :disabled="disabled" @click="editable = blocks.map((block) => block.id)">
        全选段落
      </el-button>
    </div>
    <p class="yl-safety-note">
      仅填写原有空白或占位符，固定文字、单位和版式保持原样。核对字段含义及类型，取消不需要填写的候选。
    </p>
    <article v-for="(block, index) in blocks" :key="block.id" class="block-review">
      <div class="original-block">
        <el-checkbox-group
          v-model="editable"
          :disabled="disabled"
          :aria-label="`第${index + 1}段允许填写`"
        >
          <el-checkbox :value="block.id">
            第 {{ index + 1 }} 段 · 允许填写
          </el-checkbox>
        </el-checkbox-group>
        <pre>{{ block.text || "（空白段落）" }}</pre>
      </div>
      <div class="block-fields">
        <template v-if="fields?.some((field) => field.blockId === block.id)">
          <div
            v-for="field in fields.filter((item) => item.blockId === block.id)"
            :key="field.id"
            class="field-row"
          >
            <el-checkbox
              v-model="field.enabled"
              class="field-enabled"
              :disabled="disabled || !editable.includes(block.id)"
              :aria-label="`使用字段 ${field.label}`"
            >
              填写
            </el-checkbox>
            <label>字段名称<el-input
              v-model="field.label"
              :disabled="disabled || !editable.includes(block.id)"
              :maxlength="80"
              :aria-label="`第${index + 1}段字段名称`"
            /></label>
            <label>类型<el-select
              v-model="field.kind"
              :disabled="disabled || !editable.includes(block.id)"
              aria-label="字段类型"
            ><el-option label="文本" value="text" /><el-option
              label="金额"
              value="money"
            /><el-option label="数字" value="number" /><el-option
              label="日期"
              value="date"
            /></el-select></label>
            <span class="field-unit">单位：{{ field.unit || "无" }}</span>
          </div>
        </template>
        <p v-else class="yl-safety-note">
          {{
            fields === undefined
              ? "按已有段落填写配置保存，仍不得改写固定文字。"
              : "本段未识别到填写字段。需要增加字段时，请在原文件中使用明确的空白或占位符后替换文件。"
          }}
        </p>
      </div>
    </article>
  </section>
</template>

<style scoped>
.review-heading {
  display: flex;
  gap: var(--yl-space-4);
  align-items: center;
  justify-content: space-between;
}
.review-heading h3 {
  margin: 0;
  font-size: 16px;
}
.block-review {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(340px, 1.3fr);
  gap: var(--yl-space-4);
  padding: var(--yl-space-4) 0;
  border-top: 1px solid var(--yl-color-border);
}
.original-block {
  min-width: 0;
}
.original-block pre {
  margin: var(--yl-space-2) 0;
  font: inherit;
  line-height: 1.8;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.block-fields {
  min-width: 0;
}
.field-row {
  display: grid;
  grid-template-columns: 64px minmax(100px, 1fr) 100px;
  gap: var(--yl-space-2);
  align-items: end;
  margin-bottom: var(--yl-space-3);
}
.field-row label {
  display: grid;
  gap: var(--yl-space-1);
  font-size: var(--yl-text-sm);
}
.field-unit {
  grid-column: 2 / -1;
  font-size: var(--yl-text-sm);
  color: var(--yl-color-text-muted);
}
.field-enabled {
  margin-right: 0;
}

@media (width <= 900px) {
  .block-review {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
