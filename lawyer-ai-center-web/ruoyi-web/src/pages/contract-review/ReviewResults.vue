<script setup lang="ts">
import type { ContractRisk } from './result';
import type { ReviewStage } from './workflow';
import { computed, ref, watch } from 'vue';
import LegalAnswer from '@/components/LegalAnswer.vue';
import LegalCitation from '@/components/LegalCitation.vue';
import { downloadReviewDiagnostic } from './diagnostics';
import { parseReviewResult, riskPresentation, textModeMessage } from './result';

const props = defineProps<{ content: string; stage: ReviewStage }>();
const emit = defineEmits<{ locate: [risk: ContractRisk] }>();
const result = computed(() =>
  props.stage === 'complete' ? parseReviewResult(props.content) : null,
);
const textMode = computed(
  () => props.stage === 'complete' && Boolean(props.content.trim()) && !result.value,
);
const expanded = ref<string[]>([]);
const levels = ['high', 'medium', 'low'] as const;
const hasBasicInfo = computed(
  () =>
    result.value
    && (result.value.contractType
      || result.value.basicInfo.title
      || result.value.basicInfo.parties.length
      || result.value.basicInfo.signDate),
);
watch(result, (value) => {
  expanded.value = value?.issues.length ? ['0'] : [];
});
function toggleAll() {
  expanded.value
    = expanded.value.length === result.value?.issues.length
      ? []
      : result.value?.issues.map((_, index) => String(index)) || [];
}
function locateExpanded(value: unknown) {
  const open = Array.isArray(value) ? value : [value];
  const risk = result.value?.issues[Number(open[open.length - 1])];
  if (risk)
    emit('locate', risk);
}
</script>

<template>
  <div class="review-results" :aria-busy="stage === 'reviewing'">
    <el-alert
      v-if="textMode"
      class="parse-warning"
      :title="textModeMessage"
      type="warning"
      :closable="false"
      show-icon
    />
    <p v-if="textMode" class="muted">
      完整回复已保留在本次审查会话，可继续追问或重新审查。<el-button
        text
        size="small"
        @click="downloadReviewDiagnostic(content)"
      >
        下载原始回复以排查
      </el-button>
    </p>
    <section class="risk-summary" aria-label="风险摘要">
      <div class="summary-heading">
        <h3>风险摘要</h3>
        <el-tag
          v-if="result"
          :type="riskPresentation[result.riskLevel].type"
          :class="`risk-level--${result.riskLevel}`"
          effect="light"
        >
          {{ riskPresentation[result.riskLevel].label }}
        </el-tag>
        <el-tag v-else type="info" size="small">
          {{ textMode ? '文本模式' : stage === 'reviewing' ? '生成中' : '待生成' }}
        </el-tag>
      </div>
      <p class="summary-text">
        {{
          result?.summary
            || (stage === 'reviewing'
              ? '正在审查合同，结果将逐步显示。'
              : textMode
                ? '请查看下方完整回复。'
                : '完成审查后，查看整体风险等级与综合建议。')
        }}
      </p>
      <div class="risk-counts" :aria-label="result ? '风险数量' : '风险数量尚未生成'">
        <div v-for="level in levels" :key="level" class="risk-count">
          <strong>{{
            result ? result.issues.filter((risk) => risk.riskLevel === level).length : '—'
          }}</strong>
          <span><i :class="level" aria-hidden="true" />{{ riskPresentation[level].label }}</span>
        </div>
      </div>
    </section>
    <template v-if="result">
      <section v-if="hasBasicInfo" class="result-section" aria-label="合同基本信息">
        <h3>合同基本信息</h3>
        <p v-if="result.contractType">
          合同类型：{{ result.contractType }}
        </p>
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item v-if="result.basicInfo.title" label="合同名称">
            {{ result.basicInfo.title }}
          </el-descriptions-item>
          <el-descriptions-item v-if="result.basicInfo.parties.length" label="合同当事人">
            {{ result.basicInfo.parties.join('；') }}
          </el-descriptions-item>
          <el-descriptions-item v-if="result.basicInfo.signDate" label="签署日期">
            {{ result.basicInfo.signDate }}
          </el-descriptions-item>
        </el-descriptions>
      </section>
      <section class="result-section" aria-label="风险点列表">
        <div class="section-heading">
          <h3>
            风险点列表 <span class="item-count">{{ result.issues.length }}</span>
          </h3>
          <el-button
            v-if="result.issues.length"
            text
            type="primary"
            size="small"
            @click="toggleAll"
          >
            {{ expanded.length === result.issues.length ? '全部收起' : '全部展开' }}
          </el-button>
        </div>
        <el-collapse
          v-if="result.issues.length"
          v-model="expanded"
          class="risk-collapse"
          @change="locateExpanded"
        >
          <el-collapse-item
            v-for="(risk, index) in result.issues"
            :key="index"
            :name="String(index)"
            class="risk-item"
          >
            <template #title>
              <div class="risk-heading" @click="emit('locate', risk)">
                <el-tag
                  :type="riskPresentation[risk.riskLevel].type"
                  :class="`risk-level--${risk.riskLevel}`"
                  size="small"
                >
                  {{ riskPresentation[risk.riskLevel].label }}
                </el-tag>
                <span>{{ risk.clauseTitle }}</span>
              </div>
            </template>
            <dl class="risk-details">
              <el-alert
                v-for="warning in risk.qualityWarnings"
                :key="warning"
                :title="warning"
                type="warning"
                :closable="false"
                show-icon
              />
              <dt>条款位置</dt>
              <dd>
                {{
                  risk.position && risk.position !== '未明确标注'
                    ? risk.position
                    : '合同未标注条号，请对照原文摘录核查'
                }}
                <el-button text type="primary" size="small" @click="emit('locate', risk)">
                  查看原文 ↗
                </el-button>
              </dd>
              <dt>原文条款</dt>
              <dd class="original-clause">
                {{ risk.quote || '未提供原文条款' }}
              </dd>
              <dt>风险分析</dt>
              <dd>{{ risk.problem || '未提供风险分析' }}</dd>
              <dt>修改建议</dt>
              <dd class="suggestion">
                {{ risk.suggestion || '未提供修改建议' }}
              </dd>
              <dt>法律依据</dt>
              <dd>
                <ul v-if="risk.legalBasis.length" class="legal-basis">
                  <li v-for="(basis, basisIndex) in risk.legalBasis" :key="basisIndex">
                    {{ basis }}
                  </li>
                </ul>
                <span v-else-if="!risk.citations.length" class="muted">未提供法律依据</span><LegalCitation
                  v-for="(citation, citationIndex) in risk.citations"
                  :key="citationIndex"
                  :citation="citation"
                />
              </dd>
            </dl>
          </el-collapse-item>
        </el-collapse>
        <el-empty v-else :image-size="54" description="未列出风险点" />
      </section>
      <section class="result-section" aria-label="缺失条款">
        <h3>缺失条款</h3>
        <ul v-if="result.missingClauses.length" class="missing-clauses">
          <li v-for="(clause, index) in result.missingClauses" :key="index">
            {{ clause }}
          </li>
        </ul>
        <p v-else class="muted">
          未提供缺失条款信息
        </p>
      </section>
      <section class="result-section" aria-label="整体建议">
        <h3>整体建议</h3>
        <p class="overall-suggestion">
          {{ result.overallSuggestion || '未提供整体建议' }}
        </p>
      </section>
    </template>
    <section
      v-else-if="content"
      class="result-section"
      :aria-label="textMode ? '文本审查结果' : '流式审查内容'"
    >
      <h3>{{ textMode ? '审查原文' : stage === 'error' ? '已收到的内容' : '实时审查内容' }}</h3>
      <LegalAnswer :content="content" />
    </section>
    <el-empty
      v-else
      :image-size="74"
      :description="stage === 'reviewing' ? '正在等待审查结果' : '暂无风险点'"
    />
  </div>
