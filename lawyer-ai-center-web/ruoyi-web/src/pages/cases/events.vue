<script setup lang="ts">
import type { Material, Overview } from '@/api/lawyer/dossier';
import type { EventDetail, EventGraph, EventListing } from '@/api/lawyer/events';
import { ElMessage } from 'element-plus';
import { computed, nextTick, onMounted, onScopeDispose, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router';
import { claimStates } from '@/api/lawyer/claims';
import { request } from '@/api/lawyer/dossier';
import { dateKinds, eventDate, eventFilter, eventQuery, eventRequest } from '@/api/lawyer/events';
import { rememberReviewQueue, reviewLink, validId } from '@/api/lawyer/review';
import AppPageHeader from '@/components/AppPageHeader.vue';
import AppState from '@/components/AppState.vue';
import { useUserStore } from '@/stores';
import EventEditor from './EventEditor.vue';

const route = useRoute();
const router = useRouter();
const user = useUserStore();
const caseId = computed(() => typeof route.query.id === 'string' ? route.query.id : '');
const root = ref<HTMLElement>();
const filter = reactive(eventFilter());
const listing = ref<EventListing>();
const files = ref<Material[]>([]);
const overview = ref<Overview>();
const checked = ref<EventDetail[]>([]);
const editorVisible = ref(false);
const editorId = ref<string>();
const merging = ref<EventDetail[]>([]);
const editor = ref<InstanceType<typeof EventEditor>>();
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const groupLabel = ref('');
const reason = ref('');
const beforeId = ref('');
const afterId = ref('');
const showOrders = ref(false);
const graph = ref<EventGraph>();
const orderError = ref('');
let epoch = 0;
let serial = 0;
let disposed = false;
let scrollContainer: HTMLElement | undefined;
const pendingOnPage = computed(() => (listing.value?.rows || []).filter(row => !row.event.dateStart && row.event.reviewStatus !== 'excluded'));
function scopeKey() { return `event-view:${user.userInfo?.tenantId}:${user.userInfo?.userId}:${caseId.value}`; }
function remember() { if (user.token) sessionStorage.setItem(scopeKey(), JSON.stringify({ filter, top: scrollContainer?.scrollTop || 0 })); }
function active(run: number, token: string | undefined) { return !disposed && run === epoch && token === user.token; }
async function refresh(top?: number) {
  if (!user.token || !validId(caseId.value)) { error.value = '请从有效案件进入时间线'; return; }
  const run = epoch;
  const sequence = ++serial;
  const token = user.token;
  loading.value = true;
  try {
    const [events, materialList, context] = await Promise.all([eventRequest<EventListing>(caseId.value, `?${eventQuery(filter)}`), request<{ rows: Material[]; total: number }>(caseId.value, '/materials?pageNum=1&pageSize=200'), request<Overview>(caseId.value, '/overview')]);
    if (!active(run, token) || sequence !== serial) return;
    listing.value = events; files.value = materialList.rows; overview.value = context; checked.value = []; error.value = '';
    await nextTick();
    if (active(run, token) && sequence === serial && top !== undefined && scrollContainer) scrollContainer.scrollTop = top;
    if (showOrders.value) await loadOrders();
  }
  catch (e) { if (active(run, token) && sequence === serial) { error.value = (e as Error).message; if (/案件不可用|访问权限|登录/.test(error.value)) { listing.value = undefined; files.value = []; overview.value = undefined; editorVisible.value = false; merging.value = []; graph.value = undefined; } } }
  finally { if (active(run, token) && sequence === serial) { loading.value = false; remember(); } }
}
async function reset() {
  ++epoch; ++serial; listing.value = undefined; files.value = []; overview.value = undefined; checked.value = []; editorVisible.value = false; editorId.value = undefined; merging.value = []; error.value = ''; graph.value = undefined; reason.value = ''; groupLabel.value = ''; beforeId.value = ''; afterId.value = ''; orderError.value = ''; busy.value = false; showOrders.value = false;
  Object.assign(filter, eventFilter());
  let top = 0;
  try { const saved = JSON.parse(sessionStorage.getItem(scopeKey()) || '{}'); if (saved.filter) for (const key of Object.keys(filter)) { const value = saved.filter[key]; if (typeof value === typeof filter[key as keyof typeof filter]) Object.assign(filter, { [key]: value }); } top = Number.isFinite(saved.top) ? Math.max(0, saved.top) : 0; } catch { /* no invented preference */ }
  await refresh(top);
  if (validId(route.query.eventId)) { editorId.value = route.query.eventId; editorVisible.value = true; }
}
function search() { filter.pageNum = 1; void refresh(); }
function newItem() { remember(); merging.value = []; editorId.value = undefined; editorVisible.value = true; }
function open(row: EventDetail) { remember(); merging.value = []; editorId.value = row.event.id; editorVisible.value = true; }
function openId(id: string) { remember(); merging.value = []; editorId.value = id; editorVisible.value = true; }
function merge() { if (checked.value.length < 2 || checked.value.length > 100) return; remember(); merging.value = [...checked.value]; editorId.value = undefined; editorVisible.value = true; }
async function reviewed() { editorVisible.value = false; await refresh(); }
function source(row: EventDetail) { remember(); rememberReviewQueue(String(user.userInfo?.tenantId || ''), String(user.userInfo?.userId || ''), caseId.value, 'event', (listing.value?.rows || []).filter(d => ['pending_review', 'stale'].includes(d.event.reviewStatus)).map(d => d.event.id)); void router.push(reviewLink(caseId.value, 'event', row.event.id, row.sources[0])); }
async function mutate(action: () => Promise<unknown>) { if (busy.value) return; const run = epoch; const token = user.token; busy.value = true; error.value = ''; try { await action(); if (active(run, token)) { ElMessage.success('已保存，时间线修订已更新'); await refresh(); } } catch (e) { if (active(run, token)) error.value = (e as Error).message; } finally { if (active(run, token)) busy.value = false; } }
async function group() { if (!checked.value.length || reason.value.trim().length < 4) { error.value = '请选择事件并填写至少4字的操作理由'; return; } await mutate(() => eventRequest(caseId.value, '/group', 'POST', { items: checked.value.map(d => ({ id: d.event.id, version: d.event.version })), groupLabel: groupLabel.value, reason: reason.value })); }
async function loadOrders() { const run = epoch; const token = user.token; try { const value = await request<EventGraph>(caseId.value, '/timeline/graph-data'); if (active(run, token)) { graph.value = value; orderError.value = ''; } } catch (e) { if (active(run, token)) orderError.value = (e as Error).message; } }
async function toggleOrders() { showOrders.value = !showOrders.value; if (showOrders.value) await loadOrders(); }
async function addOrder() { const value = listing.value?.rows.find(d => d.event.id === beforeId.value); if (!value || !afterId.value || beforeId.value === afterId.value || reason.value.trim().length < 4) { error.value = '请选择不同的前后事件并填写至少4字理由'; return; } await mutate(() => eventRequest(caseId.value, '/precedence', 'POST', { expectedVersion: value.event.version, beforeId: beforeId.value, afterId: afterId.value, reason: reason.value })); }
async function excludeOrder(edge: EventGraph['precedence'][number]) { if (reason.value.trim().length < 4) { error.value = '请先填写排除该草稿的理由'; return; } await mutate(() => eventRequest(caseId.value, `/precedence/${edge.id}/exclude`, 'POST', { expectedVersion: edge.version, reason: reason.value })); }
function title(id: string) { return graph.value?.events.find(node => node.event.id === id)?.event.title || `事件${id}`; }
async function leave() { if (editorVisible.value && editor.value && !await editor.value.canLeave()) return false; remember(); return true; }
watch(() => [caseId.value, user.token], reset);
onMounted(() => { scrollContainer = root.value?.closest('.main-scroll') as HTMLElement | undefined; scrollContainer?.addEventListener('scroll', remember, { passive: true }); void reset(); });
onBeforeRouteLeave(leave);
onBeforeRouteUpdate(leave);
onScopeDispose(() => { disposed = true; ++epoch; scrollContainer?.removeEventListener('scroll', remember); });
</script>

<template>
  <main ref="root" class="yl-page event-workbench">
    <AppPageHeader title="事实事件时间线" :eyebrow="overview?.caseTitle || '当前案件'" description="按真实时间表达整理事件；缺日期、约略日期和不同陈述均保留。整理/核对不等于主办终核。"><template #actions><el-button @click="router.push({ path: '/my-cases/materials', query: { id: caseId } })">材料对账</el-button><el-button @click="router.push({ path: '/my-cases/claims', query: { id: caseId } })">事实候选</el-button><el-button type="primary" :disabled="!user.token || loading" @click="newItem">新建事件</el-button></template></AppPageHeader>
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon /><AppState v-if="loading" kind="loading" title="正在读取已保存事件与修订号" />
    <form class="yl-panel event-filters" @submit.prevent="search"><el-input v-model="filter.search" placeholder="搜索标题、简述、原始日期" aria-label="搜索事件" clearable /><el-select v-model="filter.dateKind" placeholder="全部日期确定性" aria-label="日期确定性" clearable><el-option v-for="(label, key) in dateKinds" :key="key" :label="label" :value="key" /></el-select><el-select v-model="filter.datePool" placeholder="全部日期池" aria-label="日期池" clearable><el-option label="日期待核（含相对/无绝对日期）" value="unknown" /><el-option label="有日期或日期范围" value="known" /></el-select><el-select v-model="filter.status" placeholder="全部审核状态" aria-label="事件审核状态" clearable><el-option label="待核" value="pending_review" /><el-option label="对象已核" value="confirmed" /><el-option label="复核失效" value="stale" /><el-option label="已排除（历史保留）" value="excluded" /></el-select><el-input v-model="filter.participant" placeholder="参与主体名称" aria-label="参与主体筛选" clearable /><el-select v-model="filter.fileVersionId" placeholder="全部来源文件" aria-label="来源文件筛选" clearable><el-option v-for="file in files.filter(f => f.current)" :key="file.current!.id" :value="file.current!.id" :label="file.material.displayName" /></el-select><el-input v-model="filter.from" placeholder="日期起 YYYY[-MM[-DD]]" aria-label="日期起" clearable /><el-input v-model="filter.to" placeholder="日期止 YYYY[-MM[-DD]]" aria-label="日期止" clearable /><el-input v-model="filter.groupLabel" placeholder="事件分组" aria-label="分组筛选" clearable /><el-button native-type="submit" :disabled="loading">筛选</el-button><el-button :disabled="loading" @click="Object.assign(filter, eventFilter()); search()">清除筛选</el-button></form>
    <section v-if="listing" class="yl-panel event-pool" aria-label="固定日期待核池"><h2>日期待核池 · 当前筛选{{ listing.unknownCount }}条</h2><p>完全未知、相对时间、约略但无法确定绝对日期的事件保留在此；不会补成某月1日。下列为当前页条目。</p><el-button @click="filter.datePool = 'unknown'; search()">查看全部日期待核条目</el-button><el-button v-for="row in pendingOnPage" :key="row.event.id" link @click="open(row)">{{ row.event.title }} · {{ dateKinds[row.event.dateKind] }} · {{ row.event.dateRaw || '缺少日期' }}</el-button><p v-if="!pendingOnPage.length">当前页没有待定日期；总量以当前筛选计数为准。</p></section>
    <section v-if="listing" class="yl-panel"><div class="event-batch"><span>已保存{{ listing.total }}条 · 时间线修订{{ listing.revision }} · 已选{{ checked.length }}条</span><el-button :disabled="checked.length < 2 || checked.length > 100 || busy" @click="merge">合并选中事件</el-button><el-input v-model="groupLabel" placeholder="批量分组名称（可空清除）" aria-label="批量分组名称" :maxlength="100" /><el-input v-model="reason" placeholder="批量/先后草稿操作理由（至少4字）" aria-label="操作理由" :maxlength="500" /><el-button :disabled="!checked.length || busy" @click="group">批量调整分组</el-button><el-button @click="toggleOrders">{{ showOrders ? '收起' : '展开' }}先后关系草稿</el-button></div>
      <section v-if="showOrders" class="event-order"><h2>人工先后草稿</h2><p>仅表达待核的先后关系，不是确切日期；循环矛盾会被拒绝。选择当前页事件可新建关系。</p><el-alert v-if="orderError" type="error" :title="orderError" :closable="false" /><div class="event-batch"><el-select v-model="beforeId" placeholder="在前事件" aria-label="在前事件"><el-option v-for="row in listing.rows.filter(d => d.event.reviewStatus !== 'excluded')" :key="row.event.id" :value="row.event.id" :label="row.event.title" /></el-select><span>先于（草稿）</span><el-select v-model="afterId" placeholder="在后事件" aria-label="在后事件"><el-option v-for="row in listing.rows.filter(d => d.event.reviewStatus !== 'excluded')" :key="row.event.id" :value="row.event.id" :label="row.event.title" /></el-select><el-button :loading="busy" @click="addOrder">保存先后草稿</el-button></div><article v-for="edge in graph?.precedence" :key="edge.id" class="event-batch"><el-button link @click="openId(edge.beforeId)">{{ title(edge.beforeId) }}</el-button><span>→ 先后草稿 →</span><el-button link @click="openId(edge.afterId)">{{ title(edge.afterId) }}</el-button><span>{{ edge.reason }}</span><el-button :disabled="busy" @click="excludeOrder(edge)">排除此关系（保留审计）</el-button></article><p v-if="graph && !graph.precedence.length">暂无先后草稿。</p></section>
      <div class="event-table"><el-table :data="listing.rows" row-key="event.id" @selection-change="checked = $event" @row-dblclick="open"><el-table-column type="selection" width="48" /><el-table-column label="原始日期与确定性" min-width="240"><template #default="{ row }">{{ eventDate(row.event) }}</template></el-table-column><el-table-column label="事件" min-width="220"><template #default="{ row }"><el-button link @click="open(row)">{{ row.event.title }}</el-button><p>{{ row.event.summary }}</p><span>{{ row.event.eventType }} · {{ row.event.groupLabel || '未分组' }}</span></template></el-table-column><el-table-column label="核对/冲突" min-width="160"><template #default="{ row }"><el-tag>{{ claimStates[row.event.reviewStatus] || row.event.reviewStatus }}</el-tag><el-tag v-if="row.conflict" type="warning">存在矛盾待复核</el-tag><p>{{ claimStates[row.event.sourceStatus] || row.event.sourceStatus }} · {{ row.links.length }}个来源</p></template></el-table-column><el-table-column label="操作" width="210" fixed="right"><template #default="{ row }"><el-button @click="source(row)">原页连续复核</el-button><el-button link @click="open(row)">编辑/全部来源</el-button></template></el-table-column></el-table></div>
      <el-pagination v-model:current-page="filter.pageNum" v-model:page-size="filter.pageSize" :page-sizes="[25, 50, 100]" :total="listing.total" layout="total, sizes, prev, pager, next" @change="refresh()" />
      <AppState v-if="!listing.total && !loading" kind="empty" title="当前筛选下没有事件" description="可新建事件，或从事实候选中选择添加；没有日期的事件也可保存。" />
    </section>
    <AppState v-else-if="!loading" :kind="error ? 'error' : 'empty'" title="时间线尚未载入"><el-button @click="refresh()">重试</el-button></AppState>
    <EventEditor ref="editor" :visible="editorVisible" :case-id="caseId" :event-id="editorId" :materials="files" :merge-items="merging" @close="editorVisible = false" @saved="reviewed" />
  </main>
</template>

<style scoped lang="scss">
.event-pool > .el-button { max-width: 100%; height: auto; white-space: normal; overflow-wrap: anywhere; }
.event-table :deep(.el-button.is-link) { max-width: 100%; height: auto; white-space: normal; text-align: left; overflow-wrap: anywhere; }
.event-workbench { display: flex; flex-direction: column; gap: var(--yl-space-3); }
.event-filters { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: var(--yl-space-2); }
.event-batch { display: flex; flex-wrap: wrap; align-items: center; gap: var(--yl-space-2); margin: var(--yl-space-3) 0; }
.event-batch .el-input, .event-batch .el-select { width: 240px; }
.event-pool { display: flex; flex-wrap: wrap; align-items: center; gap: var(--yl-space-2); }
.event-pool h2, .event-pool p { width: 100%; margin: 0; }
.event-workbench h2 { font-size: var(--yl-title-md); }
.event-table { min-width: 0; overflow: auto; }
.event-order { padding: var(--yl-space-3) 0; border-block: 1px solid var(--yl-color-border); }
</style>
