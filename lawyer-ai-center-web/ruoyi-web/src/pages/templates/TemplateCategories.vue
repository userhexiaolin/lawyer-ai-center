<script setup lang="ts">
import type { TemplateCategory } from '@/api/document/templateManagement';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onScopeDispose, reactive, ref, watch } from 'vue';
import { categoryList, removeCategory, saveCategory } from '@/api/document/templateManagement';
import AppState from '@/components/AppState.vue';
import { useScreenStore } from '@/hooks/useScreen';
import { useUserStore } from '@/stores';
import CategoryEditor from './CategoryEditor.vue';
import { failureMessage, hasTemplatePermission } from './workflow';

const props = defineProps<{ permissions: string[]; active: boolean }>();
const { isMobile } = useScreenStore();
const user = useUserStore();
const allowed = (action: string) => hasTemplatePermission(props.permissions, 'category', action);
const filters = reactive({ name: '', status: '', source: '' });
const applied = ref({ ...filters });
const rows = ref<TemplateCategory[]>([]);
const visible = computed(() =>
  rows.value.filter(
    row =>
      (!applied.value.status || row.status === applied.value.status)
      && (!applied.value.source || (row.builtin ? 'builtin' : 'custom') === applied.value.source),
  ),
);
const loading = ref(false);
const error = ref('');
const actionError = ref('');
const pending = ref('');
const open = ref(false);
const initial = ref<TemplateCategory>();
const page = ref(1);
const filtered = computed(() => Object.values(applied.value).some(Boolean));
let generation = 0;
let confirming = false;
onScopeDispose(() => {
  generation++;
  if (confirming)
    ElMessageBox.close();
});
async function load() {
  if (!allowed('list'))
    return;
  const request = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const result = await categoryList(applied.value.name);
    if (request !== generation)
      return;
    rows.value = result;
    page.value = Math.min(page.value, Math.max(1, Math.ceil(visible.value.length / 20)));
  }
  catch (reason) {
    if (request === generation) {
      rows.value = [];
      error.value = failureMessage(reason);
    }
  }
  finally {
    if (request === generation)
      loading.value = false;
  }
}
function query(reset = false) {
  if (reset)
    Object.assign(filters, { name: '', status: '', source: '' });
  applied.value = { ...filters, name: filters.name.trim() };
  page.value = 1;
  void load();
}
function edit(row?: TemplateCategory) {
  if (pending.value || loading.value || !allowed(row ? 'edit' : 'add'))
    return;
  initial.value = row ? { ...row } : undefined;
  open.value = true;
}
async function mutate(row: TemplateCategory, remove = false) {
  if (
    !row.id
    || pending.value
    || loading.value
    || !allowed(remove ? 'remove' : 'edit')
    || (remove && row.builtin)
  ) {
    return;
  }
  const token = user.token;
  const label = remove ? '删除' : row.status === '0' ? '停用' : '启用';
  pending.value = row.id;
  actionError.value = '';
  try {
    confirming = true;
    await ElMessageBox.confirm(
      remove
        ? '删除不可撤销；分类下仍有模板时系统会拒绝删除，请先调整关联模板。'
        : row.status === '0'
          ? '停用后，该分类不再供新模板和文书起草选择，已有模板和文书保留。'
          : '启用后，该分类可供新模板和文书起草选择。',
      `${label}“${row.name}”？`,
      { confirmButtonText: `确认${label}`, cancelButtonText: '取消', type: 'warning' },
    );
    if (user.token !== token || !allowed(remove ? 'remove' : 'edit'))
      return;
    await (remove
      ? removeCategory(row.id)
      : saveCategory({ ...row, status: row.status === '0' ? '1' : '0' }));
    if (user.token !== token)
      return;
    ElMessage.success(`分类已${label}`);
    await load();
  }
  catch (reason) {
    if (reason !== 'cancel' && reason !== 'close' && user.token === token)
      actionError.value = failureMessage(reason);
  }
  finally {
    confirming = false;
    pending.value = '';
  }
}
watch(
  [() => props.permissions, () => props.active],
  () => {
    if (props.active)
      void load();
  },
  { immediate: true },
);
</script>

