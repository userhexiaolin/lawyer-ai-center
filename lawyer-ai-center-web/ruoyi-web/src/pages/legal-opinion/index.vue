<script setup lang="ts">
import type { OpinionInput } from './document';
import type { AgentVO } from '@/api/agent/types';
import { ElMessage } from 'element-plus';
import { useHookFetch } from 'hook-fetch/vue';
import { computed, onScopeDispose, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { getAgentList } from '@/api/agent';
import { send } from '@/api/chat';
import { saveDocumentDraft } from '@/api/document';
import { templatePrompt } from '@/api/document/templates';
import { caseDetail, lawyerRequest } from '@/api/lawyer';
import { readWordResponse, wordFilename } from '@/api/legal-report/download';
import { readMediaApiResult } from '@/api/media/response';
import { postFile } from '@/api/oss';
import { create_session } from '@/api/session';
import AppPageHeader from '@/components/AppPageHeader.vue';
import CaseAssociation from '@/components/CaseAssociation.vue';
import LegalAnswer from '@/components/LegalAnswer.vue';
import LegalCitation from '@/components/LegalCitation.vue';

import DocumentEditor from '@/pages/drafting/DocumentEditor.vue';
import DocumentTemplatePicker from '@/pages/drafting/DocumentTemplatePicker.vue';
import { useUserStore } from '@/stores';
import { checkDownloadAuth } from '@/utils/authExpiry';
import { opinionMarkdown, parseOpinion, readInput } from './document';
import { createOpinionWorkflow, initialOpinionState } from './workflow';

const templateId = ref('');

const router = useRouter();
const caseId = ref('');
const editing = ref(false);
const saving = ref(false);

const user = useUserStore();
const input = reactive<OpinionInput>({ facts: '', focus: '', materialName: '', materialText: '' });
const state = reactive(initialOpinionState());
const { stream, cancel } = useHookFetch({ request: send });
const workflow = createOpinionWorkflow(state, {
  decorate: content => templatePrompt('法律意见书', templateId.value, content),
  agent: async () => {
    const { data } = await readMediaApiResult<AgentVO[]>(getAgentList());
    const agent = data.find(a => a.agentName === '法律意见书助手' && a.status === '0');
    if (!agent?.id)
      throw new Error('法律意见书助手尚未启用，请联系管理员完成配置');
    return { id: agent.id, modelName: agent.modelName };
  },
  create: async title =>
    (
      await readMediaApiResult<unknown>(
        create_session({ sessionTitle: title, caseId: caseId.value || undefined }),
      )
    ).data,
  stream: payload => stream(payload),
  cancel,
});
const reading = ref(false);
const exporting = ref(false);
const loadingHistory = ref(false);
const opening = ref(false);
const page = ref(1);
const total = ref(0);
const history = ref<{ id: string; sessionTitle: string; updateTime?: string }[]>([]);
const locked = computed(
  () => state.busy || reading.value || opening.value || exporting.value || saving.value,
);
const current = computed(() => !!state.document && state.fingerprint === JSON.stringify(input));
let epoch = 0;
let disposed = false;
let historyEpoch = 0;
let controller: AbortController | undefined;
async function loadHistory() {
  const version = ++historyEpoch;
  const token = user.token;
  if (!token)
    return;
  loadingHistory.value = true;
  try {
    const result = await lawyerRequest<{ rows: typeof history.value; total: number }>(
      `/system/session/list?opinionOnly=true&pageNum=${page.value}&pageSize=10`,
    );
    if (!disposed && version === historyEpoch && user.token === token) {
      history.value = result.rows;
      total.value = result.total;
    }
  }
  catch (e) {
    if (!disposed && version === historyEpoch && user.token === token)
      ElMessage.error((e as Error).message);
  }
  finally {
    if (!disposed && version === historyEpoch)
      loadingHistory.value = false;
  }
}
async function generate() {
  if (locked.value)
    return;
  if (!user.token) {
    user.ensureLogin('/legal-opinion');
    return;
  }
  await workflow.generate({ ...input });
  if (state.error)
    ElMessage.error(state.error);
  void loadHistory();
}
async function material(event: Event) {
  const element = event.target as HTMLInputElement;
  const file = element.files?.[0];
  element.value = '';
  if (!file || locked.value)
    return;
  const version = ++epoch;
  const token = user.token;
  reading.value = true;
  controller = new AbortController();
  try {
    if (!token)
      throw new Error('请先登录');
    if (!/\.(pdf|docx|txt|md)$/i.test(file.name) || !file.size || file.size > 10 * 1024 * 1024)
      throw new Error('请选择不超过10MB的非空 PDF、DOCX、TXT 或 Markdown 文件');
    const text = /\.(txt|md)$/i.test(file.name)
      ? await file.text()
      : (
          await postFile<{ text: string }>('/system/contract-review/content', file, {
            signal: controller.signal,
            timeout: 180000,
          })
        ).text;
    if (disposed || epoch !== version || user.token !== token)
      return;
    if (typeof text !== 'string' || !text.trim())
      throw new Error('材料中未读取到文字，请粘贴材料正文');
    if (text.length > 30000)
      throw new Error('材料超过30000字，请拆分或摘录关键内容');
    input.materialName = file.name;
    input.materialText = text;
  }
  catch (e) {
    if (!disposed && epoch === version && user.token === token)
      ElMessage.error(`材料读取失败：${(e as Error).message}`);
  }
  finally {
    if (!disposed && epoch === version)
      reading.value = false;
  }
}
async function openHistory(id: string) {
  if (locked.value)
    return;
  const version = ++epoch;
  const token = user.token;
  opening.value = true;
  try {
    const { data } = await lawyerRequest<{
      data: { sessionId: string; input: string; content: string };
    }>(`/legal/opinion/${id}`);
    if (disposed || epoch !== version || user.token !== token)
      return;
    const restored = readInput(data.input);
    workflow.reset();
    Object.assign(input, restored);
    Object.assign(state, {
      sessionId: String(data.sessionId),
      fingerprint: JSON.stringify(restored),
      raw: data.content || '',
    });
    state.document = parseOpinion(state.raw);
    state.progress = '已恢复历史意见书';
  }
  catch (e) {
    if (!disposed && epoch === version && user.token === token) {
      state.error = (e as Error).message;
      ElMessage.error(state.error);
    }
  }
  finally {
    if (!disposed && epoch === version)
      opening.value = false;
  }
}
async function prefillCase() {
  if (!caseId.value || locked.value)
    return;
  const token = user.token;
  try {
    const c = (await caseDetail(caseId.value)).data.caseInfo;
    if (user.token !== token)
      return;
    input.facts = [
      c.clientName ? `客户：${c.clientName}` : '',
      c.oppositeParty ? `对方：${c.oppositeParty}` : '',
      c.caseType,
      c.description,
      c.court ? `法院：${c.court}` : '',
    ]
      .filter(Boolean)
      .join('\n');
  }
  catch (e) {
    ElMessage.error((e as Error).message);
  }
}
async function save() {
  if (!state.document || locked.value)
    return;
  saving.value = true;
  try {
    state.raw = JSON.stringify(state.document);
    await saveDocumentDraft(state.sessionId, state.raw);
    ElMessage.success('草稿已保存');
  }
  catch (e) {
    ElMessage.error((e as Error).message);
  }
  finally {
    saving.value = false;
  }
}
watch(
  () => state.document,
  (doc) => {
    if (doc)
      state.raw = JSON.stringify(doc);
  },
  { deep: true },
);
async function copy() {
  if (!current.value || !state.document || locked.value)
    return;
  try {
    await navigator.clipboard.writeText(opinionMarkdown(state.document));
    ElMessage.success('已复制六章正文与引用信息');
  }
  catch {
    ElMessage.error('复制失败，请使用 Markdown 导出');
  }
}
async function download(format: 'docx' | 'md') {
  if (!current.value || locked.value || !state.document)
    return;
  const token = user.token;
  const version = epoch;
  exporting.value = true;
  controller = new AbortController();
  try {
    const response = await fetch(`${import.meta.env.VITE_API_URL}/legal/opinion/export`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'clientid': import.meta.env.VITE_CLIENT_ID,
        'content-type': 'application/json',
      },
      body: JSON.stringify({ sessionId: state.sessionId, content: state.raw, format }),
      signal: controller.signal,
    });
    await checkDownloadAuth(response, token);
    let blob: Blob;
    if (format === 'docx') {
      blob = await readWordResponse(response);
    }
    else {
      if (!response.ok || !response.headers.get('content-type')?.includes('text/markdown')) {
        const error = await response.json();
        throw new Error(error.msg || 'Markdown 导出失败');
      }
      blob = await response.blob();
    }
    if (disposed || epoch !== version || user.token !== token)
      return;
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download
      = format === 'docx'
        ? wordFilename(state.document.title)
        : wordFilename(state.document.title).replace(/\.docx$/, '.md');
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  catch (e) {
    if (!disposed && epoch === version && user.token === token)
      ElMessage.error((e as Error).message);
  }
  finally {
    if (!disposed && epoch === version)
      exporting.value = false;
  }
}
function fresh() {
  if (locked.value)
    return;
  workflow.reset();
  Object.assign(input, { facts: '', focus: '', materialName: '', materialText: '' });
}
watch(
  () => user.token,
  () => {
    epoch++;
    historyEpoch++;
    controller?.abort();
    workflow.reset();
    history.value = [];
    total.value = 0;
    page.value = 1;
    reading.value = false;
    exporting.value = false;
    opening.value = false;
    loadingHistory.value = false;
    Object.assign(input, { facts: '', focus: '', materialName: '', materialText: '' });
    void loadHistory();
  },
  { immediate: true },
);
onScopeDispose(() => {
  disposed = true;
  epoch++;
  historyEpoch++;
  controller?.abort();
  workflow.dispose();
});
</script>