</template>

<style scoped lang="scss">
.review-results {
  min-width: 0;
}
h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}
.parse-warning {
  margin-top: 16px;
}
.risk-summary {
  padding: 20px 0;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}
.summary-heading,
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.summary-text {
  margin: 14px 0 18px;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.risk-counts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.risk-count {
  padding: 12px 4px;
  text-align: center;
  background: var(--el-fill-color-extra-light);
  border-radius: 8px;
}
.risk-count strong {
  display: block;
  margin-bottom: 8px;
  font-size: 20px;
  font-weight: 500;
}
.risk-count span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
.risk-count i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}
.high {
  background: var(--el-color-danger);
}
.medium {
  background: var(--el-color-warning);
}
.low {
  background: #3b82f6;
}
.risk-level--low {
  --el-tag-bg-color: rgba(59, 130, 246, 0.1);
  --el-tag-border-color: rgba(59, 130, 246, 0.25);
  --el-tag-text-color: #3b82f6;
}
.result-section {
  padding-top: 20px;
}
.result-section > h3,
.section-heading {
  margin-bottom: 12px;
}
.item-count {
  margin-left: 4px;
  color: var(--el-text-color-secondary);
  font-weight: 400;
}
.risk-heading {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 0;
  line-height: 1.7;
  text-align: left;
}
.risk-heading .el-tag {
  flex-shrink: 0;
  margin-top: 1px;
}
.risk-heading > span {
  overflow-wrap: anywhere;
}
.risk-collapse :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 48px;
  font-weight: 500;
}
.risk-details {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.8;
}
.risk-details dt {
  margin-top: 14px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}
.risk-details dd {
  margin: 5px 0 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.original-clause {
  padding: 10px 12px;
  background: var(--el-fill-color-extra-light);
  border-left: 3px solid var(--el-border-color);
  border-radius: 4px;
}
.suggestion {
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}
.legal-basis,
.missing-clauses {
  margin: 0;
  padding-left: 18px;
}
.legal-basis li,
.missing-clauses li {
  margin: 6px 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.missing-clauses,
.overall-suggestion,
.muted {
  font-size: 12px;
  line-height: 1.8;
}
.overall-suggestion {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.muted {
  color: var(--el-text-color-secondary);
}
.stream-result {
  max-height: 600px;
  overflow: auto;
  margin: 0;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
