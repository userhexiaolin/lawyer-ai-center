<script setup lang="ts">
import type { ReviewHistory } from './history';
import type { ContractRisk } from './result';
import type { AgentVO } from '@/api/agent/types';
import { Clock, Document, UploadFilled } from '@element-plus/icons-vue';
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getAgentList } from '@/api/agent';
import { readMediaApiResult } from '@/api/media/response';
import AppPageHeader from '@/components/AppPageHeader.vue';
import CaseAssociation from '@/components/CaseAssociation.vue';
import LawyerUsage from '@/components/LawyerUsage.vue';
import { useUserStore } from '@/stores';
import { useAgentStore } from '@/stores/modules/agent';
import { useChatStore } from '@/stores/modules/chat';
import { checkDownloadAuth } from '@/utils/authExpiry';
import CollectionLetterPanel from './CollectionLetterPanel.vue';
import { loadReviewDetail, loadReviewHistory } from './history';
import { locateClause } from './location';
import { parseReviewResult } from './result';
import ReviewResults from './ReviewResults.vue';
import { downloadRevision, reviewFilename } from './revision';
import { useContractReview } from './useContractReview';
import { contractChecklists } from './workflow';

const coverage = computed(() => {
  const entries
    = contractChecklists[state.appliedSettings?.contractType || state.contractType] || [];
  return entries.map(label => ({
    label,
    mentioned: label.split(/[与和/]/).some(term => term.length > 1 && state.text.includes(term)),
  }));
});

