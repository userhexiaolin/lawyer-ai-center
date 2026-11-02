<script setup lang="ts">
import type {
  BankDuplicate,
  BankExport,
  BankFile,
  BankListing,
  BankManual,
  BankRow,
  BankUnread,
} from '@/api/lawyer/bank';
import type { ParseJob } from '@/api/lawyer/dossier';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, nextTick, onMounted, onScopeDispose, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import {
  bankQuery,
  bankRequest,
  downloadBank,
  emptyBankFilter,
  flags,
  label,
} from '@/api/lawyer/bank';
import { activeJob, request, statusText } from '@/api/lawyer/dossier';
import { rememberReviewQueue, reviewLink } from '@/api/lawyer/review';
import AppPageHeader from '@/components/AppPageHeader.vue';
import AppState from '@/components/AppState.vue';
import { useUserStore } from '@/stores';
import BankRowEditor from './BankRowEditor.vue';

const route = useRoute();
const router = useRouter();
const user = useUserStore();
const caseTitle = ref('');
const root = ref<HTMLElement>();
let scrollContainer: HTMLElement | undefined;
let restoreTop: number | undefined;
const caseId = computed(() => (typeof route.query.id === 'string' ? route.query.id : ''));
const filters = reactive(emptyBankFilter());
const files = ref<BankFile[]>([]);
const selectedFiles = ref<string[]>([]);
const listing = ref<BankListing>();
const jobs = ref<ParseJob[]>([]);
const unread = ref<BankUnread[]>([]);
const checked = ref<BankRow[]>([]);
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const editor = ref(false);
const selected = ref<BankRow>();
const manual = ref<BankManual>();
const exportVisible = ref(false);
const exportPending = ref(false);
const exportDedupe = ref(false);
const exportFormat = ref('xlsx');
const exported = ref<BankExport[]>([]);
const manualVersion = ref('');
const manualPage = ref(1);
const manualStart = ref(0);
const manualEnd = ref(1);
const manualDialog = ref(false);
let epoch = 0;
let serial = 0;
let disposed = false;
let timer: ReturnType<typeof setTimeout> | undefined;
function key() {
  return user.userInfo?.userId
    ? `bank-view:${user.userInfo.tenantId}:${user.userInfo.userId}:${caseId.value}`
    : '';
}
const stateLabel = (state: string) => statusText[state] || state;
function remember() {
  if (key()) {
    sessionStorage.setItem(
      key(),
      JSON.stringify({ filters, top: scrollContainer?.scrollTop || 0, exports: exported.value.map(v => ({ id: v.id })) }),
    );
  }
}
const pendingJobs = computed(
  () =>
    jobs.value.some(activeJob)
    || exported.value.some(v => !['success', 'failed', 'cancelled'].includes(v.state || 'queued')),
);
function filename(version: string) {
  return (
    files.value.find(f => f.currentVersionId === version)?.displayName || `历史版本 ${version}`
  );
}
async function refresh(quiet = false) {
  if (!caseId.value || !user.token || disposed)
    return;
  const v = epoch;
  const sequence = ++serial;
  const token = user.token;
  if (!quiet)
    loading.value = true;
  const current = () => !disposed && v === epoch && token === user.token && sequence === serial;
  try {
    const [available, rows, work, failures, context, deliveries] = await Promise.all([
      bankRequest<BankFile[]>(caseId.value, '/files'),
      bankRequest<BankListing>(caseId.value, `?${bankQuery(filters)}`),
      request<ParseJob[]>(caseId.value, '/jobs'),
      bankRequest<BankUnread[]>(caseId.value, '/unparsed'),
      request<{ caseTitle: string }>(caseId.value, '/overview'),
      bankRequest<BankExport[]>(caseId.value, '/exports'),
    ]);
    if (!current())
      return;
    caseTitle.value = context.caseTitle;
    files.value = available;
    listing.value = rows;
    jobs.value = work;
    unread.value = failures;
    exported.value = deliveries;
    error.value = '';
    checked.value = [];
    if (restoreTop !== undefined) {
      await nextTick();
      if (current() && scrollContainer) {
        scrollContainer.scrollTop = restoreTop;
        restoreTop = undefined;
      }
    }
  }
  catch (e) {
    if (current()) {
      error.value = (e as Error).message;
      if (/登录|案件不可用|无访问权限/.test(error.value)) {
        files.value = [];
        caseTitle.value = '';
        listing.value = undefined;
        unread.value = [];
        jobs.value = [];
        exported.value = [];
        editor.value = false;
        selected.value = undefined;
        manual.value = undefined;
      }
    }
  }
  finally {
    if (current()) {
      loading.value = false;
      remember();
      schedule();
    }
  }
}
function schedule() {
  clearTimeout(timer);
  if (!disposed && pendingJobs.value)
    timer = setTimeout(() => void refresh(true), 3000);
}
async function run(action: () => Promise<unknown>) {
  if (busy.value)
    return;
  const v = epoch;
  const token = user.token;
  busy.value = true;
  error.value = '';
  try {
    await action();
    if (v === epoch && token === user.token)
      await refresh();
  }
  catch (e) {
    if (v === epoch && token === user.token)
      error.value = (e as Error).message;
  }
  finally {
    if (v === epoch) {
      busy.value = false;
      schedule();
    }
  }
}
function changeFilter() {
  filters.pageNum = 1;
  remember();
  void refresh();
}
async function extract() {
  if (!selectedFiles.value.length)
    return;
  try {
    await ElMessageBox.confirm(
      '所选当前版本将按流水提取；重跑会使已有核验过期，保留人工值和旧审查，新结果作为建议。不会改写原件或未读页状态。',
      '提取流水',
      { confirmButtonText: '开始提取', cancelButtonText: '取消' },
    );
  }
  catch {
    return;
  }
  await run(async () => {
    for (const id of selectedFiles.value) {
      const file = files.value.find(f => f.id === id);
      if (!file?.currentVersionId)
        continue;
      await bankRequest(caseId.value, `/files/${id}/category`, 'POST', { category: 'bank' });
      await bankRequest(caseId.value, '/extract', 'POST', {
        fileVersionId: file.currentVersionId,
        idempotencyKey: crypto.randomUUID(),
      });
    }
  });
}
async function openById(id: string) {
  const current = epoch;
  const token = user.token;
  try {
    const value = await bankRequest<BankRow>(caseId.value, `/${id}`);
    if (current === epoch && token === user.token)
      open(value);
  }
  catch (e) {
    if (current === epoch && token === user.token)
      error.value = (e as Error).message;
  }
}
function open(row: BankRow) {
  remember();
  rememberReviewQueue(String(user.userInfo?.tenantId || ''), String(user.userInfo?.userId || ''), caseId.value, 'bank', (listing.value?.rows || []).filter(r => ['pending_review', 'stale'].includes(r.reviewStatus)).map(r => r.id));
  router.push(reviewLink(caseId.value, 'bank', row.id));
}
function openAdvanced(row: BankRow) {
  selected.value = row;
  manual.value = undefined;
  editor.value = true;
}
function openManual(value: BankManual) {
  selected.value = undefined;
  manual.value = value;
  manualDialog.value = false;
  editor.value = true;
}
async function closeEditor(done?: () => void) {
  try {
    await ElMessageBox.confirm('尚未保存的输入不会写入记录。确认返回列表？', '离开本行核对', {
      confirmButtonText: '返回列表',
      cancelButtonText: '继续核对',
    });
    editor.value = false;
    done?.();
  }
  catch {}
}
async function saved() {
  editor.value = false;
  selected.value = undefined;
  manual.value = undefined;
  ElMessage.success('本行核对已保存，状态以服务器结果为准');
  await refresh();
}
async function duplicate(pair: BankDuplicate) {
  let reason: string;
  try {
    reason = (
      await ElMessageBox.prompt(
        '请输入核对原件后的判定依据（至少4字）。同一笔仅影响人工去重统计，原始行全部保留。',
        '重复判定依据',
        {
          inputValidator: value => value.trim().length >= 4 || '请至少填写4字',
          confirmButtonText: '保存判定',
          cancelButtonText: '取消',
        },
      )
    ).value;
  }
  catch {
    return;
  }
  await run(() =>
    bankRequest(caseId.value, `/duplicates/${pair.id}`, 'POST', {
      expectedVersion: pair.version,
      decision: pair.decision,
      reason,
    }),
  );
}
async function markPending() {
  let reason: string;
  try {
    reason = (
      await ElMessageBox.prompt(
        '仅把选中条目恢复为待核，不会批量确认金额。请填写原因：',
        '批量恢复待核',
        { inputValidator: value => value.trim().length >= 4 || '请至少填写4字' },
      )
    ).value;
  }
  catch {
    return;
  }
  await run(() =>
    bankRequest(caseId.value, '/pending', 'POST', { ids: checked.value.map(v => v.id), reason }),
  );
}
async function startExport() {
  await run(async () => {
    const value = await bankRequest<BankExport>(caseId.value, '/export', 'POST', {
      filter: { ...filters, pageNum: 1 },
      includePending: exportPending.value,
      deduplicate: exportDedupe.value,
      format: exportFormat.value,
      idempotencyKey: crypto.randomUUID(),
    });
    exported.value = [value, ...exported.value].slice(0, 10);
    exportVisible.value = false;
    remember();
  });
}
async function download(value: BankExport) {
  try {
    await downloadBank(caseId.value, value);
  }
  catch (e) {
    error.value = (e as Error).message;
  }
}
async function retryExtract(job: ParseJob) {
  await run(() => bankRequest(caseId.value, '/extract', 'POST', { fileVersionId: job.fileVersionId, idempotencyKey: crypto.randomUUID() }));
}
watch(
  () => [caseId.value, user.token],
  () => {
    epoch++;
    caseTitle.value = '';
    clearTimeout(timer);
    files.value = [];
    listing.value = undefined;
    selectedFiles.value = [];
    jobs.value = [];
    unread.value = [];
    exported.value = [];
    checked.value = [];
    editor.value = false;
    selected.value = undefined;
    manual.value = undefined;
    busy.value = false;
    error.value = '';
    Object.assign(filters, emptyBankFilter());
    try {
      const saved = JSON.parse(sessionStorage.getItem(key()) || '{}');
      restoreTop = Number.isFinite(saved.top) ? Math.max(0, saved.top) : 0;
      if (saved.filters)
        Object.assign(filters, emptyBankFilter(), saved.filters);
      if (Array.isArray(saved.exports))
        exported.value = saved.exports.slice(0, 10);
    }
    catch {}
    void refresh();
  },
  { immediate: true, flush: 'sync' },
);
onBeforeRouteLeave(async () => {
  remember();
  if (!editor.value)
    return true;
  await closeEditor();
  return !editor.value;
});
onMounted(() => {
  scrollContainer = root.value?.closest('.main-scroll') as HTMLElement | undefined;
  scrollContainer?.addEventListener('scroll', remember, { passive: true });
});
onScopeDispose(() => {
  scrollContainer?.removeEventListener('scroll', remember);
  disposed = true;
  epoch++;
  clearTimeout(timer);
});
</script>

