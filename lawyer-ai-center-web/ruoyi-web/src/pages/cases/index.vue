<script setup lang="ts">
import type { AgentVO } from '@/api/agent/types';
import type { CaseDetail, CaseForm, CaseSession, LawyerCase } from '@/api/lawyer';
import { ElMessage, ElMessageBox } from 'element-plus';
import { onMounted, onScopeDispose, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getAgentList } from '@/api/agent';
import { caseDetail, caseList, emptyCase, lawyerRequest, saveCase } from '@/api/lawyer';
import { readMediaApiResult } from '@/api/media/response';
import { create_session } from '@/api/session';
import AppPageHeader from '@/components/AppPageHeader.vue';
import AppState from '@/components/AppState.vue';
import CaseDossier from '@/components/CaseDossier.vue';
import CaseMaterials from '@/components/CaseMaterials.vue';
import CaseReminders from '@/components/CaseReminders.vue';
import CaseTasks from '@/components/CaseTasks.vue';
import WorkTimer from '@/components/WorkTimer.vue';
import { useUserStore } from '@/stores';
import { useAgentStore } from '@/stores/modules/agent';
import { useChatStore } from '@/stores/modules/chat';
import { useSessionStore } from '@/stores/modules/session';

const route = useRoute();
const router = useRouter();
const user = useUserStore();
const activeTab = ref('tasks');
let listVersion = 0;
const reminderVersion = ref(0);
const rows = ref<LawyerCase[]>([]);
const total = ref(0);
const page = ref(1);
const query = reactive({ keyword: '', status: '', caseType: '' });
const listLoading = ref(false);
const detailLoading = ref(false);
const busy = ref(false);
const error = ref('');
const detail = ref<CaseDetail>();
const selectedId = ref('');
let detailVersion = 0;
let disposed = false;
const form = reactive<CaseForm>(emptyCase());
const formVisible = ref(false);
const sessionsVisible = ref(false);
const options = ref<CaseSession[]>([]);
const optionsPage = ref(0);
const optionsMore = ref(false);
const selectedSession = ref('');
function dateClass(date?: string | null) {
  if (!date)
    return '';
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
  const days
    = (Date.parse(`${date}T00:00:00+08:00`) - Date.parse(`${today}T00:00:00+08:00`)) / 86400000;
  return days >= 0 && days <= 3 ? 'danger' : days <= 7 && days >= 0 ? 'warning' : '';
}
async function loadList(reset = false) {
  if (reset)
    page.value = 1;
  const version = ++listVersion;
  const token = user.token;
  if (!token) {
    rows.value = [];
    total.value = 0;
    return;
  }
  listLoading.value = true;
  try {
    const result = await caseList({ ...query, pageNum: page.value, pageSize: 20 });
    if (disposed || version !== listVersion || token !== user.token)
      return;
    rows.value = result.rows;
    total.value = result.total;
  }
  catch (e) {
    if (!disposed && version === listVersion && token === user.token)
      error.value = (e as Error).message;
  }
  finally {
    if (version === listVersion)
      listLoading.value = false;
  }
}
async function open(id: string) {
  if (busy.value || disposed || !user.token)
    return;
  const version = ++detailVersion;
  selectedId.value = id;
  detail.value = undefined;
  detailLoading.value = true;
  error.value = '';
  try {
    const result = await caseDetail(id);
    if (version !== detailVersion)
      return;
    detail.value = result.data;
    await router.replace({ query: { id } });
  }
  catch (e) {
    if (version === detailVersion) {
      detail.value = undefined;
      error.value = (e as Error).message;
    }
  }
  finally {
    if (version === detailVersion)
      detailLoading.value = false;
  }
}
function edit(value?: LawyerCase) {
  Object.assign(form, emptyCase(), value || {}, { id: value?.id });
  formVisible.value = true;
}
function setDate(key: 'filingDate' | 'nextHearingDate' | 'limitationDate', value: unknown) {
  form[key] = typeof value === 'string' ? value : null;
}
function selectRow(row: LawyerCase) {
  void open(row.id);
}
async function save() {
  if (busy.value)
    return;
  if (!form.title.trim()) {
    ElMessage.warning('请填写案件标题');
    return;
  }
  busy.value = true;
  try {
    const result = await saveCase({ ...form, title: form.title.trim() });
    const id = form.id || String(result.data);
    formVisible.value = false;
    reminderVersion.value++;
    await loadList();
    busy.value = false;
    await open(id);
  }
  catch (e) {
    error.value = (e as Error).message;
  }
  finally {
    busy.value = false;
  }
}
async function remove() {
  if (!detail.value || busy.value)
    return;
  const id = selectedId.value;
  busy.value = true;
  try {
    await ElMessageBox.confirm('删除案件会解除会话和文件关联，原始会话与材料仍保留。', '删除案件');
    await lawyerRequest(`/lawyer/case/${id}`, 'DELETE');
    reminderVersion.value++;
    detail.value = undefined;
    selectedId.value = '';
    await router.replace({ query: {} });
    await loadList();
  }
  catch (e) {
    if (e !== 'cancel' && e !== 'close')
      error.value = (e as Error).message;
  }
  finally {
    busy.value = false;
  }
}
async function consultation() {
  if (!detail.value || busy.value)
    return;
  busy.value = true;
  try {
    const response = await readMediaApiResult<AgentVO[]>(getAgentList());
    const agent = response.data.find(a => a.agentName === '法律咨询助手' && a.status === '0');
    if (disposed)
      return;
    if (!agent)
      throw new Error('法律咨询助手暂不可用');
    const created = await readMediaApiResult<unknown>(
      create_session({
        sessionTitle: `案件咨询 - ${detail.value.caseInfo.title}`.slice(0, 80),
        caseId: selectedId.value,
      }),
    );
    if (disposed)
      return;
    useSessionStore().setCurrentSession({
      id: String(created.data),
      sessionTitle: `案件咨询 - ${detail.value.caseInfo.title}`,
      caseId: selectedId.value,
    });
    void useSessionStore().requestSessionList(1, true);
    useChatStore().clearCurrentWorkflow();
    useAgentStore().setCurrentAgentInfo(agent);
    await router.push(`/chat/${String(created.data)}`);
  }
  catch (e) {
    error.value = (e as Error).message;
  }
  finally {
    busy.value = false;
  }
}
async function openSession(session: CaseSession) {
  if (busy.value)
    return;
  if (/^合同(快审|审查)/.test(session.sessionTitle || '')) {
    await router.push({ path: '/contract-review', query: { sessionId: session.id } });
    return;
  }
  busy.value = true;
  try {
    const name = (session.sessionTitle || '').startsWith('文书') ? '文书起草助手' : '法律咨询助手';
    const result = await readMediaApiResult<AgentVO[]>(getAgentList());
    const agent = result.data.find(a => a.agentName === name && a.status === '0');
    if (!agent)
      throw new Error(`${name}暂不可用`);
    if (disposed)
      return;
    useChatStore().clearCurrentWorkflow();
    useAgentStore().setCurrentAgentInfo(agent);
    await router.push(`/chat/${session.id}`);
  }
  catch (e) {
    error.value = (e as Error).message;
  }
  finally {
    busy.value = false;
  }
}
async function loadSessions(more = false) {
  if (busy.value)
    return;
  busy.value = true;
  try {
    const next = more ? optionsPage.value + 1 : 1;
    const result = await lawyerRequest<{ rows: CaseSession[]; total: number }>(
      `/system/session/list?pageNum=${next}&pageSize=20&orderByColumn=id&isAsc=desc`,
    );
    options.value = more ? [...options.value, ...result.rows] : result.rows;
    optionsPage.value = next;
    optionsMore.value = options.value.length < result.total;
    sessionsVisible.value = true;
  }
  catch (e) {
    error.value = (e as Error).message;
  }
  finally {
    busy.value = false;
  }
}
async function linkSession() {
  if (!selectedSession.value || busy.value)
    return;
  busy.value = true;
  try {
    await lawyerRequest(`/lawyer/case/${selectedId.value}/sessions`, 'POST', {
      sessionId: selectedSession.value,
    });
    sessionsVisible.value = false;
    busy.value = false;
    await open(selectedId.value);
  }
  catch (e) {
    error.value = (e as Error).message;
  }
  finally {
    busy.value = false;
  }
}
async function unlink(kind: 'sessions' | 'files', id: string) {
  if (busy.value)
    return;
  busy.value = true;
  try {
    await lawyerRequest(`/lawyer/case/${selectedId.value}/${kind}/${id}`, 'DELETE');
    busy.value = false;
    await open(selectedId.value);
  }
  catch (e) {
    error.value = (e as Error).message;
  }
  finally {
    busy.value = false;
  }
}
watch(
  () => route.query.id,
  (id) => {
    if (typeof id === 'string' && id !== selectedId.value) {
      void open(id);
    }
    else if (!id) {
      detailVersion++;
      selectedId.value = '';
      detail.value = undefined;
    }
  },
  { immediate: true },
);
watch(
  () => user.token,
  () => {
    listVersion++;
    detailVersion++;
    selectedId.value = '';
    detail.value = undefined;
    rows.value = [];
    total.value = 0;
    page.value = 1;
    busy.value = false;
    listLoading.value = false;
    detailLoading.value = false;
    error.value = '';
    formVisible.value = false;
    sessionsVisible.value = false;
    options.value = [];
    Object.assign(form, emptyCase());
    reminderVersion.value++;
    void loadList();
    if (typeof route.query.id === 'string')
      void open(route.query.id);
  },
  { flush: 'sync' },
);
onMounted(() => loadList());
onScopeDispose(() => {
  disposed = true;
  detailVersion++;
  listVersion++;
});
</script>

