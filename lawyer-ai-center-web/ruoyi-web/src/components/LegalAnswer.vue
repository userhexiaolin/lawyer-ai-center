<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { splitLegalContent } from '@/utils/legalCitation';
import { codeXRender } from '@/utils/markdownRenderers';
import { presentMessage } from '@/utils/messagePresentation';
import LegalCitation from './LegalCitation.vue';

const props = defineProps<{ content: string; streaming?: boolean; collapsible?: boolean }>();
const expanded = ref(!!props.streaming);
const answer = computed(() => {
  const source = splitLegalContent(props.content);
  const display = presentMessage(source.markdown, { streaming: props.streaming });
  return { ...source, markdown: display.markdown, text: display.text };
});
const canCollapse = computed(() => !!props.collapsible && !props.streaming && answer.value.text.length > 4000);
watch(() => props.streaming, (streaming) => {
  if (streaming)
    expanded.value = true;
});
</script>

<template>
  <div>
    <div :class="{ 'answer-collapsed': canCollapse && !expanded }">
      <XMarkdown :markdown="answer.markdown" :code-x-render="codeXRender" class="markdown-body" :themes="{ light: 'github-light', dark: 'github-dark' }" default-theme-mode="dark" />
    </div>
    <el-button v-if="canCollapse" class="answer-toggle" text :aria-expanded="expanded" @click="expanded = !expanded">
      {{ expanded ? '收起长回答' : '展开完整回答' }}
    </el-button>
    <div v-if="answer.citations.length" aria-label="法规与类案引用">
      <LegalCitation v-for="(citation, index) in answer.citations" :key="index" :citation="citation" />
    </div>
  </div>
</template>

<style scoped>
.answer-collapsed {
  max-height: 420px;
  overflow: hidden;
}
.answer-toggle {
  min-height: 32px;
  margin-top: 8px;
}
</style>