<template>
  <main ref="root" class="yl-page bank-workbench">
    <AppPageHeader
      title="银行流水核对"
      description="逐行核对原件、纠正字段与判定重复。仅人工核验后才参与已核汇总，疑似流水不等于真实交易。"
      :eyebrow="caseTitle || `案件 ${caseId}`"
    >
      <template #actions>
        <el-button @click="router.push({ path: '/my-cases/materials', query: { id: caseId } })">
          材料对账
        </el-button><el-button :disabled="busy || !listing?.total" @click="exportVisible = true">
          下载核对结果
        </el-button><el-button type="primary" :disabled="busy || !selectedFiles.length" @click="extract">
          解析所选材料
        </el-button>
      </template>
    </AppPageHeader>
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><AppState
      v-if="loading && !listing"
      kind="loading"
      title="正在读取流水与覆盖范围"
    />
    <section v-if="listing" class="bank-summary" aria-label="仅已核汇总">
      <strong>已核收入 {{ listing.totals.income }}</strong><strong>已核支出 {{ listing.totals.expense }}</strong><strong>差额 {{ listing.totals.net }}</strong><span>待核/过期 {{ listing.totals.pending }} · 不计合计
        {{ listing.totals.excludedFromTotals }}</span>
      <p>{{ listing.totals.method }}</p>
      <p v-for="(sum, currency) in listing.totals.currencies" :key="currency">
        {{ currency }}：收入 {{ sum.income }} · 支出 {{ sum.expense }} · 差额 {{ sum.net }}
      </p>
    </section>
    <section class="bank-files">
      <h2>材料与读取覆盖</h2>
      <p>勾选材料后“解析所选材料”会明确按流水处理。疑似提示不会自动确认分类或交易。</p>
      <el-checkbox-group v-model="selectedFiles">
        <article v-for="file in files" :key="file.id">
          <el-checkbox
            :value="file.id"
            :disabled="
              busy || !file.currentVersionId || !['success', 'partial'].includes(file.parseStatus)
            "
          >
            <span :title="file.displayName">{{ file.displayName }}</span>
          </el-checkbox><span>{{ file.suspected ? "疑似流水 · " : ""
          }}{{ file.category === "bank" ? "按流水处理 · " : "" }}{{ file.parsedPages || 0 }}/{{
            file.pageCount ?? "总页未知"
          }}页 · {{ stateLabel(file.parseStatus || "not_started") }}</span><el-button
            v-if="file.category === 'bank'"
            text
            :disabled="busy"
            @click="
              run(() =>
                bankRequest(caseId, `/files/${file.id}/category`, 'POST', { category: 'ordinary' }),
              )
            "
          >
            改回普通材料
          </el-button>
        </article>
      </el-checkbox-group><AppState
        v-if="!files.length && !loading"
        title="尚无可核对材料"
        description="先在材料对账上传并完成逐页读取。"
      >
        <el-button @click="router.push({ path: '/my-cases/materials', query: { id: caseId } })">
          进入材料对账
        </el-button>
      </AppState>
    </section>
    <form class="bank-filters" @submit.prevent="changeFilter">
      <el-select
        v-model="filters.fileVersionId"
        placeholder="全部来源文件"
        clearable
        @change="changeFilter"
      >
        <el-option
          v-for="file in files.filter((f) => f.currentVersionId)"
          :key="file.id"
          :value="file.currentVersionId!"
          :label="file.displayName"
        />
      </el-select><el-input
        v-model="filters.search"
        placeholder="搜索摘要、交易号、时间"
        clearable
        @clear="changeFilter"
      /><el-input
        v-model="filters.counterparty"
        placeholder="对方户名"
        clearable
        @clear="changeFilter"
      /><el-date-picker
        v-model="filters.from"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="起始日期"
        @change="changeFilter"
      /><el-date-picker
        v-model="filters.to"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="截止日期"
        @change="changeFilter"
      /><el-select
        v-model="filters.direction"
        placeholder="全部方向"
        clearable
        @change="changeFilter"
      >
        <el-option
          v-for="s in ['income', 'expense', 'unknown']"
          :key="s"
          :value="s"
          :label="label(s)"
        />
      </el-select><el-select
        v-model="filters.status"
        placeholder="全部核验状态"
        clearable
        @change="changeFilter"
      >
        <el-option
          v-for="s in ['pending_review', 'confirmed', 'stale', 'cannot_determine', 'rejected']"
          :key="s"
          :value="s"
          :label="label(s)"
        />
      </el-select><el-select v-model="filters.anomaly" placeholder="全部异常" clearable @change="changeFilter">
        <el-option value="any" label="任何异常/未知" /><el-option
          v-for="s in [
            'BALANCE_MISMATCH',
            'DUPLICATE_SUSPECTED',
            'COLUMN_MISALIGNMENT',
            'DIRECTION_CONFLICT',
            'DATE_OUT_OF_RANGE',
            'CURRENCY_MISMATCH',
          ]"
          :key="s"
          :value="s"
          :label="label(s)"
        />
      </el-select><el-button native-type="submit" :loading="loading">
        查询
      </el-button><el-button :disabled="busy" @click="manualDialog = true">
        手工补录
      </el-button><el-button :disabled="busy || !checked.length" @click="markPending">
        所选恢复待核
      </el-button>
    </form>
    <el-table
      v-loading="loading"
      :data="listing?.rows || []"
      row-key="id"
      empty-text="当前筛选没有流水，可提取已有材料或补录待核行"
      @selection-change="(rows: BankRow[]) => (checked = rows)"
    >
      <el-table-column type="selection" width="42" /><el-table-column
        label="交易时间"
        min-width="155"
      >
        <template #default="{ row }">
          {{ row.tradeTimeRaw || "时间未解析" }}
        </template>
      </el-table-column><el-table-column label="对方（脱敏）" min-width="140">
        <template #default="{ row }">
          {{ row.counterparty || "未知" }}
          <p>{{ row.counterAccount || "账户未知" }}</p>
        </template>
      </el-table-column><el-table-column label="收支" width="70">
        <template #default="{ row }">
          {{ label(row.direction) }}
        </template>
      </el-table-column><el-table-column label="金额" min-width="135" align="right">
        <template #default="{ row }">
          {{ row.amount ?? "未知" }} {{ row.currency || "" }}
        </template>
      </el-table-column><el-table-column label="余额" min-width="130" align="right">
        <template #default="{ row }">
          {{ row.balance ?? "未知" }}
        </template>
      </el-table-column><el-table-column label="来源" min-width="175">
        <template #default="{ row }">
          <el-button link @click="open(row)">
            {{ filename(row.fileVersionId) }} · P{{ row.pageIndex }} · 行{{
              row.tableRow || "手工"
            }}
          </el-button>
        </template>
      </el-table-column><el-table-column label="状态 / 异常" min-width="190">
        <template #default="{ row }">
          <el-tag :type="row.reviewStatus === 'confirmed' ? 'success' : 'warning'">
            {{ label(row.reviewStatus) }}
          </el-tag>
          <p>{{ [...flags(row.anomalyJson), ...flags(row.reasonsJson)].map(label).join("、") }}</p>
        </template>
      </el-table-column><el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link :disabled="busy" @click="open(row)">
            连续原页复核
          </el-button>
          <el-button link :disabled="busy" @click="openAdvanced(row)">
            拆分/更多核对
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="filters.pageNum"
      :page-size="filters.pageSize"
      :total="listing?.total || 0"
      layout="total,prev,pager,next"
      @current-change="refresh()"
    />
    <el-collapse>
      <el-collapse-item :title="`疑似重复与人工判定（${listing?.duplicates.length || 0}组）`">
        <article v-for="pair in listing?.duplicates" :key="pair.id" class="bank-pair">
          <el-button text @click="openById(pair.leftRowId)">
            原页 {{ pair.leftRowId }}
          </el-button><span>↔</span><el-button text @click="openById(pair.rightRowId)">
            原页 {{ pair.rightRowId }}
          </el-button><el-select v-model="pair.decision" :disabled="busy">
            <el-option
              v-for="s in ['pending', 'same', 'different']"
              :key="s"
              :value="s"
              :label="label(s)"
            />
          </el-select><el-button :disabled="busy" @click="duplicate(pair)">
            保存人工判定
          </el-button>
          <p>{{ pair.reason }}</p>
        </article>
      </el-collapse-item><el-collapse-item title="未解析行 / 覆盖缺口 / 任务">
        <article v-for="runItem in listing?.runs" :key="runItem.id">
          <p>
            {{ filename(runItem.fileVersionId) }} · 任务{{ runItem.jobId }} ·
            {{ stateLabel(runItem.state) }} · {{ runItem.readablePages }}/{{
              runItem.totalPages ?? "总页未知"
            }}页 · {{ runItem.rowCount }}行
          </p>
          <p class="bank-warning">
            {{ runItem.errorMessage }}
          </p>
        </article>
        <article v-for="page in unread" :key="`${page.fileVersionId}:${page.pageIndex}`">
          <p>{{ filename(page.fileVersionId) }} · P{{ page.pageIndex }} · {{ page.rowCount }}行</p>
          <template v-if="page.lines?.length">
            <p v-for="line in page.lines" :key="line.start">
              <span>{{ line.text }}</span><el-button
                text
                @click="
                  openManual({
                    fileVersionId: page.fileVersionId,
                    pageIndex: page.pageIndex,
                    start: line.start,
                    end: line.end,
                  })
                "
              >
                定位并手工补录
              </el-button>
            </p>
          </template>
          <pre v-else>{{ page.unparsedJson }}</pre>
          <p>{{ page.warningsJson }}</p>
        </article>
        <p
          v-for="job in jobs.filter((job) => !job.jobType || job.jobType.startsWith('bank_'))"
          :key="job.id"
        >
          任务 {{ job.id }} · {{ stateLabel(job.state) }} · {{ job.progressDone }}/{{
            job.progressTotal ?? "未知"
          }}
          <span class="bank-warning">{{ job.errorMessage }}</span>
          <el-button
            v-if="job.jobType === 'bank_extract' && ['failed', 'cancelled'].includes(job.state)"
            text
            :disabled="busy"
            @click="retryExtract(job)"
          >
            恢复流水提取
          </el-button>
          <el-button
            v-if="activeJob(job)"
            text
            @click="run(() => request(caseId, `/jobs/${job.id}/cancel`, 'POST'))"
          >
            取消未完成部分
          </el-button>
        </p>
      </el-collapse-item>
    </el-collapse>
    <section v-if="exported.length" aria-label="导出任务">
      <h2>固定快照导出</h2>
      <article v-for="value in exported" :key="value.id">
        <span>{{ value.rowCount }}行 · 含待核{{ value.pendingCount }}行 ·
          {{ stateLabel(value.state || "queued") }} · {{ value.snapshotHash }}</span><el-button :disabled="value.state !== 'success'" @click="download(value)">
            下载 {{ value.format }}
          </el-button><el-button
          v-if="['failed', 'cancelled'].includes(value.state || '')"
          :disabled="busy"
          @click="run(() => bankRequest(caseId, `/exports/${value.id}/retry`, 'POST'))"
        >
          恢复同一快照
        </el-button>
        <p class="bank-warning">
          {{ value.errorMessage }}
        </p>
      </article>
    </section>
    <el-drawer
      v-model="editor"
      size="min(1320px,98vw)"
      title="流水原件对照与逐行核验"
      :before-close="closeEditor"
      destroy-on-close
    >
      <BankRowEditor
        v-if="editor"
        :case-id="caseId"
        :row="selected"
        :manual="manual"
        @saved="saved"
        @close="closeEditor()"
      />
    </el-drawer>
    <el-dialog v-model="manualDialog" title="选择手工补录来源" width="min(560px,96vw)">
      <p>
        可从“未解析行”直接定位；已读页需提供真实片段的字符起止位置。未读页只允许暂存待核，不能确认。
      </p>
      <el-form label-position="top">
        <el-form-item label="当前文件版本">
          <el-select v-model="manualVersion">
            <el-option
              v-for="file in files.filter((f) => f.currentVersionId)"
              :key="file.id"
              :value="file.currentVersionId!"
              :label="file.displayName"
            />
          </el-select>
        </el-form-item><el-form-item label="物理/系统页">
          <el-input-number v-model="manualPage" :min="1" :max="200" />
        </el-form-item><el-form-item label="片段起点 / 终点（未读页可保持默认）">
          <el-input-number v-model="manualStart" :min="0" /><el-input-number
            v-model="manualEnd"
            :min="1"
          />
        </el-form-item>
      </el-form><el-button
        type="primary"
        :disabled="!manualVersion"
        @click="
          openManual({
            fileVersionId: manualVersion,
            pageIndex: manualPage,
            start: manualStart,
            end: manualEnd,
          })
        "
      >
        打开原页并补录
      </el-button>
    </el-dialog>
    <el-dialog v-model="exportVisible" title="导出流水核对快照" width="min(600px,96vw)">
      <p>
        当前筛选共 {{ listing?.total || 0 }} 行，其中
        {{ listing?.totals.pending || 0 }}
        行待核/过期。读取覆盖与未解析行会写入说明；不是银行出具的认证证明。
      </p>
      <el-checkbox v-model="exportPending">
        含待核/过期行（各行显著标记；不计已核合计）
      </el-checkbox><el-checkbox v-model="exportDedupe">
        仅按人工“同一笔”组去重展示（来源仍保留全部原行）
      </el-checkbox><el-radio-group v-model="exportFormat">
        <el-radio value="xlsx">
          Excel：明细、来源、异常、说明
        </el-radio><el-radio value="csv">
          CSV：逐行状态与来源编号
        </el-radio>
      </el-radio-group>
      <p>默认只导出已核行。快照固定后，后续编辑不会改写已生成文件。</p>
      <el-button type="primary" :disabled="busy" @click="startExport">
        创建导出任务
      </el-button>
    </el-dialog>
  </main>