<template>
  <section aria-label="模板分类">
    <div class="section-heading">
      <p>维护文书分类、排序与使用状态。内置分类用于专用起草流程，可停用，不能改名或删除。</p>
      <el-button
        v-if="allowed('add')"
        type="primary"
        :disabled="loading || !!pending"
        @click="edit()"
      >
        新增分类
      </el-button>
    </div>
    <AppState
      v-if="!allowed('list')"
      title="暂无模板分类查看权限"
      description="请联系管理员配置模板分类权限后，重新登录。"
    />
    <section v-else class="yl-panel">
      <details class="filter-disclosure" :open="!isMobile">
        <summary v-show="isMobile" class="filter-toggle">
          筛选分类{{ filtered ? "（已筛选）" : "" }}
        </summary>
        <el-form :inline="true" class="filters" @submit.prevent="query()">
          <el-form-item label="分类名称" label-for="category-filter-name">
            <el-input
              id="category-filter-name"
              v-model="filters.name"
              placeholder="搜索分类名称"
              maxlength="50"
              clearable
            />
          </el-form-item>
          <el-form-item label="来源" label-for="category-filter-source">
            <el-select
              id="category-filter-source"
              v-model="filters.source"
              class="select-filter"
              placeholder="全部来源"
              clearable
            >
              <el-option label="内置" value="builtin" /><el-option label="自定义" value="custom" />
            </el-select>
          </el-form-item>
          <el-form-item label="使用状态" label-for="category-filter-status">
            <el-select
              id="category-filter-status"
              v-model="filters.status"
              class="select-filter"
              placeholder="全部状态"
              clearable
            >
              <el-option label="启用" value="0" /><el-option label="停用" value="1" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button native-type="submit" :disabled="loading || !!pending">
              查询
            </el-button>
            <el-button :disabled="loading || !!pending" @click="query(true)">
              重置
            </el-button>
          </el-form-item>
        </el-form>
      </details>
      <el-alert
        v-if="actionError"
        :title="actionError"
        type="error"
        :closable="false"
        role="alert"
      />
      <AppState v-if="loading" title="正在加载模板分类" kind="loading" />
      <AppState v-else-if="error" title="分类加载失败" :description="error" kind="error">
        <el-button @click="load">
          重试
        </el-button>
      </AppState>
      <template v-else>
        <ul v-if="isMobile && visible.length" class="mobile-records" aria-label="分类条目">
          <li
            v-for="row in visible.slice((page - 1) * 20, page * 20)"
            :key="row.id"
            class="mobile-record"
          >
            <div class="record-heading">
              <h3>{{ row.name }}</h3>
              <el-tag
                :type="row.status === '0' ? 'success' : 'info'"
                :class="{ 'neutral-tag': row.status === '1' }"
              >
                {{ row.status === "0" ? "启用" : "停用" }}
              </el-tag>
            </div>
            <p class="record-meta">
              {{ row.builtin ? "内置分类" : "自定义分类" }} · 排序 {{ row.sortOrder }}
            </p>
            <p v-if="row.remark" class="record-meta">
              {{ row.remark }}
            </p>
            <div class="record-actions">
              <el-button v-if="allowed('edit')" :disabled="!!pending" @click="edit(row)">
                编辑
              </el-button>
              <el-button v-if="allowed('edit')" :disabled="!!pending" @click="mutate(row)">
                {{
                  row.status === "0" ? "停用" : "启用"
                }}
              </el-button>
              <el-button
                v-if="allowed('remove') && !row.builtin"
                type="danger"
                plain
                :disabled="!!pending"
                @click="mutate(row, true)"
              >
                删除
              </el-button>
              <span v-if="!allowed('edit') && (!allowed('remove') || row.builtin)">只读</span>
            </div>
          </li>
        </ul>
        <el-table
          v-else-if="visible.length"
          :data="visible.slice((page - 1) * 20, page * 20)"
          row-key="id"
          class="management-table"
        >
          <el-table-column
            prop="name"
            label="分类名称"
            min-width="200"
            fixed
            show-overflow-tooltip
          />
          <el-table-column label="来源" width="100">
            <template #default="{ row }">
              <el-tag type="info" class="neutral-tag">
                {{ row.builtin ? "内置" : "自定义" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序号" width="100" align="right" />
          <el-table-column label="使用状态" width="100">
            <template #default="{ row }">
              <el-tag
                :type="row.status === '0' ? 'success' : 'info'"
                :class="{ 'neutral-tag': row.status === '1' }"
              >
                {{ row.status === "0" ? "启用" : "停用" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="208" fixed="right">
            <template #default="{ row }">
              <el-button v-if="allowed('edit')" link :disabled="!!pending" @click="edit(row)">
                编辑
              </el-button>
              <el-button v-if="allowed('edit')" link :disabled="!!pending" @click="mutate(row)">
                {{ row.status === "0" ? "停用" : "启用" }}
              </el-button>
              <el-button
                v-if="allowed('remove') && !row.builtin"
                link
                type="danger"
                :disabled="!!pending"
                @click="mutate(row, true)"
              >
                删除
              </el-button>
              <span v-if="!allowed('edit') && (!allowed('remove') || row.builtin)">只读</span>
            </template>
          </el-table-column>
        </el-table>
        <AppState
          v-else
          :title="filtered ? '没有匹配的分类' : '暂无模板分类'"
          description="可调整筛选，或新增文书分类。"
        >
          <el-button v-if="filtered" @click="query(true)">
            清除筛选
          </el-button>
        </AppState>
        <el-pagination
          v-if="visible.length"
          v-model:current-page="page"
          class="pagination"
          :page-size="20"
          :total="visible.length"
          layout="total, prev, pager, next"
        />
      </template>
    </section>
    <CategoryEditor
      v-if="open"
      v-model="open"
      :initial="initial"
      :permissions="permissions"
      @saved="load"
    />
  </section>
</template>

<style scoped>
@import "./management.css";
</style>
