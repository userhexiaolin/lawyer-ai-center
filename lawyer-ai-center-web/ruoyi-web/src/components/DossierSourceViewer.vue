<script setup lang="ts">
import type { Region, ResolvedSource } from '@/api/lawyer/claims';
import { computed, onScopeDispose, ref, watch } from 'vue';
import { geometry } from '@/api/lawyer/claims';
import { authenticatedBlob, downloadVersion } from '@/api/lawyer/dossier';
import AppState from '@/components/AppState.vue';
import { useUserStore } from '@/stores';

const props = defineProps<{
  caseId: string;
  source?: ResolvedSource;
  fileVersionId?: string;
  fileName?: string;
  pageIndex?: number;
  selectRegion?: boolean;
  browsePage?: number;
}>();
const emit = defineEmits<{
  region: [
        value: Region,
  ];
  navigate: [
        page: number,
  ];
}>();
const user = useUserStore();
const imageUrl = ref('');
const error = ref('');
const loading = ref(false);
const zoom = ref(100);
const pane = ref<HTMLElement>();
const originalImage = ref<HTMLImageElement>();
const search = ref('');
const matchIndex = ref(0);
const downloadError = ref('');
const selection = ref<Region>();
const dragging = ref<{
  x: number;
  y: number;
}>();
let serial = 0;
let disposed = false;
const versionId = computed(() => props.source?.anchor.fileVersionId || props.fileVersionId);
const page = computed(() => props.browsePage || props.source?.anchor.pageIndex || props.pageIndex);
const atSource = computed(() => !props.source || page.value === props.source.anchor.pageIndex);
const box = computed(() => atSource.value ? selection.value || geometry(props.source?.anchor.geometryJson) : undefined);
const matches = computed(() => {
  const result: number[] = [];
  if (!search.value || !atSource.value)
    return result;
  const text = props.source?.pageText || '';
  for (let start = text.indexOf(search.value); start >= 0 && result.length < 2000; start = text.indexOf(search.value, start + 1))
    result.push(start);
  return result;
});
const startOffset = computed(() => search.value && matches.value.length ? matches.value[matchIndex.value % matches.value.length] : props.source?.anchor.fragmentStart ?? 0);
const endOffset = computed(() => search.value && matches.value.length ? startOffset.value + search.value.length : props.source?.anchor.fragmentEnd ?? 0);
const before = computed(() => props.source?.pageText.slice(0, startOffset.value) || '');
const highlighted = computed(() => props.source?.pageText.slice(startOffset.value, endOffset.value) || '');
const after = computed(() => props.source?.pageText.slice(endOffset.value) || '');
function fitHeight() {
  const image = originalImage.value;
  const scroll = pane.value;
  if (image?.naturalWidth && image.naturalHeight && scroll?.clientWidth)
    zoom.value = Math.max(1, Math.min(300, Math.floor(scroll.clientHeight / image.naturalHeight * image.naturalWidth / scroll.clientWidth * 100)));
}
async function download() {
  if (!versionId.value)
    return;
  downloadError.value = '';
  try {
    await downloadVersion(props.caseId, { id: versionId.value, fileName: props.source?.fileName || props.fileName || `原件-${versionId.value}` });
  }
  catch (e) {
    downloadError.value = (e as Error).message;
  }
}
function clear() {
  if (imageUrl.value)
    URL.revokeObjectURL(imageUrl.value);
  imageUrl.value = '';
}
async function load() {
  const epoch = ++serial;
  clear();
  error.value = '';
  selection.value = undefined;
  zoom.value = 100;
  if (!props.caseId || !versionId.value || !page.value || !user.token)
    return;
  loading.value = true;
  const token = user.token;
  try {
    const blob = await authenticatedBlob(props.caseId, versionId.value, page.value);
    if (epoch === serial && !disposed && user.token === token)
      imageUrl.value = URL.createObjectURL(blob);
  }
  catch (e) {
    if (epoch === serial && !disposed && user.token === token)
      error.value = (e as Error).message;
  }
  finally {
    if (epoch === serial)
      loading.value = false;
  }
}
function point(e: PointerEvent) {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  return { x: Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width)), y: Math.max(0, Math.min(1, (e.clientY - rect.top) / rect.height)) };
}
function start(e: PointerEvent) {
  if (!props.selectRegion)
    return;
  dragging.value = point(e);
  (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
}
function finish(e: PointerEvent) {
  if (!dragging.value)
    return;
  const end = point(e);
  const begin = dragging.value;
  dragging.value = undefined;
  const region = { x: Math.min(begin.x, end.x), y: Math.min(begin.y, end.y), width: Math.abs(begin.x - end.x), height: Math.abs(begin.y - end.y) };
  if (region.width > 0.001 && region.height > 0.001) {
    selection.value = region;
    emit('region', region);
  }
}
watch(() => [props.caseId, versionId.value, page.value, user.token], load, { immediate: true });
watch(() => props.source?.anchor.id, () => {
  selection.value = undefined;
  search.value = '';
  matchIndex.value = 0;
});
watch(search, () => {
  matchIndex.value = 0;
});
onScopeDispose(() => {
  disposed = true;
  serial++;
  clear();
});
</script>

<template>
  <section class="dossier-source" aria-label="原页与来源定位">
    <div class="source-toolbar">
      <span v-if="source">{{ source.fileName }} · 物理/系统第{{ source.anchor.pageIndex }}页 {{ source.pageLabel || '' }}</span>
      <el-button @click="zoom = 100">
        适宽
      </el-button>
      <el-button @click="fitHeight">
        适高
      </el-button>
      <el-button :disabled="!versionId" @click="download">
        下载原件
      </el-button>
      <el-slider v-model="zoom" :min="1" :max="300" :step="1" aria-label="原页缩放" />
      <span>{{ zoom }}%</span>
    </div>
    <el-alert v-if="source && !source.verified" type="error" :closable="false" :title="`出处未验证：${source.error || '请人工定位'}；当前不能确认`" />
    <el-alert v-if="source && !source.current" type="warning" :closable="false" :title="source.anchor.pinnedHistory ? '明确固定的历史版本，请留意与当前材料差异' : '材料版本已替换，需要重新核对来源'" />
    <AppState v-if="loading" kind="loading" title="正在读取原页" />
    <AppState v-else-if="error" kind="error" :title="error" description="可从材料页下载原件人工定位；预览失败不会变成来源已核实。">
      <el-button @click="load">
        重试预览
      </el-button>
    </AppState>
    <div v-else-if="imageUrl" ref="pane" class="source-scroll">
      <div class="source-image" :style="{ width: `${zoom}%` }" :class="{ selecting: selectRegion }" @pointerdown="start" @pointerup="finish" @pointercancel="dragging = undefined">
        <img ref="originalImage" :src="imageUrl" alt="所选材料的真实原页" draggable="false">
        <div v-if="box" class="source-highlight" :style="{ left: `${box.x * 100}%`, top: `${box.y * 100}%`, width: `${box.width * 100}%`, height: `${box.height * 100}%` }" />
      </div>
    </div>
    <el-alert v-if="downloadError" type="error" :title="downloadError" :closable="false" />
    <el-alert v-if="!atSource" type="warning" :closable="false" title="正在浏览其他页，当前页不作为候选出处">
      <el-button @click="emit('navigate', source!.anchor.pageIndex)">
        返回候选来源页
      </el-button>
    </el-alert>
    <template v-if="source && atSource">
      <p v-if="source.anchor.kind === 'image_region'">
        人工选定图像区域；没有机器文字，不代表图中文字已被自动验证。
      </p>
      <template v-else>
        <div class="source-toolbar">
          <el-input v-model="search" placeholder="在真实页文本中查找" aria-label="页文本搜索" clearable /><span v-if="search">{{ matches.length ? matchIndex % matches.length + 1 : 0 }}/{{ matches.length }}（最多2000处）</span><el-button :disabled="!matches.length" @click="matchIndex++">
            下一匹配
          </el-button>
        </div>
        <p>字符范围 {{ source.anchor.fragmentStart }}～{{ source.anchor.fragmentEnd }}；无可靠坐标时只在下方页文本高亮，原页不绘制推测框。</p>
        <p v-if="search && box">
          图像框仍为原候选的真实区域；搜索仅定位下方页文本。
        </p>
        <pre class="source-text">{{ before }}<mark>{{ highlighted }}</mark>{{ after }}</pre>
      </template>
    </template>
  </section>
</template>

<style scoped lang="scss">
.dossier-source {
min-width: 0;
}
.source-toolbar {
display: flex;
flex-wrap: wrap;
gap: var(--yl-space-2);
align-items: center;
margin-bottom: var(--yl-space-3);
}
.source-toolbar .el-slider {
width: 140px;
}
.source-scroll {
max-height: 60vh;
overflow: auto;
background: var(--yl-color-page);
border: 1px solid var(--yl-color-border);
}
.source-image {
position: relative;
min-width: 1%;
line-height: 0;
}
.source-image img {
width: 100%;
height: auto;
user-select: none;
}
.selecting {
touch-action: none;
cursor: crosshair;
}
.source-highlight {
position: absolute;
box-sizing: border-box;
pointer-events: none;
background: var(--el-color-warning-light-8);
border: 2px solid var(--el-color-warning);
opacity: 0.45;
}
.source-text {
max-height: 24vh;
padding: var(--yl-space-3);
overflow: auto;
font: inherit;
color: var(--yl-color-text);
overflow-wrap: anywhere;
white-space: pre-wrap;
background: var(--yl-color-page);
}
mark {
color: var(--yl-color-text);
background: var(--el-color-warning-light-7);
}
</style>
