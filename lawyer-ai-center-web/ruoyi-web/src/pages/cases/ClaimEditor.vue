<script setup lang="ts">
import type { ClaimDetail, ClaimEdit, ClaimLink, ResolvedSource, SourceAnchor, Suggestion } from '@/api/lawyer/claims';
import type { Material } from '@/api/lawyer/dossier';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onScopeDispose, ref, watch } from 'vue';
import { claimKinds, claimRequest, claimStates, claimValues } from '@/api/lawyer/claims';
import { request } from '@/api/lawyer/dossier';
import AppState from '@/components/AppState.vue';
import DossierAnchorPicker from '@/components/DossierAnchorPicker.vue';
import DossierSourceViewer from '@/components/DossierSourceViewer.vue';
import { useUserStore } from '@/stores';

const props = defineProps<{
  caseId: string;
  claimId?: string;
  visible: boolean;
  materials: Material[];
}>();
const emit = defineEmits<{
  close: [
  ];
  saved: [
  ];
}>();
const user = useUserStore();
const detail = ref<ClaimDetail>();
const form = ref<ClaimEdit>({ expectedVersion: 1, kind: 'excerpt', text: '', values: {}, sources: [], reviewStatus: 'pending_review', reason: '' });
const sources = ref<ResolvedSource[]>([]);
const activeSource = ref('');
const picker = ref(false);
const error = ref('');
const loading = ref(false);
const busy = ref(false);
const conflict = ref<ClaimDetail>();
const splitTexts = ref<string[]>([]);
let baseline = '';
let serial = 0;
let disposed = false;
const currentSource = computed(() => sources.value.find(s => s.anchor.id === activeSource.value));
const canConfirm = computed(() => form.value.sources.some(s => s.relation === 'supports') && form.value.sources.every(link => sources.value.some(source => source.anchor.id === link.anchorId && source.verified)));
function changeDatePrecision(value: unknown) {
  form.value.values.datePrecision = typeof value === 'string' ? value : null;
  if (value !== 'range') {
    form.value.values.dateStart = null;
    form.value.values.dateEnd = null;
  }
}
const dirty = computed(() => baseline !== JSON.stringify(form.value));
function active(epoch: number, token: string | undefined) {
  return !disposed && serial === epoch && token === user.token && props.visible;
}
async function load() {
  const epoch = ++serial;
  const token = user.token;
  busy.value = false;
  detail.value = undefined;
  sources.value = [];
  activeSource.value = '';
  conflict.value = undefined;
  error.value = '';
  picker.value = false;
  splitTexts.value = [];
  form.value = { expectedVersion: 1, kind: 'excerpt', text: '', values: {}, sources: [], reviewStatus: 'pending_review', reason: '' };
  baseline = JSON.stringify(form.value);
  if (!props.visible || !token)
    return;
  if (!props.claimId) {
    picker.value = true;
    return;
  }
  loading.value = true;
  try {
    const result = await claimRequest<ClaimDetail>(props.caseId, `/${props.claimId}`);
    if (!active(epoch, token))
      return;
    detail.value = result;
    sources.value = result.sources;
    activeSource.value = result.sources[0]?.anchor.id || '';
    form.value = { expectedVersion: result.claim.version, kind: result.claim.kind, text: result.claim.claimText, values: claimValues(result.claim), sources: result.links.map(l => ({ ...l })), reviewStatus: result.claim.reviewStatus === 'stale' ? 'pending_review' : result.claim.reviewStatus, reason: '' };
    baseline = JSON.stringify(form.value);
  }
  catch (e) {
    if (active(epoch, token))
      error.value = (e as Error).message;
  }
  finally {
    if (epoch === serial)
      loading.value = false;
  }
}
async function close() {
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('离开将丢弃尚未保存的纠正。已存来源和审计保留。', '未保存内容', { type: 'warning' });
    }
    catch {
      return;
    }
  }
  emit('close');
}
async function added(anchor: SourceAnchor) {
  const epoch = serial;
  const token = user.token;
  try {
    const source = await request<ResolvedSource>(props.caseId, `/anchors/${anchor.id}/resolve`);
    if (!active(epoch, token))
      return;
    if (!form.value.sources.some(s => s.anchorId === anchor.id))
      form.value.sources.push({ anchorId: anchor.id, relation: 'supports' });
    if (!sources.value.some(s => s.anchor.id === anchor.id))
      sources.value.push(source);
    activeSource.value = anchor.id;
    picker.value = false;
  }
  catch (e) {
    if (active(epoch, token))
      error.value = (e as Error).message;
  }
}
function remove(link: ClaimLink) {
  form.value.sources = form.value.sources.filter(l => l !== link);
}
async function save(state: string) {
  const epoch = serial;
  const token = user.token;
  error.value = '';
  busy.value = true;
  try {
    const data = { ...form.value, reviewStatus: props.claimId ? state : 'pending_review' };
    await claimRequest(props.caseId, props.claimId ? `/${props.claimId}` : '', props.claimId ? 'PATCH' : 'POST', data);
    if (!active(epoch, token))
      return;
    baseline = JSON.stringify(form.value);
    ElMessage.success('已保存；对象核对不等于主办终核');
    emit('saved');
    emit('close');
  }
  catch (e) {
    if (active(epoch, token))
      error.value = (e as Error).message;
  }
  finally {
    if (epoch === serial)
      busy.value = false;
  }
}
async function compare() {
  if (!props.claimId)
    return;
  const epoch = serial;
  const token = user.token;
  try {
    const result = await claimRequest<ClaimDetail>(props.caseId, `/${props.claimId}`);
    if (active(epoch, token))
      conflict.value = result;
  }
  catch (e) {
    if (active(epoch, token))
      error.value = (e as Error).message;
  }
}
function useLatestVersion() {
  if (conflict.value) {
    form.value.expectedVersion = conflict.value.claim.version;
    conflict.value = undefined;
    error.value = '';
  }
}
async function decide(suggestion: Suggestion, decision: string) {
  if (!props.claimId)
    return;
  try {
    await ElMessageBox.confirm(decision === 'adopt' ? '将此建议写入当前候选并恢复待核，原人工值留在审计。请先对照来源和差异。' : '将此建议标记不采用，当前候选保持。', '处理重跑建议', { type: 'warning' });
  }
  catch {
    return;
  }
  const epoch = serial;
  const token = user.token;
  busy.value = true;
  try {
    await claimRequest(props.caseId, `/${props.claimId}/suggestions/${suggestion.id}`, 'POST', { expectedVersion: suggestion.version, claimVersion: form.value.expectedVersion, decision, reason: form.value.reason });
    if (active(epoch, token)) {
      emit('saved');
      await load();
    }
  }
  catch (e) {
    if (active(epoch, token))
      error.value = (e as Error).message;
  }
  finally {
    if (epoch === serial)
      busy.value = false;
  }
}
async function split() {
  if (!props.claimId)
    return;
  const epoch = serial;
  const token = user.token;
  busy.value = true;
  try {
    await claimRequest(props.caseId, `/${props.claimId}/split`, 'POST', { expectedVersion: form.value.expectedVersion, reason: form.value.reason, parts: splitTexts.value.map(text => ({ ...form.value, expectedVersion: 1, text, reviewStatus: 'pending_review' })) });
    if (active(epoch, token)) {
      baseline = JSON.stringify(form.value);
      emit('saved');
      emit('close');
    }
  }
  catch (e) {
    if (active(epoch, token))
      error.value = (e as Error).message;
  }
  finally {
    if (epoch === serial)
      busy.value = false;
  }
}
watch(() => [props.caseId, props.claimId, props.visible, user.token], load, { immediate: true });
onScopeDispose(() => {
  disposed = true;
  serial++;
});
</script>

