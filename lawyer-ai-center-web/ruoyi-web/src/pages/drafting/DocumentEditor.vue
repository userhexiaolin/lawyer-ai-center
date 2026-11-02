<script setup lang="ts">
import type { DocumentFormat } from '@/api/document';
import { computed, onScopeDispose, ref, watch } from 'vue';
import { documentBlob, downloadDocument, loadDocumentDraft, saveDocumentDraft } from '@/api/document';
import LegalAnswer from '@/components/LegalAnswer.vue';
import { useDraftActivity } from '@/hooks/useDraftWorkspace';
import { useUserStore } from '@/stores';
import { presentMessage } from '@/utils/messagePresentation';
import CalculationDetails from './CalculationDetails.vue';
import {
  deliveryText,
  documentDeliveryIssues,
  documentPresentation,
  documentText,
  editDocumentPresentation,
  editTemplateBlock,
  templateDocument,
  templateFillState,
} from './documentText';

const props = withDefaults(
  defineProps<{
    modelValue: string;
    title: string;
    sessionId?: string;
    busy?: boolean;
    persistedContent?: string;
    warnings?: string[];
  }>(),
  { warnings: () => [] },
);
const emit = defineEmits<{ 'update:modelValue': [string]; 'saved': [] }>();
const user = useUserStore();
const editing = ref(false);
const action = ref('');
const savedContent = ref(props.persistedContent ?? props.modelValue);
const dirty = computed(() => props.modelValue !== savedContent.value);
const saveState = computed(() => action.value === 'save' ? '正在保存草稿…' : dirty.value ? '正文有未保存修改' : '正文无新修改');
useDraftActivity(() => dirty.value, () => action.value === 'save');
const message = ref('');
const error = ref('');
const legacyLayout = ref(false);
const text = computed({
  get: () => props.modelValue,
  set: (s: string) => emit('update:modelValue', s),
});
const structured = computed(() => templateDocument(text.value));
const protocolOnly = computed(() => presentMessage(text.value).structured && !structured.value);
const fillState = computed(() => templateFillState(text.value));
const content = computed(() =>
  structured.value ? structured.value.blocks.map(block => documentPresentation(block.text, block.fields)).join('\n\n') : documentPresentation(deliveryText(text.value, props.warnings)),
);
const displayBlocks = computed(() => structured.value?.blocks.map(block => ({ ...block, text: documentPresentation(block.text, block.fields) })) || []);
const editingText = computed({ get: () => documentPresentation(text.value), set: value => text.value = editDocumentPresentation(text.value, value) });
const format = ref<DocumentFormat>('docx');
const formats = { docx: 'Word（DOCX）', pdf: 'PDF', md: 'Markdown', txt: 'TXT' };
const previewUrl = ref('');
function updateBlock(id: string, value: string) {
  const block = structured.value?.blocks.find(block => block.id === id);
  if (block)
    text.value = editTemplateBlock(text.value, id, editDocumentPresentation(block.text, value, block.fields));
}
function clearPreview() {
  if (previewUrl.value)
    URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = '';
}
onScopeDispose(clearPreview);
const locked = computed(() => props.busy || !!action.value || protocolOnly.value);
const issues = computed(() => documentDeliveryIssues(text.value));
const otherIssues = computed(() => issues.value.filter(issue => issue !== fillState.value?.description));
const deliveryLocked = computed(() => locked.value || issues.value.length > 0);
let epoch = 0;
onScopeDispose(() => epoch++);
watch(
  () => props.sessionId,
  () => {
    epoch++;
    savedContent.value = props.persistedContent ?? props.modelValue;
    editing.value = false;
    message.value = '';
    error.value = '';
    action.value = '';
    legacyLayout.value = false;
    clearPreview();
  },
);
watch(() => props.persistedContent, (content) => {
  if (content !== undefined)
    savedContent.value = content;
});
watch(
  () => props.modelValue,
  () => {
    message.value = '';
    clearPreview();
  },
);
watch(
  () => user.token,
  () => {
    epoch++;
    action.value = '';
    error.value = '';
    message.value = '';
    legacyLayout.value = false;
    clearPreview();
  },
  { flush: 'sync' },
);
async function run(kind: string, job: () => Promise<unknown>) {
  if ((kind === 'save' ? locked.value : deliveryLocked.value) || !text.value.trim())
    return;
  const id = epoch;
  action.value = kind;
  error.value = '';
  message.value = '';
  legacyLayout.value = false;
  try {
    await job();
    if (id === epoch) {
      message.value
        = kind === 'save'
          ? dirty.value ? '本次草稿已保存，当前正文还有未保存修改' : '草稿已保存'
          : kind === 'copy'
            ? '已复制全文'
            : kind === 'preview'
              ? 'PDF版式预览已生成'
              : `${formats[format.value]}已下载`;
      if (kind === 'save')
        emit('saved');
    }
  }
  catch (e) {
    if (id === epoch) {
      error.value = (e as Error).message || '操作失败，请重试';
      legacyLayout.value = /历史模板|未保存原文件|仅保存.*正文/.test(error.value);
    }
  }
  finally {
    if (id === epoch)
      action.value = '';
  }
}
async function save() {
  if (props.sessionId) {
    const session = props.sessionId;
    const snapshot = text.value;
    const token = user.token;
    await saveDocumentDraft(session, documentText(snapshot));
    let saved;
    try {
      saved = await loadDocumentDraft(session);
    }
    catch (reason) {
      throw new Error(`保存请求已完成，但未能读取最新草稿确认：${(reason as Error).message}。当前修改已保留，请重试保存或稍后恢复核对。`);
    }
    if (props.sessionId === session && user.token === token) {
      if (text.value === snapshot)
        text.value = saved.content;
      savedContent.value = saved.content;
    }
  }
}
async function copy() {
  if (structured.value?.calculationResults?.some(result => result.status === 'stale'))
    throw new Error('计算所在段落已编辑，请先保存草稿完成校验后再复制');
  await navigator.clipboard.writeText(presentMessage(content.value).text);
}
function exportContent() {
  return structured.value ? text.value : content.value;
}
async function download(layout?: 'standard') {
  await downloadDocument({
    title: props.title,
    content: exportContent(),
    sessionId: props.sessionId,
    format: format.value,
    ...(layout ? { layout } : {}),
  });
}
async function preview() {
  const id = epoch;
  const snapshot = text.value;
  const title = props.title;
  const blob = await documentBlob({
    title: props.title,
    content: exportContent(),
    sessionId: props.sessionId,
    format: 'pdf',
  });
  if (id !== epoch)
    return;
  if (snapshot !== text.value || title !== props.title)
    throw new Error('正文已变化，请重新生成 PDF 版式预览');
  clearPreview();
  previewUrl.value = URL.createObjectURL(blob);
}
</script>

