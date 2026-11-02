<script setup lang="ts">
import type { Claim, ClaimFilter, ClaimListing, ClaimRun, ResolvedSource } from '@/api/lawyer/claims';
import type { Material, Overview } from '@/api/lawyer/dossier';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, onScopeDispose, ref, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import { claimKinds, claimQuery, claimRequest, claimStates, emptyClaimFilter } from '@/api/lawyer/claims';
import { request } from '@/api/lawyer/dossier';
import { rememberReviewQueue, reviewLink } from '@/api/lawyer/review';
import AppPageHeader from '@/components/AppPageHeader.vue';
import AppState from '@/components/AppState.vue';
import DossierSourceViewer from '@/components/DossierSourceViewer.vue';
import { useUserStore } from '@/stores';
import ClaimEditor from './ClaimEditor.vue';

const route = useRoute();
const router = useRouter();
const user = useUserStore();
const caseId = computed(() => typeof route.query.id === 'string' ? route.query.id : '');
const overview = ref<Overview>();
const filter = ref<ClaimFilter>(emptyClaimFilter());
const listing = ref<ClaimListing>({ rows: [], total: 0, groups: {} });
const materials = ref<Material[]>([]);
const runs = ref<ClaimRun[]>([]);
const models = ref<{
  id: string;
  name: string;
  region: string;
}[]>([]);
const budget = ref<{
  period: string;
  tokensCharged: number;
  tokenBudget: number;
  pagesCharged: number;
  pageBudget: number;
  method: string;
}>();
const selectedVersions = ref<string[]>([]);
const modelId = ref('');
const selectedRows = ref<Claim[]>([]);
const editor = ref(false);
const claimId = ref<string>();
const error = ref('');
const loading = ref(false);
const busy = ref(false);
const chunks = ref<{
  id: string;
  chunkIndex: number;
  state: string;
  anchorIds: string;
  errorCode: string | null;
  rejections: string;
}[]>([]);
const chunksVisible = ref(false);
const source = ref<ResolvedSource>();
const sourceVisible = ref(false);
const root = ref<HTMLElement>();
let serial = 0;
let epoch = 0;
let disposed = false;
let timer: ReturnType<typeof setTimeout> | undefined;
let scrollContainer: HTMLElement | undefined;
function storageKey() {
  return `claim-view:${user.userInfo?.tenantId}:${user.userInfo?.userId}:${caseId.value}`;
}
function saveView() {
  if (user.token)
    sessionStorage.setItem(storageKey(), JSON.stringify({ filter: filter.value, top: scrollContainer?.scrollTop || 0 }));
}
function active(run: number, token: string | undefined) {
  return !disposed && run === epoch && token === user.token;
}
async function refresh(quiet = false) {
  if (!caseId.value || !user.token)
    return;
  const run = epoch;
  const token = user.token;
  const requestId = ++serial;
  const current = () => active(run, token) && requestId === serial;
  if (!quiet)
    loading.value = true;
  error.value = '';
  try {
    const results = await Promise.all([
      claimRequest<ClaimListing>(caseId.value, `?${claimQuery(filter.value)}`),
      request<Overview>(caseId.value, '/overview'),
      request<{
        rows: Material[];
        total: number;
      }>(caseId.value, '/materials?pageNum=1&pageSize=200'),
      claimRequest<ClaimRun[]>(caseId.value, '/runs'),
      claimRequest<typeof models.value>(caseId.value, '/models'),
      claimRequest<typeof budget.value>(caseId.value, '/budget'),
    ]);
    if (!current())
      return;
    [listing.value, overview.value] = results;
    materials.value = results[2].rows;
    runs.value = results[3];
    models.value = results[4];
    budget.value = results[5];
    if (!modelId.value)
      modelId.value = models.value[0]?.id || '';
  }
  catch (e) {
    if (current()) {
      error.value = (e as Error).message;
      if (/登录|案件不可用|无访问权限/.test(error.value)) {
        clearCaseData();
        clearTimeout(timer);
      }
    }
  }
  finally {
    if (current())
      loading.value = false;
  }
}
function search() {
  filter.value.pageNum = 1;
  saveView();
  refresh();
}
async function extract() {
  try {
    await ElMessageBox.confirm('仅处理所选版本读取成功页，模型输出全部待核。重跑保留人工修改与已确认记录，新建议进入差异队列；仍须满足服务端材料准出授权。', '提取范围与影响', { type: 'warning' });
  }
  catch {
    return;
  }
  const run = epoch;
  const token = user.token;
  busy.value = true;
  try {
    await claimRequest(caseId.value, '/extract', 'POST', { fileVersionIds: selectedVersions.value, modelId: modelId.value, idempotencyKey: crypto.randomUUID().replace(/-/g, '') });
    if (active(run, token)) {
      ElMessage.success('已进入持久任务队列');
      await refresh(true);
    }
  }
  catch (e) {
    if (active(run, token))
      error.value = (e as Error).message;
  }
  finally {
    if (active(run, token))
      busy.value = false;
  }
}
function open(row?: Claim) {
  if (!overview.value || busy.value)
    return;
  saveView();
  if (row) {
    rememberReviewQueue(String(user.userInfo?.tenantId || ''), String(user.userInfo?.userId || ''), caseId.value, 'claim', listing.value.rows.filter(c => ['pending_review', 'stale'].includes(c.reviewStatus)).map(c => c.id));
    router.push(reviewLink(caseId.value, 'claim', row.id));
    return;
  }
  claimId.value = undefined;
  editor.value = true;
}
async function merge() {
  if (selectedRows.value.length < 2)
    return;
  let text = '';
  try {
    const result = await ElMessageBox.prompt('填写合并后的原子主张。原候选保留并标不采用；新条目待核，所有独立来源保留。', '合并候选', { inputType: 'textarea', inputValue: selectedRows.value.map(r => r.claimText).join('；') });
    text = result.value;
  }
  catch {
    return;
  }
  const run = epoch;
  const token = user.token;
  busy.value = true;
  try {
    await claimRequest(caseId.value, '/merge', 'POST', { ids: selectedRows.value.map(r => r.id), versions: selectedRows.value.map(r => r.version), text, reason: '逐项对照原文后人工合并主张，原记录保留' });
    if (active(run, token)) {
      selectedRows.value = [];
      await refresh();
    }
  }
  catch (e) {
    if (active(run, token))
      error.value = (e as Error).message;
  }
  finally {
    if (active(run, token))
      busy.value = false;
  }
}
async function runAction(item: ClaimRun, action: 'retry' | 'cancel') {
  const run = epoch;
  const token = user.token;
  try {
    await claimRequest(caseId.value, `/runs/${item.id}/${action}`, 'POST');
    if (active(run, token))
      await refresh(true);
  }
  catch (e) {
    if (active(run, token))
      error.value = (e as Error).message;
  }
}
async function showChunks(item: ClaimRun) {
  const run = epoch;
  const token = user.token;
  try {
    const result = await claimRequest<typeof chunks.value>(caseId.value, `/runs/${item.id}/chunks`);
    if (active(run, token)) {
      chunks.value = result;
      chunksVisible.value = true;
    }
  }
  catch (e) {
    if (active(run, token))
      error.value = (e as Error).message;
  }
}
function anchorsInChunk(raw: string): string[] {
  try {
    const result = JSON.parse(raw);
    return Array.isArray(result) ? result.filter(v => typeof v === 'string') : [];
  }
  catch {
    return [];
  }
}
async function resolve(id: string) {
  const run = epoch;
  const token = user.token;
  try {
    const result = await request<ResolvedSource>(caseId.value, `/anchors/${id}/resolve`);
    if (active(run, token)) {
      source.value = result;
      sourceVisible.value = true;
    }
  }
  catch (e) {
    if (active(run, token))
      error.value = (e as Error).message;
  }
}
function poll() {
  timer = setTimeout(async () => {
    if (runs.value.some(r => ['queued', 'running'].includes(r.state)))
      await refresh(true);
    if (!disposed)
      poll();
  }, 4000);
}
function clearCaseData() {
  listing.value = { rows: [], total: 0, groups: {} };
  overview.value = undefined;
  materials.value = [];
  runs.value = [];
  models.value = [];
  budget.value = undefined;
  chunks.value = [];
  source.value = undefined;
  sourceVisible.value = false;
  chunksVisible.value = false;
  editor.value = false;
  claimId.value = undefined;
  busy.value = false;
  selectedRows.value = [];
  selectedVersions.value = [];
  modelId.value = '';
}
async function reset() {
  epoch++;
  serial++;
  clearCaseData();
  error.value = '';
  filter.value = emptyClaimFilter();
  let top = 0;
  try {
    const view = JSON.parse(sessionStorage.getItem(storageKey()) || '{}');
    if (view.filter) {
      for (const key of Object.keys(filter.value) as (keyof ClaimFilter)[]) {
        const value = view.filter[key];
        if (typeof value === typeof filter.value[key])
          Object.assign(filter.value, { [key]: value });
      }
    }
    top = Number.isFinite(view.top) ? Math.max(0, view.top) : 0;
  }
  catch {
  }
  await refresh();
  if (scrollContainer)
    scrollContainer.scrollTop = top;
}
watch(() => [caseId.value, user.token], reset);
onMounted(() => {
  scrollContainer = root.value?.closest('.main-scroll') as HTMLElement | undefined;
  scrollContainer?.addEventListener('scroll', saveView, { passive: true });
  reset();
  poll();
});
onBeforeRouteLeave(async () => {
  if (editor.value) {
    try {
      await ElMessageBox.confirm('阅卷器可能有未保存输入，确认离开当前案件工作台？', '离开核对', { type: 'warning' });
    }
    catch {
      return false;
    }
  }
  saveView();
});
onScopeDispose(() => {
  saveView();
  disposed = true;
  epoch++;
  serial++;
  clearTimeout(timer);
  scrollContainer?.removeEventListener('scroll', saveView);
});
</script>

