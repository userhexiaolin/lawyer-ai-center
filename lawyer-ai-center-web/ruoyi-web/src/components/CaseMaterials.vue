<script setup lang="ts">
import { onScopeDispose, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@/stores';
import { lawyerRequest, type CaseFile } from '@/api/lawyer';
import { caseFiles, readCaseFileBlob, downloadCaseFile } from '@/api/lawyer/materials';
import { uploadFile } from '@/api/oss';
const props = defineProps<{ caseId: string; disabled?: boolean }>(), emit = defineEmits<{ busy: [value: boolean] }>();
const user = useUserStore(), files = ref<CaseFile[]>([]), input = ref<HTMLInputElement>(), busy = ref(false), loading = ref(false), error = ref('');
const pending = ref<{ caseId: string; ossId: string }>();
let version = 0, disposed = false;
onScopeDispose(() => { disposed = true; version++; emit('busy', false); });
async function load() {
  const v = ++version, owner = user.token; files.value = []; error.value = ''; loading.value = true;
  if (!owner) { loading.value = false; return; }
  try { const rows = await caseFiles(props.caseId); if (!disposed && v === version && user.token === owner) files.value = rows; }
  catch (e) { if (!disposed && v === version && user.token === owner) error.value = (e as Error).message; }
  finally { if (!disposed && v === version) loading.value = false; }
}
async function run(action: (id: string, check: () => void) => Promise<void>) {
  if (busy.value || props.disabled || loading.value || disposed || !user.token) return;
  const v = version, owner = user.token, id = props.caseId; busy.value = true; emit('busy', true); error.value = '';
  const active = () => !disposed && v === version && owner === user.token;
  const check = () => { if (!active()) throw Error('操作已取消'); };
  try { await action(id, check); }
  catch (e) { if (active()) { error.value = (e as Error).message; ElMessage.error(error.value); } }
  finally { if (active()) { busy.value = false; emit('busy', false); } }
}
async function bind(id: string, check: () => void) {
  if (!pending.value || pending.value.caseId !== id) return;
  try { await lawyerRequest(`/lawyer/case/${id}/files`, 'POST', { ossId: pending.value.ossId }); }
  catch (e) { throw Error('文件已上传，关联失败，请重试关联。' + (e as Error).message); }
  check(); pending.value = undefined; const rows = await caseFiles(id); check(); files.value = rows; ElMessage.success('材料已归档至本案件');
}
function upload(event: Event) {
  const element = event.target as HTMLInputElement, file = element.files?.[0]; element.value = '';
  if (!file) return;
  if (pending.value) { ElMessage.warning('请先重试关联已上传的文件'); return; }
  if (!file.size || file.size > 10 * 1024 * 1024) { ElMessage.warning('请选择非空且不超过10MB的文件'); return; }
  void run(async (id, check) => { const result = await uploadFile(file); check(); pending.value = { caseId: id, ossId: String(result.ossId) }; await bind(id, check); });
}
function open(file: CaseFile) {
  if (busy.value || props.disabled || loading.value) return;
  const popup = window.open('about:blank', '_blank'); if (popup) popup.opener = null;
  void run(async (id, check) => {
    try {
      const blob = await readCaseFileBlob(id, file); check();
      if (!popup) throw Error('打开窗口被浏览器拦截，请允许弹窗或使用下载');
      const suffix = (file.fileSuffix || file.originalName?.split('.').pop() || '').replace('.', '').toLowerCase();
      const mime = suffix === 'pdf' ? 'application/pdf' : suffix === 'png' ? 'image/png' : ['jpg','jpeg'].includes(suffix) ? 'image/jpeg' : blob.type;
      const url = URL.createObjectURL(new Blob([blob], { type: mime })); popup.location.href = url;
      setTimeout(() => URL.revokeObjectURL(url), 300000);
    } catch (e) { popup?.close(); throw e; }
  });
}
function download(file: CaseFile) { void run(async (id, check) => { await downloadCaseFile(id, file); check(); ElMessage.success('下载已开始'); }); }
async function unlink(file: CaseFile) {
  const owner = user.token, id = props.caseId;
  try { await ElMessageBox.confirm('解除关联后，原始文件仍保留。', '解除材料关联', { confirmButtonText: '解除关联', cancelButtonText: '取消' }); } catch { return; }
  if (owner !== user.token || id !== props.caseId || disposed) return;
  await run(async (caseId, check) => { await lawyerRequest(`/lawyer/case/${caseId}/files/${file.ossId}`, 'DELETE'); check(); files.value = files.value.filter(f => f.ossId !== file.ossId); });
}
watch(() => [props.caseId, user.token], () => { busy.value = false; emit('busy', false); pending.value = undefined; void load(); }, { immediate: true, flush: 'sync' });
</script>
<template>
  <section class="case-materials" v-loading="loading">
    <input ref="input" type="file" hidden :disabled="busy || disabled || loading" @change="upload">
    <div class="material-actions"><el-button type="primary" :loading="busy" :disabled="busy || disabled || loading || !!pending" @click="input?.click()">上传并归档（最大10MB）</el-button><el-button :disabled="busy || disabled || loading" @click="load">刷新</el-button><el-button v-if="pending" :disabled="busy || disabled" @click="run(bind)">重试关联已上传文件</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-empty v-if="!files.length && !loading" description="暂无材料，可在此上传并绑定当前案件" :image-size="60" />
    <article v-for="file in files" :key="file.ossId" class="material-card"><strong>{{ file.originalName || file.fileName || '未命名材料' }}</strong><p>上传时间：{{ (file.uploadedAt || '').replace('T', ' ') || '暂无记录' }}</p><div class="material-actions"><el-button link type="primary" :disabled="busy || disabled" @click="open(file)">打开</el-button><el-button link type="primary" :disabled="busy || disabled" @click="download(file)">下载</el-button><el-button link :disabled="busy || disabled" @click="unlink(file)">解除关联</el-button></div></article>
  </section>
</template>
<style scoped>.material-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin:10px 0}.material-actions .el-button+.el-button{margin-left:0}.material-card{padding:16px 0;border-bottom:1px solid var(--el-border-color-lighter);overflow-wrap:anywhere}.material-card p{font-size:12px;color:var(--el-text-color-secondary)}.case-materials .el-alert{margin:12px 0}</style>