<template>
  <section class="document-result yl-panel" aria-label="文书正文">
    <div class="editor-header" :class="{ 'editor-header--editing': editing }">
      <el-space wrap class="result-toolbar">
        <el-radio-group v-model="editing" :disabled="locked">
          <el-radio-button :value="false">
            预览
          </el-radio-button><el-radio-button :value="true">
            编辑正文
          </el-radio-button>
        </el-radio-group><span v-if="busy" role="status">正在处理文书…</span>
        <span v-else-if="text.trim()" role="status">AI 草稿 · 待人工核对</span>
      </el-space>
      <el-button
        :type="editing ? 'primary' : 'default'"
        :disabled="locked || !text.trim() || !sessionId"
        :loading="action === 'save'"
        @click="run('save', save)"
      >
        保存草稿
      </el-button>
      <p v-if="text.trim()" class="document-save-status" role="status" aria-live="polite">
        {{ saveState }}
      </p>
    </div>
    <details
      v-if="fillState"
      class="fill-review"
      :open="fillState.status !== 'complete'"
      aria-label="字段填充核对"
    >
      <summary>
        字段填充核对 · {{ fillState.title }}
        <span v-if="fillState.totalFields !== undefined" class="fill-count">
          （已填写 {{ fillState.filledFields }} / {{ fillState.totalFields }}）
        </span>
      </summary>
      <p :role="fillState.status === 'complete' ? 'status' : 'alert'">
        {{ fillState.description }}
      </p>
      <ul v-if="fillState.issues.length">
        <li v-for="(issue, index) in fillState.issues" :key="`${issue.fieldId}-${index}`">
          <strong>{{ issue.label || '未命名字段' }}</strong>：{{
            issue.reason === 'template_configuration'
              ? '模板填写位尚未配置，请核对模板；不能修改固定文字。'
              : '来源尚未匹配或资料待补充，请核对本次起草要素。'
          }}
        </li>
      </ul>
    </details>
    <el-alert
      v-for="warning in warnings"
      :key="warning"
      :title="warning"
      type="warning"
      :closable="false"
    />
    <el-alert v-for="issue in otherIssues" :key="issue" :title="issue" type="error" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="protocolOnly" title="该条记录暂不能作为可编辑的文书正文，请先核对起草结果。" type="info" :closable="false" />
    <CalculationDetails :results="structured?.calculationResults || []" />
    <div v-if="legacyLayout">
      <p>
        可以按标准版式下载当前草稿；标准版式不保留原模板字体、字号和排版。需要原模板格式时，在模板管理中为对应模板补传一次原文件，再按该模板重新起草。
      </p>
      <el-button :disabled="deliveryLocked" @click="run('download', () => download('standard'))">
        按标准版式下载当前正文（{{ formats[format] }}）
      </el-button>
    </div>
    <p v-if="message" role="status">
      {{ message }}
    </p>
    <el-form v-if="editing && structured" label-position="top">
      <p>
        仅填写模板已有的空白，不能改写模板固定文字。未填写处沿用原模板的空白或下划线，仍可保存或下载后补充。保存与下载时会按原模板校验。
      </p>
      <el-form-item
        v-for="block in displayBlocks"
        :key="block.id"
        :label="block.editable ? '模板填充区域' : '模板固定文字'"
      >
        <el-input
          :model-value="block.text"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 12 }"
          :readonly="!block.editable"
          :disabled="locked"
          @update:model-value="(value) => updateBlock(block.id, value)"
        />
      </el-form-item>
    </el-form>
    <el-input
      v-else-if="editing && !protocolOnly"
      v-model="editingText"
      type="textarea"
      :rows="24"
      aria-label="编辑文书正文"
      :disabled="locked"
    />
    <article v-else class="document-paper">
      <template v-if="structured">
        <p v-for="block in displayBlocks" :key="block.id" style="white-space: pre-wrap">
          {{ block.text }}
        </p>
      </template><LegalAnswer v-else-if="text.trim()" :content="content" />
      <p v-else>
        正文将在这里显示。生成失败后也可切换编辑，补充已收到的内容。
      </p>
    </article>
    <el-space wrap class="result-toolbar">
      <el-select v-model="format" :disabled="locked" aria-label="下载格式" style="width: 170px">
        <el-option
          v-for="(label, value) in formats"
          :key="value"
          :value="value"
          :label="label"
        />
      </el-select><el-button
        :type="editing ? 'default' : 'primary'"
        :disabled="deliveryLocked || !text.trim()"
        :loading="action === 'download'"
        @click="run('download', download)"
      >
        下载 {{ formats[format] }}
      </el-button><el-button
        :disabled="deliveryLocked || !text.trim()"
        :loading="action === 'preview'"
        @click="run('preview', preview)"
      >
        预览 PDF 版式
      </el-button><el-button :disabled="deliveryLocked || !text.trim()" @click="run('copy', copy)">
        复制全文
      </el-button>
    </el-space>
    <el-dialog
      :model-value="!!previewUrl"
      title="草稿 PDF 版式预览"
      width="90%"
      @close="clearPreview"
    >
      <iframe
        v-if="previewUrl"
        :src="previewUrl"
        title="草稿PDF"
        style="width: 100%; height: 75vh; border: 0"
      />
    </el-dialog>
    <p class="hint">
      页面为内容预览，实际字体和分页请查看PDF版式预览。使用原文件模板时，Word、PDF沿用模板版式；标准起草采用标准版式。Markdown、TXT输出文字和结构。各格式使用当前草稿，下载时不重新起草。手工修改后请保存草稿并核对校验结果。
    </p>
  </section>
