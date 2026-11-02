<script setup lang="ts">
import type { ResolvedSource, SourceAnchor } from '@/api/lawyer/claims';
import type { Material } from '@/api/lawyer/dossier';
import type { DateKind, EventDetail, EventEdit } from '@/api/lawyer/events';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onScopeDispose, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { claimStates } from '@/api/lawyer/claims';
import { request } from '@/api/lawyer/dossier';
import { dateKinds, eventDate, eventEdit, eventRequest, newEvent } from '@/api/lawyer/events';
import { reviewLink } from '@/api/lawyer/review';
import AppState from '@/components/AppState.vue';
import DossierAnchorPicker from '@/components/DossierAnchorPicker.vue';
import DossierSourceViewer from '@/components/DossierSourceViewer.vue';
import { useUserStore } from '@/stores';

const props = defineProps<{ visible: boolean; caseId: string; eventId?: string; materials: Material[]; mergeItems?: EventDetail[] }>();
const emit = defineEmits<{ close: []; saved: [id: string] }>();
const user = useUserStore();
const router = useRouter();
const detail = ref<EventDetail>();
const latest = ref<EventDetail>();
const form = ref<EventEdit>(newEvent());
const sources = ref<ResolvedSource[]>([]);
const sourceId = ref('');
const participantText = ref('');
const picker = ref(false);
const splitText = ref('');
const loading = ref(false);
const busy = ref(false);
const error = ref('');
let baseline = '';
let serial = 0;
let disposed = false;
const source = computed(() => sources.value.find(value => value.anchor.id === sourceId.value));
const dirty = computed(() => baseline !== JSON.stringify({ form: form.value, participants: participantText.value }) || !!splitText.value.trim());
const canConfirm = computed(() => form.value.sources.some(link => link.relation === 'supports') && form.value.sources.every(link => sources.value.some(s => s.anchor.id === link.anchorId && s.verified)));
function active(run: number, token: string | undefined) { return !disposed && props.visible && run === serial && token === user.token; }
function accept(value: EventDetail) { detail.value = value; form.value = eventEdit(value); participantText.value = form.value.participants.map(p => p.label).join(';'); sources.value = value.sources; sourceId.value = value.sources[0]?.anchor.id || ''; latest.value = undefined; baseline = JSON.stringify({ form: form.value, participants: participantText.value }); }
async function load() {
  const run = ++serial;
  const token = user.token;
  detail.value = undefined; latest.value = undefined; sources.value = []; sourceId.value = ''; form.value = newEvent(); participantText.value = ''; splitText.value = ''; error.value = ''; busy.value = false; picker.value = false;
  baseline = JSON.stringify({ form: form.value, participants: participantText.value });
  if (!props.visible || !token) return;
  loading.value = true;
  try {
    if (props.eventId) { const result = await eventRequest<EventDetail>(props.caseId, `/${props.eventId}`); if (active(run, token)) accept(result); }
    else if (props.mergeItems?.length) {
      form.value.title = props.mergeItems.map(d => d.event.title).join('；').slice(0, 300);
      form.value.summary = props.mergeItems.map(d => d.event.summary).join('\n').slice(0, 5000);
      const refs = new Map(props.mergeItems.flatMap(d => d.links).map(link => [`${link.anchorId}:${link.relation}`, { ...link }]));
      form.value.sources = [...refs.values()];
      sources.value = [...new Map(props.mergeItems.flatMap(d => d.sources).map(s => [s.anchor.id, s])).values()];
      sourceId.value = sources.value[0]?.anchor.id || '';
      baseline = JSON.stringify({ form: form.value, participants: participantText.value });
    }
  }
  catch (e) { if (active(run, token)) error.value = (e as Error).message; }
  finally { if (active(run, token)) loading.value = false; }
}
async function canLeave() { if (busy.value) return false; if (!dirty.value) return true; try { await ElMessageBox.confirm('事件有未保存输入，离开会丢弃本次输入；已保存事件和历史保留。', '未保存事件', { type: 'warning' }); return true; } catch { return false; } }
async function close() { if (await canLeave()) emit('close'); }
function changeKind(value: unknown) { if (typeof value !== 'string' || !(value in dateKinds)) return; form.value.dateKind = value as DateKind; if (['unknown', 'relative'].includes(value)) { form.value.dateStart = null; form.value.dateEnd = null; } }
function payload(state: string): EventEdit { return { ...form.value, participants: participantText.value.split(/[;；]/).map(label => label.trim()).filter(Boolean).map(label => ({ subjectId: null, label })), sources: form.value.sources.map(link => ({ ...link })), reviewStatus: state }; }
async function save(state: string) {
  if (busy.value) return;
  if (form.value.reason.trim().length < 4) { error.value = '请填写至少4字的核对依据/理由'; return; }
  if (state === 'confirmed' && !canConfirm.value) { error.value = '需先关联并定位全部有效来源，至少一项为支持来源'; return; }
  if (state === 'excluded') { try { await ElMessageBox.confirm('此事件将从当前时序图移出；原事件、来源和历史保留，时间线修订号变化会使依赖它的终核过期。', '不采用事件', { type: 'warning' }); } catch { return; } }
  const run = serial;
  const token = user.token;
  busy.value = true; error.value = '';
  try {
    const value = payload(state);
    let result: EventDetail;
    if (props.mergeItems?.length) {
      if (state !== 'pending_review') throw new Error('合并结果必须重新核对，请先保存为待核事件');
      await ElMessageBox.confirm('合并后原事件保留为已排除历史，全部原来源进入新事件。新事件仍待核，旧终核不能自动沿用。', '合并事件', { type: 'warning' });
      result = await eventRequest<EventDetail>(props.caseId, '/merge', 'POST', { items: props.mergeItems.map(d => ({ id: d.event.id, version: d.event.version })), result: value, reason: value.reason });
    }
    else result = await eventRequest<EventDetail>(props.caseId, props.eventId ? `/${props.eventId}` : '', props.eventId ? 'PATCH' : 'POST', value);
    if (!active(run, token)) return;
    accept(result); ElMessage.success('事件已保存，对象核对不等于主办终核'); emit('saved', result.event.id); emit('close');
  }
  catch (e) { if (active(run, token)) error.value = e instanceof Error ? e.message : '操作已取消，输入保留'; }
  finally { if (active(run, token)) busy.value = false; }
}
async function compare() { if (!props.eventId) return; const run = serial; const token = user.token; try { const value = await eventRequest<EventDetail>(props.caseId, `/${props.eventId}`); if (active(run, token)) latest.value = value; } catch (e) { if (active(run, token)) error.value = (e as Error).message; } }
function useLatestVersion() { if (latest.value) { form.value.expectedVersion = latest.value.event.version; latest.value = undefined; error.value = ''; } }
async function useLatestValues() { if (latest.value && await canLeave()) accept(latest.value); }
async function added(anchor: SourceAnchor) { const run = serial; const token = user.token; try { const value = await request<ResolvedSource>(props.caseId, `/anchors/${anchor.id}/resolve`); if (!active(run, token)) return; if (!sources.value.some(s => s.anchor.id === anchor.id)) sources.value.push(value); if (!form.value.sources.some(link => link.anchorId === anchor.id)) form.value.sources.push({ anchorId: anchor.id, relation: 'supports' }); sourceId.value = anchor.id; picker.value = false; } catch (e) { if (active(run, token)) error.value = (e as Error).message; } }
async function split() {
  if (!props.eventId || busy.value) return;
  const titles = splitText.value.split('\n').map(text => text.trim()).filter(Boolean);
  if (titles.length < 2 || titles.length > 20 || titles.some(text => text.length > 300) || form.value.reason.trim().length < 4) { error.value = '请填写2～20行独立事件标题和至少4字理由'; return; }
  try { await ElMessageBox.confirm('原事件将保留为已排除历史。每个新事件保留当前字段和全部关联来源，仍需逐条核对，不继承原确认。', '拆分事件', { type: 'warning' }); } catch { return; }
  const run = serial;
  const token = user.token;
  busy.value = true; error.value = '';
  try { const value = payload('pending_review'); const result = await eventRequest<EventDetail[]>(props.caseId, `/${props.eventId}/split`, 'POST', { expectedVersion: value.expectedVersion, parts: titles.map(title => ({ ...value, title, expectedVersion: 1 })), reason: value.reason }); if (active(run, token)) { emit('saved', result[0].event.id); emit('close'); } }
  catch (e) { if (active(run, token)) error.value = (e as Error).message; }
  finally { if (active(run, token)) busy.value = false; }
}
async function reviewSource() { if (props.eventId && source.value && await canLeave()) { emit('close'); await router.push(reviewLink(props.caseId, 'event', props.eventId, source.value)); } }
watch(() => [props.visible, props.caseId, props.eventId, user.token], load, { immediate: true });
onScopeDispose(() => { disposed = true; serial++; });
defineExpose({ canLeave });
</script>

