<script setup lang="ts">
import type {
  FileVersion,
  Material,
  MaterialPage,
  Overview,
  ParseJob,
  UploadTicket,
} from '@/api/lawyer/dossier';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, nextTick, onMounted, onScopeDispose, ref, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import {
  activeJob,
  authenticatedBlob,
  coverage,
  downloadVersion,
  fingerprint,
  request,
  statusText,
  uploadContent,
} from '@/api/lawyer/dossier';
import AppPageHeader from '@/components/AppPageHeader.vue';
import AppState from '@/components/AppState.vue';
import DossierPageThumbnail from '@/components/DossierPageThumbnail.vue';
import { useScreenStore } from '@/hooks/useScreen';
import { useUserStore } from '@/stores';

const route = useRoute();
const router = useRouter();
const user = useUserStore();
const { isMobile } = useScreenStore();
const caseId = computed(() => (typeof route.query.id === 'string' ? route.query.id : ''));
const status = ref('');
const search = ref('');
const page = ref(1);
const root = ref<HTMLElement>();
let scrollContainer: HTMLElement | undefined, restoreTop: number | undefined;
function viewKey() {
  return user.userInfo?.userId
    ? `dossier-view:${user.userInfo.tenantId}:${user.userInfo.userId}:${caseId.value}`
    : '';
}
function saveView() {
  const key = viewKey();
  if (key) {
    sessionStorage.setItem(
      key,
      JSON.stringify({
        status: status.value,
        search: search.value,
        page: page.value,
        top: scrollContainer?.scrollTop || 0,
      }),
    );
  }
}
function restoreView() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(viewKey()) || '{}');
    status.value = typeof saved.status === 'string' ? saved.status : '';
    search.value = typeof saved.search === 'string' ? saved.search : '';
    page.value = Number.isInteger(saved.page) && saved.page > 0 ? saved.page : 1;
    restoreTop = Number.isFinite(saved.top) ? Math.max(0, saved.top) : 0;
  }
  catch {
    restoreTop = 0;
  }
}

const overview = ref<Overview>();
const materials = ref<Material[]>([]);
const total = ref(0);
const jobs = ref<ParseJob[]>([]);

const loading = ref(false);
const busy = ref(false);
const error = ref('');
const input = ref<HTMLInputElement>();
const target = ref<string>();
const uploads = ref<
  {
    name: string;
    progress: number;
    state: string;
    error: string;
    file: File;
    key: string;
    materialId?: string;
  }[]
