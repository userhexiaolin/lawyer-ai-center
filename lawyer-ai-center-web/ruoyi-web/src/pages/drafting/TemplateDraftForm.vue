<script setup lang="ts">
import type { TemplateFieldEdit } from './templateForm';
import type { TemplateFormInput, TemplateFormSchema } from '@/api/document/templates';
import { ElMessageBox } from 'element-plus';
import { computed, nextTick, onScopeDispose, ref, watch } from 'vue';
import { composeTemplate, templateForm } from '@/api/document/templates';
import { readMediaApiResult } from '@/api/media/response';
import { create_session } from '@/api/session';
import CaseAssociation from '@/components/CaseAssociation.vue';
import { useDraftActivity } from '@/hooks/useDraftWorkspace';
import { sessionIdFrom } from '@/pages/contract-review/workflow';
import { useUserStore } from '@/stores';
import ContractUpload from './ContractUpload.vue';
import { documentPresentation } from './documentText';
import { generationError } from './generation';
import { emptyTemplateValues, mergeTemplateExtraction } from './templateForm';
import { useContractElements } from './useContractElements';

const props = defineProps<{ type: string; templateId: string; sessionId?: string; restored?: TemplateFormInput; hasDraft?: boolean }>();
const emit = defineEmits<{ busy: [boolean]; generated: [content: string, sessionId: string] }>();
const user = useUserStore();
const reader = useContractElements();
const schema = ref<TemplateFormSchema>();
const values = ref<TemplateFieldEdit[]>([]);
const sourceText = ref('');
const sourceName = ref('');
const sourceUrl = ref('');
const caseId = ref('');
const loading = ref(false);
const extracting = ref(false);
const composing = ref(false);
const createdSession = ref('');
const error = ref('');
const errorAnchor = ref<HTMLElement>();
const notice = ref('');
const stale = ref(false);
const nextSchema = ref<TemplateFormSchema>();
const previousValues = ref<{ label: string; value: string }[]>([]);
const inputSnapshot = () => JSON.stringify([values.value, sourceText.value, sourceName.value, props.sessionId || createdSession.value ? '' : caseId.value, previousValues.value]);
const savedInput = ref('');
const navigation = useDraftActivity(
  () => (!props.sessionId && !createdSession.value && !!caseId.value)
    || (!!(values.value.some(row => row.value.trim()) || sourceName.value || sourceText.value || previousValues.value.some(row => row.value)) && inputSnapshot() !== savedInput.value),
  () => extracting.value || composing.value,
);
let ignoredRestore: TemplateFormInput | undefined;
let appliedRestore: TemplateFormInput | undefined;
const busy = computed(() => loading.value || extracting.value || composing.value);
const filled = computed(() => values.value.filter(row => row.value.trim()).length);
const cache = new Map<string, { schema: TemplateFormSchema; values: TemplateFieldEdit[]; sourceText: string; sourceName: string; sourceUrl: string; baseline: string }>();
let epoch = 0;
let currentKey = '';
let disposed = false;
function active(version: number, token: string | undefined) {
  return !disposed && version === epoch && token === user.token;
}
watch(error, async (message) => {
  if (!message)
    return;
  await nextTick();
  if (!disposed && error.value === message) {
    errorAnchor.value?.scrollIntoView({ behavior: 'auto', block: 'nearest' });
    errorAnchor.value?.focus({ preventScroll: true });
  }
});
function remember() {
  if (schema.value && currentKey)
    cache.set(currentKey, { schema: schema.value, values: values.value, sourceText: sourceText.value, sourceName: sourceName.value, sourceUrl: sourceUrl.value, baseline: savedInput.value });
}
async function load() {
  const version = ++epoch;
  const token = user.token;
  reader.stop();
  extracting.value = false;
  composing.value = false;
  remember();
  currentKey = `${props.type}:${props.templateId}`;
  const saved = cache.get(currentKey);
  savedInput.value = saved?.baseline || '';
  schema.value = saved?.schema;
  values.value = saved?.values || [];
  sourceText.value = saved?.sourceText || '';
  sourceName.value = saved?.sourceName || '';
  sourceUrl.value = saved?.sourceUrl || '';
  error.value = '';
  notice.value = '';
  stale.value = false;
  loading.value = true;
  try {
    const result = await templateForm(props.type, props.templateId);
    if (!active(version, token))
      return;
    if (saved && saved.schema.version !== result.version) {
      nextSchema.value = result;
      stale.value = true;
      throw new Error('模板版本已变化，旧填写内容已保留。请复制需要保留的内容，重新选择模板后按新字段填写。');
    }
    schema.value = result;
    values.value = saved?.values || emptyTemplateValues(result);
    const restored = props.restored;
    if (restored && restored !== ignoredRestore && restored !== appliedRestore && restored.templateId === result.templateId) {
      if (restored.version !== result.version) {
        nextSchema.value = result;
        previousValues.value = restored.values.map(row => ({ label: row.fieldId, value: row.value }));
        stale.value = true;
        throw new Error('此草稿使用旧模板版本，原草稿可继续查看，不能把旧字段自动填入新版模板。');
      }
      values.value = result.fields.map(field => restored.values.find(row => row.fieldId === field.id) || { fieldId: field.id, value: '', quote: '', origin: 'extracted' });
      if (sourceText.value !== restored.sourceText || sourceName.value !== restored.sourceName) {
        if (sourceUrl.value)
          URL.revokeObjectURL(sourceUrl.value);
        sourceUrl.value = '';
      }
      sourceText.value = restored.sourceText;
      sourceName.value = restored.sourceName;
      appliedRestore = restored;
      savedInput.value = inputSnapshot();
    }
    if (!saved && !props.restored)
      savedInput.value = inputSnapshot();
  }
  catch (e) {
    if (active(version, token))
      error.value = generationError(e);
  }
  finally {
    if (active(version, token))
      loading.value = false;
  }
}
async function select(files: File[]) {
  const file = files[0];
  if (!file || !schema.value || busy.value || stale.value)
    return;
  const version = epoch;
  const token = user.token;
  const context = reader.context();
  extracting.value = true;
  error.value = '';
  notice.value = '';
  try {
    const result = await reader.extractTemplate(file, schema.value, () => active(version, token) && context());
    if (!active(version, token))
      return;
    values.value = mergeTemplateExtraction(values.value, result.values);
    sourceText.value = result.sourceText;
    sourceName.value = file.name;
    if (sourceUrl.value)
      URL.revokeObjectURL(sourceUrl.value);
    sourceUrl.value = URL.createObjectURL(file);
    const extracted = result.values.filter(row => row.value.trim()).length;
    notice.value = `本次按模板语义提取 ${extracted} 项，${result.values.length - extracted} 项未取得有效依据。请查看各项对应说明及合同原文，补充缺少的材料；AI 内容待核，已保留人工填写内容。`;
  }
  catch (e) {
    if (active(version, token))
      error.value = generationError(e);
  }
  finally {
    if (active(version, token))
      extracting.value = false;
  }
}
function edit(index: number) {
  values.value[index].origin = 'manual';
  values.value[index].warning = '';
}
function useNewVersion() {
  if (!nextSchema.value || busy.value)
    return;
  if (values.value.some(row => row.value))
    previousValues.value = values.value.map((row, index) => ({ label: schema.value?.fields[index]?.label || row.fieldId, value: row.value }));
  ignoredRestore = props.restored;
  schema.value = nextSchema.value;
  values.value = emptyTemplateValues(schema.value);
  nextSchema.value = undefined;
  stale.value = false;
  error.value = '';
  notice.value = '已加载新版字段。旧填写内容保留在下方备查，请按新版字段重新提取或核对填写。';
  remember();
}
async function generate() {
  if (!schema.value || busy.value || stale.value || navigation.hasPending())
    return;
  const version = epoch;
  const token = user.token;
  composing.value = true;
  error.value = '';
  try {
    if (props.hasDraft) {
      await ElMessageBox.confirm('重新合成会以当前模板填写项替换右侧正文。直接在正文中作出的修改不会自动回填到这些填写项，请先保存需要保留的正文。', '重新合成文书', { confirmButtonText: '按填写项重新合成', cancelButtonText: '返回核对', type: 'warning' });
      if (!active(version, token))
        return;
    }
    let sessionId = props.sessionId || createdSession.value;
    if (!sessionId) {
      const response = await readMediaApiResult<unknown>(create_session({ sessionTitle: `文书起草 - ${props.type}`, caseId: caseId.value || undefined }));
      if (!active(version, token))
        return;
      sessionId = sessionIdFrom(response.data);
      createdSession.value = sessionId;
    }
    const result = await composeTemplate(sessionId, {
      type: props.type,
      templateId: props.templateId,
      version: schema.value.version,
      values: values.value.map(({ fieldId, value, quote, origin, explanation }) => ({ fieldId, value, quote, origin, explanation })),
      sourceText: sourceText.value,
      sourceName: sourceName.value,
    });
    if (active(version, token)) {
      savedInput.value = inputSnapshot();
      remember();
      emit('generated', result.content, sessionId);
    }
  }
  catch (e) {
    if (e === 'cancel' || e === 'close')
      return;
    if (active(version, token))
      error.value = generationError(e);
  }
  finally {
    if (active(version, token))
      composing.value = false;
  }
}
watch(busy, value => emit('busy', value), { flush: 'sync' });
watch(() => [props.templateId, props.type, props.restored], load, { immediate: true });
watch(() => user.token, () => {
  epoch++;
  reader.stop();
  for (const item of cache.values()) {
    if (item.sourceUrl)
      URL.revokeObjectURL(item.sourceUrl);
  }
  if (sourceUrl.value)
    URL.revokeObjectURL(sourceUrl.value);
  cache.clear();
  schema.value = undefined;
  values.value = [];
  sourceText.value = '';
  sourceName.value = '';
  sourceUrl.value = '';
  currentKey = '';
  caseId.value = '';
  error.value = '';
  notice.value = '';
  stale.value = false;
  previousValues.value = [];
  nextSchema.value = undefined;
  ignoredRestore = undefined;
  appliedRestore = undefined;
  savedInput.value = '';
  createdSession.value = '';
  loading.value = extracting.value = composing.value = false;
}, { flush: 'sync' });
onScopeDispose(() => {
  disposed = true;
  epoch++;
  for (const item of cache.values()) {
    if (item.sourceUrl)
      URL.revokeObjectURL(item.sourceUrl);
  }
  if (sourceUrl.value)
    URL.revokeObjectURL(sourceUrl.value);
  emit('busy', false);
});
</script>