const caseId = ref('');
const historyFileName = ref('');
const appliedPosition = computed(() => state.appliedSettings?.position || state.position);
const appliedFocus = computed(() => state.appliedSettings?.focusClauses || state.focusClauses);
const contractName = computed(() => state.file?.name || historyFileName.value || '合同');
const {
  state,
  busy: reviewBusy,
  canReview,
  stageLabel,
  prepare,
  review,
} = useContractReview(() => caseId.value);
const letterBusy = ref(false);
const busy = computed(() => reviewBusy.value || letterBusy.value);
const fileInput = ref<HTMLInputElement>();
const route = useRoute();
const router = useRouter();
const history = ref<ReviewHistory[]>([]);
const historyPage = ref(1);
const historyTotal = ref(0);
const historyBusy = ref(false);
const historyError = ref('');
const reportBusy = ref(false);
const selectedRisk = ref<ContractRisk>();
const editingSource = ref(false);
const sourcePreview = ref<HTMLElement>();
const located = computed(() => locateClause(state.text, selectedRisk.value?.quote || ''));
const locationHint = computed(() =>
  !selectedRisk.value?.quote
    ? '模型未提供原文摘录，请结合条款位置核查。'
    : !state.text
        ? '历史记录暂无完整正文，已显示该风险的原文摘录。'
        : !located.value.found
            ? '摘录与正文未完全匹配，已显示摘录供核对，未标注猜测位置。'
            : located.value.repeated
              ? '正文有多处相同摘录，已高亮首次匹配，请结合条号核查。'
              : '已在下方合同正文中高亮对应原文。',
);
async function locateRisk(risk: ContractRisk) {
  selectedRisk.value = risk;
  editingSource.value = false;
  await nextTick();
  sourcePreview.value
    ?.querySelector('mark')
    ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
}
watch(
  () => state.stage,
  (value) => {
    if (value === 'complete' && state.contractType === '自动识别') {
      const detected = parseReviewResult(state.resultText)?.contractType || '';
      const type = Object.keys(contractChecklists).find(type =>
        type.split('/').some(term => detected.includes(term)),
      );
      if (type) {
        state.contractType = type;
        if (state.appliedSettings)
          state.appliedSettings.contractType = type;
      }
    }
    if (value !== 'complete')
      selectedRisk.value = undefined;
    editingSource.value = false;
  },
);
async function continueReview() {
  if (busy.value)
    return;
  try {
    const response = await readMediaApiResult<AgentVO[]>(getAgentList());
    const agent = response.data.find(
      item => item.agentName === '合同审查官' && item.status === '0',
    );
    if (!agent)
      throw new Error('合同审查官当前不可用，请联系管理员');
    useAgentStore().setCurrentAgentInfo(agent);
    useChatStore().clearCurrentWorkflow();
    await router.push(`/chat/${state.sessionId}`);
  }
  catch (error) {
    state.error = (error as Error).message;
  }
}
async function exportReport() {
  if (reportBusy.value || state.stage !== 'complete' || !parseReviewResult(state.resultText))
    return;
  reportBusy.value = true;
  try {
    const user = useUserStore();
    const token = user.token;
    const response = await fetch(`${import.meta.env.VITE_API_URL}/system/contract-review/report`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${user.token}`,
        'clientid': import.meta.env.VITE_CLIENT_ID,
        'content-type': 'application/json',
      },
      body: JSON.stringify({ sessionId: state.sessionId, content: state.resultText }),
    });
    await checkDownloadAuth(response, token);
    if (!response.ok || !response.headers.get('content-type')?.includes('wordprocessingml')) {
      const error = await response.json();
      throw new Error(error.msg || '报告生成失败，请重试');
    }
    const url = URL.createObjectURL(await response.blob());
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = reviewFilename(contractName.value, '审查报告', 'docx');
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  catch (error) {
    state.error = (error as Error).message;
  }
  finally {
    reportBusy.value = false;
  }
}
async function refreshHistory(page = 1) {
  if (historyBusy.value)
    return;
  historyBusy.value = true;
  try {
    const response = await loadReviewHistory(page);
    history.value = page === 1 ? response.rows : [...history.value, ...response.rows];
    historyPage.value = page;
    historyTotal.value = response.total;
    historyError.value = '';
  }
  catch (error) {
    historyError.value = (error as Error).message;
  }
  finally {
    historyBusy.value = false;
  }
}
async function openReview(id: string) {
  if (busy.value)
    return;
  state.stage = 'reading';
  state.error = '';
  try {
    const detail = await loadReviewDetail(id);
    state.sessionId = id;
    state.resultText = detail.content;
    state.stage = 'complete';
    state.file = null;
    state.fileUrl = '';
    state.text = detail.sourceText;
    state.position = detail.position;
    state.focusClauses = detail.focusClauses;
    state.appliedSettings = {
      position: detail.position,
      focusClauses: [...detail.focusClauses],
      contractType: '自动识别',
    };
    historyFileName.value = detail.fileName;
    const detected = parseReviewResult(detail.content)?.contractType || '';
    state.contractType
      = Object.keys(contractChecklists).find(type =>
        type.split('/').some(term => detected.includes(term)),
      ) || '自动识别';
    await router.replace({ path: '/contract-review', query: { sessionId: id } });
  }
  catch (error) {
    state.stage = 'error';
    state.error = (error as Error).message;
  }
}
onMounted(() => {
  void refreshHistory();
  if (typeof route.query.sessionId === 'string')
    void openReview(route.query.sessionId);
});
function selectFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (file)
    void prepare(file);
}
</script>

<template>
  <main class="contract-review yl-page">
    <AppPageHeader
      title="合同审查"
      description="上传合同并核对正文，对照风险与出处，形成可复核的审查建议。"
      eyebrow="法律工作台"
    >
      <template #meta>
        <LawyerUsage :key="state.sessionId" /><CaseAssociation
          v-model="caseId"
          :session-id="state.sessionId || undefined"
          :disabled="busy"
        />
      </template>
    </AppPageHeader>
    <el-alert
      v-if="state.error"
      class="review-error"
      :title="state.error"
      type="error"
      :closable="false"
      show-icon
    />
    <p class="yl-safety-note">
      AI 审查结果为辅助建议，请逐项核对原文、事实与引用。
    </p>
    <p class="review-status" role="status" aria-live="polite">
      {{ stageLabel }}
    </p>

    <div class="review-grid">
      <aside class="review-sidebar" aria-label="合同文件与历史审查">
        <section class="review-panel upload-panel" aria-labelledby="upload-title">
          <div class="panel-heading">
            <h2 id="upload-title">
              上传合同
            </h2>
          </div>
          <div class="upload-placeholder">
            <el-icon :size="30" aria-hidden="true">
              <UploadFilled />
            </el-icon>
            <strong>{{ state.file?.name || '从一份合同开始' }}</strong>
            <p>PDF、DOCX、JPG、PNG，最大10MB</p>
            <input
              ref="fileInput"
              class="file-input"
              type="file"
              accept=".pdf,.docx,.jpg,.jpeg,.png"
              :disabled="busy"
              aria-label="选择合同文件"
              @change="selectFile"
            >
            <el-button :icon="UploadFilled" :disabled="busy" @click="fileInput?.click()">
              {{ state.fileUrl ? '替换合同文件' : '选择合同文件' }}
            </el-button>
          </div>
          <el-progress
            v-if="state.stage === 'uploading' || state.fileUrl"
            class="upload-progress"
            :percentage="state.progress"
            :status="state.fileUrl ? 'success' : undefined"
          />
          <p v-if="state.fileUrl" class="file-success">
            文件上传成功
          </p>
          <el-link
            v-if="state.fileUrl"
            :href="state.fileUrl"
            target="_blank"
            rel="noopener noreferrer"
            type="primary"
          >
            打开已上传文件
          </el-link>
          <el-button
            v-if="state.error && state.file"
            class="retry-button"
            :disabled="busy"
            @click="prepare()"
          >
            重试文件处理
          </el-button>
          <label class="notes-label">合同专项类型</label>
          <el-select v-model="state.contractType" aria-label="合同专项类型" :disabled="busy">
            <el-option label="自动识别" value="自动识别" /><el-option
              v-for="(_, type) in contractChecklists"
              :key="type"
              :value="type"
              :label="type"
            />
          </el-select>
          <label class="notes-label">审查立场</label>
          <el-select v-model="state.position" aria-label="审查立场" :disabled="busy">
            <el-option
              v-for="item in ['中立', '己方（甲方）', '对方（乙方）', '买方', '卖方']"
              :key="item"
              :value="item"
              :label="item"
            />
          </el-select>
          <label class="notes-label">重点条款（空为全文）</label>
          <el-select
            v-model="state.focusClauses"
            placeholder="选择重点条款（可选）"
            multiple
            clearable
            aria-label="重点条款"
            :disabled="busy"
          >
            <el-option
              v-for="item in ['付款', '违约责任', '解除', '管辖', '交付验收', '保密', '知识产权']"
              :key="item"
              :value="item"
              :label="item"
            />
          </el-select>
          <label class="notes-label" for="review-notes">审查重点（选填）</label>
          <el-input
            id="review-notes"
            v-model="state.notes"
            type="textarea"
            :rows="3"
            :maxlength="1000"
            show-word-limit
            :disabled="busy"
            placeholder="例如：重点审查违约责任和管辖条款"
          />
        </section>

        <section class="review-panel history-panel" aria-labelledby="history-title">
          <div class="panel-heading">
            <h2 id="history-title">
              <el-icon aria-hidden="true">
                <Clock />
              </el-icon>历史审查
            </h2>
          </div>
          <el-button size="small" :loading="historyBusy" @click="refreshHistory()">
            刷新历史
          </el-button>
          <p v-if="historyError" role="alert">
            {{ historyError }}
          </p>
          <div v-for="item in history" :key="item.id" class="current-review">
            <el-button text :disabled="busy" @click="openReview(item.id)">
              {{ item.sessionTitle }}
            </el-button><router-link
              v-if="item.caseId"
              :to="{ path: '/my-cases', query: { id: item.caseId } }"
            >
              查看所属案件 ↗
            </router-link>
          </div>
          <el-button
            v-if="history.length < historyTotal"
            :loading="historyBusy"
            @click="refreshHistory(historyPage + 1)"
          >
            加载更多
          </el-button>
          <div v-if="state.sessionId" class="current-review">
            <strong>{{ state.file?.name }}</strong>
            <p>{{ stageLabel }}</p>
            <small>本次审查 · 会话已创建</small>
          </div>
          <el-empty v-else :image-size="62" description="暂无审查记录，上传第一份合同开始">
            <el-button :disabled="busy" @click="fileInput?.click()">
              上传合同
            </el-button>
          </el-empty>
        </section>
      </aside>

      <section class="review-panel preview-panel" aria-labelledby="preview-title">
        <div class="panel-heading">
          <h2 id="preview-title">
            合同原文
          </h2>
          <el-tag :type="state.stage === 'error' ? 'danger' : 'info'" effect="plain" size="small">
            {{ stageLabel }}
          </el-tag>
        </div>
        <section
          v-if="selectedRisk"
          class="risk-location"
          aria-live="polite"
          aria-label="所选风险的原文摘录"
        >
          <strong>{{
            selectedRisk.position && selectedRisk.position !== '未明确标注'
              ? selectedRisk.position
              : '合同未明确标注条号'
          }}
            · {{ selectedRisk.clauseTitle }}</strong>
          <blockquote>{{ selectedRisk.quote || '未提供原文摘录' }}</blockquote>
          <p>{{ locationHint }}</p>
        </section>
        <div
          v-if="state.fileUrl || state.text"
          v-loading="state.stage === 'reading'"
          class="contract-text"
        >
          <el-button
            v-if="state.stage === 'complete'"
            text
            size="small"
            @click="editingSource = !editingSource"
          >
            {{ editingSource ? '返回原文对照' : '编辑合同正文' }}
          </el-button>
          <pre
            v-if="state.stage === 'complete' && !editingSource"
            ref="sourcePreview"
            class="source-preview"
            aria-label="合同正文与风险高亮"
          ><template v-for="(segment, index) in located.segments" :key="index"><mark v-if="segment.highlighted">{{ segment.text }}</mark><span v-else>{{ segment.text }}</span></template></pre>
          <template v-else>
            <p class="text-hint">
              请核对读取的正文，尤其是金额、日期与图片识别文字，可直接修改后审查。
            </p>
            <el-input
              v-model="state.text"
              aria-label="合同正文"
              type="textarea"
              :autosize="{ minRows: 20, maxRows: 36 }"
              :maxlength="60000"
              show-word-limit
              :disabled="busy"
              placeholder="合同正文将显示在这里；读取失败时可粘贴或填写正文"
            />
          </template>
        </div>
        <div v-else class="preview-placeholder">
          <span class="document-icon"><el-icon :size="38" aria-hidden="true"><Document /></el-icon></span>
          <h3>{{ state.stage === 'complete' ? '暂无完整原文' : '请上传合同' }}</h3>
          <p>
            {{
              state.stage === 'complete'
                ? '此历史记录未保存可还原的合同正文，可先点击风险查看摘录，或重新上传原合同。'
                : '合同原文将显示在这里，便于对照风险点查看。'
            }}
          </p>
        </div>
      </section>

      <section class="review-panel result-panel" aria-labelledby="result-title">
        <div class="panel-heading">
          <h2 id="result-title">
            审查结果
          </h2>
          <el-button
            type="primary"
            size="small"
            :loading="state.stage === 'reviewing'"
            :disabled="!canReview || busy"
            @click="review"
          >
            {{ state.resultText && state.stage !== 'reviewing' ? '重新审查' : '开始审查' }}
          </el-button>
        </div>
        <p v-if="state.stage === 'complete'">
          本次立场：{{ appliedPosition }}；范围：{{ appliedFocus.join('、') || '全文' }}
        </p>
        <el-button
          :disabled="state.stage !== 'complete' || !parseReviewResult(state.resultText)"
          @click="downloadRevision(state.resultText, contractName, appliedPosition, appliedFocus)"
        >
          导出修订对照（Markdown）
        </el-button>
        <details v-if="coverage.length && state.stage === 'complete'">
          <summary>本类型关注点 · 原文提及情况</summary>
          <p>
            “已提及”仅表示正文关键词命中，不表示已完成法律核查；未提及请人工检查。{{
              appliedFocus.length ? '本次为重点范围审查。' : ''
            }}
          </p>
          <ul>
            <li v-for="item in coverage" :key="item.label">
              {{ item.label }}：{{ item.mentioned ? '已提及' : '未提及' }}
            </li>
          </ul>
        </details>
        <ReviewResults :content="state.resultText" :stage="state.stage" @locate="locateRisk" />
        <el-button
          type="primary"
          :loading="reportBusy"
          :disabled="state.stage !== 'complete' || !parseReviewResult(state.resultText)"
          @click="exportReport"
        >
          生成审查报告（Word）
        </el-button>
        <el-button v-if="state.sessionId" :disabled="busy" @click="continueReview">
          在会话中继续追问
        </el-button>
      </section>
    </div>
    <CollectionLetterPanel
      :source="state.text"
      :file-url="state.fileUrl"
      :disabled="reviewBusy"
      :case-id="caseId"
      @busy="letterBusy = $event"
    />
  </main>
</template>

<style scoped lang="scss">
.contract-review {
  min-width: 0;
}
.workbench-header {
  margin-bottom: var(--workspace-section-gap);
}
.workbench-eyebrow {
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--el-text-color-secondary);
}
.workbench-header h1 {
  margin: 10px 0 8px;
  font-size: clamp(22px, 2vw, 28px);
  font-weight: 550;
  letter-spacing: -0.04em;
  line-height: 1.4;
}
.workbench-header p {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}
.review-grid {
  display: grid;
  grid-template-columns: minmax(200px, 0.75fr) minmax(280px, 1.6fr) minmax(260px, 1fr);
  gap: 16px;
  align-items: stretch;
}
.review-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.review-panel {
  min-width: 0;
  padding: 18px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--yl-radius-panel);
}
.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 28px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}
.panel-heading h2 {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}
.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px 12px;
  margin-top: 16px;
  text-align: center;
  border: 1px dashed var(--el-border-color);
  border-radius: var(--yl-radius-panel);
  background: var(--el-fill-color-extra-light);
}
.upload-placeholder > .el-icon {
  color: var(--el-text-color-placeholder);
}
.upload-placeholder strong {
  font-size: 13px;
  font-weight: 500;
}
.upload-placeholder p,
.panel-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}
.history-panel {
  flex: 1;
}
.panel-hint {
  text-align: center;
  overflow-wrap: anywhere;
}
.preview-panel {
  display: flex;
  flex-direction: column;
  min-height: 540px;
}
.preview-placeholder {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  text-align: center;
}
.document-icon {
  display: grid;
  place-items: center;
  width: 76px;
  height: 88px;
  color: var(--el-text-color-placeholder);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: var(--yl-radius-panel);
}
.preview-placeholder h3 {
  margin: 22px 0 10px;
  font-size: 16px;
  font-weight: 500;
}
.preview-placeholder p {
  max-width: 240px;
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}
.review-error {
  margin-bottom: 12px;
}
.review-status {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.file-input {
  display: none;
}
.upload-placeholder strong,
.current-review strong {
  max-width: 100%;
  overflow-wrap: anywhere;
}
.upload-progress {
  margin-top: 16px;
}
.file-success {
  font-size: 12px;
  color: var(--el-color-success);
}
.retry-button {
  margin-top: 12px;
}
.notes-label {
  display: block;
  margin: 22px 0 10px;
  font-size: 13px;
}
.contract-text {
  padding-top: 16px;
}
.risk-location {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.8;
}
.risk-location blockquote {
  margin: 10px 0;
  padding: 8px 10px;
  border-left: 3px solid var(--el-color-warning);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 180px;
  overflow: auto;
  background: var(--el-fill-color-light);
}
.risk-location p {
  margin: 0;
  color: var(--el-text-color-secondary);
}
.source-preview {
  max-height: 70vh;
  overflow: auto;
  padding: 14px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font: inherit;
  font-size: 13px;
  line-height: 1.9;
  background: var(--el-fill-color-extra-light);
  border-radius: 8px;
}
.source-preview mark {
  background: var(--yl-color-highlight);
  color: var(--yl-color-highlight-text);
  border-radius: 3px;
  scroll-margin-top: 24px;
}
.text-hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}
.current-review {
  padding-top: 18px;
  font-size: 13px;
}
.current-review p,
.current-review small {
  color: var(--el-text-color-secondary);
}
@container workspace (max-width: 960px) {
  .review-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .preview-panel {
    min-height: 360px;
  }
}
</style>
