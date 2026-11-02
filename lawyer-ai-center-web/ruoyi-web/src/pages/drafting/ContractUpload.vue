<script setup lang="ts">
import { ref, watch } from 'vue';

const props = defineProps<{ label: string; multiple?: boolean; disabled?: boolean }>();
const emit = defineEmits<{ files: [File[]]; error: [string] }>();
const input = ref<HTMLInputElement>();
const depth = ref(0);
function receive(files: File[]) {
  if (props.disabled || !files.length)
    return;
  if (!props.multiple && files.length > 1) {
    emit('error', '单份起草每次请选择一份合同；多份合同请使用批量起草');
    return;
  }
  emit('files', files);
}
function select(event: Event) {
  const target = event.target as HTMLInputElement;
  const files = Array.from(target.files || []);
  target.value = '';
  receive(files);
}
function enter(event: DragEvent) {
  if (!props.disabled && event.dataTransfer?.types.includes('Files'))
    depth.value++;
}
function over(event: DragEvent) {
  if (event.dataTransfer)
    event.dataTransfer.dropEffect = props.disabled ? 'none' : 'copy';
}
function drop(event: DragEvent) {
  depth.value = 0;
  receive(Array.from(event.dataTransfer?.files || []));
}
function open() {
  if (!props.disabled)
    input.value?.click();
}
watch(
  () => props.disabled,
  () => {
    depth.value = 0;
  },
);
</script>

<template>
  <div class="contract-upload">
    <input
      ref="input"
      class="file-input"
      type="file"
      :multiple="multiple"
      accept=".pdf,.docx,.jpg,.jpeg,.png"
      :disabled="disabled"
      :aria-label="label"
      @change="select"
    >
    <div
      class="drop-zone"
      :class="{ 'is-over': depth > 0, 'is-disabled': disabled }"
      role="button"
      :tabindex="disabled ? -1 : 0"
      :aria-label="`${label}：拖放或点击选择文件`"
      :aria-disabled="!!disabled"
      @click="open"
      @keydown.enter.prevent="open"
      @keydown.space.prevent="open"
      @dragenter.prevent.stop="enter"
      @dragover.prevent.stop="over"
      @dragleave.prevent.stop="depth = Math.max(0, depth - 1)"
      @drop.prevent.stop="drop"
    >
      <strong>{{
        disabled
          ? '正在处理合同，请稍候'
          : depth > 0
            ? '松开鼠标添加合同'
            : multiple
              ? '将合同文件拖到此处，或点击选择（可多选）'
              : '将合同文件拖到此处，或点击选择'
      }}</strong><span>支持 PDF、DOCX、JPG、PNG；{{ multiple ? '本批文件总大小' : '文件大小' }}不超过200MB</span>
    </div>
  </div>
</template>

<style scoped>
.file-input {
  display: none;
}
.drop-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 140px;
  padding: 20px;
  border: 2px dashed var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  text-align: center;
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s;
}
.drop-zone:hover,
.drop-zone:focus-visible,
.drop-zone.is-over {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  outline: 0;
}
.drop-zone span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.drop-zone.is-disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
</style>