<template>
  <main class="cases yl-page">
    <AppPageHeader
      title="我的案件"
      description="从案件台账出发，连续处理任务、材料与关联会话。"
      eyebrow="案件管理"
    >
      <template #actions>
        <el-button type="primary" :disabled="busy" @click="edit()">
          新建案件
        </el-button>
      </template>
    </AppPageHeader>
    <el-alert v-if="error" :title="error" type="error" closable @close="error = ''" />
    <CaseReminders :key="reminderVersion" />
    <div class="filters">
      <el-input
        v-model="query.keyword"
        aria-label="搜索案件标题、客户或案由"
        placeholder="案件标题 / 客户 / 案由"
        clearable
        @keyup.enter="loadList(true)"
      /><el-select v-model="query.status" aria-label="案件状态" placeholder="全部状态" clearable>
        <el-option
          v-for="status in ['进行中', '已结案', '中止']"
          :key="status"
          :label="status"
          :value="status"
        />
      </el-select><el-input
        v-model="query.caseType"
        aria-label="按案由筛选"
        placeholder="案由筛选"
        clearable
      /><el-button :loading="listLoading" @click="loadList(true)">
        查询
      </el-button>
    </div>
    <div class="columns">
      <section>
        <el-table v-loading="listLoading" :data="rows" highlight-current-row @row-click="selectRow">
          <template #empty>
            <AppState
              :title="
                error
                  ? '案件列表暂不可用'
                  : query.keyword || query.status || query.caseType
                    ? '没有匹配的案件'
                    : '建立第一份案件台账'
              "
              :kind="error ? 'error' : 'empty'"
              :description="
                error
                  ? '请重试查询，已有案件不会受影响。'
                  : '可调整筛选条件，或新建案件归集材料与会话。'
              "
            >
              <el-button :loading="listLoading" @click="loadList(true)">
                重新查询
              </el-button>
            </AppState>
          </template><el-table-column label="案件" min-width="160">
            <template #default="{ row }">
              <el-button link type="primary" class="case-title" @click.stop="selectRow(row)">
                {{ row.title }}
              </el-button>
            </template>
          </el-table-column><el-table-column prop="caseType" label="案由" /><el-table-column
            prop="clientName"
            label="客户"
          /><el-table-column prop="status" label="状态" /><el-table-column
            label="关键 / 时效日期"
            min-width="150"
          >
            <template #default="{ row }">
              <div :class="row.status === '进行中' ? dateClass(row.nextHearingDate) : ''">
                {{ row.nextHearingDate || '—' }}
              </div>
              <div :class="row.status === '进行中' ? dateClass(row.limitationDate) : ''">
                {{ row.limitationDate || '—' }}
              </div>
            </template>
          </el-table-column>
        </el-table><el-pagination
          v-model:current-page="page"
          :page-size="20"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadList()"
        />
      </section>
      <section v-loading="detailLoading" class="detail">
        <template v-if="detail">
          <h2>{{ detail.caseInfo.title }}</h2>
          <WorkTimer :case-id="selectedId" /><el-space wrap>
            <el-button :disabled="busy" @click="edit(detail.caseInfo)">
              编辑 / 状态
            </el-button><el-button type="danger" plain :disabled="busy" @click="remove">
              删除
            </el-button><el-button type="primary" :loading="busy" @click="consultation">
              发起新咨询
            </el-button>
          </el-space>
          <el-descriptions :column="1" border class="info">
            <el-descriptions-item
              v-for="(label, key) in {
                caseType: '案由',
                clientName: '客户',
                oppositeParty: '对方',
                status: '状态',
                court: '法院',
                filingDate: '立案日期',
                nextHearingDate: '下次关键日期',
                limitationDate: '时效日期',
                description: '案情摘要',
              }"
              :key="key"
              :label="label"
            >
              {{ detail.caseInfo[key] || '未填写' }}
            </el-descriptions-item>
          </el-descriptions>
          <el-button
            :disabled="busy"
            @click="router.push({ path: '/my-cases/materials', query: { id: selectedId } })"
          >
            阅卷 / 材料溯源工作台
          </el-button>
          <h3>关联会话与审查</h3>
          <el-button :disabled="busy" @click="loadSessions()">
            关联已有会话
          </el-button>
          <p v-for="session in detail.sessions" :key="session.id">
            <el-link @click="openSession(session)">
              {{ session.sessionTitle || '会话' }}
            </el-link><el-button text :disabled="busy" @click="unlink('sessions', session.id)">
              解除关联
            </el-button>
          </p>
          <el-empty v-if="!detail.sessions.length" :image-size="40" description="暂无关联会话" />
          <el-tabs v-model="activeTab">
            <el-tab-pane label="任务与检查项" name="tasks">
              <CaseTasks
                :key="selectedId + user.token"
                :case-id="selectedId"
                :disabled="busy"
                @busy="busy = $event"
              />
            </el-tab-pane><el-tab-pane label="材料" name="materials">
              <CaseMaterials
                :key="selectedId + user.token"
                :case-id="selectedId"
                :disabled="busy"
                @busy="busy = $event"
              />
            </el-tab-pane><el-tab-pane label="阅卷" name="dossier" lazy>
              <CaseDossier
                :key="selectedId + user.token"
                :case-id="selectedId"
                :case-title="detail.caseInfo.title"
                :disabled="busy"
                @busy="busy = $event"
              />
            </el-tab-pane>
          </el-tabs>
        </template><AppState
          v-else
          title="选择案件，继续办理"
          description="从左侧列表选择案件，查看任务、材料和关联会话。"
        />
      </section>
    </div>
    <el-dialog
      v-model="formVisible"
      :title="form.id ? '编辑案件' : '新建案件'"
      width="min(680px, 94vw)"
      :close-on-click-modal="!busy"
      :close-on-press-escape="!busy"
      :show-close="!busy"
    >
      <el-form label-position="top" :disabled="busy">
        <div class="form-grid">
          <el-form-item label="案件标题（必填）">
            <el-input v-model="form.title" maxlength="200" />
          </el-form-item><el-form-item label="状态">
            <el-select v-model="form.status">
              <el-option
                v-for="status in ['进行中', '已结案', '中止']"
                :key="status"
                :value="status"
              />
            </el-select>
          </el-form-item><el-form-item
            v-for="(label, key) in {
              caseType: '案由',
              clientName: '客户',
              oppositeParty: '对方当事人',
              court: '受理法院',
            }"
            :key="key"
            :label="label"
          >
            <el-input
              v-model="form[key]"
              :maxlength="key === 'caseType' ? 100 : 200"
            />
          </el-form-item><el-form-item
            v-for="(label, key) in {
              filingDate: '立案日期',
              nextHearingDate: '下次关键日期',
              limitationDate: '诉讼时效日期',
            }"
            :key="key"
            :label="label"
          >
            <el-date-picker
              :model-value="form[key] || undefined"
              type="date"
              value-format="YYYY-MM-DD"
              clearable
              @update:model-value="setDate(key, $event)"
            />
          </el-form-item>
        </div>
        <el-form-item label="案情摘要">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="5000"
            show-word-limit
          />
        </el-form-item>
      </el-form><template #footer>
        <el-button :disabled="busy" @click="formVisible = false">
          取消
        </el-button><el-button type="primary" :loading="busy" @click="save">
          保存
        </el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="sessionsVisible" title="关联已有会话" width="min(520px, 94vw)">
      <el-select v-model="selectedSession" filterable placeholder="选择自己的会话">
        <el-option
          v-for="session in options"
          :key="session.id"
          :label="session.sessionTitle || session.id"
          :value="session.id"
        />
      </el-select><el-button v-if="optionsMore" :loading="busy" @click="loadSessions(true)">
        加载更多
      </el-button><template #footer>
        <el-button type="primary" :disabled="!selectedSession" :loading="busy" @click="linkSession">
          关联
        </el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.danger {
  color: var(--yl-color-danger);
}
.warning {
  color: var(--yl-color-warning);
}
.cases {
  min-width: 0;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
header h1 {
  margin: 0;
}
header p {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
.filters {
  display: flex;
  gap: 12px;
  margin: 22px 0;
}
.columns {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: 20px;
}
.columns > section {
  background: var(--yl-color-surface);
  min-width: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 16px;
}
.detail h2 {
  margin-top: 0;
  font-size: 20px;
}
.info {
  margin: 20px 0;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 20px;
}
.el-pagination {
  margin-top: 18px;
}
.detail p {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
@media (max-width: 1000px) {
  .columns {
    grid-template-columns: 1fr;
  }
  .filters {
    flex-wrap: wrap;
  }
  .form-grid {
    grid-template-columns: 1fr;
  }
}
.case-title {
  white-space: normal;
  text-align: left;
  height: auto;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.filters {
  flex-wrap: wrap;
}
.filters .el-input {
  flex: 1 1 180px;
}
.filters .el-select {
  width: 160px;
}
.detail h2 {
  overflow-wrap: anywhere;
}
@container workspace (max-width:960px) {
  .columns {
    grid-template-columns: 1fr;
  }
}
</style>