<template>
  <el-drawer :model-value="visible" size="min(1180px, 96vw)" :title="claimId ? '候选与真实来源核对' : '手工摘录（先保存待核）'" :before-close="close">
    <AppState v-if="loading" kind="loading" title="读取候选与来源" />
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
    <el-button v-if="error && claimId" @click="compare">
      对照服务器最新版本（保留输入）
    </el-button>
    <section v-if="conflict" class="claim-conflict">
      <p>服务器v{{ conflict.claim.version }}：{{ conflict.claim.claimText }}</p><p>当前输入v{{ form.expectedVersion }}：{{ form.text }}</p><el-button @click="useLatestVersion">
        已对照差异，使用最新版本号再提交当前输入
      </el-button>
    </section>
    <div v-if="!loading" class="claim-review">
      <section class="claim-source-panel">
        <el-select v-model="activeSource" placeholder="选择已关联来源">
          <el-option v-for="s in sources" :key="s.anchor.id" :value="s.anchor.id" :label="`${s.fileName} · 第${s.anchor.pageIndex}页 · ${s.verified ? '出处有效' : '未验证'}`" />
        </el-select>
        <DossierSourceViewer v-if="currentSource" :case-id="caseId" :source="currentSource" />
        <AppState v-else title="尚无可定位来源" description="关联真实版本与页范围；无来源只能待核，不能确认。" />
        <el-button @click="picker = !picker">
          {{ picker ? '收起来源选择' : '关联另一份材料/页' }}
        </el-button>
        <DossierAnchorPicker v-if="picker" :case-id="caseId" :materials="materials" @added="added" />
      </section>
      <section class="claim-fields">
        <p v-if="detail">
          {{ claimStates[detail.claim.reviewStatus] }} · {{ claimStates[detail.claim.sourceStatus] }} · v{{ detail.claim.version }} · {{ detail.claim.risk === 'high' ? '高风险字段需逐项核对' : '普通候选' }}
        </p>
        <el-form label-position="top">
          <el-form-item label="事实类型">
            <el-select v-model="form.kind">
              <el-option v-for="(text, key) in claimKinds" :key="key" :value="key" :label="text" />
            </el-select>
          </el-form-item>
          <el-form-item label="候选/纠正后的原子化主张">
            <el-input v-model="form.text" type="textarea" :rows="4" :maxlength="3000" show-word-limit />
          </el-form-item>
          <p v-if="detail">
            提取时原文：{{ detail.claim.originalText }}
          </p>
          <el-form-item label="日期原文（不确定时留待核）">
            <el-input v-model="form.values.dateRaw" />
          </el-form-item>
          <el-form-item label="日期确定性（不强填某个确切日）">
            <el-select :model-value="form.values.datePrecision || undefined" placeholder="未标注时按确切日期核对" @update:model-value="changeDatePrecision">
              <el-option label="确切日期" value="exact" /><el-option label="日期范围" value="range" /><el-option label="无法确定，明确保留未知" value="unknown" />
            </el-select>
          </el-form-item>
          <template v-if="form.values.datePrecision === 'range'">
            <el-form-item label="日期范围起日">
              <el-date-picker :model-value="form.values.dateStart || undefined" type="date" value-format="YYYY-MM-DD" @update:model-value="value => form.values.dateStart = typeof value === 'string' ? value : null" />
            </el-form-item><el-form-item label="日期范围止日">
              <el-date-picker :model-value="form.values.dateEnd || undefined" type="date" value-format="YYYY-MM-DD" @update:model-value="value => form.values.dateEnd = typeof value === 'string' ? value : null" />
            </el-form-item>
          </template>
          <el-form-item label="金额原文（精确字符串）">
            <el-input v-model="form.values.amountRaw" />
          </el-form-item>
          <el-form-item label="币种">
            <el-input v-model="form.values.currency" />
          </el-form-item>
          <el-form-item label="姓名/主体">
            <el-input v-model="form.values.name" />
          </el-form-item>
          <div v-for="(link, i) in form.sources" :key="`${link.anchorId}:${i}`" class="claim-link">
            <el-button link @click="activeSource = link.anchorId">
              来源 {{ i + 1 }} · {{ link.anchorId }}
            </el-button><el-select v-model="link.relation">
              <el-option label="支持" value="supports" /><el-option label="矛盾" value="contradicts" /><el-option label="提及" value="mentions" />
            </el-select><el-button @click="remove(link)">
              移除此关联
            </el-button>
          </div>
          <el-form-item label="核对依据/修改理由（至少4字）">
            <el-input v-model="form.reason" type="textarea" :maxlength="500" />
          </el-form-item>
          <div class="claim-actions">
            <el-button :loading="busy" @click="save('pending_review')">
              {{ claimId ? '纠正并保存待核' : '保存待核摘录' }}
            </el-button><el-button v-if="claimId" type="primary" :loading="busy" :disabled="!canConfirm" @click="save('confirmed')">
              确认无误
            </el-button><el-button v-if="claimId" :loading="busy" @click="save('excluded')">
              不采用
            </el-button>
          </div>
        </el-form>
        <el-collapse>
          <el-collapse-item v-if="claimId" title="拆成多条原子事实（原候选保留并排除，子项全部待核）" name="split">
            <el-input v-for="(_, i) in splitTexts" :key="i" v-model="splitTexts[i]" type="textarea" :rows="2" :aria-label="`拆分事实${i + 1}`" /><el-button :disabled="splitTexts.length >= 20" @click="splitTexts.push('')">
              添加子事实
            </el-button><el-button :disabled="splitTexts.length < 2" :loading="busy" @click="split">
              保存拆分
            </el-button>
          </el-collapse-item>
          <el-collapse-item v-if="detail?.suggestions.length" title="重跑差异建议（不会自动覆盖人工值）" name="suggestions">
            <article v-for="s in detail.suggestions" :key="s.id" class="claim-suggestion">
              <p>{{ s.createTime }} · {{ s.state }} · 运行 {{ s.runId }}</p><pre>{{ s.proposedJson }}</pre><template v-if="s.state === 'pending'">
                <el-button :loading="busy" @click="decide(s, 'adopt')">
                  对照后采纳为待核
                </el-button><el-button :loading="busy" @click="decide(s, 'dismiss')">
                  不采用建议
                </el-button>
              </template>
            </article>
          </el-collapse-item>
          <el-collapse-item v-if="detail?.history.length" title="只追加审校记录（敏感数字脱敏展示）" name="history">
            <article v-for="change in detail.history" :key="change.id" class="claim-suggestion">
              <p>{{ change.createTime }} · 操作者 {{ change.operatorId }} · {{ change.action }} · {{ change.reason }}</p><pre>{{ change.beforeJson }}</pre><pre>{{ change.afterJson }}</pre>
            </article>
          </el-collapse-item>
        </el-collapse>
        <el-tooltip content="第89/91条完成后开放，当前不创建无效跳转">
          <div class="claim-actions">
            <el-button disabled>
              加入时间线（即将开放）
            </el-button><el-button disabled>
              加入证据清单（即将开放）
            </el-button>
          </div>
        </el-tooltip>
      </section>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.claim-review {
display: grid;
grid-template-columns: minmax(0, 1.2fr) minmax(320px, 1fr);
gap: var(--yl-space-4);
}
.claim-source-panel, .claim-fields {
min-width: 0;
}
.claim-fields p {
overflow-wrap: anywhere;
}
.claim-link, .claim-actions {
display: flex;
flex-wrap: wrap;
gap: var(--yl-space-2);
margin: var(--yl-space-3) 0;
}
.claim-link .el-select {
width: 110px;
}
.claim-suggestion, .claim-conflict {
padding: var(--yl-space-3);
margin: var(--yl-space-3) 0;
border: 1px solid var(--yl-color-border);
}
.claim-suggestion pre {
max-height: 240px;
overflow: auto;
overflow-wrap: anywhere;
white-space: pre-wrap;
}

@media (width <= 960px) {
.claim-review {
grid-template-columns: minmax(0, 1fr);
}

}
</style>
