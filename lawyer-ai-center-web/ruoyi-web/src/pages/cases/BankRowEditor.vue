<script setup lang="ts">
import type { BankEdit, BankManual, BankRow, BankSource } from '@/api/lawyer/bank';
import { computed, onScopeDispose, ref, watch } from 'vue';
import { bankRequest, flags, label } from '@/api/lawyer/bank';
import { authenticatedBlob } from '@/api/lawyer/dossier';
import { useUserStore } from '@/stores';

const props = defineProps<{ caseId: string; row?: BankRow; manual?: BankManual }>();
const emit = defineEmits<{ saved: []; close: [] }>();
const user = useUserStore();
const source = ref<BankSource>();
const image = ref('');
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const previewError = ref('');
const history = ref<
  {
    id: string;
    action: string;
    beforeJson: string;
    afterJson: string;
    reason: string;
    operatorId: string;
    createTime: string;
  }[]
>([]);
const form = ref<BankEdit>({
  account: null,
  expectedVersion: 1,
  tradeTimeRaw: '',
  amountRaw: '',
  direction: 'unknown',
  currency: '',
  balanceRaw: '',
  counterAccount: null,
  counterparty: null,
  transactionRef: '',
  summary: '',
  reviewStatus: 'pending_review',
  included: true,
  reason: '',
});
const splitParts = ref<{ start: number; end: number; values: BankEdit }[]>([]);
const sourceZoom = ref(100);
const latest = ref<BankRow>();
async function compareLatest() {
  if (props.row) {
    try {
      latest.value = await bankRequest<BankRow>(props.caseId, `/${props.row.id}`);
    }
    catch (e) {
      error.value = (e as Error).message;
    }
  }
}
const sourceValid = computed(() => !!source.value?.verified && source.value.current);
let serial = 0;
function release() {
  if (image.value)
    URL.revokeObjectURL(image.value);
  image.value = '';
}
async function load() {
  const current = ++serial;
  const token = user.token;
  release();
  sourceZoom.value = 100;
  source.value = undefined;
  history.value = [];
  latest.value = undefined;
  splitParts.value = [];
  error.value = '';
  previewError.value = '';
  const row = props.row;
  form.value = {
    account: null,
    expectedVersion: row?.version || 1,
    tradeTimeRaw: row?.tradeTimeRaw || '',
    amountRaw: row?.amountRaw || '',
    direction: row?.direction || 'unknown',
    currency: row?.currency || '',
    balanceRaw: row?.balanceRaw || '',
    counterAccount: null,
    counterparty: null,
    transactionRef: row?.transactionRef || '',
    summary: row?.summary || '',
    reviewStatus:
      row?.reviewStatus === 'stale' ? 'pending_review' : row?.reviewStatus || 'pending_review',
    included: row?.included ?? true,
    reason: '',
  };
  if (!token)
    return;
  loading.value = true;
  try {
    if (row) {
      const [resolved, logs] = await Promise.all([
        bankRequest<BankSource>(props.caseId, `/${row.id}/source`),
        bankRequest<typeof history.value>(props.caseId, `/${row.id}/history`),
      ]);
      if (current !== serial || user.token !== token)
        return;
      source.value = resolved;
      history.value = logs;
    }
    const version = source.value?.fileVersionId || props.manual?.fileVersionId;
    const page = source.value?.pageIndex || props.manual?.pageIndex;
    if (version && page) {
      try {
        const blob = await authenticatedBlob(props.caseId, version, page);
        if (current !== serial || user.token !== token)
          return;
        image.value = URL.createObjectURL(blob);
      }
      catch (e) {
        if (current === serial && user.token === token)
          previewError.value = (e as Error).message;
      }
    }
  }
  catch (e) {
    if (current === serial && user.token === token)
      error.value = (e as Error).message;
  }
  finally {
    if (current === serial && user.token === token)
      loading.value = false;
  }
}
function beginSplit() {
  if (!source.value || !sourceValid.value)
    return;
  const middle = Math.floor((source.value.start + source.value.end) / 2);
  splitParts.value = [
    {
      start: source.value.start,
      end: middle,
      values: { ...form.value, expectedVersion: 1, reviewStatus: 'pending_review' },
    },
    {
      start: middle,
      end: source.value.end,
      values: { ...form.value, expectedVersion: 1, reviewStatus: 'pending_review' },
    },
  ];
}
async function saveSplit() {
  if (!props.row || !source.value || busy.value)
    return;
  const current = serial;
  const token = user.token;
  busy.value = true;
  error.value = '';
  try {
    const parts = splitParts.value.map((part, index) => ({
      ...part,
      fileVersionId: source.value!.fileVersionId,
      pageIndex: source.value!.pageIndex,
      values: { ...part.values, reason: `拆分第${index + 1}行：${form.value.reason}` },
    }));
    await bankRequest(props.caseId, `/${props.row.id}/split`, 'POST', {
      expectedVersion: form.value.expectedVersion,
      parts,
      reason: form.value.reason,
    });
    if (current === serial && token === user.token)
      emit('saved');
  }
  catch (e) {
    if (current === serial && token === user.token)
      error.value = (e as Error).message;
  }
  finally {
    if (current === serial)
      busy.value = false;
  }
}
async function save() {
  if (busy.value)
    return;
  const current = serial;
  const token = user.token;
  busy.value = true;
  error.value = '';
  try {
    if (props.row)
      await bankRequest(props.caseId, `/${props.row.id}`, 'PATCH', form.value);
    else if (props.manual)
      await bankRequest(props.caseId, '/manual', 'POST', { ...props.manual, values: form.value });
    if (current === serial && user.token === token)
      emit('saved');
  }
  catch (e) {
    if (current === serial && user.token === token)
      error.value = (e as Error).message;
  }
  finally {
    if (current === serial)
      busy.value = false;
  }
}
watch(
  () => [
    props.row?.id,
    props.manual?.fileVersionId,
    props.manual?.pageIndex,
    props.manual?.start,
    props.caseId,
    user.token,
  ],
  load,
  { immediate: true },
);
onScopeDispose(() => {
  serial++;
  release();
});
</script>

