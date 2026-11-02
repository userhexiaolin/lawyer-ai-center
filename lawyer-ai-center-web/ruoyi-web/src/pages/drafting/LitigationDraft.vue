<script setup lang="ts">
import type { TemplateFormInput } from '@/api/document/templates';
import { ElMessageBox } from 'element-plus';
import { useHookFetch } from 'hook-fetch/vue';
import { computed, onScopeDispose, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { send } from '@/api/chat';
import { loadDocumentDraft } from '@/api/document';
import { STANDARD_TEMPLATE, templatePrompt } from '@/api/document/templates';
import { caseDetail, lawyerRequest } from '@/api/lawyer';
import { readMediaApiResult } from '@/api/media/response';
import { create_session } from '@/api/session';
import CaseAssociation from '@/components/CaseAssociation.vue';
import LawyerUsage from '@/components/LawyerUsage.vue';
import { useDraftActivity } from '@/hooks/useDraftWorkspace';
import { decodeReviewEvents, sessionIdFrom } from '@/pages/contract-review/workflow';
import { useUserStore } from '@/stores';
import ContractImport from './ContractImport.vue';
import DocumentEditor from './DocumentEditor.vue';
import DocumentTemplatePicker from './DocumentTemplatePicker.vue';
import { documentText } from './documentText';
import { draftWarnings } from './draftChecks';
import { litigationCaseFields, loadDraftSnapshot, mergeCaseFields } from './draftContinuity';
import { generationError, prepareDrafting } from './generation';
import { emptyLitigation, LITIGATION_FACTS_MAX_LENGTH, litigationPrompt } from './litigation';
import TemplateDraftForm from './TemplateDraftForm.vue';
import { readTemplateFormInput } from './templateForm';

const emit = defineEmits<{ busy: [boolean] }>();
const templateId = ref('');
const restoredTemplate = ref<TemplateFormInput>();
const importing = ref(false);
const route = useRoute();
const router = useRouter();
const user = useUserStore();
const form = reactive(emptyLitigation());
const step = ref(0);
const caseId = ref('');
const sessionId = ref('');
const draft = ref('');
const savedDraft = ref('');
const busy = ref(false);
const templateBusy = ref(false);
const templateBlocked = ref(false);
const inputSnapshot = () => JSON.stringify([form.plaintiff, form.defendant, form.claims, form.facts, form.evidence, form.court, sessionId.value ? '' : caseId.value]);
const savedInput = ref(inputSnapshot());
const navigation = useDraftActivity(() => inputSnapshot() !== savedInput.value, () => busy.value || importing.value);
const error = ref('');
const prefilling = ref(false);
const caseNotice = ref('');
const caseKept = ref(false);
const recoveryNotice = ref('');
let replacingCase = false;
const warnings = computed(() => draftWarnings(draft.value, JSON.stringify(form)));
const copied = ref(false);
const { stream, cancel } = useHookFetch({ request: send });
async function templateGenerated(content: string, id: string) {
  draft.value = content;
  savedDraft.value = content;
  sessionId.value = id;
  step.value = 2;
  const target = { path: '/drafting', query: { type: form.type, sessionId: id } };
  await navigation.runInternal(target, () => router.replace(target));
}
let version = 0;
let disposed = false;
async function prefill(replace = false) {
  if (busy.value || importing.value || templateBusy.value || !caseId.value || navigation.hasPending())
    return;
  const v = version;
  const token = user.token;
  const selectedCase = caseId.value;
  const currentType = form.type;
  const active = () => !disposed && v === version && token === user.token && caseId.value === selectedCase && form.type === currentType;
  busy.value = prefilling.value = true;
  error.value = '';
  try {
    if (replace) {
      replacingCase = true;
      await ElMessageBox.confirm('仅用当前案件中非空的原被告、事实和法院替换对应字段。诉讼请求或答辩意见、证据清单保留；案件缺项不会清空已有填写。诉讼地位仍需人工核对。', '替换案件对应要素？', { confirmButtonText: '确认替换对应字段', cancelButtonText: '保留当前填写', type: 'warning' });
      replacingCase = false;
    }
    if (!active())
      return;
    const c = (await caseDetail(selectedCase)).data.caseInfo;
    if (!active())
      return;
    const result = mergeCaseFields(form, litigationCaseFields(c, currentType), replace);
    caseKept.value = result.kept > 0;
    caseNotice.value = `已带入 ${result.applied} 项，保留 ${result.kept} 项已有填写。案件缺项未改动，请核对当事人诉讼地位及本次案情。`;
  }
  catch (e) {
    if (e !== 'cancel' && e !== 'close' && active())
      error.value = generationError(e);
  }
  finally {
    replacingCase = false;
    if (!disposed && v === version && token === user.token)
      busy.value = prefilling.value = false;
  }
}
function next() {
  if (!form.plaintiff.trim() || !form.defendant.trim()) {
    error.value = '请填写双方当事人资料，缺失住所、联系方式可标待补充';
    return;
  }
  error.value = '';
  step.value = 1;
}
async function generate() {
  if (busy.value || importing.value || templateBusy.value || templateBlocked.value || navigation.hasPending())
    return;
  if ((draft.value.trim() && !await navigation.confirmDiscard()) || disposed || busy.value || importing.value)
    return;
  if (!user.token) {
    user.ensureLogin('/drafting');
    return;
  }
  let content: string;
  try {
    content = litigationPrompt(form);
  }
  catch (e) {
    error.value = generationError(e);
    return;
  }
  const v = version;
  const owner = user.token;
  const active = () => !disposed && v === version && owner === user.token;
  busy.value = true;
  error.value = '';
  let generated = '';
  copied.value = false;
  try {
    const a = await prepareDrafting();
    content = await templatePrompt(form.type, templateId.value, content);
    if (!active())
      return;
    if (!sessionId.value) {
      const created = await readMediaApiResult<unknown>(
        create_session({
          sessionTitle: `文书起草 - ${form.type}`,
          caseId: caseId.value || undefined,
        }),
      );
      if (!active())
        return;
      sessionId.value = sessionIdFrom(created.data);
    }
    step.value = 2;
    let done = false;
    for await (const chunk of stream({
      model: a.model,
      agentId: a.agentId,
      sessionId: sessionId.value,
      content,
      enableThinking: false,
    })) {
      if (!active())
        return;
      for (const event of decodeReviewEvents(chunk)) {
        if (event.error || event.event === 'error')
          throw new Error(String(event.error || '生成失败'));
        if ('code' in event && event.code !== 200)
          throw new Error(String(event.msg || '生成失败'));
        if (typeof event.content === 'string')
          generated += event.content;
        if (event.done || event.event === 'done')
          done = true;
      }
    }
    generated = documentText(generated);
    if (!done || !generated.trim())
      throw new Error('未收到完整草稿，请重试');
    draft.value = generated;
    savedDraft.value = generated;
    savedInput.value = inputSnapshot();
    const target = {
      path: '/drafting',
      query: { type: form.type, sessionId: sessionId.value },
    };
    await navigation.runInternal(target, () => router.replace(target));
  }
  catch (e) {
    if (active())
      error.value = generationError(e);
  }
  finally {
    if (active())
      busy.value = false;
  }
}
async function restore() {
  if (busy.value || importing.value || templateBusy.value || !user.token)
    return;
  if (!await navigation.confirmDiscard() || disposed || busy.value || importing.value)
    return;
  const v = version;
  const token = user.token;
  const active = () => !disposed && v === version && token === user.token;
  busy.value = true;
  error.value = '';
  try {
    const { session: item, draft: saved } = await loadDraftSnapshot(form.type, typeof route.query.sessionId === 'string' ? route.query.sessionId : '', { request: lawyerRequest, load: loadDocumentDraft }, active);
    if (!active())
      return;
    const source = saved.input || '';
    const nextForm = { plaintiff: '', defendant: '', claims: '', facts: '', evidence: '', court: '' };
    let templateInput: TemplateFormInput | undefined;
    let sourceType = '';
    let recovered = false;
    try {
      templateInput = readTemplateFormInput(source);
      const value = JSON.parse(
        source.match(/---要素JSON开始---([\s\S]*?)---要素JSON结束---/)?.[1] || '{}',
      );
      sourceType = typeof value.type === 'string' ? value.type : '';
      for (const key of [
        'plaintiff',
        'defendant',
        'claims',
        'facts',
        'evidence',
        'court',
      ] as const) {
        if (typeof value[key] === 'string') {
          nextForm[key] = value[key];
          recovered = true;
        }
      }
    }
    catch {
      /* Legacy drafts can still be edited. */
    }
    if ((templateInput?.type && templateInput.type !== form.type) || (sourceType && sourceType !== form.type))
      throw new Error('草稿填写类型与当前文书类型不一致，请返回对应类型恢复');
    Object.assign(form, nextForm);
    restoredTemplate.value = templateInput;
    templateId.value = templateInput?.templateId || STANDARD_TEMPLATE;
    caseId.value = item.caseId || '';
    sessionId.value = item.id;
    draft.value = documentText(saved.content);
    savedDraft.value = draft.value;
    savedInput.value = inputSnapshot();
    step.value = 2;
    copied.value = false;
    recoveryNotice.value = recovered || templateInput ? '' : '历史草稿缺少可恢复的诉讼要素，旧填写已清除，正文已保留。再次生成前请补充并核对要素。';
    const target = { path: '/drafting', query: { type: form.type, sessionId: item.id } };
    await navigation.runInternal(target, () => router.replace(target));
  }
  catch (e) {
    if (active())
      error.value = generationError(e);
  }
  finally {
    if (active())
      busy.value = false;
  }
}
watch(caseId, () => {
  caseNotice.value = '';
  caseKept.value = false;
}, { flush: 'sync' });
watch(
  () => form.type,
  () => {
    sessionId.value = '';
    draft.value = '';
    savedDraft.value = '';
    form.claims = '';
    step.value = 0;
    error.value = '';
    caseNotice.value = recoveryNotice.value = '';
    caseKept.value = false;
    restoredTemplate.value = undefined;
  },
  { flush: 'sync' },
);
watch(
  () => user.token,
  () => {
    version++;
    if (replacingCase)
      ElMessageBox.close();
    cancel();
    busy.value = false;
    prefilling.value = false;
    templateBusy.value = false;
    Object.assign(form, emptyLitigation());
    restoredTemplate.value = undefined;
    step.value = 0;
    caseId.value = '';
    sessionId.value = '';
    draft.value = '';
    savedDraft.value = '';
    error.value = '';
    caseNotice.value = recoveryNotice.value = '';
    caseKept.value = false;
    savedInput.value = inputSnapshot();
  },
  { flush: 'sync' },
);
watch(
  () => busy.value || importing.value || templateBusy.value,
  value => emit('busy', value),
  { flush: 'sync' },
);
onScopeDispose(() => {
  disposed = true;
  version++;
  cancel();
  if (replacingCase)
    ElMessageBox.close();
});
if (route.query.type === '民事答辩状')
  form.type = '民事答辩状';
if (typeof route.query.sessionId === 'string')
  void restore();
</script>

<template>
  <section class="litigation yl-panel">
    <h2>民事诉讼要素起草</h2>
    <p>
      填写要素，生成起诉状或答辩状草稿，再在下方编辑下载。缺失信息会标为待补充，请核对后用于案件。
    </p>
    <LawyerUsage :key="sessionId" /><el-radio-group
      v-if="false"
      v-model="form.type"
      :disabled="busy"
    >
      <el-radio-button value="民事起诉状">
        民事起诉状
      </el-radio-button><el-radio-button value="民事答辩状">
        民事答辩状
      </el-radio-button>
    </el-radio-group><el-button :disabled="busy || importing" @click="restore">
      恢复最近同类型草稿
    </el-button><DocumentTemplatePicker
      v-model="templateId"
      :type="form.type"
      :requested-id="typeof route.query.templateId === 'string' && !route.query.sessionId ? route.query.templateId : undefined"
      :disabled="busy || importing || templateBusy"
      @blocked="templateBlocked = $event"
    />
    <el-alert v-if="recoveryNotice" :title="recoveryNotice" type="info" :closable="false" />
    <TemplateDraftForm v-if="templateId && templateId !== STANDARD_TEMPLATE" :type="form.type" :template-id="templateId" :session-id="sessionId || undefined" :restored="restoredTemplate" :has-draft="!!draft" @busy="templateBusy = $event" @generated="templateGenerated" />
    <template v-else-if="!templateBlocked">
      <el-steps :active="step" simple>
        <el-step title="当事人" /><el-step title="请求与事实" /><el-step
          title="预览与交付"
        />
      </el-steps><el-alert v-if="error" :title="error" type="error" :closable="false" /><ContractImport
        :type="form.type"
        :disabled="busy"
        @busy="importing = $event"
        @apply="(value) => Object.assign(form, value)"
      /><el-form label-position="top" :disabled="busy || importing">
        <div v-show="step === 0">
          <CaseAssociation
            v-model="caseId"
            :session-id="sessionId || undefined"
            :disabled="busy"
          /><el-button :disabled="!caseId || busy" :loading="prefilling" @click="prefill()">
            从案件补充空白要素
          </el-button>
          <el-alert v-if="caseNotice" :title="caseNotice" type="info" :closable="false">
            <el-button v-if="caseKept" :disabled="busy || importing" text @click="prefill(true)">
              用案件资料替换对应字段
            </el-button>
          </el-alert>
          <p>
            带入规则：起诉状将客户填为原告，答辩状将客户填为被告；请按真实诉讼地位核对。住所和联系方式需另行补齐。
          </p>
          <el-form-item label="原告资料（姓名/名称、住所、联系方式）">
            <el-input
              v-model="form.plaintiff"
              aria-label="原告资料"
              type="textarea"
              :rows="3"
              maxlength="2000"
            />
          </el-form-item><el-form-item label="被告资料（姓名/名称、住所、联系方式）">
            <el-input
              v-model="form.defendant"
              aria-label="被告资料"
              type="textarea"
              :rows="3"
              maxlength="2000"
            />
          </el-form-item><el-button type="primary" @click="next">
            下一步：请求与事实
          </el-button>
        </div>
        <div v-show="step === 1">
          <el-form-item
            :label="
              form.type === '民事起诉状' ? '诉讼请求（逐行填写）' : '对诉讼请求的答辩意见（逐行填写）'
            "
          >
            <el-input
              v-model="form.claims"
              aria-label="请求或答辩意见"
              type="textarea"
              :rows="4"
              maxlength="5000"
            />
          </el-form-item><el-form-item label="事实与理由">
            <el-input
              v-model="form.facts"
              aria-label="事实与理由"
              type="textarea"
              :rows="7"
              :maxlength="LITIGATION_FACTS_MAX_LENGTH"
              show-word-limit
            />
          </el-form-item><el-form-item label="证据清单（选填）">
            <el-input
              v-model="form.evidence"
              aria-label="证据清单"
              type="textarea"
              :rows="3"
              maxlength="5000"
            />
          </el-form-item><el-form-item label="受理法院（选填）">
            <el-input v-model="form.court" aria-label="受理法院" maxlength="200" />
          </el-form-item><el-button @click="step = 0">
            上一步
          </el-button><el-button type="primary" :loading="busy" @click="generate">
            生成{{ form.type }}草稿
          </el-button>
        </div>
      </el-form>
    </template>
    <div v-if="step === 2">
      <el-button :disabled="busy" @click="step = 1">
        返回修改要素
      </el-button><DocumentEditor
        v-model="draft"
        :persisted-content="savedDraft"
        :title="form.type"
        :session-id="sessionId"
        :busy="busy || templateBusy"
        :warnings="warnings"
      />
    </div>
  </section>
</template>

<style scoped>
.litigation {
  max-width: none;
  margin-top: 24px;
  line-height: 1.8;
}
.litigation p {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.el-steps {
  margin: 20px 0;
}
.el-space {
  margin-top: 16px;
}
@media (max-width: 600px) {
  .el-steps {
    padding: 12px;
  }
  .el-steps :deep(.el-step__title) {
    max-width: none;
    font-size: 12px;
    white-space: nowrap;
  }
  .el-steps :deep(.el-step__head) {
    padding-right: 6px;
  }
  .el-steps :deep(.el-step__arrow) {
    flex-grow: 0;
    margin: 0 6px;
  }
}
</style>