>([]);
const selected = ref<FileVersion>();
const history = ref<FileVersion[]>([]);
const pages = ref<MaterialPage[]>([]);
const checked = ref<number[]>([]);
const pagesVisible = ref(false);
const failedOnly = ref(false);
const previewScale = ref(1);
const imageUrl = ref('');
const imageError = ref('');
const imageLoading = ref(false);
const currentPage = ref(1);
let epoch = 0;
let disposed = false;
let timer: ReturnType<typeof setTimeout> | undefined;
let controller: AbortController | undefined;
let previewEpoch = 0;
let refreshSerial = 0;
let detailSerial = 0;
const visiblePages = computed(() =>
  failedOnly.value ? pages.value.filter(p => p.extractStatus !== 'success') : pages.value,
);
const hasRunning = computed(() => jobs.value.some(activeJob));
const stateLabel = (s: string) => statusText[s] || s;
const newKey = () => crypto.randomUUID().replace(/-/g, '');
function active(v: number, token: string | undefined) {
  return !disposed && epoch === v && user.token === token;
}
function clearImage() {
  previewEpoch++;
  if (imageUrl.value)
    URL.revokeObjectURL(imageUrl.value);
  imageUrl.value = '';
  imageError.value = '';
}
async function refresh(quiet = false) {
  if (!caseId.value || !user.token || disposed)
    return;
  const v = epoch;
  const owner = user.token;
  const serial = ++refreshSerial;
  const current = () => active(v, owner) && serial === refreshSerial;
  if (!quiet)
    loading.value = true;
  try {
    const query = new URLSearchParams({
      search: search.value,
      status: status.value,
      pageNum: String(page.value),
      pageSize: '20',
    });
    const [summary, list, running] = await Promise.all([
      request<Overview>(caseId.value, '/overview'),
      request<{ rows: Material[]; total: number }>(caseId.value, `/materials?${query}`),
      request<ParseJob[]>(caseId.value, '/jobs'),
    ]);
    if (!current())
      return;
    overview.value = summary;
    materials.value = list.rows;
    total.value = list.total;
    jobs.value = running;
    error.value = '';
    if (restoreTop !== undefined) {
      await nextTick();
      scrollContainer?.scrollTo({ top: restoreTop });
      restoreTop = undefined;
    }
    if (pagesVisible.value && selected.value) {
      const versionId = selected.value.id;
      const all = await request<FileVersion[]>(
        caseId.value,
        `/materials/${selected.value.materialId}/versions`,
      );
      if (!current() || selected.value?.id !== versionId)
        return;
      history.value = all;
      selected.value = all.find(x => x.id === versionId);
      if (selected.value) {
        const rows = await request<MaterialPage[]>(caseId.value, `/files/${versionId}/pages`);
        if (current() && selected.value?.id === versionId)
          pages.value = rows;
      }
    }
  }
  catch (e) {
    if (current()) {
      error.value = (e as Error).message;
      if (/无访问权限|案件不可用|登录/.test(error.value)) {
        overview.value = undefined;
        materials.value = [];
        jobs.value = [];
        pages.value = [];
        pagesVisible.value = false;
        clearImage();
      }
    }
  }
  finally {
    if (current()) {
      loading.value = false;
      schedule();
    }
  }
}
function schedule() {
  clearTimeout(timer);
  if (!disposed && (hasRunning.value || busy.value))
    timer = setTimeout(() => void refresh(true), 3000);
}
async function changedFilter() {
  page.value = 1;
  saveView();
  await refresh();
}
function choose(materialId?: string) {
  if (busy.value || !overview.value)
    return;
  target.value = materialId;
  input.value?.click();
}
async function filesChosen(event: Event) {
  const element = event.target as HTMLInputElement;
  const files = Array.from(element.files || []);
  element.value = '';
  await addFiles(files);
}
async function drop(event: DragEvent) {
  if (busy.value || !overview.value)
    return;
  target.value = undefined;
  await addFiles(Array.from(event.dataTransfer?.files || []));
}
async function addFiles(files: File[]) {
  if (busy.value || !overview.value || !caseId.value || !user.token)
    return;
  if (files.length > 100 || (target.value && files.length > 1)) {
    ElMessage.warning(target.value ? '替换版本时请只选择一份文件' : '每批最多100份材料');
    return;
  }
  uploads.value = files.map(file => ({
    name: file.name,
    progress: 0,
    state: '等待上传',
    error: '',
    file,
    key: newKey(),
    materialId: target.value,
  }));
  target.value = undefined;
  await runUploads();
}
function cancelUploads() {
  controller?.abort();
  for (const row of uploads.value) {
    if (row.state === '等待上传')
      row.state = '已取消本项';
  }
}
async function runUploads(onlyFailed = false) {
  if (busy.value || !overview.value)
    return;
  const v = epoch;
  const owner = user.token;
  const id = caseId.value;
  busy.value = true;
  controller = new AbortController();
  for (const row of uploads.value) {
    if (!active(v, owner) || controller.signal.aborted)
      break;
    if (onlyFailed && !row.error)
      continue;
    try {
      row.error = '';
      row.state = '校验原件';
      const sha256 = await fingerprint(row.file);
      if (!active(v, owner))
        break;
      const cache = `dossier-request:${id}:${sha256}:${encodeURIComponent(row.file.name)}:${row.materialId || 'new'}`;
      row.key = sessionStorage.getItem(cache) || row.key;
      sessionStorage.setItem(cache, row.key);
      const ticket = await request<{ upload: UploadTicket; duplicateNames: string[] }>(
        id,
        '/materials/init-upload',
        'POST',
        {
          fileName: row.file.name,
          fileBytes: row.file.size,
          sha256,
          idempotencyKey: row.key,
          materialId: row.materialId,
        },
      );
      if (!active(v, owner))
        break;
      let keepDuplicate = false;
      if (ticket.duplicateNames.length && ticket.upload.state !== 'completed') {
        row.state = '等待副本确认';
        try {
          await ElMessageBox.confirm(
            `相同内容已存在：${ticket.duplicateNames.join('、')}。保留副本会建立独立材料或版本。`,
            '发现内容重复',
            { confirmButtonText: '保留副本', cancelButtonText: '取消本项' },
          );
          keepDuplicate = true;
        }
        catch {
          row.state = '已取消本项';
          sessionStorage.removeItem(cache);
          continue;
        }
      }
      if (!active(v, owner))
        break;
      if (!['stored', 'completed'].includes(ticket.upload.state)) {
        row.state = '上传原件';
        await uploadContent(
          id,
          ticket.upload.id,
          row.file,
          (n) => {
            if (active(v, owner))
              row.progress = n;
          },
          controller.signal,
        );
      }
      if (!active(v, owner))
        break;
      row.state = '归档校验';
      await request<FileVersion>(id, `/uploads/${ticket.upload.id}/complete`, 'POST', {
        keepDuplicate,
      });
      if (!active(v, owner))
        break;
      row.progress = 100;
      row.state = '原件已存，解析已排队';
      sessionStorage.removeItem(cache);
    }
    catch (e) {
      if (active(v, owner)) {
        row.error = (e as Error).message;
        row.state = '上传/归档失败';
      }
    }
  }
  if (active(v, owner)) {
    busy.value = false;
    await refresh();
  }
}
async function action(fn: () => Promise<unknown>) {
  if (busy.value)
    return;
  const v = epoch;
  const owner = user.token;
  busy.value = true;
  try {
    await fn();
    if (active(v, owner))
      await refresh();
  }
  catch (e) {
    if (active(v, owner)) {
      error.value = (e as Error).message;
      ElMessage.error(error.value);
    }
  }
  finally {
    if (active(v, owner)) {
      busy.value = false;
      schedule();
    }
  }
}
async function openMaterial(row: Material, failures = false) {
  if (!row.current) {
    await action(() =>
      request(caseId.value, `/materials/${row.material.id}/adopt`, 'POST', {
        idempotencyKey: newKey(),
      }),
    );
    return;
  }
  selected.value = row.current;
  pages.value = [];
  history.value = [];
  failedOnly.value = failures;
  pagesVisible.value = true;
  checked.value = [];
  clearImage();
  const v = epoch;
  const owner = user.token;
  const serial = ++detailSerial;
  try {
    const [list, versions] = await Promise.all([
      request<MaterialPage[]>(caseId.value, `/files/${row.current.id}/pages`),
      request<FileVersion[]>(caseId.value, `/materials/${row.material.id}/versions`),
    ]);
    if (!active(v, owner) || serial !== detailSerial)
      return;
    pages.value = list;
    history.value = versions;
    currentPage.value = failures
      ? list.find(p => p.extractStatus !== 'success')?.pageIndex || 1
      : 1;
    await showPage(currentPage.value);
  }
  catch (e) {
    if (active(v, owner) && serial === detailSerial)
      imageError.value = (e as Error).message;
  }
}
async function changeVersion(id: string) {
  const version = history.value.find(x => x.id === id);
  if (!version)
    return;
  await openMaterial({
    material: {
      id: version.materialId,
      displayName: version.fileName,
      currentVersionId: version.id,
    },
    current: version,
  });
}
async function showPage(index: number) {
  clearImage();
  currentPage.value = index;
  const v = epoch;
  const owner = user.token;
  const p = ++previewEpoch;
  const versionId = selected.value?.id;
  if (!versionId)
    return;
  imageLoading.value = true;
  try {
    const blob = await authenticatedBlob(caseId.value, versionId, index);
    if (!active(v, owner) || p !== previewEpoch)
      return;
    imageUrl.value = URL.createObjectURL(blob);
  }
  catch (e) {
    if (active(v, owner) && p === previewEpoch)
      imageError.value = (e as Error).message;
  }
  finally {
    if (active(v, owner) && p === previewEpoch)
      imageLoading.value = false;
  }
}
async function retry(version: FileVersion, list: number[] = []) {
  await action(() =>
    request(caseId.value, '/jobs/parse', 'POST', {
      fileVersionId: version.id,
      idempotencyKey: newKey(),
      pages: list,
    }),
  );
  checked.value = [];
}
async function blank(p: MaterialPage) {
  if (!selected.value)
    return;
  try {
    await ElMessageBox.confirm(
      '请先查看本页原图或下载原件。确认本物理页真实空白将记录操作者和时间；识别失败不能直接当作空白。',
      '人工确认空白页',
      { confirmButtonText: '已查看原件，确认空白', cancelButtonText: '继续待核' },
    );
  }
  catch {
    return;
  }
  await action(() =>
    request(
      caseId.value,
      `/files/${selected.value!.id}/pages/${p.pageIndex}/confirm-blank`,
      'POST',
      { expectedVersion: p.version },
    ),
  );
}
async function download(version: FileVersion) {
  try {
    await downloadVersion(caseId.value, version);
  }
  catch (e) {
    ElMessage.error((e as Error).message);
  }
}
function unload(event: BeforeUnloadEvent) {
  saveView();
  if (busy.value) {
    event.preventDefault();
    event.returnValue = '';
  }
}
window.addEventListener('beforeunload', unload);
onBeforeRouteLeave(async () => {
  saveView();
  if (!busy.value)
    return true;
  try {
    await ElMessageBox.confirm(
      '上传尚未完成。已归档文件会继续解析；未完成上传需要重新选择同一原件恢复。',
      '离开材料工作台',
      { confirmButtonText: '离开', cancelButtonText: '继续上传' },
    );
    return true;
  }
  catch {
    return false;
  }
});
watch(
  () => [caseId.value, user.token],
  () => {
    epoch++;
    controller?.abort();
    clearTimeout(timer);
    clearImage();
    overview.value = undefined;
    materials.value = [];
    jobs.value = [];
    uploads.value = [];
    pages.value = [];
    history.value = [];
    selected.value = undefined;
    pagesVisible.value = false;
    busy.value = false;
    error.value = '';
    search.value = '';
    page.value = 1;
    status.value = '';
    restoreView();
    void refresh();
  },
  { immediate: true, flush: 'sync' },
);
onMounted(() => {
  scrollContainer = root.value?.closest<HTMLElement>('.layout-main') || undefined;
  scrollContainer?.addEventListener('scroll', saveView, { passive: true });
});
watch(page, saveView);
onScopeDispose(() => {
  disposed = true;
  epoch++;
  controller?.abort();
  clearTimeout(timer);
  clearImage();
  window.removeEventListener('beforeunload', unload);
  scrollContainer?.removeEventListener('scroll', saveView);
});
</script>

