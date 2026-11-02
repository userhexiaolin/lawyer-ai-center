<script setup lang="ts">
import { watch } from 'vue';
import { useRouter } from 'vue-router';
import AppState from '@/components/AppState.vue';
import { useScreenStore } from '@/hooks/useScreen';
import TemplateEditor from './TemplateEditor.vue';
import { useTemplateLibrary } from './useTemplateLibrary';
import { canStartDraft, hasTemplatePermission, templateState } from './workflow';

const props = defineProps<{ permissions: string[]; active: boolean }>();
const { isMobile } = useScreenStore();
const router = useRouter();
const allowed = (action: string) => hasTemplatePermission(props.permissions, 'template', action);
const {
  filters,
  applied,
  rows,
  types,
  page,
  total,
  loading,
  error,
  typeError,
  actionError,
  pending,
  open,
  mode,
  initial,
  load,
  loadTypes,
  query,
  changePage,
  edit,
  mutate,
} = useTemplateLibrary(allowed);
watch(
  [() => props.permissions, () => props.active],
  () => {
    if (props.active && allowed('list')) {
      void load();
      void loadTypes();
    }
  },
  { immediate: true },
);
</script>

<template>
  <section aria-label="模板列表">
    <div class="section-heading">
      <p>{{ isMobile ? '核对模板字段后，再用于起草。' : '原文件、解析和字段确认分别维护。启用且完成字段核对的模板可供起草使用。' }}</p>
      <div class="actions">
        <el-button v-if="allowed('add')" :disabled="loading || !!pending" @click="edit()">
          新增正文模板
        </el-button>
        <el-button
          v-if="allowed('add')"
          type="primary"
          :disabled="loading || !!pending"
          @click="edit(undefined, true)"
        >
          上传模板
        </el-button>
      </div>
    </div>
    <AppState
      v-if="!allowed('list')"
      title="暂无模板查看权限"
      description="请联系管理员配置模板管理权限后，重新登录。"
    />
    <section v-else class="yl-panel" :aria-busy="loading || !!pending">
      <details class="filter-disclosure" :open="!isMobile">
        <summary v-show="isMobile" class="filter-toggle">
          筛选模板{{ applied.name || applied.type ? "（已筛选）" : "" }}
        </summary>
        <el-form :inline="true" class="filters" @submit.prevent="query()">
          <el-form-item label="模板名称" label-for="template-filter-name">
            <el-input
              id="template-filter-name"
              v-model="filters.name"
              placeholder="搜索模板名称"
              maxlength="100"
              clearable
            />
          </el-form-item>
          <el-form-item label="模板分类" label-for="template-filter-type">
            <el-select
              id="template-filter-type"
              v-model="filters.type"
              class="select-filter"
              placeholder="全部分类"
              clearable
              @visible-change="$event && loadTypes()"
            >
              <el-option
                v-for="item in types"
                :key="item.value"
                :value="item.value"
                :label="item.label + (item.disabled ? '（停用）' : '')"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button native-type="submit" :disabled="loading || !!pending">
              查询
            </el-button><el-button :disabled="loading || !!pending" @click="query(true)">
              重置
            </el-button>
          </el-form-item>
        </el-form>
      </details>
      <el-alert
        v-if="typeError"
        :title="`分类加载失败：${typeError}`"
        type="warning"
        :closable="false"
      >
        <el-button text @click="loadTypes">
          重试分类
        </el-button>
      </el-alert>
      <el-alert
        v-if="actionError"
        :title="actionError"
        type="error"
        :closable="false"
        role="alert"
      />
      <p v-if="pending" role="status" class="result-context">
        正在处理所选模板…
      </p>
      <div v-if="!loading && !error" class="result-context" role="status" aria-live="polite">
        <p>
          共 {{ total }} 个模板<span v-if="applied.name"> · 名称：{{ applied.name }}</span><span v-if="applied.type"> · 分类：{{ applied.type }}</span>
        </p>
        <el-button v-if="applied.name || applied.type" :disabled="!!pending" text @click="query(true)">
          清除筛选
        </el-button>
      </div>
      <AppState v-if="loading" title="正在加载模板" kind="loading" />
      <AppState v-else-if="error" title="模板加载失败" :description="error" kind="error">
        <el-button @click="load">
          重试
        </el-button>
      </AppState>
      <template v-else>
        <ul v-if="isMobile && rows.length" class="mobile-records" aria-label="模板条目">
          <li v-for="row in rows" :key="row.id" class="mobile-record">
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
              {{ row.documentType }} · 排序 {{ row.sortOrder }}
            </p>
            <el-tag :type="row.fieldsConfirmed ? 'success' : 'warning'">
              {{
                templateState(row)
              }}
            </el-tag>
            <p v-if="row.sourceFilename" class="record-meta">
              {{ row.sourceFilename }}
            </p>
            <p v-if="row.sourceVersion" class="record-version">
              版本 {{ row.sourceVersion }}
            </p>
            <div class="record-actions">
              <el-button
                v-if="canStartDraft(row, types)"
                type="primary"
                :disabled="!!pending"
                @click="router.push({ path: '/drafting', query: { type: row.documentType, templateId: row.id } })"
              >
                起草
              </el-button>
              <el-button v-if="allowed('query')" :disabled="!!pending" @click="edit(row)">
                {{
                  allowed("edit") ? "编辑" : "详情"
                }}
              </el-button>
              <el-button
                v-if="allowed('query') && allowed('edit')"
                :disabled="!!pending"
                @click="edit(row, true)"
              >
                {{ row.sourceVersion ? "核对字段" : "补传原件" }}
              </el-button>
              <el-button v-if="allowed('edit')" :disabled="!!pending" @click="mutate(row)">
                {{
                  row.status === "0" ? "停用" : "启用"
                }}
              </el-button>
              <el-button
                v-if="allowed('remove')"
                type="danger"
                plain
                :disabled="!!pending"
                @click="mutate(row, true)"
              >
                删除
              </el-button>
              <span v-if="!allowed('query') && !allowed('edit') && !allowed('remove')">只读</span>
            </div>
          </li>
        </ul>
        <el-table v-else-if="rows.length" :data="rows" row-key="id" class="management-table">
          <el-table-column label="模板名称 / 原文件" min-width="230" fixed>
            <template #default="{ row }">
              <div class="record-heading">
                <strong>{{ row.name }}</strong>
                <el-tag :type="row.status === '0' ? 'success' : 'info'" :class="{ 'neutral-tag': row.status === '1' }">
                  {{ row.status === '0' ? '启用' : '停用' }}
                </el-tag>
              </div>
              <span v-if="row.sourceFilename" class="source-name">{{ row.sourceFilename }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="documentType" label="模板分类" min-width="140" />
          <el-table-column label="填写配置" min-width="180">
            <template #default="{ row }">
              <el-tag :type="row.fieldsConfirmed ? 'success' : 'warning'">
                {{ templateState(row) }}
              </el-tag><span v-if="row.sourceVersion" class="source-name">版本 {{ row.sourceVersion }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序号" align="right" width="90" />
          <el-table-column label="操作" width="312" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="canStartDraft(row, types)"
                link
                type="primary"
                :disabled="!!pending"
                @click="router.push({ path: '/drafting', query: { type: row.documentType, templateId: row.id } })"
              >
                起草
              </el-button>
              <el-button v-if="allowed('query')" link :disabled="!!pending" @click="edit(row)">
                {{ allowed("edit") ? "编辑" : "详情" }}
              </el-button>
              <el-button
                v-if="allowed('query') && allowed('edit')"
                link
                :disabled="!!pending"
                @click="edit(row, true)"
              >
                {{ row.sourceVersion ? "核对字段" : "补传原件" }}
              </el-button>
              <el-button v-if="allowed('edit')" link :disabled="!!pending" @click="mutate(row)">
                {{ row.status === "0" ? "停用" : "启用" }}
              </el-button>
              <el-button
                v-if="allowed('remove')"
                link
                type="danger"
                :disabled="!!pending"
                @click="mutate(row, true)"
              >
                删除
              </el-button>
              <span v-if="!allowed('query') && !allowed('edit') && !allowed('remove')">只读</span>
            </template>
          </el-table-column>
        </el-table>
        <AppState
          v-else
          :title="applied.name || applied.type ? '没有匹配的模板' : '暂无模板'"
          description="可调整筛选条件，或上传常用的文书原文件。"
        >
          <el-button v-if="applied.name || applied.type" @click="query(true)">
            清除筛选
          </el-button>
        </AppState>
        <el-pagination
          v-if="total"
          :current-page="page"
          class="pagination"
          :page-size="20"
          :total="total"
          layout="total, prev, pager, next"
          :disabled="loading || !!pending"
          @current-change="changePage"
        />
      </template>
    </section>
    <TemplateEditor
      v-if="open"
      v-model="open"
      :initial="initial"
      :mode="mode"
      :types="types"
      :permissions="permissions"
      @saved="load"
    />
  </section>
</template>

<style scoped>
@import "./management.css";
</style>
