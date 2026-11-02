<script setup lang="ts">
import type { BankRow } from '@/api/lawyer/bank';
import type { ClaimLink, ResolvedSource, SourceAnchor } from '@/api/lawyer/claims';
import type { Material, MaterialPage, Overview } from '@/api/lawyer/dossier';
import type { ReviewPointer, ReviewView } from '@/api/lawyer/review';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, onScopeDispose, ref, watch } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router';
import { bankRequest } from '@/api/lawyer/bank';
import { claimStates } from '@/api/lawyer/claims';
import { request } from '@/api/lawyer/dossier';
import { dateKinds } from '@/api/lawyer/events';
import { changedValues, editValues, maskReviewValue, pointer, reviewKey, reviewLink, reviewQuery, reviewRequest, storedReviewQueue, validId } from '@/api/lawyer/review';
import AppPageHeader from '@/components/AppPageHeader.vue';
import AppState from '@/components/AppState.vue';
import DossierAnchorPicker from '@/components/DossierAnchorPicker.vue';
import DossierSourceViewer from '@/components/DossierSourceViewer.vue';
import { useUserStore } from '@/stores';
import BankRowEditor from './BankRowEditor.vue';
import ClaimEditor from './ClaimEditor.vue';

const route = useRoute();
const router = useRouter();
const user = useUserStore();
const caseId = computed(() => typeof route.query.id === 'string' ? route.query.id : '');
const view = ref<ReviewView>();
const latest = ref<ReviewView>();
const overview = ref<Overview>();
const files = ref<Material[]>([]);
const pages = ref<MaterialPage[]>([]);
const edits = ref<Record<string, string>>({});
const reason = ref('');
const links = ref<ClaimLink[]>([]);
const sources = ref<ResolvedSource[]>([]);
const sourceId = ref('');
const browseVersion = ref('');
const browsePage = ref(1);
const directory = ref(true);
const showSource = ref(true);
const picker = ref(false);
const advanced = ref(false);
const bankRow = ref<BankRow>();
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const pageError = ref('');
const saved = ref('');
const autoNext = ref(false);
const queue = ref<string[]>([]);
let originalLinks = '';
let epoch = 0;
let pageSerial = 0;
let disposed = false;
let currentPointer: ReviewPointer;
const source = computed(() => sources.value.find(s => s.anchor.id === sourceId.value));
const atSource = computed(() => !!source.value && browseVersion.value === source.value.anchor.fileVersionId && browsePage.value === source.value.anchor.pageIndex);
const sourceValid = computed(() => !!view.value && (view.value.objectKind === 'bank' ? sources.value.some(s => s.verified) : links.value.some(l => l.relation === 'supports') && links.value.every(l => sources.value.some(s => s.anchor.id === l.anchorId && s.verified))));
const changes = computed(() => changedValues(view.value?.fields || [], edits.value));
const dirty = computed(() => Object.keys(changes.value).length > 0 || reason.value !== '' || JSON.stringify(links.value) !== originalLinks);
const index = computed(() => view.value ? queue.value.indexOf(view.value.objectId) : -1);
const isClaim = computed(() => view.value?.objectKind === 'claim');
const isEvent = computed(() => view.value?.objectKind === 'event');
const linkedSources = computed(() => isClaim.value || isEvent.value);
const isSourceOnly = computed(() => view.value?.objectKind === 'source');
function scopeKey(kind: string) {
  return `review-queue:${user.userInfo?.tenantId}:${user.userInfo?.userId}:${caseId.value}:${kind}`;
}
function active(run: number, token: string | undefined) {
  return !disposed && run === epoch && token === user.token;
}
function accept(result: ReviewView) {
  view.value = result;
  sources.value = result.sources;
  links.value = result.links.map(l => ({ ...l }));
  originalLinks = JSON.stringify(links.value);
  edits.value = editValues(result.fields);
  reason.value = '';
  latest.value = undefined;
  sourceId.value = result.sources.find(s => s.anchor.id === currentPointer.anchorId)?.anchor.id || result.sources[0]?.anchor.id || '';
  browseVersion.value = source.value?.anchor.fileVersionId || '';
  browsePage.value = source.value?.anchor.pageIndex || 1;
}
async function load() {
  const run = ++epoch;
  const token = user.token;
  view.value = undefined;
  latest.value = undefined;
  sources.value = [];
  links.value = [];
  originalLinks = '[]';
  edits.value = {};
  reason.value = '';
  files.value = [];
  overview.value = undefined;
  pages.value = [];
  browseVersion.value = '';
  sourceId.value = '';
  queue.value = [];
  saved.value = '';
  error.value = '';
  pageError.value = '';
  busy.value = false;
  picker.value = false;
  advanced.value = false;
  bankRow.value = undefined;
  if (!token || !validId(caseId.value)) {
    error.value = '缺少有效案件上下文，请从案件内进入复核';
    return;
  }
  loading.value = true;
  try {
    currentPointer = pointer(route.query);
    const results = await Promise.all([reviewRequest<ReviewView>(caseId.value, `/resolve?${reviewQuery(currentPointer)}`), request<{
      rows: Material[];
      total: number;
    }>(caseId.value, '/materials?pageNum=1&pageSize=200'), request<Overview>(caseId.value, '/overview')]);
    if (!active(run, token))
      return;
    files.value = results[1].rows;
    overview.value = results[2];
    accept(results[0]);
    let hasStoredQueue = false;
    try {
      const cached = storedReviewQueue(sessionStorage.getItem(scopeKey(results[0].objectKind)));
      if (cached) {
        hasStoredQueue = true;
        queue.value = cached.ids;
        autoNext.value = cached.autoNext;
      }
    }
    catch {
    }
    if (!hasStoredQueue && ['claim', 'bank', 'event'].includes(results[0].objectKind)) {
      const endpoint = results[0].objectKind === 'claim' ? 'claims' : results[0].objectKind === 'event' ? 'events' : 'bank-rows';
      const path = `/${endpoint}?status=pending_review&pageNum=1&pageSize=100`;
      const data = await request<{
        rows: {
          id?: string;
          event?: { id: string };
        }[];
      }>(caseId.value, path);
      if (active(run, token))
        queue.value = data.rows.map(row => row.event?.id || row.id).filter(validId);
    }
    if (queue.value.length && ['claim', 'bank', 'event'].includes(results[0].objectKind)) {
      const pending = await reviewRequest<string[]>(caseId.value, '/queue', 'POST', { kind: results[0].objectKind, ids: queue.value });
      if (active(run, token))
        queue.value = pending;
    }
  }
  catch (e) {
    if (active(run, token))
      error.value = (e as Error).message;
  }
  finally {
    if (active(run, token))
      loading.value = false;
  }
}
async function loadPages() {
  const run = epoch;
  const requestId = ++pageSerial;
  const token = user.token;
  pages.value = [];
  pageError.value = '';
  if (!browseVersion.value || !token)
    return;
  try {
    const result = await request<MaterialPage[]>(caseId.value, `/files/${browseVersion.value}/pages`);
    if (active(run, token) && requestId === pageSerial)
      pages.value = result;
  }
  catch (e) {
    if (active(run, token) && requestId === pageSerial)
      pageError.value = (e as Error).message;
  }
}
function selectSource(id: string) {
  sourceId.value = id;
  const selected = sources.value.find(s => s.anchor.id === id);
  if (selected) {
    browseVersion.value = selected.anchor.fileVersionId;
    browsePage.value = selected.anchor.pageIndex;
    showSource.value = true;
  }
}
function browse(version: string, page = 1) {
  browseVersion.value = version;
  browsePage.value = page;
  showSource.value = true;
}
async function leave() {
  if ((!dirty.value && !advanced.value) || busy.value)
    return !busy.value;
  try {
    await ElMessageBox.confirm(advanced.value ? '高级核对面板可能有未保存内容，离开会丢弃本次输入。' : '有未保存的字段/来源修改，离开会丢弃这些输入。原件和已保存历史保留。', '未保存内容', { type: 'warning' });
    return true;
  }
  catch {
    return false;
  }
}
async function openAdvanced() {
  if (!view.value || !await leave())
    return;
  if (isEvent.value) {
    await router.push({ path: '/my-cases/events', query: { id: caseId.value, eventId: view.value.objectId } });
    return;
  }
  accept(view.value);
  const run = epoch;
  const token = user.token;
  if (view.value.objectKind === 'bank') {
    try {
      const value = await bankRequest<BankRow>(caseId.value, `/${view.value.objectId}`);
      if (!active(run, token))
        return;
      bankRow.value = value;
    }
    catch (e) {
      if (active(run, token))
        error.value = (e as Error).message;
      return;
    }
  }
  advanced.value = true;
}
async function closeAdvanced() {
  if (await leave())
    advanced.value = false;
}
function next(step: number) {
  const target = index.value < 0 ? queue.value[0] : queue.value[index.value + step];
  if (target && view.value)
    router.replace(reviewLink(caseId.value, view.value.objectKind, target));
}
async function save(action: string) {
  if (!view.value || view.value.version === null || busy.value)
    return;
  const run = epoch;
  const token = user.token;
  const value = view.value;
  const nextId = index.value < 0 ? queue.value[0] : queue.value[index.value + 1];
  busy.value = true;
  error.value = '';
  saved.value = '';
  try {
    const result = await reviewRequest<ReviewView>(caseId.value, `/${value.objectKind}/${value.objectId}`, 'PATCH', { expectedVersion: value.version, action, fields: changes.value, sources: linkedSources.value ? links.value : null, reason: reason.value });
    if (!active(run, token))
      return;
    accept(result);
    const event = result.history[result.history.length - 1];
    saved.value = event ? `已保存 · ${event.createTime} · 操作者${event.operatorId} · v${result.version}` : `已保存 · v${result.version}`;
    ElMessage.success('已保存，对象核对不等于主办终核');
    if (autoNext.value && action === 'confirm' && nextId && nextId !== value.objectId) {
      busy.value = false;
      await router.replace(reviewLink(caseId.value, value.objectKind, nextId));
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
async function compare() {
  if (!view.value)
    return;
  const run = epoch;
  const token = user.token;
  try {
    const result = await reviewRequest<ReviewView>(caseId.value, `/resolve?${reviewQuery({ objectKind: view.value.objectKind, objectId: view.value.objectId })}`);
    if (active(run, token))
      latest.value = result;
  }
  catch (e) {
    if (active(run, token))
      error.value = (e as Error).message;
  }
}
function useLatestVersion() {
  if (view.value && latest.value) {
    view.value.version = latest.value.version;
    latest.value = undefined;
    error.value = '';
  }
}
async function replaceWithLatest() {
  if (!latest.value || !await leave())
    return;
  accept(latest.value);
}
async function added(anchor: SourceAnchor) {
  const run = epoch;
  const token = user.token;
  try {
    const result = await request<ResolvedSource>(caseId.value, `/anchors/${anchor.id}/resolve`);
    if (!active(run, token))
      return;
    if (!sources.value.some(s => s.anchor.id === anchor.id))
      sources.value.push(result);
    if (!links.value.some(l => l.anchorId === anchor.id))
      links.value.push({ anchorId: anchor.id, relation: 'supports' });
    selectSource(anchor.id);
    picker.value = false;
  }
  catch (e) {
    if (active(run, token))
      error.value = (e as Error).message;
  }
}
function removeSource(index: number) {
  links.value.splice(index, 1);
}
function returnToList() {
  router.push({ path: view.value?.objectKind === 'bank' ? '/my-cases/bank' : view.value?.objectKind === 'event' ? '/my-cases/events' : '/my-cases/claims', query: { id: caseId.value } });
}
function rememberPreference() {
  if (view.value)
    sessionStorage.setItem(scopeKey(view.value.objectKind), JSON.stringify({ ids: queue.value, autoNext: autoNext.value }));
}
function keyboard(e: KeyboardEvent) {
  if (advanced.value) {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
      e.preventDefault();
      ElMessage.info('请使用高级核对面板内的保存按钮');
    }
    return;
  }
  const target = e.target as HTMLElement;
  const editing = !!target.closest('input,textarea,select,[contenteditable="true"],[role="combobox"],.el-overlay');
  const action = reviewKey(e.key, e.ctrlKey, e.metaKey, editing);
  if (action === 'save') {
    e.preventDefault();
    if (!isSourceOnly.value)
      save('correct');
  }
  else if (!busy.value && action === 'next') {
    e.preventDefault();
    next(1);
  }
  else if (!busy.value && action === 'previous') {
    e.preventDefault();
    next(-1);
  }
}
watch(() => [route.fullPath, user.token], load, { immediate: true });
watch(browseVersion, loadPages);
watch(autoNext, rememberPreference);
watch(() => edits.value.datePrecision, (value) => {
  if (value === 'unknown' || value === 'exact') {
    edits.value.dateStart = '';
    edits.value.dateEnd = '';
  }
});
onBeforeRouteLeave(leave);
onBeforeRouteUpdate(leave);
onMounted(() => window.addEventListener('keydown', keyboard));
watch(() => edits.value.dateKind, (value) => {
  if (value === 'unknown' || value === 'relative') {
    edits.value.dateStart = '';
    edits.value.dateEnd = '';
  }
});
onScopeDispose(() => {
  disposed = true;
  epoch++;
  pageSerial++;
  window.removeEventListener('keydown', keyboard);
});
</script>

<template>
  <div class="yl-page review-workbench">
    <AppPageHeader title="原页双栏复核" :description="`${overview?.caseTitle || '当前案件'} · 对象核对与主办终核分别记录`">
      <template #actions>
        <el-button @click="returnToList">
          返回列表（保留筛选）
        </el-button><el-button @click="directory = !directory">
          {{ directory ? '收起' : '展开' }}文件/页目录
        </el-button><el-button @click="showSource = !showSource">
          {{ showSource ? '收起' : '展开' }}原页
        </el-button>
      </template>
    </AppPageHeader>
    <AppState v-if="loading" kind="loading" title="正在定位对象和真实出处" />
    <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" />
    <el-button v-if="error && view" @click="compare">
      对照服务器最新版本（保留输入）
    </el-button>
    <AppState v-if="!loading && !view" kind="error" title="复核对象不可用" description="请从案件内已授权的事实/流水进入；跨案和失效直链不会显示其他对象。">
      <el-button @click="load">
        重试定位
      </el-button>
    </AppState>
    <template v-if="view">
      <div class="review-queue">
        <span>{{ index >= 0 ? `${index + 1}/${queue.length}` : '独立定位' }} · 当前队列来自列表筛选/当前页，不代表全案已核</span><el-button :disabled="busy || index <= 0" @click="next(-1)">
          上一待核 K
        </el-button><el-button :disabled="busy || !queue.length || index >= queue.length - 1" @click="next(1)">
          下一待核 J
        </el-button><el-switch v-model="autoNext" active-text="确认后自动下一条" /><span>Ctrl/Cmd+S 保存待核；输入文字时J/K不切换</span>
      </div>
      <el-alert v-if="saved" type="success" :title="saved" :closable="false" />
      <section v-if="latest" class="yl-panel review-conflict">
        <h2>版本冲突：服务器v{{ latest.version }}，本次v{{ view.version }}</h2><div v-for="field in latest.fields" :key="field.key">
          <strong>{{ field.label }}</strong>：服务器 {{ maskReviewValue(field, field.value) }}；当前输入 {{ field.sensitive && !edits[field.key] ? '保留原值' : edits[field.key] }}
        </div><p>服务器关联{{ latest.links.length }}条来源，本次关联{{ links.length }}条；提交本人来源会替换当前关联，历史保留。</p><el-button @click="useLatestVersion">
          已对照差异，保留本人字段/来源，使用新版本号再提交
        </el-button><el-button @click="replaceWithLatest">
          丢弃本次输入，载入服务器版本
        </el-button>
      </section>
      <div class="review-layout" :class="{ 'with-directory': directory, 'without-source': !showSource }">
        <aside v-if="directory" class="yl-panel review-directory" aria-label="材料和页目录">
          <h2>材料与物理/系统页</h2><el-button v-for="file in files.filter(f => f.current)" :key="file.material.id" text class="directory-file" @click="browse(file.current!.id)">
            {{ file.material.displayName }} · v{{ file.current!.versionNo }} · {{ file.current!.parsedPages }}/{{ file.current!.pageCount ?? '?' }}页
          </el-button><p v-if="pageError" role="alert">
            {{ pageError }}
          </p><div class="directory-pages">
            <el-button v-for="page in pages" :key="page.id" :type="browsePage === page.pageIndex ? 'primary' : 'default'" @click="browsePage = page.pageIndex">
              P{{ page.pageIndex }} · {{ page.extractStatus }}
            </el-button>
          </div>
        </aside>
        <section v-if="showSource" class="yl-panel review-source" aria-label="原件预览与出处">
          <el-select :model-value="sourceId" placeholder="选择独立来源" aria-label="候选来源" @update:model-value="selectSource">
            <el-option v-for="s in sources" :key="s.anchor.id" :value="s.anchor.id" :label="`${s.fileName} · 第${s.anchor.pageIndex}页 · ${s.verified ? '出处有效' : '未验证'}`" />
          </el-select><div class="review-pages">
            <el-button :disabled="browsePage <= 1" @click="browsePage--">
              上页
            </el-button><span>第{{ browsePage }}页</span><el-button :disabled="!pages.length || browsePage >= pages[pages.length - 1].pageIndex" @click="browsePage++">
              下页
            </el-button><el-button v-if="source && !atSource" @click="selectSource(source.anchor.id)">
              返回此候选原出处
            </el-button>
          </div><DossierSourceViewer v-if="browseVersion" :case-id="caseId" :source="browseVersion === source?.anchor.fileVersionId ? source : undefined" :file-version-id="browseVersion" :file-name="files.find(f => f.current?.id === browseVersion)?.current?.fileName" :browse-page="browsePage" @navigate="browsePage = $event" /><AppState v-else title="尚无可定位原页" description="无来源无法确认；可下载原件或在右侧关联来源，先保留待核。" />
        </section>
        <section class="yl-panel review-fields" aria-label="字段级核对">
          <h2>{{ view.objectKind === 'bank' ? '银行交易' : view.objectKind === 'claim' ? '事实/摘录' : isEvent ? '时间线事件' : '原文定位' }} · {{ claimStates[view.reviewStatus] || view.reviewStatus }} · v{{ view.version ?? '只读' }}</h2><p>{{ view.initialNote }}</p><el-alert v-if="!isSourceOnly && (!sourceValid || !atSource)" type="warning" title="来源无法核实或当前正在浏览其他页；请先定位有效原出处，不能直接确认。" :closable="false" />
          <el-button v-if="!isSourceOnly" :disabled="busy" @click="openAdvanced">
            {{ isClaim ? '拆分、重跑差异与更多核对' : isEvent ? '事件拆分、合并及更多编辑' : '流水拆分与更多核对' }}
          </el-button>
          <el-form v-if="!isSourceOnly" label-position="top" @submit.prevent="save('correct')">
            <template v-for="field in view.fields" :key="field.key">
              <el-form-item v-if="!['dateStart', 'dateEnd'].includes(field.key) || (isEvent ? !['unknown', 'relative'].includes(edits.dateKind) : edits.datePrecision === 'range')" :label="field.label">
                <p class="field-baseline">
                  最初候选：{{ maskReviewValue(field, field.original) }}<br>当前保存：{{ maskReviewValue(field, field.value) }}
                </p><el-select v-if="field.key === 'dateKind'" v-model="edits[field.key]">
                  <el-option v-for="(label, key) in dateKinds" :key="key" :value="key" :label="label" />
                </el-select><el-select v-else-if="field.key === 'datePrecision'" v-model="edits[field.key]" placeholder="未标注时按确切日期核对">
                  <el-option label="确切日期" value="exact" /><el-option label="日期范围（不强填某天）" value="range" /><el-option label="无法确定（明确保留未知）" value="unknown" />
                </el-select><el-select v-else-if="field.key === 'direction'" v-model="edits[field.key]">
                  <el-option label="收入" value="income" /><el-option label="支出" value="expense" /><el-option label="待核方向" value="unknown" />
                </el-select><el-date-picker v-else-if="['dateStart', 'dateEnd'].includes(field.key) && !isEvent" v-model="edits[field.key]" type="date" value-format="YYYY-MM-DD" /><el-input v-else v-model="edits[field.key]" :type="['text', 'summary'].includes(field.key) ? 'textarea' : 'text'" :rows="3" :maxlength="field.key === 'summary' && isEvent ? 5000 : field.key === 'title' ? 300 : field.key === 'text' ? 3000 : 1000" :placeholder="field.sensitive ? '留空保留当前原值；填写新值会记录审计' : field.key.toLowerCase().includes('amount') ? '保留精确金额文本，不用浮点合计' : ''" /><p v-if="field.key === 'name'">
                  仅作为文本核对；同名不自动合并为同一主体，关联主体在第93条开放。
                </p>
              </el-form-item>
            </template>
            <template v-if="linkedSources">
              <div v-for="(link, i) in links" :key="`${link.anchorId}:${i}`" class="review-link">
                <el-button link @click="selectSource(link.anchorId)">
                  来源{{ i + 1 }}
                </el-button><el-select v-model="link.relation">
                  <el-option label="支持" value="supports" /><el-option label="矛盾" value="contradicts" /><el-option label="提及" value="mentions" />
                </el-select><el-button @click="removeSource(i)">
                  移除关联
                </el-button>
              </div><el-button @click="picker = !picker">
                {{ picker ? '收起来源选择' : '关联其他原页/片段' }}
              </el-button><DossierAnchorPicker v-if="picker" :case-id="caseId" :materials="files" @added="added" />
            </template>
            <el-form-item label="核对依据/修改理由（至少4字）">
              <el-input v-model="reason" type="textarea" :maxlength="500" />
            </el-form-item><div class="review-actions">
              <el-button type="primary" :loading="busy" :disabled="!sourceValid || !atSource" @click="save('confirm')">
                确认无误
              </el-button><el-button :loading="busy" @click="save('correct')">
                纠正并保存
              </el-button><el-button :loading="busy" @click="save('defer')">
                暂无法判断
              </el-button><el-button :loading="busy" @click="save('exclude')">
                不采用
              </el-button>
            </div>
          </el-form>
          <el-collapse>
            <el-collapse-item title="字段级审校回放（只追加，不删除历史）" name="history">
              <article v-for="event in view.history" :key="event.id" class="review-history">
                <p>{{ event.createTime }} · 操作者{{ event.operatorId }} · {{ event.action }} · {{ event.reason }}</p><p class="review-fingerprint">
                  前指纹{{ event.beforeFingerprint }}<br>后指纹{{ event.afterFingerprint }}
                </p><el-table :data="event.fields" size="small">
                  <el-table-column prop="field" label="字段" min-width="110" /><el-table-column prop="oldValue" label="旧值" min-width="130" /><el-table-column prop="newValue" label="新值" min-width="130" /><el-table-column label="受控原值引用" min-width="170">
                    <template #default="{ row }">
                      <span v-if="row.sensitive">脱敏展示 · {{ row.protectedRef }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </article>
            </el-collapse-item>
          </el-collapse>
        </section>
      </div>
      <ClaimEditor v-if="isClaim" :visible="advanced" :case-id="caseId" :claim-id="view.objectId" :materials="files" @close="advanced = false" @saved="advanced = false; load()" />
      <el-drawer v-else-if="view.objectKind === 'bank'" :model-value="advanced" title="流水拆分与更多核对" size="min(1100px, 96vw)" :before-close="closeAdvanced">
        <BankRowEditor v-if="advanced && bankRow" :case-id="caseId" :row="bankRow" @saved="advanced = false; load()" @close="closeAdvanced" />
      </el-drawer>
    </template>
  </div>
</template>

<style scoped lang="scss">
.review-source {
position: sticky;
top: var(--yl-space-3);
}

@media (width <= 960px) {
.review-source {
position: static;
}
}
.review-workbench {
display: flex;
flex-direction: column;
gap: var(--yl-space-3);
}
.review-queue, .review-pages, .review-actions, .review-link {
display: flex;
flex-wrap: wrap;
gap: var(--yl-space-2);
align-items: center;
margin: var(--yl-space-3) 0;
}
.review-layout {
display: grid;
grid-template-columns: minmax(0, 1.2fr) minmax(330px, 1fr);
gap: var(--yl-space-3);
align-items: start;
}
.review-layout.with-directory {
grid-template-columns: 180px minmax(0, 1.2fr) minmax(330px, 1fr);
}
.review-layout.without-source {
grid-template-columns: minmax(0, 1fr);
}
.review-layout.with-directory.without-source {
grid-template-columns: 180px minmax(0, 1fr);
}
.review-directory, .review-source, .review-fields {
min-width: 0;
}
.review-directory {
max-height: 76vh;
overflow: auto;
}
.directory-file {
justify-content: flex-start;
width: 100%;
height: auto;
text-align: left;
white-space: normal;
}
.directory-pages {
display: flex;
flex-direction: column;
gap: var(--yl-space-2);
}
.directory-pages .el-button {
margin-left: 0;
}
.review-fields h2, .review-directory h2 {
margin: 0 0 var(--yl-space-3);
font-size: var(--yl-title-md);
}
.field-baseline {
width: 100%;
margin: 0 0 var(--yl-space-2);
color: var(--yl-color-text-muted);
overflow-wrap: anywhere;
}
.review-link .el-select {
width: 100px;
}
.review-history {
padding: var(--yl-space-3) 0;
border-bottom: 1px solid var(--yl-color-border);
}
.review-fingerprint {
font-size: var(--yl-text-sm);
overflow-wrap: anywhere;
}

@media (width <= 1100px) {
.review-layout.with-directory {
grid-template-columns: minmax(0, 1.2fr) minmax(320px, 1fr);
}
 .review-layout .review-directory {
grid-column: 1 / -1;
max-height: 240px;
}

}

@media (width <= 960px) {
.review-layout, .review-layout.with-directory, .review-layout.with-directory.without-source {
grid-template-columns: minmax(0, 1fr);
}

}
</style>
