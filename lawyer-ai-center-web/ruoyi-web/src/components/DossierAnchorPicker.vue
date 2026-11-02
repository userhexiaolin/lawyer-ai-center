<script setup lang="ts">
import type { Region, SourceAnchor } from '@/api/lawyer/claims';
import type { FileVersion, Material, MaterialPage } from '@/api/lawyer/dossier';
import { computed, onScopeDispose, ref, watch } from 'vue';
import { textareaSourceOffsets } from '@/api/lawyer/claims';
import { request } from '@/api/lawyer/dossier';
import DossierSourceViewer from '@/components/DossierSourceViewer.vue';
import { useUserStore } from '@/stores';

const props = defineProps<{
  caseId: string;
  materials: Material[];
}>();
const emit = defineEmits<{
  added: [
        anchor: SourceAnchor,
  ];
}>();
const user = useUserStore();
const materialId = ref('');
const versionId = ref('');
const versions = ref<FileVersion[]>([]);
const pages = ref<(MaterialPage & {
  textContent: string;
  pageLabel?: string;
})[]>([]);
const pageIndex = ref(1);
const kind = ref('text_offset');
const region = ref<Region>();
const start = ref(0);
const end = ref(0);
const tableRow = ref(1);
const tableColumn = ref(1);
const pinHistory = ref(false);
const busy = ref(false);
const error = ref('');
const page = computed(() => pages.value.find(p => p.pageIndex === pageIndex.value));
const fragment = computed(() => page.value?.textContent?.slice(start.value, end.value) || '');
const historical = computed(() => versionId.value !== props.materials.find(m => m.material.id === materialId.value)?.current?.id);
let serial = 0;
let disposed = false;
async function files() {
  const run = ++serial;
  const token = user.token;
  versions.value = [];
  pages.value = [];
  error.value = '';
  if (!materialId.value || !token)
    return;
  try {
    const result = await request<FileVersion[]>(props.caseId, `/materials/${materialId.value}/versions`);
    if (run !== serial || disposed || token !== user.token)
      return;
    versions.value = result;
    versionId.value = props.materials.find(m => m.material.id === materialId.value)?.current?.id || result[0]?.id || '';
  }
  catch (e) {
    if (run === serial && token === user.token)
      error.value = (e as Error).message;
  }
}
async function loadPages() {
  const run = ++serial;
  const token = user.token;
  pages.value = [];
  error.value = '';
  region.value = undefined;
  pinHistory.value = false;
  if (!versionId.value || !token)
    return;
  try {
    const result = await request<typeof pages.value>(props.caseId, `/files/${versionId.value}/pages`);
    if (run !== serial || disposed || token !== user.token)
      return;
    pages.value = result;
    pageIndex.value = result[0]?.pageIndex || 1;
    start.value = 0;
    end.value = 0;
  }
  catch (e) {
    if (run === serial && token === user.token)
      error.value = (e as Error).message;
  }
}
function selectText(e: Event) {
  const input = e.target as HTMLTextAreaElement;
  const offsets = textareaSourceOffsets(page.value?.textContent || '', input.selectionStart, input.selectionEnd);
  start.value = offsets.start;
  end.value = offsets.end;
}
async function save() {
  busy.value = true;
  error.value = '';
  const token = user.token;
  const run = serial;
  try {
    const anchor = await request<SourceAnchor>(props.caseId, '/anchors', 'POST', { fileVersionId: versionId.value, pageIndex: pageIndex.value, kind: kind.value, start: kind.value === 'image_region' ? null : start.value, end: kind.value === 'image_region' ? null : end.value, fragment: kind.value === 'image_region' ? '' : fragment.value, region: ['ocr_bbox', 'image_region'].includes(kind.value) ? region.value : null, tableRow: kind.value === 'table_cell' ? tableRow.value : null, tableColumn: kind.value === 'table_cell' ? tableColumn.value : null, pinHistory: pinHistory.value });
    if (run === serial && !disposed && user.token === token)
      emit('added', anchor);
  }
  catch (e) {
    if (run === serial && user.token === token)
      error.value = (e as Error).message;
  }
  finally {
    if (run === serial)
      busy.value = false;
  }
}
watch(() => [props.caseId, user.token], () => {
  serial++;
  materialId.value = '';
  versionId.value = '';
  versions.value = [];
  pages.value = [];
  error.value = '';
});
watch(materialId, files);
watch(versionId, loadPages);
watch(pageIndex, () => {
  region.value = undefined;
  start.value = 0;
  end.value = 0;
});
onScopeDispose(() => {
  disposed = true;
  serial++;
});
</script>