</template>

<style scoped>
.bank-workbench {
  max-width: 1800px;
  margin: auto;
}
.bank-summary {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yl-space-5);
  padding: var(--yl-space-4);
  background: var(--yl-color-surface);
  border: 1px solid var(--yl-color-border);
  border-radius: var(--yl-radius-panel);
}
.bank-summary p {
  width: 100%;
  margin: 0;
  color: var(--yl-color-text-muted);
}
.bank-files {
  margin: var(--yl-space-5) 0;
}
.bank-files article {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yl-space-3);
  align-items: center;
}
.bank-filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yl-space-3);
  margin: var(--yl-space-4) 0;
}
.bank-filters > .el-input,
.bank-filters > .el-select {
  width: 190px;
}
.bank-workbench h2 {
  font-size: var(--yl-title-md);
}
.bank-workbench .el-pagination {
  margin: var(--yl-space-4) 0;
}
.bank-pair {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yl-space-3);
  align-items: center;
  margin-bottom: var(--yl-space-4);
}
.bank-pair .el-select {
  width: 200px;
}
.bank-pair p {
  width: 100%;
  color: var(--yl-color-text-muted);
}
.bank-warning {
  color: var(--el-color-danger);
}
.bank-workbench pre {
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.bank-workbench .el-checkbox {
  height: auto;
  min-height: 32px;
  white-space: normal;
}

@media (width <= 1000px) {
  .bank-filters > * {
    flex: 1 1 160px;
  }
  .bank-files article {
    padding: var(--yl-space-3) 0;
  }
}
</style>