<template>
  <div ref="root" class="yl-page claims-workbench">
    <AppPageHeader title="事实与摘录" :description="`${overview?.caseTitle || '当前案件'} · 所有AI输出先待核，出处有效不等于事实真实`">
      <template #actions>
        <el-button @click="router.push({ path: '/my-cases/materials', query: { id: caseId } })">
          材料与逐页对账
        </el-button><el-button :disabled="!overview || busy" @click="open()">
          手工摘录
        </el-button>
      </template>
    </AppPageHeader>
    <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" />
    <AppState v-if="!caseId" kind="error" title="缺少案件上下文" description="请从我的案件进入材料工作台。" />
    <AppState v-else-if="!overview" :kind="loading ? 'loading' : 'error'" :title="loading ? '读取案件工作台' : '案件工作台暂不可用'" :description="loading ? '正在核对访问权限与材料覆盖。' : error || '请重新加载案件上下文。'">
      <el-button v-if="!loading" @click="refresh()">
        重新加载
      </el-button>
    </AppState>
    <template v-else>
      <section class="yl-panel claim-extract" aria-label="提取范围">
        <p v-if="overview">
          当前材料{{ overview.files }}份，已读{{ overview.parsedPages }}/{{ overview.knownPages }}页；总页未知{{ overview.unknownPageFiles }}份。未读页不进入模型。
        </p>
        <el-select v-model="selectedVersions" multiple placeholder="选择当前材料版本" aria-label="提取材料版本">
          <el-option v-for="m in materials.filter(m => m.current)" :key="m.material.id" :value="m.current!.id" :label="`${m.material.displayName} · v${m.current!.versionNo} · ${m.current!.parsedPages}/${m.current!.pageCount ?? '?'}页`" />
        </el-select>
        <el-select v-model="modelId" placeholder="选择已核准地域的模型" aria-label="提取模型">
          <el-option v-for="m in models" :key="m.id" :value="m.id" :label="`${m.name} · ${m.region}`" />
        </el-select>
        <el-button type="primary" :disabled="!selectedVersions.length || !modelId" :loading="busy" @click="extract">
          提取事实候选
        </el-button>
        <p v-if="!models.length">
          暂无可用模型地域配置；请联系管理员，仍可完整手工摘录。授权状态由服务端逐次核验。
        </p>
        <p v-if="budget">
          {{ budget.period }}额度记账：预留/计入{{ budget.tokensCharged }}/{{ budget.tokenBudget }} token，{{ budget.pagesCharged }}/{{ budget.pageBudget }}页。{{ budget.method }}
        </p>
      </section>
      <section class="yl-panel" aria-label="候选筛选与分组">
        <div class="claim-groups">
          <el-button :type="!filter.group ? 'primary' : 'default'" @click="filter.group = ''; search()">
            全部
          </el-button><el-button v-for="group in ['high_risk', 'source_unverified', 'pending', 'confirmed', 'excluded']" :key="group" :type="filter.group === group ? 'primary' : 'default'" @click="filter.group = group; search()">
            {{ claimStates[group] }} {{ listing.groups[group] || 0 }}
          </el-button>
        </div>
        <el-form inline @submit.prevent="search">
          <el-form-item label="搜索">
            <el-input v-model="filter.search" clearable @keyup.enter="search" />
          </el-form-item><el-form-item label="类型">
            <el-select v-model="filter.kind" clearable>
              <el-option v-for="(name, key) in claimKinds" :key="key" :value="key" :label="name" />
            </el-select>
          </el-form-item><el-form-item label="状态">
            <el-select v-model="filter.status" clearable>
              <el-option v-for="state in ['pending_review', 'confirmed', 'excluded', 'stale']" :key="state" :value="state" :label="claimStates[state]" />
            </el-select>
          </el-form-item><el-form-item label="来源文件">
            <el-select v-model="filter.fileVersionId" clearable>
              <el-option v-for="m in materials.filter(m => m.current)" :key="m.material.id" :value="m.current!.id" :label="m.material.displayName" />
            </el-select>
          </el-form-item><el-form-item label="审核人ID">
            <el-input v-model="filter.reviewerId" />
          </el-form-item><el-form-item label="创建起日">
            <el-date-picker v-model="filter.from" type="date" value-format="YYYY-MM-DD" />
          </el-form-item><el-form-item label="创建止日">
            <el-date-picker v-model="filter.to" type="date" value-format="YYYY-MM-DD" />
          </el-form-item><el-button native-type="submit">
            应用筛选
          </el-button>
        </el-form>
        <el-button :disabled="selectedRows.length < 2 || busy" @click="merge">
          合并所选{{ selectedRows.length }}项
        </el-button>
        <AppState v-if="loading" kind="loading" title="读取候选与来源状态" />
        <AppState v-else-if="!listing.rows.length" title="当前范围没有候选" description="可调整筛选、手工摘录或对成功读取且已授权的材料提取；不会生成演示事实。" />
        <div v-else class="claim-table">
          <el-table :data="listing.rows" row-key="id" @selection-change="selectedRows = $event" @row-dblclick="open">
            <el-table-column type="selection" width="42" /><el-table-column label="主张/摘录" min-width="300">
              <template #default="{ row }">
                <el-button link class="claim-text-button" @click="open(row)">
                  {{ row.claimText }}
                </el-button>
              </template>
            </el-table-column><el-table-column label="类型" width="110">
              <template #default="{ row }">
                {{ claimKinds[row.kind] }}
              </template>
            </el-table-column><el-table-column label="风险与出处" width="180">
              <template #default="{ row }">
                <span>{{ row.risk === 'high' ? '高风险' : '普通' }} · {{ claimStates[row.sourceStatus] }}</span>
              </template>
            </el-table-column><el-table-column label="核对" width="140">
              <template #default="{ row }">
                {{ claimStates[row.reviewStatus] }} · v{{ row.version }}
              </template>
            </el-table-column><el-table-column prop="reviewerId" label="审核人" width="120" /><el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button link @click="open(row)">
                  原页核对
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <el-pagination v-model:current-page="filter.pageNum" :total="listing.total" :page-size="filter.pageSize" layout="prev,pager,next,total" @current-change="saveView(); refresh()" />
      </section>
      <el-collapse class="yl-panel">
        <el-collapse-item title="本次材料覆盖、处理缺口与模型任务" name="runs">
          <article v-for="run in runs" :key="run.id" class="claim-run">
            <p>{{ run.modelName }} · {{ claimStates[run.state] }} · 读取成功{{ run.readablePages }}/{{ run.totalPages ?? '总页未知' }}页 · 块成功{{ run.successfulChunks }}/{{ run.chunkCount }}，缺口{{ run.failedChunks }} · {{ run.promptVersion }}</p><p>预留{{ run.reservedTokens }} token；实际{{ run.actualTokens === null ? '未知（不记成0）' : run.actualTokens }}。{{ run.errorMessage || run.errorCode || '' }}</p><el-button @click="showChunks(run)">
              查看块与来源
            </el-button><el-button v-if="['partial', 'failed', 'cancelled'].includes(run.state)" @click="runAction(run, 'retry')">
              仅重试未成功块
            </el-button><el-button v-if="['queued', 'running'].includes(run.state)" @click="runAction(run, 'cancel')">
              取消未完成块
            </el-button>
          </article>
        </el-collapse-item>
      </el-collapse>
    </template>
    <ClaimEditor :visible="editor" :case-id="caseId" :claim-id="claimId" :materials="materials" @close="editor = false" @saved="refresh(true)" />
    <el-dialog v-model="chunksVisible" title="分块记录（不代表全案完成）" width="min(900px, 95vw)">
      <article v-for="chunk in chunks" :key="chunk.id" class="claim-run">
        <p>块{{ chunk.chunkIndex }} · {{ chunk.state }} · {{ chunk.errorCode }} · {{ chunk.rejections }}</p><el-button v-for="id in anchorsInChunk(chunk.anchorIds)" :key="id" link @click="resolve(id)">
          来源 {{ id }}
        </el-button>
      </article>
    </el-dialog>
    <el-dialog v-model="sourceVisible" title="块的真实来源" width="min(900px, 95vw)">
      <DossierSourceViewer v-if="sourceVisible && source" :case-id="caseId" :source="source" />
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.claims-workbench {
display: flex;
flex-direction: column;
gap: var(--yl-space-4);
}
.claim-extract {
display: flex;
flex-wrap: wrap;
gap: var(--yl-space-3);
align-items: center;
}
.claim-extract p {
flex-basis: 100%;
margin: 0;
color: var(--yl-color-text-muted);
}
.claim-extract .el-select {
width: min(100%, 360px);
}
.claim-groups {
display: flex;
flex-wrap: wrap;
gap: var(--yl-space-2);
margin-bottom: var(--yl-space-4);
}
.el-form .el-select {
width: 160px;
}
.claim-table {
margin: var(--yl-space-3) 0;
overflow-x: auto;
}
.claim-text-button {
height: auto;
text-align: left;
white-space: normal;
}
.claim-run {
padding: var(--yl-space-3) 0;
border-bottom: 1px solid var(--yl-color-border);
}
</style>
