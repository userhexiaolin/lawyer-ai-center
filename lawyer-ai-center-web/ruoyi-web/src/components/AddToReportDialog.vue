<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores';
import { reportApi } from '@/api/legal-report';
import type { LawStatus, ReportSnapshot, SearchReport } from '@/api/legal-report/types';
import { citationLabel, normalizeCitation } from '@/utils/legalCitation';
import { currentReport, selectReport } from '@/pages/search-reports/helpers';

const props = defineProps<{ reference?: unknown; content?: string; fragmentId?: string; title?: string }>();
const emit = defineEmits<{ close: [] }>();
const user = useUserStore(), router = useRouter();
const reference = computed(() => normalizeCitation(props.reference));
const fragmentId = computed(() => reference.value?.fragmentId || props.fragmentId || '');
const rows = ref<SearchReport[]>([]), page = ref(1), total = ref(0), selected = ref(currentReport(user.token));
const newReport = ref(false), newTitle = ref(''), title = ref(reference.value ? citationLabel(reference.value) : props.title || '');
const content = ref(props.content || ''), busy = ref(false), loading = ref(false), error = ref('');
let epoch = 0, disposed = false;
onScopeDispose(() => { disposed = true; epoch++; });
async function load() {
  const version = ++epoch, token = user.token;
  loading.value = true; error.value = '';
  try {
    const list = await reportApi.list(page.value, 10);
    if (disposed || version !== epoch || token !== user.token) return;
    rows.value = list.rows; total.value = list.total;
    // Keep the current report selectable even when it is on another page.
    if (selected.value && !rows.value.some(row => row.id === selected.value)) {
      try {
        const detail = await reportApi.detail(selected.value);
        if (disposed || version !== epoch || token !== user.token) return;
        rows.value.unshift(detail.report);
      } catch {
        if (disposed || version !== epoch || token !== user.token) return;
        selected.value = '';
      }
    }
    if (!total.value) newReport.value = true;
  } catch (e) { if (!disposed && version === epoch && token === user.token) error.value = (e as Error).message; }
  finally { if (!disposed && version === epoch && token === user.token) loading.value = false; }
}
async function submit() {
  if (busy.value || loading.value) return;
  const citation = reference.value, token = user.token;
  if (!citation && !fragmentId.value) { ElMessage.warning('引用信息不完整'); return; }
  if (newReport.value ? !newTitle.value.trim() : !selected.value) { ElMessage.warning(newReport.value ? '请输入报告标题' : '请选择报告或新建报告'); return; }
  if (!fragmentId.value && (!title.value.trim() || !content.value.trim())) { ElMessage.warning('请补充条目标题和正文快照'); return; }
  busy.value = true; error.value = '';
  const active = () => !disposed && user.token === token;
  try {
    if (newReport.value) {
      const report = await reportApi.create(newTitle.value.trim());
      if (!active()) return;
      rows.value.unshift(report); selected.value = report.id; newReport.value = false;
      selectReport(token, report.id);
    }
    let snapshot: ReportSnapshot;
    if (fragmentId.value) snapshot = { itemType: citation?.type === 'law' ? 'law' : citation?.type === 'case' ? 'case_section' : 'viewpoint', fragmentId: fragmentId.value };
    else {
      if (!citation) throw new Error('引用信息不完整');
      snapshot = { itemType: citation.type === 'law' ? 'law' : 'case_section', snapshotTitle: title.value.trim(), snapshotContent: content.value };
      if (citation.sourceUrl && /^https?:\/\//i.test(citation.sourceUrl)) snapshot.sourceUrl = citation.sourceUrl;
      if (citation.type === 'law') {
        snapshot.lawName = citation.lawName; snapshot.articleNo = citation.articleNo;
        snapshot.lawStatus = (['effective', 'amended', 'repealed'].includes(citation.status || '') ? citation.status : 'unknown') as LawStatus;
      }
    }
    const result = await reportApi.add(selected.value, snapshot);
    if (!active()) return;
    selectReport(token, selected.value);
    ElMessage.success(result.duplicate ? '该法条已在报告中，已保留原有条目' : '已加入检索报告'); emit('close');
  } catch (e) { if (active()) { error.value = (e as Error).message; ElMessage.error(error.value); } }
  finally { if (active()) busy.value = false; }
}
function openReport() { emit('close'); void router.push({ name: 'searchReportWorkbench', params: { id: selected.value } }); }
watch(() => user.token, (_value, old) => { if (old !== undefined) emit('close'); }, { flush: 'sync' });
void load();
</script>

<template>
  <el-dialog :model-value="true" title="加入检索报告" width="min(600px, 94vw)" :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" append-to-body @close="emit('close')">
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-form label-position="top" :disabled="busy" class="add-report-form" @submit.prevent="submit">
      <el-form-item label="收录到">
        <el-radio-group v-model="newReport" :disabled="loading"><el-radio-button :value="false">选择已有报告</el-radio-button><el-radio-button :value="true">新建报告</el-radio-button></el-radio-group>
      </el-form-item>
      <template v-if="!newReport">
        <el-form-item label="我的报告" required>
          <el-select v-model="selected" placeholder="请选择检索报告" filterable :loading="loading" :disabled="loading" style="width:100%">
            <el-option v-for="report in rows" :key="report.id" :value="report.id" :label="report.title"><span>{{ report.title }} · {{ report.status === 'generated' ? '已生成' : '草稿' }}</span></el-option>
          </el-select>
        </el-form-item>
        <el-pagination v-if="total > 10" :current-page="page" :page-size="10" :total="total" :disabled="busy || loading" layout="prev, pager, next" @current-change="value => { page = value; load(); }" />
        <el-button v-if="error" link type="primary" :disabled="busy || loading" @click="load">重新加载报告列表</el-button>
      </template>
      <el-form-item v-else label="新报告标题" required><el-input v-model="newTitle" maxlength="200" show-word-limit /></el-form-item>
      <el-alert v-if="fragmentId" :title="`将收录：${reference ? citationLabel(reference) : title || '知识库片段'}`" description="保存时从知识库获取正文与法条状态，作为报告快照。" type="info" :closable="false" />
      <template v-else>
        <el-alert title="请补充需要收录的正文" description="当前引用没有知识库原文片段，请粘贴原文或填写内容。" type="info" :closable="false" />
        <el-form-item label="条目标题" required><el-input v-model="title" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="正文快照" required><el-input v-model="content" type="textarea" :rows="7" maxlength="100000" show-word-limit /></el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button v-if="selected && !newReport" :disabled="busy || loading" @click="openReport">打开工作台</el-button>
      <el-button :disabled="busy" @click="emit('close')">取消</el-button>
      <el-button type="primary" :loading="busy" :disabled="busy || loading" @click="submit">确认加入</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.add-report-form{margin-top:20px}.add-report-form .el-alert{margin:12px 0 20px}.el-pagination{margin-bottom:16px}
</style>
