<script setup lang="ts">
import type { TableColumnsType } from 'ant-design-vue';

import type { DocTemplateType } from '#/api/legal/doc-template-type';

import { computed, onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';

import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue';
import {
  Alert,
  Button,
  Empty,
  Form,
  FormItem,
  Input,
  Modal,
  Pagination,
  Select,
  Space,
  Table,
  Tag,
} from 'ant-design-vue';

import TypeEditor from './type-editor.vue';
import { useTemplateTypes } from './use-template-types';

const { hasAccessByCodes } = useAccess();
const allowed = (action: string) =>
  hasAccessByCodes([`legal:doc-template-type:${action}`]);
const {
  filters,
  visibleRows,
  loading,
  error,
  actionError,
  pendingId,
  page,
  filtered,
  load,
  query,
  reset,
  mutate,
} = useTemplateTypes();
const blocked = computed(() => loading.value || !!pendingId.value);
const open = ref(false);
const initial = ref<Partial<DocTemplateType>>({});
const canWrite = computed(() => allowed(initial.value.id ? 'edit' : 'add'));
const columns: TableColumnsType<DocTemplateType> = [
  { title: '类型名称', dataIndex: 'name', width: 224, fixed: 'left' },
  { title: '来源', key: 'source', width: 100 },
  { title: '排序号', dataIndex: 'sortOrder', width: 88, align: 'right' },
  { title: '使用状态', key: 'status', width: 104 },
  { title: '备注', key: 'remark' },
  { title: '操作', key: 'action', width: 192, fixed: 'right' },
];
function edit(id?: string) {
  const row = id
    ? visibleRows.value.find((value) => value.id === id)
    : undefined;
  if (id && !row) return;
  if (blocked.value || !allowed(row ? 'edit' : 'add')) return;
  initial.value = row ? { ...row } : {};
  open.value = true;
}
function confirm(id: string, action: 'delete' | 'toggle') {
  const row = visibleRows.value.find((value) => value.id === id);
  if (!row) return;
  if (
    blocked.value ||
    !allowed(action === 'delete' ? 'remove' : 'edit') ||
    (action === 'delete' && row.builtin)
  )
    return;
  const removing = action === 'delete';
  let label = row.status === '0' ? '停用' : '启用';
  if (removing) label = '删除';
  let content = '启用后，该类型可供新增模板和文书起草选择。';
  if (removing)
    content =
      '删除后不可撤销；该类型下仍有关联模板时，系统会拒绝删除。请先调整关联模板的类型。';
  else if (row.status === '0')
    content =
      '停用后，该类型不再供新增模板和新的文书起草选择；已有模板和文书保留。';
  Modal.confirm({
    title: `${label}“${row.name}”？`,
    content,
    okText: `确认${label}`,
    cancelText: '取消',
    okButtonProps: { danger: removing },
    onOk: () => mutate(row, action),
  });
}
onMounted(load);
</script>

<template>
  <Page>
    <div class="type-workspace">
      <header class="type-header">
        <div>
          <h1>模板类型</h1>
          <p>维护文书模板的分类、排序与使用范围。</p>
        </div>
        <Button
          v-if="allowed('add')"
          type="primary"
          :disabled="blocked"
          @click="edit()"
        >
          <template #icon><PlusOutlined aria-hidden="true" /></template>
          <span>新增类型</span>
        </Button>
      </header>
      <section class="type-surface" aria-label="模板类型检索与列表">
        <Form
          :model="filters"
          layout="inline"
          class="type-filters"
          @finish="query"
        >
          <FormItem label="类型名称" html-for="filter-type-name">
            <Input
              id="filter-type-name"
              v-model:value="filters.name"
              class="name-filter"
              placeholder="输入类型名称"
              allow-clear
              :maxlength="50"
            />
          </FormItem>
          <FormItem label="来源" html-for="filter-type-source">
            <Select
              id="filter-type-source"
              v-model:value="filters.source"
              class="select-filter"
              placeholder="全部来源"
              allow-clear
              :options="[
                { value: 'builtin', label: '内置' },
                { value: 'custom', label: '自定义' },
              ]"
            />
          </FormItem>
          <FormItem label="使用状态" html-for="filter-type-status">
            <Select
              id="filter-type-status"
              v-model:value="filters.status"
              class="select-filter"
              placeholder="全部状态"
              allow-clear
              :options="[
                { value: '0', label: '启用' },
                { value: '1', label: '停用' },
              ]"
            />
          </FormItem>
          <Space>
            <Button
              html-type="submit"
              :loading="loading"
              :disabled="!!pendingId"
            >
              <template #icon><SearchOutlined aria-hidden="true" /></template>
              <span>查询</span>
            </Button>
            <Button :disabled="blocked" @click="reset">
              <template #default> 重置 </template>
            </Button>
          </Space>
        </Form>
        <div class="list-heading">
          <div class="list-title">
            <h2>类型列表</h2>
            <p v-if="!error && !loading" class="muted">
              共 {{ visibleRows.length }} 个{{ filtered ? '匹配类型' : '类型' }}
            </p>
          </div>
          <Button :loading="loading" :disabled="!!pendingId" @click="load">
            <template #icon><ReloadOutlined aria-hidden="true" /></template>
            <span>刷新</span>
          </Button>
        </div>
        <p class="type-guidance">
          启用的类型可用于新增模板和文书起草。内置类型名称固定；自定义类型改名后，关联模板将同步归类。
        </p>
        <div v-if="error" class="list-error" role="alert">
          <Alert
            message="模板类型加载失败"
            :description="error"
            type="error"
            show-icon
          />
          <Button :loading="loading" @click="load">
            <template #default> 重新加载 </template>
          </Button>
        </div>
        <template v-else>
          <Alert
            v-if="actionError"
            class="action-error"
            message="操作未完成"
            :description="actionError"
            type="error"
            show-icon
            role="alert"
          />
          <Table
            :columns="columns"
            :data-source="visibleRows.slice((page - 1) * 20, page * 20)"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 960 }"
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <p v-if="column.dataIndex === 'name'" class="type-name">
                {{ record.name }}
              </p>
              <Tag v-else-if="column.key === 'source'">
                <template #default>
                  {{ record.builtin ? '内置' : '自定义' }}
                </template>
              </Tag>
              <Tag
                v-else-if="column.key === 'status'"
                :color="record.status === '0' ? 'green' : 'default'"
              >
                <template #default>
                  {{ record.status === '0' ? '启用' : '停用' }}
                </template>
              </Tag>
              <p v-else-if="column.key === 'remark'" class="type-remark">
                {{ record.remark }}
              </p>
              <Space
                v-else-if="column.key === 'action'"
                :size="4"
                class="row-actions"
              >
                <Button
                  v-if="allowed('edit')"
                  type="link"
                  :disabled="blocked"
                  @click="edit(record.id)"
                >
                  <template #default> 编辑 </template>
                </Button>
                <Button
                  v-if="allowed('edit')"
                  type="link"
                  :loading="pendingId === record.id"
                  :disabled="blocked"
                  @click="confirm(record.id, 'toggle')"
                >
                  <template #default>
                    {{ record.status === '0' ? '停用' : '启用' }}
                  </template>
                </Button>
                <Button
                  v-if="!record.builtin && allowed('remove')"
                  type="link"
                  danger
                  :disabled="blocked"
                  @click="confirm(record.id, 'delete')"
                >
                  <template #default> 删除 </template>
                </Button>
                <p
                  v-if="
                    !allowed('edit') && (record.builtin || !allowed('remove'))
                  "
                  class="muted"
                >
                  只读
                </p>
              </Space>
            </template>
            <template #emptyText>
              <Empty
                :image="Empty.PRESENTED_IMAGE_SIMPLE"
                :description="filtered ? '没有匹配的模板类型' : '暂无模板类型'"
              >
                <Button v-if="filtered" @click="reset">
                  <template #default> 清除筛选 </template>
                </Button>
                <Button v-else-if="allowed('add')" @click="edit()">
                  <template #default> 新增第一个类型 </template>
                </Button>
              </Empty>
            </template>
          </Table>
          <footer v-if="visibleRows.length > 0" class="list-footer">
            <span class="muted">排序号越小越靠前；同序号按系统顺序展示。</span>
            <Pagination
              v-model:current="page"
              :total="visibleRows.length"
              :page-size="20"
              :show-size-changer="false"
              :disabled="blocked"
            />
          </footer>
        </template>
      </section>
      <TypeEditor
        v-model:open="open"
        :initial="initial"
        :can-write="canWrite"
        @saved="load"
      />
    </div>
  </Page>
