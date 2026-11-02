<script setup lang="ts">
import type { TableColumnsType } from 'ant-design-vue';

import type { DocTemplate } from '#/api/legal/doc-template';

import { computed, onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';

import {
  DownOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  UploadOutlined,
} from '@ant-design/icons-vue';
import {
  Alert,
  Button,
  Dropdown,
  Empty,
  Form,
  FormItem,
  Input,
  Menu,
  MenuItem,
  Modal,
  Pagination,
  Select,
  Space,
  Table,
  Tag,
  TypographyText,
} from 'ant-design-vue';

import {
  documentTypes,
  templateFieldAnalysisResult,
} from '#/api/legal/doc-template';

import TemplateAiIcon from './template-ai-icon.vue';
import TemplateBatchUploadModal from './template-batch-upload-modal.vue';
import TemplateBulkActionsModal from './template-bulk-actions-modal.vue';
import TemplateEditorDrawer from './template-editor-drawer.vue';
import { processingOptions, templateProcessing } from './template-processing';
import TemplateUploadModal from './template-upload-modal.vue';
import { useTemplateFieldAnalysis } from './use-template-field-analysis';
import { useTemplateLibrary } from './use-template-library';

const { hasAccessByCodes } = useAccess();
const canEdit = computed(() => hasAccessByCodes(['legal:doc-template:edit']));
const canRemove = computed(() =>
  hasAccessByCodes(['legal:doc-template:remove']),
);
const canQuery = computed(() => hasAccessByCodes(['legal:doc-template:query']));
const {
  filters,
  page,
  pageSize,
  rows,
  types,
  total,
  busy,
  error,
  actionError,
  pendingId,
  selectedKeys,
  selectedRows,
  filtered,
  processing,
  filterProcessing,
  load,
  query,
  reset,
  detail,
  mutate,
} = useTemplateLibrary();
const fieldAnalysis = useTemplateFieldAnalysis(rows);
const compact = ref(true);
const open = ref(false);
const batchOpen = ref(false);
const uploadOpen = ref(false);
const adding = ref(false);
const initial = ref<Partial<DocTemplate>>({});
const uploadInitial = ref<Partial<DocTemplate>>({});
const fieldOpener = ref<HTMLElement>();
const openingFields = ref('');
const bulkModal = ref<InstanceType<typeof TemplateBulkActionsModal>>();
const filterTypes = computed(() =>
  types.value.map((type) => ({ ...type, disabled: false })),
);
const blocked = computed(() => busy.value || !!pendingId.value || adding.value);
const range = computed(() =>
  total.value
    ? `${(page.value - 1) * pageSize + 1}–${Math.min(
        page.value * pageSize,
        total.value,
      )}`
    : '0',
);
const columns: TableColumnsType<DocTemplate> = [
  {
    title: '模板名称 / 备注',
    key: 'name',
    dataIndex: 'name',
    width: 240,
    fixed: 'left',
  },
  { title: '文书类型', dataIndex: 'documentType', width: 144 },
  { title: '原文件 / 填写配置', key: 'source', width: 232 },
  { title: '使用状态', key: 'status', width: 88 },
  { title: '排序号', dataIndex: 'sortOrder', width: 72, align: 'right' },
  { title: '操作', key: 'action', width: 292, fixed: 'right' },
];
const taskRecoveryHints: Record<string, string> = {
  failed: '解析未完成，可重试或核对已保存字段',
  partial: '解析结果仍需人工核对，原因见问题详情',
  stale: '文件或字段配置已更新，请重新解析后核对',
};
function rowAction(record: DocTemplate) {
  if (fieldAnalysis.active(record)) return { label: '解析中', parse: true };
  const state = templateProcessing(record);
  if (state === 'review') return { label: '核对字段', parse: false };
  if (state === 'confirmed') return { label: '编辑字段', parse: false };
  return { label: state === 'error' ? '重试解析' : 'AI解析字段', parse: true };
}
function processingProblem(record: DocTemplate) {
  return (
    ['failed', 'partial', 'stale'].includes(
      record.fieldAnalysis?.state || '',
    ) || !!fieldAnalysis.errors.value[record.id || '']
  );
}
function sourceState(record: DocTemplate) {
  const task = record.fieldAnalysis;
  if (fieldAnalysis.active(record as DocTemplate))
    return {
      label: task?.phase || '正在启动解析',
      color: 'blue',
      hint: '完成后请核对字段，确认保存后生效',
    };
  if (task && ['failed', 'partial', 'ready', 'stale'].includes(task.state)) {
    return {
      label: task.phase,
      color: ['failed', 'stale'].includes(task.state) ? 'red' : 'orange',
      hint: taskRecoveryHints[task.state] || task.warning,
    };
  }
  if (!record.sourceVersion)
    return {
      label: '仅文字',
      color: 'default',
      hint: '需补传原文件以保留版式',
    };
  if (record.parseStatus === 'uploaded')
    return {
      label: '未解析',
      color: 'default',
      hint: '解析并核对字段后可用于起草',
    };
  if (record.fieldsConfirmed)
    return { label: '字段已确认', color: 'green', hint: '' };
  return { label: '字段待核对', color: 'orange', hint: '解析结果仍需人工核对' };
}
async function add(upload = false) {
  if (blocked.value || !hasAccessByCodes(['legal:doc-template:add'])) return;
  adding.value = true;
  actionError.value = '';
  try {
    types.value = await documentTypes();
    if (!types.value.some((type) => !type.disabled)) {
      actionError.value =
        '暂无启用的文书类型，请先在“模板类型”中新增或启用类型，再创建模板。';
      return;
    }
    if (upload) batchOpen.value = true;
    else {
      initial.value = {};
      open.value = true;
    }
  } catch (error_) {
    actionError.value = (error_ as Error).message || '文书类型加载失败，请重试';
  } finally {
    adding.value = false;
  }
}
async function edit(id: string) {
  if (!canQuery.value || blocked.value) return;
  const current = await detail(id);
  if (current) {
    initial.value = current;
    open.value = true;
  }
}
async function uploadFile(id: string, configure = false) {
  if (!canEdit.value || !canQuery.value || blocked.value) return;
  fieldOpener.value =
    document.activeElement instanceof HTMLElement
      ? document.activeElement
      : undefined;
  let current;
  if (configure) {
    openingFields.value = id;
    pendingId.value = id;
    actionError.value = '';
    try {
      current = await templateFieldAnalysisResult(id);
    } catch (error) {
      actionError.value =
        (error as Error).message || '字段读取失败，请刷新后重试';
    } finally {
      pendingId.value = '';
      openingFields.value = '';
    }
  } else current = await detail(id);
  if (current) {
    uploadInitial.value = { ...current, configureExisting: configure };
    uploadOpen.value = true;
  }
}
function parseFields(record: DocTemplate) {
  if (
    !canEdit.value ||
    blocked.value ||
    fieldAnalysis.active(record as DocTemplate)
  )
    return;
  if (record.fieldsConfirmed || fieldAnalysis.pending(record)) {
    Modal.confirm({
      title: `重新解析“${record.name}”的字段？`,
      content:
        '将生成一份新的待核对建议，替换上次尚未保存的解析建议；已保存并确认的字段配置继续保留，人工核对保存前不会被覆盖。',
      okText: '重新解析',
      cancelText: '取消',
      onOk: () => {
        void fieldAnalysis.start(record, true);
      },
    });
  } else void fieldAnalysis.start(record);
}
function nextAction(record: DocTemplate) {
  if (rowAction(record).parse) parseFields(record);
  else if (record.id) void uploadFile(record.id, true);
}
function confirmAction(
  record: DocTemplate,
  action: 'delete' | 'replace' | 'toggle',
) {
  if (blocked.value || !record.id) return;
  if (
    action === 'delete' ? !canRemove.value : !canEdit.value || !canQuery.value
  )
    return;
  const deleting = action === 'delete';
  const id = record.id;
  const title = {
    delete: `删除“${record.name}”？`,
    replace: `替换“${record.name}”的原文件？`,
    toggle: `${record.status === '0' ? '停用' : '启用'}“${record.name}”？`,
  }[action];
  const content = {
    delete: '删除后不可撤销，该模板不再供新文书选择；已有文书保留。',
    replace:
      '替换将创建新文件版本，需重新核对填写字段。已有文书仍使用原版本；在后续窗口保存后才生效。',
    toggle:
      record.status === '0'
        ? '停用后不再供新的文书起草选择，已有文书保留。'
        : '启用后，已解析并人工核对字段的模板可供文书起草选择；未核对的模板仍需完成字段配置。',
  }[action];
  Modal.confirm({
    title,
    content,
    okText: { delete: '确认删除', replace: '选择替换文件', toggle: '确认' }[
      action
    ],
    cancelText: '取消',
    okButtonProps: { danger: deleting },
    onOk: () =>
      action === 'replace' ? uploadFile(id) : mutate(id, action, record.status),
  });
}
function startBulk(action: 'delete' | 'type') {
  if (blocked.value || selectedRows.value.length === 0) return;
  if (action === 'delete' ? !canRemove.value : !canEdit.value) return;
  void bulkModal.value?.start(selectedRows.value, action);
}
onMounted(load);
</script>

<template>
  <Page>
    <div class="template-library">
      <header class="library-header">
        <div>
          <h1>模板管理</h1>
          <p>维护文书模板、原文件与填写配置，供文书起草使用。</p>
        </div>
        <Space wrap>
          <Button
            v-access:code="['legal:doc-template:add']"
            :disabled="blocked"
            @click="add()"
          >
            <template #icon><PlusOutlined /></template>新增模板
          </Button>
          <Button
            v-access:code="['legal:doc-template:add']"
            type="primary"
            :loading="adding"
            :disabled="busy || !!pendingId"
            @click="add(true)"
          >
            <template #icon><UploadOutlined /></template>上传模板
          </Button>
        </Space>
      </header>

      <section class="library-surface" aria-label="模板检索与列表">
        <Form
          :model="filters"
          layout="inline"
          class="filter-form"
          @finish="query"
        >
          <FormItem label="模板名称" html-for="filter-template-name">
            <Input
              id="filter-template-name"
              v-model:value="filters.name"
              placeholder="输入模板名称"
              allow-clear
              :maxlength="100"
              class="name-filter"
            />
          </FormItem>
          <FormItem label="文书类型" html-for="filter-document-type">
            <Select
              id="filter-document-type"
              v-model:value="filters.type"
              :options="filterTypes"
              placeholder="全部文书类型"
              allow-clear
              class="type-filter"
              aria-label="筛选文书类型"
            />
          </FormItem>
          <Space>
            <Button html-type="submit" :loading="busy" :disabled="!!pendingId">
              <template #icon><SearchOutlined /></template>查询
            </Button>
            <Button :disabled="blocked" @click="reset">重置</Button>
          </Space>
        </Form>

        <Alert
          v-if="actionError"
          :message="actionError"
          type="error"
          show-icon
          closable
          class="action-error"
          role="alert"
          @close="actionError = ''"
        />
        <div class="list-heading">
          <div class="list-title">
            <h2>模板列表</h2>
            <span
              v-if="!error"
              class="muted"
              v-text="`${filtered ? '匹配' : '共'} ${total} 份`"
            ></span>
          </div>
          <Space>
            <Button
              size="small"
              :disabled="blocked"
              :aria-pressed="compact"
              @click="compact = !compact"
            >
              {{ compact ? '舒适密度' : '紧凑密度' }}
            </Button>
            <Button
              size="small"
              :loading="busy"
              :disabled="!!pendingId"
              @click="load"
            >
              <template #icon><ReloadOutlined /></template>刷新
            </Button>
          </Space>
        </div>
        <div
          class="processing-filters"
          role="group"
          aria-label="按处理状态筛选模板"
        >
          <Button
            v-for="option in processingOptions"
            :key="option.value"
            size="small"
            :type="processing === option.value ? 'primary' : 'default'"
            :aria-pressed="processing === option.value"
            :disabled="blocked"
            @click="filterProcessing(option.value)"
          >
            {{ option.label }}
          </Button>
        </div>
        <div v-if="selectedRows.length > 0" class="selection-bar" role="status">
          <span v-text="`已选 ${selectedRows.length} 份（仅当前页）`"></span>
          <Button type="link" :disabled="blocked" @click="selectedKeys = []">
            取消选择
          </Button>
          <div class="selection-actions">
            <Button
              v-access:code="['legal:doc-template:edit']"
              :disabled="blocked"
              @click="startBulk('type')"
            >
              <template #icon>
                <TemplateAiIcon :disabled="blocked" />
              </template>
              <span>AI识别类型</span>
            </Button>
            <Button
              v-access:code="['legal:doc-template:remove']"
              danger
              :disabled="blocked"
              @click="startBulk('delete')"
            >
              批量删除
            </Button>
          </div>
        </div>

        <div v-if="error" class="list-error" role="alert">
          <Alert
            message="模板列表加载失败"
            :description="error"
            type="error"
            show-icon
          />
          <Button :loading="busy" @click="load">重试加载</Button>
        </div>
        <Table
          v-else
          :columns="columns"
          :data-source="rows"
          :loading="busy"
          :pagination="false"
          :row-selection="
            canEdit || canRemove
              ? {
                  selectedRowKeys: selectedKeys,
                  onChange: (keys) => (selectedKeys = keys),
                  getCheckboxProps: () => ({ disabled: blocked }),
                }
              : undefined
          "
          :scroll="{ x: 1100 }"
          :size="compact ? 'small' : 'middle'"
          row-key="id"
        >
          <template #emptyText>
            <Empty
              :image="Empty.PRESENTED_IMAGE_SIMPLE"
              :description="filtered ? '没有匹配的模板' : '还没有文书模板'"
            >
              <p class="muted">
                {{
                  filtered
                    ? '试试其他名称或文书类型，或清空筛选条件。'
                    : '上传默认启用；解析并核对字段后可用于起草。'
                }}
              </p>
              <Button v-if="filtered" @click="reset">清空筛选</Button>
              <Button
                v-else
                v-access:code="['legal:doc-template:add']"
                @click="add(true)"
              >
                上传第一份模板
              </Button>
            </Empty>
          </template>
          <template #bodyCell="{ column, record }">
            <div v-if="column.key === 'name'" class="template-name-cell">
              <span class="template-name">{{ record.name }}</span>
              <span v-if="record.remark" class="muted template-remark">{{
                record.remark
              }}</span>
            </div>
            <div v-else-if="column.key === 'source'" class="source-cell">
              <Space :size="4" wrap aria-live="polite">
                <span v-if="record.sourceVersion" class="file-format">{{
                  record.sourceFormat?.toUpperCase() || '原文件'
                }}</span>
                <Tag :color="sourceState(record as DocTemplate).color">
                  {{ sourceState(record as DocTemplate).label }}
                </Tag>
              </Space>
              <span
                v-if="record.sourceFilename"
                class="source-filename muted"
                :title="record.sourceFilename"
                v-text="record.sourceFilename"
              ></span>
              <span
                v-if="sourceState(record as DocTemplate).hint"
                class="muted"
                v-text="sourceState(record as DocTemplate).hint"
              ></span>
              <span
                v-if="fieldAnalysis.errors.value[record.id]"
                class="muted"
                role="alert"
                v-text="fieldAnalysis.errors.value[record.id]"
              ></span>
              <details
                v-if="processingProblem(record as DocTemplate)"
                class="processing-details muted"
              >
                <summary :aria-label="`${record.name}的问题详情`">
                  问题详情
                </summary>
                <div class="problem-content">
                  <p>
                    {{
                      record.fieldAnalysis?.warning ||
                      fieldAnalysis.errors.value[record.id] ||
                      sourceState(record as DocTemplate).hint
                    }}
                  </p>
                  <template
                    v-if="record.sourceVersion || record.fieldAnalysis?.taskId"
                  >
                    <p>如需协助，可复制以下标识用于排查。</p>
                    <dl class="diagnostic-identifiers">
                      <div v-if="record.sourceVersion">
                        <dt>文件标识</dt>
                        <dd>
                          <TypographyText
                            :copyable="{ text: record.sourceVersion }"
                          >
                            {{ record.sourceVersion }}
                          </TypographyText>
                        </dd>
                      </div>
                      <div v-if="record.fieldAnalysis?.taskId">
                        <dt>解析任务编号</dt>
                        <dd>
                          <TypographyText
                            :copyable="{ text: record.fieldAnalysis.taskId }"
                          >
                            {{ record.fieldAnalysis.taskId }}
                          </TypographyText>
                        </dd>
                      </div>
                    </dl>
                  </template>
                </div>
              </details>
            </div>
            <Tag
              v-else-if="column.key === 'status'"
              :color="record.status === '0' ? 'green' : 'default'"
            >
              {{ record.status === '0' ? '启用' : '停用' }}
            </Tag>
            <Space
              v-else-if="column.key === 'action'"
              :size="0"
              class="row-actions"
            >
              <Button
                v-if="
                  record.sourceVersion &&
                  canEdit &&
                  (rowAction(record as DocTemplate).parse || canQuery)
                "
                type="link"
                class="primary-row-action"
                :loading="
                  fieldAnalysis.active(record as DocTemplate) ||
                  openingFields === record.id
                "
                :disabled="blocked"
                @click="nextAction(record as DocTemplate)"
              >
                <template #icon>
                  <TemplateAiIcon
                    v-if="rowAction(record as DocTemplate).parse"
                    :disabled="blocked"
                  />
                </template>
                {{ rowAction(record as DocTemplate).label }}
              </Button>
              <Button
                v-access:code="['legal:doc-template:query']"
                type="link"
                :loading="
                  pendingId === record.id && openingFields !== record.id
                "
                :disabled="blocked && pendingId !== record.id"
                @click="edit(record.id)"
              >
                {{ canEdit ? '基本信息' : '查看详情' }}
              </Button>
              <Dropdown
                v-if="(canQuery && canEdit) || canRemove"
                :trigger="['click']"
                placement="bottomRight"
              >
                <Button
                  type="link"
                  :disabled="blocked"
                  :aria-label="`${record.name}的更多操作`"
                >
                  更多<DownOutlined />
                </Button>
                <template #overlay>
                  <Menu>
                    <MenuItem
                      v-if="record.sourceVersion && canEdit"
                      key="reparse"
                      :disabled="
                        blocked || fieldAnalysis.active(record as DocTemplate)
                      "
                      @click="parseFields(record as DocTemplate)"
                    >
                      重新解析字段
                    </MenuItem>
                    <MenuItem
                      v-if="
                        record.sourceVersion &&
                        canEdit &&
                        canQuery &&
                        rowAction(record as DocTemplate).parse
                      "
                      key="fields"
                      :disabled="fieldAnalysis.active(record as DocTemplate)"
                      @click="uploadFile(record.id, true)"
                    >
                      核对已保存字段
                    </MenuItem>
                    <MenuItem
                      v-if="canEdit && canQuery"
                      key="replace"
                      @click="confirmAction(record as DocTemplate, 'replace')"
                    >
                      {{ record.sourceVersion ? '替换文件' : '补传原文件' }}
                    </MenuItem>
                    <MenuItem
                      v-if="canEdit && canQuery"
                      key="toggle"
                      @click="confirmAction(record as DocTemplate, 'toggle')"
                    >
                      {{ record.status === '0' ? '停用模板' : '启用模板' }}
                    </MenuItem>
                    <MenuItem
                      v-if="canRemove"
                      key="delete"
                      danger
                      @click="confirmAction(record as DocTemplate, 'delete')"
                    >
                      删除模板
                    </MenuItem>
                  </Menu>
                </template>
              </Dropdown>
            </Space>
          </template>
        </Table>
        <footer v-if="!error" class="list-footer">
          <span
            class="muted"
            v-text="`第 ${range} 份，共 ${total} 份 · 每页 ${pageSize} 份`"
          ></span>
          <Pagination
            v-model:current="page"
            :total="total"
            :page-size="pageSize"
            :show-size-changer="false"
            :disabled="blocked"
            @change="load"
          />
        </footer>
      </section>
      <TemplateEditorDrawer
        v-model:open="open"
        :initial="initial"
        :types="types"
        :can-write="
          initial.id ? canEdit : hasAccessByCodes(['legal:doc-template:add'])
        "
        @saved="load"
      />
      <TemplateBatchUploadModal
        v-model:open="batchOpen"
        :types="types"
        @saved="load"
      />
      <TemplateBulkActionsModal ref="bulkModal" :types="types" :reload="load" />
      <TemplateUploadModal
        v-model:open="uploadOpen"
        :types="types"
        :initial="uploadInitial"
        :return-focus="fieldOpener"
        @saved="load"
      />
    </div>
  </Page>
</template>

<style scoped>
.template-library {
  min-width: 0;
  color: hsl(var(--foreground));
}

.library-header {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.library-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.4;
}

.library-header p {
  margin: 8px 0 0;
  line-height: 1.6;
  color: hsl(var(--muted-foreground));
}

.library-surface {
  min-width: 0;
  padding: 20px;
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: var(--radius);
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 0;
  align-items: flex-start;
  padding-bottom: 20px;
  border-bottom: 1px solid hsl(var(--border));
}

.name-filter {
  width: 240px;
}

.type-filter {
  width: 200px;
}

.list-heading,
.list-title,
.list-footer,
.selection-bar {
  display: flex;
  gap: 12px;
  align-items: center;
}

.list-heading {
  justify-content: space-between;
  padding: 16px 0;
}

.list-title h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.muted {
  font-size: 13px;
  line-height: 1.6;
  color: hsl(var(--muted-foreground));
}

.selection-bar {
  flex-wrap: wrap;
  padding: 12px;
  margin-bottom: 12px;
  background: hsl(var(--muted));
  border: 1px solid hsl(var(--border));
  border-radius: var(--radius);
}

.selection-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
}

.template-name-cell,
.source-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  overflow-wrap: anywhere;
}

.template-name {
  font-weight: 500;
}

.template-remark {
  white-space: pre-wrap;
}

.diagnostic-identifiers {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
}

.diagnostic-identifiers dd {
  margin: 0;
  font-family: monospace;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.source-filename {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.processing-details summary {
  width: fit-content;
  cursor: pointer;
}

.processing-details summary:focus-visible {
  outline: 2px solid hsl(var(--ring));
  outline-offset: 2px;
}

.problem-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 8px;
}

.problem-content p {
  margin: 0;
}

.processing-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.primary-row-action {
  font-weight: 600;
}

.file-format {
  margin-right: 4px;
  font-size: 12px;
  font-weight: 600;
}

.row-actions {
  white-space: nowrap;
}

.list-footer {
  flex-wrap: wrap;
  justify-content: space-between;
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
  margin-top: 16px;
}

@media (max-width: 1100px) {
  .library-header {
    flex-wrap: wrap;
    align-items: flex-start;
  }

  .library-surface {
    padding: 16px;
  }
}

@media (max-width: 640px) {
  .name-filter,
  .type-filter {
    width: 180px;
  }

  .selection-actions {
    margin-left: 0;
  }
}
</style>