<template>
  <el-drawer :model-value="visible" :title="mergeItems?.length ? '合并为新的待核事件' : eventId ? '事件详情与结构化核对' : '新建事件'" size="min(1160px, 96vw)" :before-close="close" destroy-on-close>
    <AppState v-if="loading" kind="loading" title="正在读取事件及全部来源" />
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
    <el-button v-if="error && eventId" @click="compare">对照最新事件（保留输入）</el-button>
    <section v-if="latest" class="yl-panel event-conflict"><p>服务器最新 v{{ latest.event.version }}：{{ latest.event.title }} · {{ eventDate(latest.event) }} · {{ claimStates[latest.event.reviewStatus] }}</p><p>{{ latest.event.summary }}</p><el-button @click="useLatestVersion">保留输入，明确使用此版本号再提交</el-button><el-button @click="useLatestValues">丢弃输入并加载服务器内容</el-button></section>
    <div v-if="!loading && (!eventId || detail)" class="event-editor">
      <section class="event-original" aria-label="事件来源原页"><el-select v-model="sourceId" aria-label="选择事件来源" placeholder="选择来源原页"><el-option v-for="s in sources" :key="s.anchor.id" :value="s.anchor.id" :label="`${s.fileName} · P${s.anchor.pageIndex} · ${s.verified ? '出处有效' : '待核'}`" /></el-select><DossierSourceViewer v-if="source" :case-id="caseId" :source="source" /><AppState v-else title="暂无可定位来源" description="可先保存待核；没有有效支持来源不能确认事件。" /><el-button v-if="eventId && source" @click="reviewSource">统一原页连续复核</el-button></section>
      <section class="event-fields"><p v-if="detail">{{ claimStates[detail.event.reviewStatus] }} · v{{ detail.event.version }} · 时间线修订{{ detail.revision }} · {{ detail.event.origin }}</p><el-alert v-if="detail?.conflict" type="warning" title="存在来源/日期矛盾，请逐份对照，不能把整理状态当作事实已终核。" :closable="false" />
        <el-form label-position="top" @submit.prevent="save('pending_review')">
          <el-form-item label="事件标题"><el-input v-model="form.title" :maxlength="300" show-word-limit /></el-form-item>
          <el-form-item label="简述（原始材料与判断需区分）"><el-input v-model="form.summary" type="textarea" :rows="4" :maxlength="5000" show-word-limit /></el-form-item>
          <el-form-item label="事件类型"><el-input v-model="form.eventType" :maxlength="50" placeholder="例如：签约、付款、交付、通知、fact" /></el-form-item>
          <el-form-item label="参与主体（分号分隔，名称不等于已确认同一实体）"><el-input v-model="participantText" /></el-form-item>
          <el-form-item label="日期确定性"><el-select :model-value="form.dateKind" @update:model-value="changeKind"><el-option v-for="(label, key) in dateKinds" :key="key" :label="label" :value="key" /></el-select></el-form-item>
          <el-form-item label="原始时间表述（始终保留，不猜测）"><el-input v-model="form.dateRaw" :maxlength="500" placeholder="例如春节前后、同年四季度、某日" /></el-form-item>
          <template v-if="!['unknown', 'relative'].includes(form.dateKind)"><el-form-item label="起始日期（约略可填YYYY或YYYY-MM，不强填某天）"><el-input :model-value="form.dateStart || ''" @update:model-value="value => form.dateStart = value || null" placeholder="YYYY / YYYY-MM / YYYY-MM-DD" /></el-form-item><el-form-item label="结束日期（区间必填完整日，其他情况可空）"><el-input :model-value="form.dateEnd || ''" @update:model-value="value => form.dateEnd = value || null" placeholder="YYYY-MM-DD" /></el-form-item></template>
          <p v-else>绝对日期留空，事件进入固定“日期待核”池；相对先后草稿不会替代真实时间。</p>
          <el-form-item label="时区（可空）"><el-input :model-value="form.timezone || ''" @update:model-value="value => form.timezone = value || null" placeholder="Asia/Shanghai" /></el-form-item>
          <el-form-item label="事件分组"><el-input v-model="form.groupLabel" :maxlength="100" /></el-form-item><el-form-item label="同日人工排序（不代替真实日期）"><el-input-number v-model="form.sortOrder" :precision="0" :min="-1000000" :max="1000000" /></el-form-item>
          <div v-for="(link, index) in form.sources" :key="`${link.anchorId}:${link.relation}`" class="event-links"><el-button link @click="sourceId = link.anchorId">来源{{ index + 1 }}</el-button><el-select v-model="link.relation" aria-label="来源关联关系"><el-option label="支持" value="supports" /><el-option label="矛盾" value="contradicts" /><el-option label="提及" value="mentions" /></el-select><el-button @click="form.sources.splice(index, 1)">移除关联</el-button></div>
          <el-button @click="picker = !picker">{{ picker ? '收起' : '关联其他原页/片段' }}</el-button><DossierAnchorPicker v-if="picker" :case-id="caseId" :materials="materials" @added="added" />
          <el-form-item label="核对依据/操作理由（至少4字）"><el-input v-model="form.reason" type="textarea" :maxlength="500" /></el-form-item>
          <div class="event-actions"><el-button type="primary" :loading="busy" @click="save('pending_review')">{{ mergeItems?.length ? '合并并保存待核事件' : '保存为待核' }}</el-button><el-button v-if="!mergeItems?.length" :loading="busy" :disabled="!canConfirm" @click="save('confirmed')">确认无误</el-button><el-button v-if="eventId" :loading="busy" @click="save('excluded')">不采用（保留历史）</el-button><el-button :disabled="busy" @click="close">关闭</el-button></div><p v-if="dirty" role="status">有未保存输入；保存失败会保留输入。</p>
        </el-form>
        <el-collapse v-if="eventId"><el-collapse-item title="拆分为独立待核事件" name="split"><p>每行一个标题；当前字段和全部来源保留到各新事件，再逐条编辑日期/主体。</p><el-input v-model="splitText" type="textarea" :rows="4" placeholder="事件一标题&#10;事件二标题" /><el-button :loading="busy" @click="split">拆分并保留原事件历史</el-button></el-collapse-item><el-collapse-item title="字段级审校历史" name="history"><article v-for="history in detail?.history" :key="history.id" class="event-history"><p>{{ history.createTime }} · 操作者{{ history.operatorId }} · {{ history.action }} · {{ history.reason }}</p><p class="event-fingerprint">{{ history.beforeFingerprint }} → {{ history.afterFingerprint }}</p><el-table :data="history.fields" size="small"><el-table-column prop="field" label="字段" min-width="120" /><el-table-column prop="oldValue" label="旧值" min-width="120" /><el-table-column prop="newValue" label="新值" min-width="120" /><el-table-column label="受控引用" min-width="160"><template #default="{ row }"><span v-if="row.sensitive">{{ row.protectedRef }}</span></template></el-table-column></el-table></article></el-collapse-item></el-collapse>
      </section>
    </div>
    <AppState v-else-if="!loading && !detail" kind="error" title="事件不可用" description="无法读取时不会显示其他案件内容。"><el-button @click="load">重试</el-button></AppState>
  </el-drawer>
</template>

<style scoped lang="scss">
.event-original { position: sticky; top: 0; align-self: start; }
@media (max-width: 960px) { .event-original { position: static; } }
.event-editor { display: grid; grid-template-columns: minmax(0, 1fr) minmax(320px, 1fr); gap: var(--yl-space-4); }
.event-original, .event-fields { min-width: 0; }
.event-links, .event-actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--yl-space-2); margin: var(--yl-space-3) 0; }
.event-links .el-select { width: 100px; }
.event-history { padding: var(--yl-space-3) 0; border-bottom: 1px solid var(--yl-color-border); }
.event-fingerprint { overflow-wrap: anywhere; font-size: var(--yl-text-sm); }
.event-conflict { margin: var(--yl-space-3) 0; }
@media (max-width: 960px) { .event-editor { grid-template-columns: minmax(0, 1fr); } }
</style>