<template>
  <main class="opinion-page yl-page">
    <el-button text @click="router.push('/drafting')">
      ← 返回文书类型
    </el-button><AppPageHeader
      title="法律意见书"
      description="梳理案情与材料，逐章核对事实、分析和引用后再交付。"
      eyebrow="文书起草"
    >
      <template #actions>
        <el-button :disabled="locked" @click="fresh">
          新建意见书
        </el-button>
      </template>
    </AppPageHeader>
    <p class="yl-safety-note">
      AI 生成内容待人工核对；引用、事实与结论请分别复核。
    </p>
    <div class="opinion-layout">
      <aside>
        <h2>案情与材料</h2>
        <el-form label-position="top" :disabled="locked">
          <DocumentTemplatePicker
            v-model="templateId"
            type="法律意见书"
            :disabled="locked"
          /><CaseAssociation
            v-model="caseId"
            :session-id="state.sessionId || undefined"
            :disabled="locked"
          /><el-button :disabled="!caseId || locked" @click="prefillCase">
            从案件带入案情
          </el-button>
          <p>已知信息可修改，请核对后再生成。</p>
          <el-form-item label="案情（必填）">
            <el-input
              v-model="input.facts"
              type="textarea"
              :rows="8"
              maxlength="30000"
              show-word-limit
              placeholder="填写当事人、关键日期、经过、争议及证据情况"
            />
          </el-form-item>
          <el-form-item label="分析重点（选填）">
            <el-input
              v-model="input.focus"
              type="textarea"
              :rows="3"
              maxlength="2000"
              placeholder="例如：可主张的请求、证据缺口、协商策略"
            />
          </el-form-item>
          <el-form-item label="补充材料（选填）">
            <label class="file-button"><input
              type="file"
              accept=".pdf,.docx,.txt,.md"
              :disabled="locked"
              @change="material"
            >{{ reading ? '正在读取材料…' : '选择材料文件（10MB以内）' }}</label>
            <p v-if="input.materialName">
              {{ input.materialName }}
            </p>
            <el-input
              v-model="input.materialText"
              type="textarea"
              :rows="4"
              maxlength="30000"
              placeholder="上传后可核对文字，也可直接粘贴材料正文"
            />
          </el-form-item>
          <el-button
            type="primary"
            :loading="state.busy"
            :disabled="locked || !input.facts.trim()"
            @click="generate"
          >
            {{ state.document ? '重新生成意见书' : '生成法律意见书' }}
          </el-button>
        </el-form>
        <h2>我的历史</h2>
        <el-button link :loading="loadingHistory" @click="loadHistory">
          刷新历史
        </el-button>
        <p v-if="!history.length">
          暂无意见书历史
        </p>
        <el-button
          v-for="row in history"
          :key="row.id"
          class="history-item"
          text
          :disabled="locked"
          @click="openHistory(row.id)"
        >
          {{ row.sessionTitle }}
        </el-button>
        <el-pagination
          v-if="total > 10"
          v-model:current-page="page"
          small
          layout="prev, pager, next"
          :total="total"
          :page-size="10"
          @current-change="loadHistory"
        />
      </aside>
      <section class="opinion-result" aria-label="意见书结果">
        <p v-if="state.progress" role="status">
          {{ state.progress }}<span v-if="state.busy"> · 已接收 {{ state.raw.length }} 字</span>
        </p>
        <el-alert v-if="state.error" :title="state.error" type="error" :closable="false" />
        <el-alert
          v-if="state.document && !current"
          title="案情或材料已修改，请重新生成后复制或导出"
          type="warning"
          :closable="false"
        />
        <div class="result-actions">
          <el-button :disabled="!current || locked" @click="editing = !editing">
            {{ editing ? '预览' : '编辑正文' }}
          </el-button><el-button :disabled="!current || locked" :loading="saving" @click="save">
            保存草稿
          </el-button><el-button :disabled="!current || locked" @click="copy">
            复制全文
          </el-button><el-button
            :disabled="!current || locked"
            :loading="exporting"
            type="primary"
            @click="download('docx')"
          >
            下载 Word
          </el-button><el-button :disabled="!current || locked" @click="download('md')">
            导出 Markdown
          </el-button>
        </div>
        <template v-if="state.document">
          <h2>{{ state.document.title }}</h2>
          <article v-for="(section, index) in state.document.sections" :key="section.key">
            <h3>{{ index + 1 }}. {{ section.title }}</h3>
            <el-input
              v-if="editing"
              v-model="section.content"
              type="textarea"
              :rows="8"
              :disabled="locked"
            /><LegalAnswer v-else :content="section.content" />
            <div class="opinion-citations">
              <LegalCitation
                v-for="(citation, n) in section.citations"
                :key="n"
                :citation="citation"
              />
            </div>
          </article>
        </template>
        <el-empty
          v-else
          :description="
            state.busy ? '正在生成，完成后将按六个章节展示' : '输入案情，开始生成法律意见书'
          "
        />
        <DocumentEditor
          v-if="state.error && state.raw && !state.document"
          v-model="state.raw"
          title="法律意见书待核对稿"
          :session-id="state.sessionId"
          :busy="state.busy"
          :warnings="['结构未通过校验，请核对正文后交付']"
        />
        <details v-if="state.error && state.raw">
          <summary>查看未完成的原始结果</summary>
          <pre>{{ state.raw }}</pre>
        </details>
      </section>
    </div>
  </main>
