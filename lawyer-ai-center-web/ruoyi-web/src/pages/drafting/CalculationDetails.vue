<script setup lang="ts">
import type { CalculationResult } from './documentText';

defineProps<{ results: CalculationResult[] }>();
const states = { calculated: '程序已计算 · 待复核', pending: '依据不足 · 未计算', stale: '正文已编辑 · 待重新校验' };
</script>

<template>
  <details v-if="results.length" class="calculation-details">
    <summary>计算明细（{{ results.length }} 项）· 请核对输入和计算口径</summary>
    <p class="hint">
      金额由程序精确计算，按人民币元保留两位小数、四舍五入。计算完成不代表法律事实已确认。
      依据为本次起草要素的原文摘录，请对照原件复核；缺少依据的项目保留模板空白。
    </p>
    <section v-for="result in results" :key="result.id" class="calculation-item" :aria-label="result.label">
      <div class="calculation-heading">
        <strong>{{ result.label }}</strong>
        <el-tag :type="result.status === 'calculated' ? 'info' : 'warning'" size="small">
          {{ states[result.status] }}
        </el-tag>
        <span v-if="result.value !== null" class="calculation-value">
          {{ result.value }}{{ result.unit === '日期' ? '' : result.unit }}
        </span>
      </div>
      <p v-if="result.formula" class="formula">
        {{ result.formula }}
      </p>
      <p v-if="result.status !== 'calculated'" role="status">
        {{ result.message }}
      </p>
      <details v-if="result.sources.length" class="calculation-sources">
        <summary>查看起草要素中的依据（{{ result.sources.length }} 条）</summary>
        <blockquote v-for="(source, index) in result.sources" :key="index">
          {{ source }}
        </blockquote>
      </details>
    </section>
  </details>
</template>

<style scoped>
.calculation-details {
  padding: 12px 16px;
  margin-block: 16px;
  overflow-wrap: anywhere;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
}
summary {
  line-height: 1.8;
  cursor: pointer;
}
summary:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 4px;
}
.calculation-item + .calculation-item {
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.calculation-heading {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.calculation-value {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}
.formula,
.hint {
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
blockquote {
  padding-left: 12px;
  margin: 8px 0;
  white-space: pre-wrap;
  border-left: 2px solid var(--el-border-color);
}
</style>
