<script setup lang="ts">
import type { AgentVO } from '@/api/agent/types';
import { ElMessage } from 'element-plus';
import { useHookFetch } from 'hook-fetch/vue';
import { computed, onScopeDispose, reactive, ref, watch } from 'vue';
import { getAgentList } from '@/api/agent';
import { send } from '@/api/chat';
import { downloadDocument } from '@/api/document';
import { readMediaApiResult } from '@/api/media/response';
import { create_session } from '@/api/session';
import LegalAnswer from '@/components/LegalAnswer.vue';
import { deliveryText } from '@/pages/drafting/documentText';
import { useUserStore } from '@/stores';
import {
  checkLetter,
  contractKeys,
  emptyCollectionDetails,
  emptyContractFields,
  extractionPrompt,
  fieldLabels,
  generateCollectionLetter,
  parseContractFields,
} from './collection-letter';
import { decodeReviewEvents, sessionIdFrom } from './workflow';

const props = defineProps<{ source: string; fileUrl: string; disabled: boolean; caseId: string }>();
const emit = defineEmits<{ busy: [boolean] }>();
const editing = ref(false);
const exporting = ref(false);
const visible = ref(false);
const extracting = ref(false);
const extracted = ref(false);
const error = ref('');
const warnings = ref<string[]>([]);
const preview = ref('');
const fields = reactive(emptyContractFields());
const details = reactive(emptyCollectionDetails());
const user = useUserStore();
const { stream, cancel } = useHookFetch({ request: send });
let generation = 0;
let sessionId = '';
let lastPrompt = '';
let disposed = false;
const sourceReady = computed(() => Boolean(props.source.trim()) && !props.disabled);
const missing = computed(() => contractKeys.filter(k => !fields[k]).map(k => fieldLabels[k]));
const consistent = computed(
  () => Boolean(preview.value) && checkLetter(preview.value, fields, details),
);
watch(
  () => [props.source, props.fileUrl, props.caseId, user.token],
  () => {
    generation++;
    cancel();
    Object.assign(fields, emptyContractFields());
    Object.assign(details, emptyCollectionDetails());
    extracted.value = false;
    preview.value = '';
    warnings.value = [];
    error.value = '';
    sessionId = '';
    lastPrompt = '';
    extracting.value = false;
    emit('busy', false);
  },
);
watch([fields, details], () => {
  if (preview.value)
    error.value = '字段已修改，请重新生成后再复制或导出';
});
async function extract() {
  if (extracting.value || !sourceReady.value)
    return;
  if (!user.token) {
    user.ensureLogin('/contract-review', '登录后提取合同字段');
    return;
  }
  const id = ++generation;
  extracting.value = true;
  emit('busy', true);
  error.value = '';
  warnings.value = [];
  preview.value = '';
  extracted.value = false;
  try {
    const prompt = extractionPrompt(props.source);
    const response = await readMediaApiResult<AgentVO[]>(getAgentList());
    const agent = response.data.find(a => a.agentName === '文书起草助手' && a.status === '0');
    if (!agent?.id)
      throw new Error('文书起草助手未启用，请在管理端配置');
    if (disposed || id !== generation)
      return;
    // A fresh CHAT session avoids charging an unstarted contract REVIEW for extraction.
    // Retrying the same input reuses this session and the backend idempotent usage mark.
    if (!sessionId || lastPrompt !== prompt) {
      const created = await readMediaApiResult<unknown>(
        create_session({
          sessionTitle: '文书起草 - 租金催缴函',
          caseId: props.caseId || undefined,
        }),
      );
      if (disposed || id !== generation)
        return;
      sessionId = sessionIdFrom(created.data);
      lastPrompt = prompt;
    }
    let raw = '';
    let done = false;
    for await (const chunk of stream({
      model: agent.modelName || '',
      agentId: agent.id,
      sessionId,
      content: prompt,
      enableThinking: false,
    })) {
      if (disposed || id !== generation)
        return;
      for (const event of decodeReviewEvents(chunk)) {
        if (event.event === 'error' || ('code' in event && event.code !== 200))
          throw new Error(String(event.error || event.msg || '提取失败，请重试'));
        if (event.event === 'content' && typeof event.content === 'string')
          raw += event.content;
        if (raw.length > 200000)
          throw new Error('提取结果过长，请重试');
        if (event.event === 'done' || event.done === true) {
          done = true;
          break;
        }
      }
      if (done)
        break;
    }
    if (!done)
      throw new Error('连接中断，未收到完整提取结果，请重试');
    const result = parseContractFields(raw, props.source);
    Object.assign(fields, result.fields);
    warnings.value = result.warnings;
    extracted.value = true;
  }
  catch (e) {
    if (id === generation && !disposed) {
      cancel();
      error.value = (e as Error).message;
    }
  }
  finally {
    if (id === generation && !disposed) {
      extracting.value = false;
      emit('busy', false);
    }
  }
}
function generate() {
  try {
    preview.value = generateCollectionLetter(fields, details);
    error.value = '';
  }
  catch (e) {
    error.value = (e as Error).message;
  }
}
function ready() {
  if (!consistent.value) {
    error.value = '主体、金额或固定事实段已变化，请核对字段并重新生成';
    return false;
  }
  return true;
}
async function copy() {
  if (!ready())
    return;
  try {
    await navigator.clipboard.writeText(preview.value);
    ElMessage.success('已复制催款函全文');
  }
  catch {
    error.value = '复制失败，请选中文本手动复制或导出TXT';
  }
}
async function word() {
  if (!ready() || exporting.value)
    return;
  exporting.value = true;
  try {
    await downloadDocument({
      title: '租金催缴函',
      content: deliveryText(preview.value, warnings.value),
      sessionId: sessionId || undefined,
    });
    ElMessage.success('Word已下载');
  }
  catch (e) {
    error.value = (e as Error).message || '导出失败，请重试';
  }
  finally {
    exporting.value = false;
  }
}
function download() {
  if (!ready())
    return;
  const url = URL.createObjectURL(
    new Blob([`\uFEFF${preview.value}`], { type: 'text/plain;charset=utf-8' }),
  );
  const a = document.createElement('a');
  a.href = url;
  a.download = '租金催缴函.txt';
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
onScopeDispose(() => {
  disposed = true;
  generation++;
  cancel();
  emit('busy', false);
});
</script>

<template>
  <section class="letter-entry" aria-label="基于合同生成文书">
    <el-button :disabled="!sourceReady || extracting" @click="visible = !visible">
      基于本合同生成文书
    </el-button>
    <p v-if="!source.trim()">
      上传合同并核对正文后，可提取信息生成催款函。
    </p>
    <div v-if="visible" class="letter-workbench">
      <h2>基于本合同生成文书</h2>
      <label for="letter-template">选择文书模板</label>
      <el-select
        id="letter-template"
        model-value="lease"
        aria-label="选择文书模板"
        :disabled="extracting"
      >
        <el-option label="租金催缴函" value="lease" />
      </el-select>
      <p>先核对合同原文，再提取并确认字段。欠费区间、欠款总额和期限需自行确认。</p>
      <el-button type="primary" :loading="extracting" :disabled="!sourceReady" @click="extract">
        {{ extracted ? '重新提取合同字段' : '提取合同字段' }}
      </el-button>
      <el-button :disabled="extracting || disabled" @click="extracted = true">
        手动填写字段
      </el-button>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-alert
        v-for="warning in warnings"
        :key="warning"
        :title="warning"
        type="warning"
        :closable="false"
      />
      <template v-if="extracted">
        <el-alert
          v-if="missing.length"
          :title="`以下字段未提取到，请补充：${missing.join('、')}`"
          type="warning"
          :closable="false"
        />
        <el-alert
          v-if="fields.contractType === 'property'"
          title="识别为物业合同，当前仅提供租金催缴函，请勿直接套用"
          type="warning"
          :closable="false"
        />
        <p>
          合同类型：{{
            fields.contractType === 'lease'
              ? '租赁'
              : fields.contractType === 'property'
                ? '物业'
                : '未确定，请核对适用范围'
          }}。字段可修改；修改后须重新生成。
        </p>
        <el-form label-position="top" class="letter-fields">
          <el-form-item v-for="key in contractKeys" :key="key" :label="fieldLabels[key]">
            <el-input
              v-model="fields[key]"
              :aria-label="fieldLabels[key]"
              :maxlength="2000"
              :disabled="extracting || disabled"
              placeholder="未提取到，请补充"
            />
          </el-form-item>
          <el-form-item label="本次欠费区间">
            <el-input
              v-model="details.overduePeriod"
              aria-label="本次欠费区间"
              :maxlength="200"
              placeholder="合同未载明时请补充"
            />
          </el-form-item>
          <el-form-item label="本次欠款总额（元）">
            <el-input
              v-model="details.totalDue"
              aria-label="本次欠款总额（元）"
              :maxlength="100"
              placeholder="请核实后填写；不会按月租金自动计算"
            />
          </el-form-item>
          <el-form-item label="付款期限">
            <el-input
              v-model="details.deadline"
              aria-label="付款期限"
              :maxlength="200"
              placeholder="请补充"
            />
          </el-form-item>
          <el-form-item label="落款日期">
            <el-input
              v-model="details.issueDate"
              aria-label="落款日期"
              :maxlength="100"
              placeholder="请补充"
            />
          </el-form-item>
        </el-form>
        <details v-if="fields.other.rawClauses">
          <summary>查看提取依据</summary>
          <pre>{{ fields.other.rawClauses }}</pre>
        </details>
        <el-button
          type="primary"
          :disabled="extracting || disabled || fields.contractType === 'property'"
          @click="generate"
        >
          生成租金催缴函
        </el-button>
        <template v-if="preview">
          <h3>催款函正文</h3>
          <el-button @click="editing = !editing">
            {{ editing ? '预览' : '编辑正文' }}
          </el-button>
          <article v-if="!editing" class="letter-paper">
            <LegalAnswer :content="preview" />
          </article>
          <el-input
            v-if="editing"
            v-model="preview"
            aria-label="催款函预览"
            type="textarea"
            :rows="17"
            :maxlength="20000"
          />
          <p role="status">
            {{
              consistent
                ? '主体、金额与最终字段的固定事实段校验通过。其余补充文字请人工核对。'
                : '字段或固定事实段已修改，请重新生成后再复制或导出。'
            }}
          </p>
          <p>核对后下载 Word，可继续编辑用于客户沟通；正式送达前请复核事实与期限。</p>
          <el-button
            type="primary"
            :disabled="!consistent || exporting"
            :loading="exporting"
            @click="word"
          >
            下载 Word
          </el-button><el-button :disabled="!consistent" @click="copy">
            复制全文
          </el-button><el-button :disabled="!consistent" @click="download">
            导出 TXT
          </el-button>
        </template>
      </template>
    </div>
  </section>
</template>

<style scoped>
.letter-paper {
  background: var(--yl-color-surface);
  color: #202020;
  padding: 32px;
  border: 1px solid #ddd;
  line-height: 1.9;
  border-radius: 8px;
}

.letter-entry {
  margin-top: 16px;
}
.letter-entry p {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.8;
}
.letter-workbench {
  margin-top: 16px;
  padding: 22px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: var(--yl-radius-panel);
}
.letter-workbench h2 {
  font-size: 18px;
  margin: 0 0 20px;
}
.letter-workbench > label {
  display: block;
  margin: 12px 0;
}
.letter-workbench .el-alert {
  margin: 14px 0;
}
.letter-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
  margin-top: 20px;
}
.letter-workbench pre {
  white-space: pre-wrap;
  max-height: 200px;
  overflow: auto;
}
.letter-workbench h3 {
  margin: 24px 0 14px;
  font-size: 15px;
}
@media (max-width: 700px) {
  .letter-fields {
    grid-template-columns: 1fr;
  }
}
</style>