</template>

<style scoped>
.document-result {
  margin-top: 20px;
}
.result-toolbar {
  margin: 12px 0;
}
.editor-header {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yl-space-2) var(--yl-space-3);
  align-items: center;
  justify-content: space-between;
  padding: var(--yl-space-2) 0;
  background: var(--yl-color-surface);
}
.editor-header--editing {
  position: sticky;
  top: 0;
  z-index: var(--el-index-normal);
  border-bottom: 1px solid var(--yl-color-border);
}
.document-save-status {
  width: 100%;
  margin: 0;
  font-size: var(--yl-text-sm);
  color: var(--yl-color-text-muted);
}
.fill-review {
  padding: 12px 16px;
  margin: 12px 0;
  overflow-wrap: anywhere;
  border: 1px solid var(--yl-color-border);
  border-radius: 8px;
}
.fill-review summary {
  font-weight: 600;
  cursor: pointer;
}
.fill-review p,
.fill-review ul {
  margin: 12px 0 0;
  line-height: 1.7;
}
.fill-review ul {
  padding-left: 20px;
}
.fill-review li + li {
  margin-top: 8px;
}
.fill-count {
  font-weight: 400;
}
.document-paper {
  box-sizing: border-box;
  max-width: 100%;
  min-height: 260px;
  padding: 40px clamp(20px, 5vw, 70px);
  line-height: 1.9;
  color: var(--yl-color-paper-text);
  overflow-wrap: anywhere;
  background: var(--yl-color-paper);
  border: 1px solid var(--yl-color-border);
  border-radius: 8px;
}
.document-paper :deep(h1) {
  font-size: 26px;
  text-align: center;
}
.document-paper :deep(h2) {
  font-size: 21px;
}
.document-paper :deep(pre) {
  white-space: pre-wrap;
}
.hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.document-paper :deep(.elx-xmarkdown-container) {
  color: var(--yl-color-paper-text);
  background: transparent;
}
</style>