<template>
  <section class="anchor-picker" aria-label="选择真实来源">
    <el-form label-position="top">
      <el-form-item label="材料">
        <el-select v-model="materialId" placeholder="选择已有材料">
          <el-option v-for="m in materials" :key="m.material.id" :label="m.material.displayName" :value="m.material.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="材料版本">
        <el-select v-model="versionId">
          <el-option v-for="v in versions" :key="v.id" :label="`${v.fileName} · v${v.versionNo}`" :value="v.id" />
        </el-select>
      </el-form-item>
      <el-checkbox v-if="historical && versionId" v-model="pinHistory">
        明确固定此历史版本（不会自动替换为当前材料）
      </el-checkbox>
      <el-form-item label="物理/系统页">
        <el-select v-model="pageIndex">
          <el-option v-for="p in pages" :key="p.id" :value="p.pageIndex" :label="`第${p.pageIndex}页 · ${p.extractStatus} ${p.pageLabel || ''}`" />
        </el-select>
      </el-form-item>
      <el-form-item label="定位方式">
        <el-select v-model="kind">
          <el-option label="页文本字符范围" value="text_offset" /><el-option label="表格原始行列" value="table_cell" /><el-option label="图像区域＋本地OCR复核" value="ocr_bbox" /><el-option label="人工图像区域（不冒充OCR文字）" value="image_region" />
        </el-select>
      </el-form-item>
      <DossierSourceViewer v-if="versionId && page" :case-id="caseId" :file-version-id="versionId" :file-name="versions.find(v => v.id === versionId)?.fileName" :page-index="pageIndex" :select-region="['ocr_bbox', 'image_region'].includes(kind)" @region="region = $event" />
      <template v-if="kind !== 'image_region'">
        <label for="anchor-page-text">选中下方真实文字，或输入精确字符范围；同一金额重复时以范围区分</label>
        <textarea id="anchor-page-text" class="anchor-text" readonly :value="page?.textContent || ''" @select="selectText" />
        <div class="anchor-range">
          <el-input-number v-model="start" :min="0" aria-label="片段开始字符" /><span>～</span><el-input-number v-model="end" :min="0" aria-label="片段结束字符" />
        </div>
        <p>所选原文：{{ fragment || '尚未选定' }}</p>
      </template>
      <div v-if="kind === 'table_cell'" class="anchor-range">
        <el-input-number v-model="tableRow" :min="1" aria-label="原始表格行" /><el-input-number v-model="tableColumn" :min="1" aria-label="原始表格列" />
      </div>
      <p v-if="['ocr_bbox', 'image_region'].includes(kind)">
        在真实原页拖动选择区域。{{ region ? '已选择区域' : '尚未选择区域' }}；坐标与图像指纹由服务端核对。
      </p>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-button :loading="busy" :disabled="!page || (historical && !pinHistory)" @click="save">
        校验并关联此来源
      </el-button>
    </el-form>
  </section>
</template>

<style scoped lang="scss">
.anchor-picker {
padding: var(--yl-space-4);
border: 1px solid var(--yl-color-border);
border-radius: var(--yl-radius-panel);
}
.anchor-text {
box-sizing: border-box;
width: 100%;
min-height: 160px;
padding: var(--yl-space-3);
font: inherit;
color: var(--yl-color-text);
background: var(--yl-color-surface);
border: 1px solid var(--yl-color-border);
}
.anchor-range {
display: flex;
flex-wrap: wrap;
gap: var(--yl-space-2);
margin: var(--yl-space-3) 0;
}
</style>
