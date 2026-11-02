<script setup lang="ts">
import type { SearchReport } from '@/api/legal-report/types';
import { ElMessage, ElMessageBox } from 'element-plus';
import { onScopeDispose, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { reportApi } from '@/api/legal-report';
import AppPageHeader from '@/components/AppPageHeader.vue';
import { useUserStore } from '@/stores';
import { selectReport } from './helpers';
import './reports.css';

const router = useRouter();
const user = useUserStore();
const rows = ref<SearchReport[]>([]);
const total = ref(0);
const page = ref(1);
const loading = ref(false);
const error = ref('');
const creating = ref(false);
const title = ref('');
const createVisible = ref(false);
const deleting = ref('');
let epoch = 0;
let disposed = false;
onScopeDispose(() => {
  disposed = true;
  epoch++;
});
async function load() {
  const version = ++epoch;
  const token = user.token;
  if (!token) {
    rows.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const result = await reportApi.list(page.value);
    if (!disposed && version === epoch && user.token === token) {
      rows.value = result.rows;
      total.value = result.total;
    }
  }
  catch (e) {
    if (!disposed && version === epoch && user.token === token)
      error.value = (e as Error).message;
  }
  finally {
    if (!disposed && version === epoch)
      loading.value = false;
  }
}
function open(report: SearchReport) {
  selectReport(user.token, report.id);
  void router.push({ name: 'searchReportWorkbench', params: { id: report.id } });
}
async function create() {
  if (creating.value)
    return;
  if (!title.value.trim()) {
    ElMessage.warning('请输入报告标题');
    return;
  }
  const token = user.token;
  creating.value = true;
  try {
    const result = await reportApi.create(title.value.trim());
    if (!disposed && user.token === token) {
      createVisible.value = false;
      open(result);
    }
  }
  catch (e) {
    if (!disposed && user.token === token)
      ElMessage.error((e as Error).message);
  }
  finally {
    if (user.token === token)
      creating.value = false;
  }
}
async function remove(report: SearchReport) {
  if (deleting.value)
    return;
  const token = user.token;
  try {
    await ElMessageBox.confirm(`删除报告“${report.title}”？`, '删除报告', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  }
  catch {
    return;
  }
  if (disposed || user.token !== token || deleting.value)
    return;
  deleting.value = report.id;
  try {
    await reportApi.remove(report.id);
    if (!disposed && user.token === token) {
      selectReport(token, '');
      if (rows.value.length === 1 && page.value > 1)
        page.value--;
      await load();
      ElMessage.success('报告已删除');
    }
  }
  catch (e) {
    if (!disposed && user.token === token)
      ElMessage.error((e as Error).message);
  }
  finally {
    if (user.token === token)
      deleting.value = '';
  }
}
watch(
  () => user.token,
  () => {
    epoch++;
    page.value = 1;
    rows.value = [];
    total.value = 0;
    loading.value = false;
    error.value = '';
    createVisible.value = false;
    creating.value = false;
    deleting.value = '';
    title.value = '';
    void load();
  },
  { immediate: true, flush: 'sync' },
);
</script>

<template>
  <section class="report-page yl-page">
    <AppPageHeader
      title="我的检索报告"
      description="归集法规、类案与原文快照，整理为可编辑的检索报告。"
      eyebrow="法律检索"
    >
      <template #actions>
        <el-button
          type="primary"
          :disabled="!user.token || !!deleting"
          @click="
            title = '';
            createVisible = true;
          "
        >
          新建报告
        </el-button>
      </template>
    </AppPageHeader>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-button v-if="error" class="report-retry" :loading="loading" @click="load">
      重新加载
    </el-button>
    <div v-loading="loading" class="report-panel">
      <el-table
        :data="rows"
        :empty-text="error ? '报告列表暂不可用，请重新加载' : '暂无报告，点击「新建报告」开始'"
        row-key="id"
      >
        <el-table-column label="报告标题" min-width="220">
          <template #default="{ row }">
            <el-button link type="primary" class="report-title-link" @click="open(row)">
              {{ row.title }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'generated' ? 'success' : 'info'">
              {{ row.status === 'generated' ? '已生成' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="open(row)">
              进入工作台
            </el-button><el-button
              link
              type="danger"
              :disabled="!!deleting"
              :loading="deleting === row.id"
              @click="remove(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > 20"
        class="report-pagination"
        :current-page="page"
        :page-size="20"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="
          (value) => {
            page = value;
            load();
          }
        "
      />
    </div>
    <el-dialog
      v-model="createVisible"
      title="新建检索报告"
      width="min(480px, 94vw)"
      :close-on-click-modal="!creating"
      :close-on-press-escape="!creating"
      :show-close="!creating"
      append-to-body
    >
      <el-form label-position="top" @submit.prevent="create">
        <el-form-item label="报告标题" required>
          <el-input
            v-model="title"
            maxlength="200"
            show-word-limit
            placeholder="例如：劳动争议检索报告"
            :disabled="creating"
            @keyup.enter="create"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="creating" @click="createVisible = false">
          取消
        </el-button><el-button type="primary" :loading="creating" @click="create">
          创建并进入
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