</template>

<style scoped>
.opinion-page {
  min-width: 0;
}
.opinion-page header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.opinion-page h1 {
  font-size: 24px;
  margin: 0 0 8px;
}
.opinion-page h2 {
  font-size: 18px;
  margin: 16px 0;
}
.opinion-page p {
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
.opinion-layout {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 24px;
}
.opinion-layout aside,
.opinion-result {
  min-width: 0;
  border: 1px solid var(--el-border-color);
  border-radius: var(--yl-radius-panel);
  padding: 22px;
  background: var(--el-bg-color);
}
.file-button {
  cursor: pointer;
  color: var(--yl-color-brand-strong);
  padding-bottom: 8px;
}
.file-button input {
  display: block;
  max-width: 100%;
  margin-bottom: 8px;
}
.history-item {
  display: block;
  text-align: left;
  white-space: normal;
  height: auto;
  width: 100%;
  margin: 8px 0 !important;
  overflow-wrap: anywhere;
}
.result-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 16px 0;
}
.result-actions .el-button {
  margin: 0;
}
.opinion-result article {
  padding: 12px 0 20px;
  border-bottom: 1px solid var(--el-border-color-light);
}
.opinion-result h3 {
  font-size: 18px;
  margin: 12px 0;
}
.opinion-citations {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}
.opinion-result pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 320px;
  overflow: auto;
}
@media (max-width: 1000px) {
  .opinion-layout {
    grid-template-columns: 1fr;
  }
  .opinion-page {
    padding: 16px;
  }
}
@container workspace (max-width:940px) {
  .opinion-layout {
    grid-template-columns: 1fr;
  }
}
</style>
