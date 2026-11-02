<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, defineAsyncComponent, ref } from 'vue';
import { useRoute } from 'vue-router';
import { lawyerRequest } from '@/api/lawyer';
import { useUserStore } from '@/stores';
import {
  citationLabel,
  citationTarget,
  citationWarning,
  normalizeCitation,
} from '@/utils/legalCitation';

const props = defineProps<{ citation: unknown }>();
const AddToReportDialog = defineAsyncComponent(() => import('./AddToReportDialog.vue'));
const user = useUserStore();
const route = useRoute();
const addingReport = ref(false);
function addToReport() {
  if (!user.token) {
    user.ensureLogin(route.fullPath, '登录后可将引用加入检索报告');
    return;
  }
  addingReport.value = true;
}
const reference = computed(() => normalizeCitation(props.citation));
const visible = ref(false);
const loading = ref(false);
const error = ref('');
const detail = ref<{ content?: string; sourceName?: string; reference?: unknown } | null>(null);
let generation = 0;
async function open() {
  if (!reference.value)
    return;
  const target = citationTarget(reference.value);
  if (target.kind === 'source') {
    window.open(target.value, '_blank', 'noopener,noreferrer');
    return;
  }
  if (target.kind === 'missing') {
    ElMessage.info('暂无原文，仅展示引用信息');
    return;
  }
  visible.value = true;
  loading.value = true;
  error.value = '';
  detail.value = null;
  const current = ++generation;
  try {
    const result = await lawyerRequest<{ data: NonNullable<typeof detail.value> }>(
      `/legal/citations/fragments/${encodeURIComponent(target.value)}`,
    );
    if (current === generation)
      detail.value = result.data;
  }
  catch (e) {
    if (current === generation)
      error.value = (e as Error).message;
  }
  finally {
    if (current === generation)
      loading.value = false;
  }
}
</script>

<template>
  <span v-if="reference" class="legal-reference">
    <button
      type="button"
      class="citation-link"
      :class="{ 'citation-missing': citationTarget(reference).kind === 'missing' }"
      :data-source-kind="citationTarget(reference).kind"
      :title="citationTarget(reference).kind === 'missing' ? '暂无原文' : '查看引用原文'"
      @click="open"
    >
      {{ citationLabel(reference) }}
      <span v-if="citationTarget(reference).kind === 'missing'" class="source-unavailable">暂无原文</span>
      <strong
        v-if="citationWarning(reference)"
        :class="
          reference.type === 'law' && reference.status === 'repealed' ? 'repealed' : 'warning'
        "
      >{{ citationWarning(reference) }}</strong>
    </button>
    <button type="button" class="report-add-link" @click="addToReport">加入检索报告</button>
    <AddToReportDialog
      v-if="addingReport"
      :reference="reference"
      :content="detail?.content"
      @close="addingReport = false"
    />
    <el-drawer v-model="visible" title="引用原文" size="min(640px, 100vw)" append-to-body>
      <div v-loading="loading">
        <p>{{ citationLabel(reference) }}</p>
        <strong v-if="citationWarning(reference)" class="warning">{{
          citationWarning(reference)
        }}</strong>
        <el-alert v-if="error" :title="error" type="error" :closable="false" />
        <el-button v-if="error" @click="open">重试</el-button>
        <template v-if="detail"><strong
                                  v-if="
                                    normalizeCitation(detail.reference)
                                      && citationWarning(normalizeCitation(detail.reference)!)
                                  "
                                  class="warning"
                                >{{ citationWarning(normalizeCitation(detail.reference)!) }}</strong>
          <p>{{ detail.sourceName }}</p>
          <pre class="source-content">{{ detail.content || '暂无原文，仅展示引用信息' }}</pre>
        </template>
        <el-button v-if="detail" type="primary" @click="addToReport">加入检索报告</el-button>
      </div>
    </el-drawer>
  </span>
  <span v-else>{{ typeof citation === 'string' ? citation : '引用信息不完整' }}</span>
</template>

<style scoped>
.legal-reference {
  display: inline-block;
  max-width: 100%;
  margin: 3px 5px 3px 0;
}
.citation-link {
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
  color: var(--yl-color-brand-strong);
  padding: 5px 8px;
  cursor: pointer;
  font: inherit;
  text-align: left;
  white-space: normal;
  overflow-wrap: anywhere;
  line-height: 1.6;
}
.citation-link:hover {
  text-decoration: underline;
}
.warning,
.repealed {
  display: inline-block;
  margin-left: 4px;
  padding: 0 4px;
  border-radius: 3px;
  color: #9a4b00;
  background: #fff0d6;
}
.repealed {
  color: #b42318;
  background: #fee4e2;
}
.source-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font: inherit;
  line-height: 1.8;
  margin-top: 16px;
}
</style>

<style scoped>
.report-add-link {
  margin: 3px 0 3px 5px;
  padding: 3px 5px;
  border: 0;
  background: transparent;
  color: var(--yl-color-brand-strong);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  white-space: nowrap;
}
.report-add-link:hover {
  text-decoration: underline;
}
.citation-missing {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-style: dashed;
}
.citation-missing:hover {
  text-decoration: none;
}
.source-unavailable {
  margin-left: 5px;
  font-size: 12px;
}
</style>