</template>

<style scoped>
.type-workspace {
  min-width: 0;
  color: hsl(var(--foreground));
}

.type-header,
.list-heading,
.list-title,
.list-footer {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.type-header {
  margin-bottom: 20px;
}

.type-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.4;
}

.type-header p {
  margin: 8px 0 0;
  line-height: 1.6;
  color: hsl(var(--muted-foreground));
}

.type-surface {
  min-width: 0;
  padding: 20px;
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: var(--radius);
}

.type-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 0;
  padding-bottom: 20px;
  border-bottom: 1px solid hsl(var(--border));
}

.name-filter {
  width: 200px;
}

.select-filter {
  width: 120px;
}

.list-heading {
  padding: 16px 0 8px;
}

.list-title {
  flex-wrap: wrap;
  justify-content: flex-start;
}

.list-title h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.muted {
  margin: 0;
}

.muted,
.type-guidance {
  font-size: 13px;
  line-height: 1.6;
  color: hsl(var(--muted-foreground));
}

.type-guidance {
  margin: 0 0 16px;
}

.type-name {
  margin: 0;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.type-remark {
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.row-actions {
  white-space: nowrap;
}

.list-footer {
  flex-wrap: wrap;
  padding-top: 16px;
}

.list-error {
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: flex-start;
  padding: 24px 0;
}

.action-error {
  margin-bottom: 16px;
}

@media (max-width: 1100px) {
  .type-header {
    flex-wrap: wrap;
  }

  .type-surface {
    padding: 16px;
  }
}
</style>
