<script setup lang="ts">
import type { ContractElements } from './contractElements';
import type { DocumentFormat } from '@/api/document';
import { computed, ref, watch } from 'vue';
import { documentBlob, saveDocumentDraft } from '@/api/document';
import { STANDARD_TEMPLATE, templatePrompt } from '@/api/document/templates';
import { useDraftActivity } from '@/hooks/useDraftWorkspace';
import { useUserStore } from '@/stores';
import { archiveName, batchArchive } from './batchArchive';
import { batchPrompt, isLitigation, runContractQueue } from './contractElements';
import { contractBytes, validateDraftingFiles } from './contractFiles';
import ContractUpload from './ContractUpload.vue';
import DocumentEditor from './DocumentEditor.vue';
import DocumentTemplatePicker from './DocumentTemplatePicker.vue';
import { documentDeliveryIssues } from './documentText';
import { generationError, prepareDrafting } from './generation';
import { useContractElements } from './useContractElements';

type Status
  = | '等待提取'
    | '提取中'
    | '待核对'
    | '提取失败'
    | '生成中'
    | '生成失败'
    | '待保存'
    | '已完成';
interface Item {
  id: number;
  file: File;
  status: Status;
  elements?: ContractElements;
  error: string;
  content: string;
  sessionId: string;
}
const props = defineProps<{ type: string }>();
const emit = defineEmits<{ busy: [boolean] }>();
const user = useUserStore();
const worker = useContractElements();
const rows = ref<Item[]>([]);
const busy = ref(false);
const error = ref('');
const templateId = ref('');
const purpose = ref('');
const requirements = ref('');
const role = ref('');
const selected = ref<Item>();
const format = ref<DocumentFormat>('docx');
const progress = ref('');
useDraftActivity(() => !!(rows.value.length || purpose.value || requirements.value || role.value), () => busy.value);
watch(busy, value => emit('busy', value), { flush: 'sync' });
let nextId = 0;
const totalBytes = computed(() => contractBytes(rows.value.map(row => row.file)));
const completed = computed(() => rows.value.filter(row => row.status === '已完成').length);
const ready = computed(() =>
  rows.value.filter(
    row => row.status === '待核对' || row.status === '生成失败' || row.status === '待保存',
  ),
);
function add(files: File[]) {
  if (busy.value)
    return;
  error.value = '';
  try {
    validateDraftingFiles(
      rows.value.map(row => row.file),
      files,
    );
  }
  catch (e) {
    error.value = generationError(e);
    return;
  }
  for (const file of files) {
    rows.value.push({
      id: ++nextId,
      file,
      status: '等待提取' as const,
      error: '',
      content: '',
      sessionId: '',
    });
  }
}
function stop() {
  worker.stop();
  busy.value = false;
  progress.value = '已停止，可继续处理剩余文件';
  for (const row of rows.value) {
    if (row.status === '提取中')
      row.status = '等待提取';
    if (row.status === '生成中')
      row.status = '生成失败';
  }
}
async function extract() {
  if (busy.value)
    return;
  if (!user.token) {
    user.ensureLogin('/drafting');
    return;
  }
  const active = worker.context();
  busy.value = true;
  error.value = '';
  await runContractQueue(
    rows.value.filter(row => row.status === '等待提取' || row.status === '提取失败'),
    active,
    async (row) => {
      row.status = '提取中';
      row.error = '';
      progress.value = `正在提取：${row.file.name}`;
      const elements = await worker.extract(row.file, active);
      if (!active())
        return;
      row.elements = elements;
      row.status = '待核对';
    },
    (row, e) => {
      row.status = '提取失败';
      row.error = generationError(e);
    },
  );
  if (active()) {
    busy.value = false;
    progress.value = '提取结束，请逐份核对要素并补充实际履行情况。';
  }
}
async function generate() {
  if (busy.value || !ready.value.length)
    return;
  if (!purpose.value.trim()) {
    error.value = '请填写共同起草目的或请求；履行、逾期等事实可在每份要素中补充';
    return;
  }
  if (isLitigation(props.type) && !role.value) {
    error.value = '请选择甲乙双方的诉讼地位；不同地位的合同请分批处理';
    return;
  }
  if (!templateId.value || templateId.value === STANDARD_TEMPLATE) {
    error.value = '批量起草请选择已上传的文书模板';
    return;
  }
  const active = worker.context();
  const items = [...ready.value];
  const snapshot = {
    type: props.type,
    template: templateId.value,
    purpose: purpose.value,
    requirements: requirements.value,
    role: role.value,
  };
  busy.value = true;
  error.value = '';
  try {
    const model = await prepareDrafting();
    if (!active())
      return;
    await runContractQueue(
      items,
      active,
      async (row) => {
        row.error = '';
        progress.value = `正在生成：${row.file.name}`;
        if (row.status !== '待保存') {
          row.status = '生成中';
          const prompt = batchPrompt(
            row.elements!,
            snapshot.type,
            snapshot.purpose,
            snapshot.requirements,
            snapshot.role,
            row.file.name,
          );
          const content = await templatePrompt(snapshot.type, snapshot.template, prompt);
          if (!active())
            return;
          const result = await worker.complete(
            content,
            `文书起草 - ${snapshot.type}`,
            model,
            active,
          );
          if (!active())
            return;
          row.content = result.content;
          row.sessionId = result.sessionId;
          row.status = '待保存';
        }
        const issues = documentDeliveryIssues(row.content);
        if (issues.length) {
          row.status = '生成失败';
          throw new Error(issues.join('；'));
        }
        await saveDocumentDraft(row.sessionId, row.content);
        if (active())
          row.status = '已完成';
      },
      (row, e) => {
        if (row.status !== '待保存')
          row.status = '生成失败';
        row.error = generationError(e);
      },
    );
  }
  catch (e) {
    if (active())
      error.value = generationError(e);
  }
  finally {
    if (active()) {
      busy.value = false;
      progress.value = `本轮结束，已完成 ${completed.value} / ${rows.value.length} 份。`;
    }
  }
}
async function download() {
  if (busy.value)
    return;
  const active = worker.context();
  const files: { name: string; bytes: Uint8Array }[] = [];
  const failures: string[] = [];
  busy.value = true;
  error.value = '';
  const snapshot = rows.value.filter(row => row.status === '已完成');
  try {
    for (const row of snapshot) {
      if (!active())
        return;
      progress.value = `正在打包：${row.file.name}`;
      try {
        const blob = await documentBlob({
          title: props.type,
          content: row.content,
          sessionId: row.sessionId,
          format: format.value,
        });
        if (!active())
          return;
        files.push({
          name: archiveName(rows.value.indexOf(row), row.file.name, props.type, format.value),
          bytes: new Uint8Array(await blob.arrayBuffer()),
        });
      }
      catch (e) {
        if (!active())
          return;
        failures.push(`${row.file.name}：${generationError(e)}`);
      }
    }
    if (!files.length)
      throw new Error(failures.join('；') || '暂无可下载文书');
    const report = rows.value.map(row => ({
      合同: row.file.name,
      状态: row.status,
      会话: row.sessionId,
      错误: row.error,
    }));
    files.push({
      name: '处理清单.json',
      bytes: new TextEncoder().encode(
        JSON.stringify({ 文书: report, 导出失败: failures }, null, 2),
      ),
    });
    if (!active())
      return;
    const blob = batchArchive(files);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${props.type}-批量文书.zip`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    progress.value = `已打包 ${files.length - 1} 份文书`;
    if (failures.length)
      error.value = `有 ${failures.length} 份导出失败，详见压缩包处理清单，可重新下载。`;
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
watch(
  () => user.token,
  () => {
    stop();
    rows.value = [];
    selected.value = undefined;
    purpose.value = '';
    requirements.value = '';
    role.value = '';
    error.value = '';
    progress.value = '';
  },
  { flush: 'sync' },
);
</script>

<template>
  <section class="batch-draft yl-panel">
    <h2>{{ type }} · 批量起草</h2>
    <p>
      上传合同 → 提取并核对每份要素 → 选择模板批量生成 →
      逐份编辑或打包下载。每份合同生成一份独立文书，不限份数，本批文件总大小不超过200MB。
    </p>
    <el-alert
      title="合同约定不代表已发生履行或违约。请在每份要素中补充真实经过，并核对共同请求是否适用于全部合同。处理中请保持页面打开；已生成的文书可从会话恢复。"
      type="info"
      :closable="false"
    /><DocumentTemplatePicker
      v-model="templateId"
      :type="type"
      :disabled="busy || rows.some((row) => !!row.sessionId)"
    /><el-form label-position="top" :disabled="busy">
      <el-form-item label="共同起草目的 / 请求（适用于每份文书，必填）">
        <el-input
          v-model="purpose"
          type="textarea"
          :rows="3"
          maxlength="5000"
          placeholder="例如：催告依合同履行付款义务；具体金额、日期以各合同及已核对的事实为准"
        />
      </el-form-item><el-form-item label="共同补充要求（选填）">
        <el-input v-model="requirements" maxlength="2000" />
      </el-form-item><el-form-item v-if="isLitigation(type)" label="本批合同的诉讼地位">
        <el-select v-model="role" placeholder="请明确原被告与合同甲乙方的对应关系">
          <el-option value="partyA" label="甲方为原告、乙方为被告" /><el-option
            value="partyB"
            label="乙方为原告、甲方为被告"
          />
        </el-select>
      </el-form-item>
    </el-form><ContractUpload
      label="批量上传合同"
      multiple
      :disabled="busy"
      @files="add"
      @error="error = $event"
    />
    <p>
      支持 PDF、DOCX、JPG、PNG。已选 {{ rows.length }} 份，合计
      {{ (totalBytes / 1024 / 1024).toFixed(2) }} / 200MB，已完成 {{ completed }} 份。
    </p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><el-space wrap>
      <el-button
        :disabled="
          busy || !rows.some((row) => row.status === '等待提取' || row.status === '提取失败')
        "
        @click="extract"
      >
        提取要素 / 重试提取失败项
      </el-button><el-button type="primary" :disabled="busy || !ready.length" @click="generate">
        核对后按模板生成 / 重试（{{ ready.length }}份）
      </el-button><el-button v-if="busy" @click="stop">
        停止处理
      </el-button><el-select v-model="format" :disabled="busy" aria-label="批量下载格式" style="width: 140px">
        <el-option value="docx" label="Word（DOCX）" /><el-option
          value="pdf"
          label="PDF"
        /><el-option value="md" label="Markdown" /><el-option value="txt" label="TXT" />
      </el-select><el-button :disabled="busy || !completed" @click="download">
        打包下载（{{ completed }}份）
      </el-button>
    </el-space>
    <p role="status">
      {{ progress }}
    </p>
    <el-table :data="rows" row-key="id" empty-text="请选择合同文件">
      <el-table-column label="合同文件" min-width="200">
        <template #default="{ row }">
          {{ row.file.name }}
        </template>
      </el-table-column><el-table-column prop="status" label="状态" width="100" /><el-table-column
        prop="error"
        label="失败原因"
        min-width="180"
      /><el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link :disabled="busy || !row.elements" @click="selected = row">
            核对要素 / 查看文书
          </el-button><el-button
            link
            :disabled="busy"
            @click="rows = rows.filter((item) => item.id !== row.id)"
          >
            移除
          </el-button>
        </template>
      </el-table-column>
    </el-table><el-dialog
      :model-value="!!selected"
      title="合同要素与独立文书"
      width="90%"
      @close="selected = undefined"
    >
      <template v-if="selected?.elements">
        <p>{{ selected.file.name }}</p>
        <el-form label-position="top" :disabled="busy || !!selected.content">
          <el-form-item label="甲方">
            <el-input
              v-model="selected.elements.partyA"
              type="textarea"
              maxlength="2000"
            />
          </el-form-item><el-form-item label="乙方">
            <el-input
              v-model="selected.elements.partyB"
              type="textarea"
              maxlength="2000"
            />
          </el-form-item><el-form-item label="合同约定与实际经过（请补充并核对）">
            <el-input
              v-model="selected.elements.facts"
              type="textarea"
              :rows="8"
              maxlength="20000"
            />
          </el-form-item><el-form-item label="合同期限">
            <el-input v-model="selected.elements.deadline" maxlength="2000" />
          </el-form-item>
        </el-form><DocumentEditor
          v-if="selected.content"
          v-model="selected.content"
          :title="type"
          :session-id="selected.sessionId"
          :busy="busy"
          @saved="
            selected.error = documentDeliveryIssues(selected.content).join('；');
            selected.status = selected.error ? '待保存' : '已完成';
          "
        />
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.batch-draft {
  margin-top: 24px;
}
.batch-draft p {
  line-height: 1.8;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.batch-draft > .el-space {
  margin: 12px 0;
}
.batch-draft > .el-form {
  margin-top: 20px;
}
</style>
