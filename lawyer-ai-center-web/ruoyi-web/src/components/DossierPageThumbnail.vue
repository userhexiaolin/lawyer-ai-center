<script setup lang="ts">
import { onMounted, onScopeDispose, ref, watch } from 'vue';
import { authenticatedBlob } from '@/api/lawyer/dossier';
import { useUserStore } from '@/stores';

const props = defineProps<{
  caseId: string;
  versionId: string;
  fileName: string;
  versionNo: number;
  page: number;
}>();
const user = useUserStore();
const root = ref<HTMLElement>();
const url = ref('');
const error = ref('');
const visible = ref(false);
let observer: IntersectionObserver | undefined;
let generation = 0;
function release() {
  generation++;
  if (url.value)
    URL.revokeObjectURL(url.value);
  url.value = '';
  error.value = '';
}
async function load() {
  release();
  if (!visible.value || !user.token)
    return;
  const current = generation;
  try {
    const blob = await authenticatedBlob(props.caseId, props.versionId, props.page);
    if (generation === current)
      url.value = URL.createObjectURL(blob);
  }
  catch {
    if (generation === current)
      error.value = '缩略图不可用，可查看原页或下载原件';
  }
}
watch(() => [visible.value, props.caseId, props.versionId, props.page, user.token], load);
onMounted(() => {
  observer = new IntersectionObserver((entries) => {
    visible.value = !!entries[0]?.isIntersecting;
  });
  if (root.value)
    observer.observe(root.value);
});
onScopeDispose(() => {
  observer?.disconnect();
  release();
});
</script>

<template>
  <figure ref="root" class="thumbnail">
    <img v-if="url" :src="url" :alt="`${fileName} V${versionNo} 第${page}物理页`">
    <span v-else>{{ error || '原页预览加载中' }}</span>
    <figcaption :title="fileName">
      {{ fileName }} · V{{ versionNo }} · 第{{ page }}物理页
    </figcaption>
  </figure>
</template>

<style scoped>
.thumbnail {
  line-height: 1.6;
  margin: 8px 0;
  min-height: 145px;
  background: var(--el-fill-color-light);
  padding: 8px;
  text-align: center;
}
.thumbnail img {
  width: 100%;
  height: 130px;
  object-fit: contain;
}
.thumbnail span {
  display: block;
  height: 130px;
  padding-top: 40px;
  font-size: 12px;
}
.thumbnail figcaption {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  margin-top: 5px;
}
</style>
