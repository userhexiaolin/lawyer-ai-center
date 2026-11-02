<script setup lang="ts">
import type { ContractElements } from './contractElements';
import { ref, watch } from 'vue';
import { useUserStore } from '@/stores';
import { contractForm, isLitigation } from './contractElements';
import ContractUpload from './ContractUpload.vue';
import { generationError } from './generation';
import { useContractElements } from './useContractElements';

const props = defineProps<{ type: string; disabled?: boolean }>();
const emit = defineEmits<{ apply: [ReturnType<typeof contractForm>]; busy: [boolean] }>();
const user = useUserStore();
const reader = useContractElements();
const busy = ref(false);
const error = ref('');
const elements = ref<ContractElements>();
const name = ref('');
const role = ref('');
const imported = ref(false);
async function select(files: File[]) {
  const file = files[0];
  if (!file || busy.value || props.disabled)
    return;
  if (!user.token) {
    user.ensureLogin('/drafting');
    return;
  }
  const active = reader.context();
  busy.value = true;
  emit('busy', true);
  error.value = '';
  elements.value = undefined;
  imported.value = false;
  role.value = '';
  name.value = file.name;
  try {
    const value = await reader.extract(file, active);
    if (active()) {
      elements.value = value;
      if (!isLitigation(props.type))
        apply();
    }
  }
  catch (e) {
    if (active())
      error.value = generationError(e);
  }
  finally {
    if (active()) {
      busy.value = false;
      emit('busy', false);
    }
  }
}
function apply() {
  try {
    if (elements.value) {
      emit('apply', contractForm(elements.value, props.type, role.value));
      imported.value = true;
      elements.value = undefined;
    }
  }
  catch (e) {
    error.value = generationError(e);
  }
}
watch(
  () => user.token,
  () => {
    busy.value = false;
    emit('busy', false);
    elements.value = undefined;
    error.value = '';
    name.value = '';
    role.value = '';
    imported.value = false;
  },
  { flush: 'sync' },
);
</script>

<template>
  <details class="contract-import">
    <summary>从合同提取要素（可选）</summary>
    <ContractUpload
      label="上传合同提取要素"
      :disabled="disabled || busy"
      @files="select"
      @error="error = $event"
    />
    <p>
      支持 PDF、DOCX、JPG、PNG，文件不超过 200MB。{{
        busy
          ? '正在读取合同并提取要素…'
          : isLitigation(type)
            ? '识别后请确认原被告身份，再代入诉讼要素。'
            : '提取成功后自动填入下方起草要素，起草目的按文书类型预填，可直接核对修改。'
      }}
    </p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert
      v-if="imported"
      :title="`已从“${name}”填入下方起草要素，请核对后生成。`"
      type="success"
      :closable="false"
    />
    <p v-if="imported && !isLitigation(type)">
      已保留合同全文及附件中的履行记录和发函资料，请核对识别结果。未记载的信息请按实际情况补充；使用模板时，缺失字段保留模板原有空白。
    </p>
    <div v-if="elements">
      <strong>{{ name }}</strong><el-form label-position="top">
        <el-form-item label="甲方">
          <el-input v-model="elements.partyA" type="textarea" maxlength="2000" />
        </el-form-item><el-form-item label="乙方">
          <el-input v-model="elements.partyB" type="textarea" maxlength="2000" />
        </el-form-item><el-form-item label="合同约定与事实">
          <el-input
            v-model="elements.facts"
            type="textarea"
            :rows="5"
            maxlength="60000"
          />
        </el-form-item><el-form-item label="合同期限">
          <el-input v-model="elements.deadline" maxlength="2000" />
        </el-form-item><el-form-item v-if="isLitigation(type)" label="诉讼地位（请按实际情况选择）">
          <el-select v-model="role" placeholder="请选择原告对应合同一方">
            <el-option value="partyA" label="甲方为原告、乙方为被告" /><el-option
              value="partyB"
              label="乙方为原告、甲方为被告"
            />
          </el-select>
        </el-form-item>
      </el-form><el-button type="primary" :disabled="disabled" @click="apply">
        核对后代入要素（替换对应字段）
      </el-button><el-button @click="elements = undefined">
        取消
      </el-button>
    </div>
  </details>
</template>

<style scoped>
.contract-import {
  padding: 16px;
  margin: 16px 0;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}

.contract-import p {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.contract-import strong {
  display: block;
  margin: 12px 0;
  overflow-wrap: anywhere;
}
.contract-import summary {
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: var(--yl-color-brand-strong);
  line-height: 1.7;
}
.contract-import[open] summary {
  margin-bottom: 16px;
}
</style>