<template>
  <div v-loading="loading" class="bank-editor">
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><el-button
      v-if="error && row"
      :disabled="busy"
      @click="compareLatest"
    >
      对照最新记录（保留当前输入）
    </el-button>
    <section v-if="latest">
      <p>当前服务器版本 {{ latest.version }}，以下为最新字段；你的未保存输入仍在表单中。</p>
      <pre>{{ latest }}</pre>
      <el-button
        :disabled="busy"
        @click="
          form.expectedVersion = latest!.version;
          latest = undefined;
        "
      >
        我已逐项对照，保留表单作为新的核对稿
      </el-button>
    </section>
    <el-alert
      v-if="!sourceValid"
      title="来源尚未完成核对或版本已过期，可暂存待核，不能标记已核。保存失败会保留输入。"
      type="warning"
      :closable="false"
    />
    <div class="bank-editor__columns">
      <section class="bank-editor__source" aria-label="流水原页">
        <h2>
          {{ source?.fileName || "手工补录来源" }} · 第{{
            source?.pageIndex || manual?.pageIndex
          }}页
        </h2>
        <p>{{ source?.pageLabel }} · 版本 {{ source?.fileVersionId || manual?.fileVersionId }}</p>
        <p v-if="source">
          原页字符 {{ source.start }}–{{
            source.end
          }}，以下为该位置实际片段（无坐标时不伪造图像高亮）：
        </p>
        <pre v-if="source">{{ source.fragment }}</pre>
        <el-alert v-if="previewError" :title="previewError" type="warning" :closable="false" />
        <div v-if="image" class="bank-paper-tools">
          <el-button
            :disabled="sourceZoom <= 50"
            @click="sourceZoom = Math.max(50, sourceZoom - 25)"
          >
            缩小
          </el-button><span>原页 {{ sourceZoom }}%</span><el-button
            :disabled="sourceZoom >= 300"
            @click="sourceZoom = Math.min(300, sourceZoom + 25)"
          >
            放大
          </el-button><el-button @click="sourceZoom = 100">
            适宽
          </el-button>
        </div>
        <div v-if="image" class="bank-paper-scroll">
          <img
            :src="image"
            :style="{ width: `${sourceZoom}%` }"
            :alt="`${source?.fileName || '补录材料'} 第${source?.pageIndex || manual?.pageIndex}页`"
          >
        </div>
        <p v-else>
          原图暂不可用，可返回材料工作台下载原件核对。
        </p>
      </section>
      <section>
        <h2>{{ row ? "逐字段纠正" : "手工补录（保存为待核）" }}</h2>
        <p v-if="row">
          原识别金额 {{ row.amountRaw || "未知" }}；余额 {{ row.balanceRaw || "未知" }}；对方
          {{ row.counterparty || "未知" }} /
          {{ row.counterAccount || "未知" }}。未填对方修改项时保留原值。
        </p>
        <p v-if="row">
          {{
            [...flags(row.anomalyJson), ...flags(row.reasonsJson)].map(label).join("、")
              || "未发现规则异常，仍需核对原件"
          }}
        </p>
        <el-collapse v-if="row?.suggestionJson">
          <el-collapse-item title="重跑建议（不会覆盖人工值）">
            <pre>{{ row.suggestionJson }}</pre>
          </el-collapse-item>
        </el-collapse>
        <el-form label-position="top" :disabled="busy" @submit.prevent="save">
          <el-form-item label="交易时间原文">
            <el-input v-model="form.tradeTimeRaw" maxlength="100" />
          </el-form-item>
          <div class="bank-editor__fields">
            <el-form-item label="金额原文（保留精确小数）">
              <el-input v-model="form.amountRaw" maxlength="100" />
            </el-form-item><el-form-item label="方向">
              <el-select v-model="form.direction">
                <el-option
                  v-for="s in ['income', 'expense', 'unknown']"
                  :key="s"
                  :value="s"
                  :label="label(s)"
                />
              </el-select>
            </el-form-item><el-form-item label="余额原文">
              <el-input v-model="form.balanceRaw" maxlength="100" />
            </el-form-item><el-form-item label="币种（如 CNY）">
              <el-input v-model="form.currency" maxlength="8" />
            </el-form-item>
          </div>
          <el-form-item label="修改本方账号（可不填）">
            <el-input v-model="form.account" maxlength="160" autocomplete="off" />
          </el-form-item><el-form-item label="修改对方账号（可不填）">
            <el-input
              v-model="form.counterAccount"
              maxlength="160"
              autocomplete="off"
            />
          </el-form-item><el-form-item label="修改对方户名（可不填）">
            <el-input v-model="form.counterparty" maxlength="200" autocomplete="off" />
          </el-form-item>
          <el-form-item label="交易参考号">
            <el-input v-model="form.transactionRef" maxlength="200" />
          </el-form-item><el-form-item label="摘要">
            <el-input v-model="form.summary" maxlength="1000" type="textarea" />
          </el-form-item>
          <el-form-item v-if="row" label="核验决定">
            <el-select v-model="form.reviewStatus">
              <el-option
                v-for="s in ['pending_review', 'confirmed', 'cannot_determine', 'rejected']"
                :key="s"
                :value="s"
                :label="label(s)"
                :disabled="s === 'confirmed' && !sourceValid"
              />
            </el-select>
          </el-form-item>
          <el-checkbox v-model="form.included">
            纳入导出范围（未核仍不计已核合计）
          </el-checkbox><el-form-item label="核对依据/修改理由（必填，至少4字）">
            <el-input v-model="form.reason" type="textarea" maxlength="500" show-word-limit />
          </el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="busy"
            :disabled="form.reason.trim().length < 4"
          >
            {{ row ? "保存本行核对" : "保存待核补录" }}
          </el-button><el-button :disabled="busy" @click="emit('close')">
            返回列表
          </el-button>
        </el-form>
        <el-button v-if="row" :disabled="busy || !sourceValid" @click="beginSplit">
          拆分错误行
        </el-button>
        <section v-if="splitParts.length">
          <h3>拆分为待核行</h3>
          <p>
            各片段必须在原行范围内且不能重叠。原行与审查历史保留并排除，拆分行一律待核。请逐条核对金额，使用上方的核对理由。
          </p>
          <el-form
            v-for="(part, index) in splitParts"
            :key="index"
            label-position="top"
            :disabled="busy"
          >
            <p>待核行 {{ index + 1 }}</p>
            <el-form-item label="来源片段起点 / 终点">
              <el-input-number
                v-model="part.start"
                :min="source?.start || 0"
                :max="source?.end"
              /><el-input-number v-model="part.end" :min="source?.start || 0" :max="source?.end" />
            </el-form-item>
            <el-form-item label="交易时间原文">
              <el-input v-model="part.values.tradeTimeRaw" maxlength="100" />
            </el-form-item><el-form-item label="金额原文">
              <el-input v-model="part.values.amountRaw" maxlength="100" />
            </el-form-item><el-form-item label="余额原文">
              <el-input v-model="part.values.balanceRaw" maxlength="100" />
            </el-form-item><el-form-item label="方向">
              <el-select v-model="part.values.direction">
                <el-option
                  v-for="state in ['income', 'expense', 'unknown']"
                  :key="state"
                  :value="state"
                  :label="label(state)"
                />
              </el-select>
            </el-form-item><el-form-item label="交易参考号">
              <el-input v-model="part.values.transactionRef" maxlength="200" />
            </el-form-item>
          </el-form><el-button
            type="primary"
            :loading="busy"
            :disabled="form.reason.trim().length < 4"
            @click="saveSplit"
          >
            保存拆分待核行
          </el-button><el-button :disabled="busy" @click="splitParts = []">
            取消拆分
          </el-button>
        </section>
      </section>
    </div>
    <el-collapse v-if="history.length">
      <el-collapse-item title="只追加的字段变更历史（敏感字段脱敏）">
        <article v-for="item in history" :key="item.id">
          <p>
            {{ item.action }} · {{ item.operatorId }} · {{ item.createTime }} · {{ item.reason }}
          </p>
          <pre>
旧：{{ item.beforeJson }}
新：{{ item.afterJson }}</pre>
        </article>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.bank-editor {
  padding: var(--yl-space-4);
}
.bank-editor__columns {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 1fr);
  gap: var(--yl-space-6);
  margin: var(--yl-space-4) 0;
}
.bank-editor__source {
  min-width: 0;
}
.bank-editor__source img {
  width: 100%;
  max-width: none;
  height: auto;
  border: 1px solid var(--yl-color-border);
}
.bank-paper-scroll {
  max-height: 70vh;
  overflow: auto;
}
.bank-paper-tools {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yl-space-2);
  align-items: center;
  margin: var(--yl-space-3) 0;
}
.bank-editor pre {
  padding: var(--yl-space-3);
  font-size: var(--yl-text-sm);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  background: var(--el-fill-color-light);
}
.bank-editor h2 {
  font-size: var(--yl-title-md);
}
.bank-editor__fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--yl-space-3);
}

@media (width <= 1000px) {
  .bank-editor__columns {
    grid-template-columns: 1fr;
  }
  .bank-editor__source {
    max-height: 50vh;
    overflow: auto;
  }
}
</style>
