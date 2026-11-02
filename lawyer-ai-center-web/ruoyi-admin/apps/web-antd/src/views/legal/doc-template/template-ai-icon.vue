<script setup lang="ts">
import { useId } from 'vue';

import { Sparkles } from '@vben/icons';

defineProps<{ disabled?: boolean }>();
const gradientId = `template-ai-${useId()}`;
</script>
<template>
  <Sparkles
    :size="18"
    :stroke="disabled ? 'currentColor' : `url(#${gradientId})`"
    :fill="disabled ? 'none' : `url(#${gradientId})`"
    :fill-opacity="0.16"
    :stroke-width="2"
    class="template-ai-icon"
    aria-hidden="true"
    focusable="false"
  >
    <defs>
      <linearGradient :id="gradientId" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" class="gradient-start" />
        <stop offset="100%" class="gradient-end" />
      </linearGradient>
    </defs>
  </Sparkles>
</template>
<style scoped>
.template-ai-icon {
  display: inline-block;
  flex-shrink: 0;
  vertical-align: -0.15em;
}

/* Reuse the existing Tailwind palette for this explicitly requested AI accent. */
.gradient-start {
  stop-color: theme('colors.blue.500');
}

.gradient-end {
  stop-color: theme('colors.violet.500');
}
</style>