<template>
  <div class="template-form" :aria-busy="busy">
    <p v-if="loading" role="status">
      正在加载模板填写项…
    </p>
    <div v-if="schema">
      <h3>{{ schema.templateName }} · 填写内容</h3>
      <p :title="schema.version">
        模板版本：{{ schema.version }}
      </p>
      <p role="status">
        共 {{ schema.fields.length }} 项，已填写 {{ filled }} 项，{{ schema.fields.length - filled }} 项留空。填写完成后仍需人工核对。
      </p>
      <details class="template-contract">
        <summary>从合同提取当前模板填写内容（可选）</summary>
        <ContractUpload label="上传合同提取模板字段" :disabled="busy || stale" @files="select" @error="error = $event" />
        <p>{{ extracting ? '正在按模板字段查找合同原文…' : '支持 PDF、DOCX、JPG、PNG，最大 200MB。只填合同明确记载的对应信息，找不到则留空。再次提取保留人工填写项。' }}</p>
      </details>
      <el-alert v-if="notice" :title="notice" type="info" show-icon :closable="false" />
      <p v-if="sourceName" class="source-file">
        来源合同：{{ sourceName }}
        <a v-if="sourceUrl" :href="sourceUrl" :download="sourceName">打开/下载本次原件</a>
      </p>
      <p v-if="sourceName && !sourceUrl">
        已恢复提取文字快照；重新上传原合同可核对原件。
      </p>
      <el-form label-position="top" :disabled="busy || stale">
        <CaseAssociation v-model="caseId" :session-id="sessionId || undefined" :disabled="busy" />
        <el-form-item v-for="(field, index) in schema.fields" :key="field.id" :label="`${index + 1}. ${field.label}${field.unit ? `（${field.unit}）` : ''}`">
          <p class="field-context">
            模板原文：{{ documentPresentation(field.context) }}
          </p>
          <el-input v-model="values[index].value" :aria-label="`${index + 1}. ${field.label}`" type="textarea" :autosize="{ minRows: 1, maxRows: 5 }" maxlength="20000" :placeholder="field.kind === 'money' || field.kind === 'number' ? '仅填写数值；无信息留空' : '填写对应内容；无信息留空'" @input="edit(index)" />
          <p class="field-state">
            {{ values[index].origin === 'manual' ? '人工填写，请核对' : values[index].value ? 'AI 建议，待人工核对' : '未填写，合成时保留模板原有空白' }}
          </p>
          <p v-if="values[index].value && values[index].explanation && values[index].origin === 'extracted'" class="field-state">
            AI 对应说明：{{ values[index].explanation }}
          </p>
          <details v-if="values[index].quote" class="field-source">
            <summary>查看合同原文依据{{ values[index].origin === 'manual' ? '（提取时的原值依据）' : '' }}</summary>
            <blockquote>{{ values[index].quote }}</blockquote>
          </details>
          <p v-if="values[index].warning" class="field-state">
            {{ values[index].warning }}
          </p>
          <p v-else-if="!values[index].value && values[index].explanation" class="field-state">
            本次未提取到有效对应信息：{{ values[index].explanation }}
          </p>
        </el-form-item>
        <el-button type="primary" :loading="composing" :disabled="busy || stale" @click="generate">
          生成{{ type }}
        </el-button>
      </el-form>
    </div>
    <div v-if="error" ref="errorAnchor" role="alert" aria-live="assertive" tabindex="-1" class="template-error">
      <el-alert :title="error" type="error" show-icon :closable="false" />
      <el-button :disabled="busy" @click="load">
        重新加载模板字段
      </el-button>
      <el-button v-if="nextSchema" :disabled="busy" @click="useNewVersion">
        使用新版模板（保留旧值备查）
      </el-button>
    </div>
    <details v-if="previousValues.length" class="field-source">
      <summary>旧版本填写内容（仅备查，不自动填入新版）</summary>
      <p v-for="(row, index) in previousValues" :key="index">
        {{ row.label }}：{{ row.value || '原填写为空' }}
      </p>
    </details>
  </div>
</template>

<style scoped>
.template-form h3 {
  margin: 16px 0 8px;
  font-size: 16px;
}
.template-form p {
  margin: 6px 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}
.template-contract {
  padding: 12px;
  margin: 16px 0;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
}
.template-form summary {
  line-height: 1.8;
  cursor: pointer;
}
.template-form .field-context {
  width: 100%;
  white-space: pre-wrap;
}
.template-form .field-state {
  width: 100%;
  font-size: 12px;
}
.field-source {
  width: 100%;
  font-size: 13px;
}
.field-source blockquote {
  padding-left: 12px;
  margin: 8px 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  border-left: 2px solid var(--el-border-color);
}
.source-file a {
  display: inline-block;
  margin-left: 8px;
  color: var(--yl-color-brand-strong);
  text-decoration: underline;
}
.template-form .el-form {
  margin-top: 16px;
}
.template-error {
  margin-top: 16px;
}
</style>