<template>
  <main ref="root" class="dossier-workbench yl-page" @dragover.prevent @drop.prevent="drop">
    <el-button
      class="yl-back"
      text
      @click="router.push({ path: '/my-cases', query: { id: caseId } })"
    >
      ← 返回案件
    </el-button><AppPageHeader
      title="材料溯源工作台"
      description="原件、版本与逐页读取状态分别记录；从异常定位原页，再继续处理。"
      eyebrow="案件管理"
    >
      <template #actions>
        <el-button :disabled="!overview" @click="router.push({ path: '/my-cases/events', query: { id: caseId } })">
          事实事件时间线
        </el-button>
        <el-button :disabled="!overview" @click="router.push({ path: '/my-cases/claims', query: { id: caseId } })">
          事实与摘录
        </el-button>
        <el-button :disabled="!overview" @click="router.push({ path: '/my-cases/bank', query: { id: caseId } })">
          银行流水
        </el-button>
        <el-button type="primary" :disabled="busy || !overview" @click="choose()">
          上传材料
        </el-button>
      </template><template #meta>
        <span class="case-context-label">当前案件：{{ overview?.caseTitle || (loading ? '正在加载…' : error ? '案件不可用' : '待选择案件') }}</span>
      </template>
    </AppPageHeader>
    <input
      ref="input"
      type="file"
      multiple
      accept=".pdf,.docx,.png,.jpg,.jpeg,.csv,.xlsx"
      hidden
      :disabled="busy || !overview"
      @change="filesChosen"
    >
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
    <section v-if="overview" class="summary" aria-label="本次导入覆盖">
      <span>文件总数 <b>{{ overview.files }}</b></span><span>成功 {{ overview.success }} / 部分 {{ overview.partial }} / 失败
        {{ overview.failed }}</span><span>已解析 {{ overview.parsedPages }}/{{ overview.knownPages }} 页
        <strong v-if="overview.unknownPageFiles">（另{{ overview.unknownPageFiles }}份总页未知）</strong></span><el-button
        text
        @click="
          status = 'issues';
          changedFilter();
        "
      >
        待处理异常 {{ overview.issues }}
      </el-button>
    </section>
    <section v-if="uploads.length" class="uploads" aria-label="上传清单">
      <h2>本次上传</h2>
      <el-button v-if="busy" @click="cancelUploads">
        取消剩余上传
      </el-button>
      <article v-for="(u, i) in uploads" :key="i">
        <span :title="u.name">{{ u.name }}</span><el-progress
          :percentage="u.progress"
          :status="u.error ? 'exception' : undefined"
        /><span>{{ u.state }}</span>
        <p v-if="u.error" class="error">
          {{ u.error }}
        </p>
      </article>
      <el-button v-if="uploads.some((u) => u.error)" :disabled="busy || !overview" @click="runUploads(true)">
        重试失败上传
      </el-button>
      <p>
        “原件已存”表示上传归档完成，解析结果请查看下方状态。刷新前未完成的上传，可重新选择同一文件恢复。
      </p>
    </section>
    <section class="toolbar">
      <el-input
        v-model="search"
        aria-label="搜索材料文件名"
        placeholder="搜索完整文件名"
        clearable
        @clear="changedFilter"
        @keyup.enter="changedFilter"
      /><el-select
        v-model="status"
        aria-label="解析状态筛选"
        placeholder="全部解析状态"
        clearable
        @change="changedFilter"
      >
        <el-option
          v-for="s in [
            'issues',
            'not_started',
            'queued',
            'running',
            'success',
            'partial',
            'failed',
            'cancelled',
          ]"
          :key="s"
          :value="s"
          :label="stateLabel(s)"
        />
      </el-select><el-button :loading="loading" @click="refresh()">
        刷新
      </el-button>
    </section>
    <el-table v-loading="loading" :data="materials" row-key="material.id" empty-text="">
      <template #empty>
        <AppState
          :title="
            error
              ? '材料列表暂不可用'
              : search || status
                ? '没有符合筛选的材料'
                : '上传材料，建立原件档案'
          "
          :description="
            error
              ? '请重新加载，或返回案件列表选择可用案件。'
              : search || status
                ? '请调整文件名或解析状态，已有材料不会被删除。'
                : '支持 PDF、DOCX、PNG、JPG；原件与解析状态分别保存。'
          "
          :kind="error ? 'error' : 'empty'"
        >
          <el-button v-if="error" :loading="loading" @click="refresh()">
            重新加载
          </el-button><el-button
            v-else-if="search || status"
            @click="
              search = '';
              status = '';
              changedFilter();
            "
          >
            清除筛选
          </el-button><el-button v-else :disabled="busy || !overview" @click="choose()">
            选择材料
          </el-button>
        </AppState>
      </template>
      <el-table-column label="材料名称" min-width="220">
        <template #default="{ row }">
          <span :title="row.material.displayName">{{ row.material.displayName }}</span>
          <div class="muted">
            {{ row.current ? '原件已存' : '历史归档，页状态未核实' }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="版本" width="80">
        <template #default="{ row }">
          {{ row.current ? `V${row.current.versionNo}` : '待建立' }}
        </template>
      </el-table-column>
      <el-table-column label="读取覆盖" width="150">
        <template #default="{ row }">
          {{ coverage(row.current)
          }}<el-button
            v-if="row.current?.failedPages"
            text
            type="danger"
            @click="openMaterial(row, true)"
          >
            未读{{ row.current.failedPages }}页
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="解析状态" min-width="180">
        <template #default="{ row }">
          <el-tag
            :type="
              row.current?.parseStatus === 'success'
                ? 'success'
                : row.current?.parseStatus === 'failed'
                  ? 'danger'
                  : 'warning'
            "
          >
            {{ stateLabel(row.current?.parseStatus || 'not_started') }}
          </el-tag>
          <p v-if="row.current?.errorMessage" class="error">
            {{ row.current.errorMessage }}
          </p>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="270" :fixed="isMobile ? false : 'right'">
        <template #default="{ row }">
          <el-button link :disabled="busy" @click="openMaterial(row)">
            {{ row.current ? '原页 / 版本' : '纳入逐页解析' }}
          </el-button><el-button v-if="row.current" link :disabled="busy" @click="download(row.current)">
            下载原件
          </el-button><el-button
            v-if="
              row.current && ['partial', 'failed', 'cancelled'].includes(row.current.parseStatus)
            "
            link
            :disabled="busy"
            @click="retry(row.current)"
          >
            仅重试未读页
          </el-button><el-button link :disabled="busy" @click="choose(row.material.id)">
            上传新版本
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      :total="total"
      :page-size="20"
      layout="total,prev,pager,next"
      @current-change="refresh()"
    />
    <section v-if="jobs.length" class="jobs">
      <h2>任务中心</h2>
      <article v-for="job in jobs" :key="job.id">
        <span>任务 {{ job.id }} · {{ stateLabel(job.state) }} · {{ job.progressDone }}/{{
          job.progressTotal ?? '总页未知'
        }}
          · 尝试{{ job.attempts }}次</span><el-button
            v-if="activeJob(job)"
            text
            :disabled="busy"
            @click="action(() => request(caseId, `/jobs/${job.id}/cancel`, 'POST'))"
          >
            取消未完成部分
          </el-button>
        <p v-if="job.errorMessage" class="error">
          {{ job.errorMessage }}
        </p>
      </article>
    </section>
    <el-dialog
      v-model="pagesVisible"
      top="5vh"
      :style="{ maxHeight: '90dvh', overflow: 'auto' }"
      width="min(1320px,98vw)"
      :title="selected ? `${selected.fileName} · V${selected.versionNo}` : '原页核对'"
      @closed="clearImage"
    >
      <template v-if="selected">
        <div class="page-tools">
          <el-select aria-label="原件版本" :model-value="selected.id" @change="changeVersion">
            <el-option
              v-for="v in history"
              :key="v.id"
              :value="v.id"
              :label="`V${v.versionNo} · ${coverage(v)}`"
            />
          </el-select><el-checkbox v-model="failedOnly">
            仅看未读页
          </el-checkbox><el-button :disabled="busy || !checked.length" @click="retry(selected, checked)">
            重试所选失败页
          </el-button><el-button @click="download(selected)">
            下载此版本原件
          </el-button>
        </div>
        <p>
          读取覆盖：{{ coverage(selected) }}；解析器
          {{ selected.parserVersion }}。历史版本原件不会被新上传覆盖。
        </p>
        <div class="page-layout">
          <section class="page-list">
            <el-checkbox-group v-model="checked">
              <article v-for="p in visiblePages" :key="p.id">
                <el-checkbox :value="p.pageIndex" :disabled="p.extractStatus === 'success'">
                  物理页 {{ p.pageIndex }}
                </el-checkbox><el-button text @click="showPage(p.pageIndex)">
                  查看原页
                </el-button><DossierPageThumbnail
                  :case-id="caseId"
                  :version-id="selected.id"
                  :file-name="selected.fileName"
                  :version-no="selected.versionNo"
                  :page="p.pageIndex"
                />
                <p>
                  {{ stateLabel(p.extractStatus) }} · {{ p.extractionMethod }} · {{ p.charCount }}字
                </p>
                <p>
                  OCR：{{ p.ocrStatus === 'not_needed' ? '无需识别' : stateLabel(p.ocrStatus) }} ·
                  原页预览：{{
                    p.previewStatus === 'success'
                      ? '可用'
                      : p.previewStatus === 'failed'
                        ? '失败'
                        : '待处理'
                  }}
                </p>
                <p v-if="p.errorMessage" class="error">
                  {{ p.errorMessage }}
                </p>
                <el-button
                  v-if="p.extractStatus !== 'success'"
                  text
                  :disabled="busy"
                  @click="retry(selected, [p.pageIndex])"
                >
                  仅重试本页
                </el-button><el-button
                  v-if="p.errorCode === 'EMPTY_UNCONFIRMED'"
                  text
                  :disabled="busy"
                  @click="blank(p)"
                >
                  已查看原件，人工确认空白
                </el-button>
                <p v-if="p.emptyPageConfirmed">
                  此页已由人工确认为空白，非OCR自动成功。
                </p>
              </article>
            </el-checkbox-group><el-empty
              v-if="!visiblePages.length"
              description="当前没有页记录或没有符合筛选的页面；解析任务完成后可刷新"
              :image-size="50"
            />
          </section>
          <section v-loading="imageLoading" class="preview" :aria-busy="imageLoading">
            <div class="page-tools">
              <el-button :disabled="currentPage <= 1" @click="showPage(currentPage - 1)">
                上一页
              </el-button><el-input-number
                v-model="currentPage"
                aria-label="原件物理页码"
                :min="1"
                :max="selected.pageCount || 1"
                controls-position="right"
                @change="(n) => n && showPage(n)"
              /><span> / {{ selected.pageCount ?? '未知' }}</span><el-button
                :disabled="!selected.pageCount || currentPage >= selected.pageCount"
                @click="showPage(currentPage + 1)"
              >
                下一页
              </el-button>
            </div>
            <div class="page-tools preview-zoom">
              <el-button
                :disabled="!imageUrl || previewScale <= 0.5"
                @click="previewScale = Math.max(0.5, previewScale - 0.25)"
              >
                缩小
              </el-button><span role="status">{{ Math.round(previewScale * 100) }}%</span><el-button
                :disabled="!imageUrl || previewScale >= 2"
                @click="previewScale = Math.min(2, previewScale + 0.25)"
              >
                放大
              </el-button><el-button :disabled="!imageUrl" @click="previewScale = 1">
                适宽
              </el-button>
            </div>
            <el-alert v-if="imageError" :title="imageError" type="warning" :closable="false" /><img
              v-if="imageUrl"
              :style="{ width: `${previewScale * 100}%` }"
              :src="imageUrl"
              :alt="`${selected.fileName} V${selected.versionNo} 第${currentPage}物理页`"
            ><AppState
              v-else
              :kind="imageLoading ? 'loading' : imageError ? 'error' : 'empty'"
              :title="imageLoading ? '正在加载原页' : '原页暂不可预览'"
              description="可下载当前版本原件核对，或重新选择物理页。"
            />
          </section>
        </div>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.dossier-workbench {
  min-width: 0;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}
h1 {
  font-size: 24px;
  margin: 8px 0;
}
h2 {
  font-size: 17px;
}
.summary {
  display: flex;
  gap: 24px;
  align-items: center;
  flex-wrap: wrap;
  padding: 20px 0;
}
.summary strong,
.error {
  color: var(--el-color-danger);
}
.toolbar,
.page-tools {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin: 16px 0;
}
.toolbar .el-input {
  width: 320px;
}
.toolbar .el-select,
.page-tools .el-select {
  width: 220px;
}
.muted,
header p,
.uploads > p {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.uploads,
.jobs {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 16px;
  margin: 16px 0;
}
.uploads article {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) minmax(120px, 1fr) 220px;
  gap: 12px;
  margin: 12px 0;
}
.uploads article > span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.uploads article p {
  grid-column: 1/-1;
  margin: 0;
}
.jobs article {
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding: 10px 0;
}
.page-layout {
  display: grid;
  grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
  gap: 20px;
}
.page-list {
  max-height: min(62vh, calc(90dvh - 220px));
  overflow: auto;
}
.page-list article {
  font-size: var(--yl-text-base);
  line-height: 1.7;
  padding: 12px;
  border-bottom: 1px solid var(--el-border-color);
}
.page-list p {
  line-height: 1.7;
  font-size: 13px;
  margin: 4px 0;
}
.preview {
  max-height: min(62vh, calc(90dvh - 220px));
  overflow: auto;
  background: var(--el-fill-color-lighter);
  padding: 12px;
}
.preview img {
  display: block;
  width: 100%;
  height: auto;
}
.el-pagination {
  margin: 16px 0;
}
@media (max-width: 850px) {
  .page-layout {
    grid-template-columns: 1fr;
  }
  .page-list {
    max-height: 30vh;
  }
  .uploads article {
    grid-template-columns: 1fr;
  }
  .dossier-workbench {
    padding: 12px;
  }
}
.summary {
  background: var(--yl-color-surface);
  border: 1px solid var(--yl-color-border);
  border-radius: var(--yl-radius-panel);
  padding: var(--yl-space-4);
  margin-bottom: var(--yl-space-6);
}
.summary b {
  font-variant-numeric: tabular-nums;
}
.uploads,
.jobs {
  background: var(--yl-color-surface);
}
.case-context-label {
  font-size: 13px;
  color: var(--yl-color-text-muted);
  overflow-wrap: anywhere;
}
.page-tools {
  gap: 8px;
}
.page-list .el-checkbox-group {
  width: 100%;
}
.preview-zoom {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--yl-color-border);
}
.page-tools .el-input-number {
  width: 110px;
}
</style>
