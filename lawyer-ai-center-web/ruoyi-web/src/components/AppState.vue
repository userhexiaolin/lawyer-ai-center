<script setup lang="ts">
import { Document, Loading, Warning } from '@element-plus/icons-vue';

withDefaults(
  defineProps<{
    title: string;
    description?: string;
    kind?: 'empty' | 'loading' | 'error';
  }>(),
  { kind: 'empty' },
);
</script>

<template>
  <section
    class="yl-state"
    :class="`yl-state--${kind}`"
    :role="kind === 'error' ? 'alert' : 'status'"
    aria-live="polite"
    :aria-busy="kind === 'loading'"
  >
    <span class="yl-state__icon" aria-hidden="true"><el-icon :size="24" :class="{ 'is-loading': kind === 'loading' }"><Loading v-if="kind === 'loading'" /><Warning v-else-if="kind === 'error'" /><Document
      v-else
    /></el-icon></span>
    <h2>{{ title }}</h2>
    <p v-if="description">
      {{ description }}
    </p>
    <div v-if="$slots.default" class="yl-state__actions">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.yl-state {
  display: flex;
  flex-direction: column;
  gap: var(--yl-space-3);
  align-items: center;
  justify-content: center;
  min-height: 200px;
  padding: var(--yl-space-6);
  color: var(--yl-color-text-muted);
  text-align: center;
}
.yl-state__icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--yl-color-border);
  border-radius: var(--yl-radius-panel);
}
.yl-state h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  color: var(--yl-color-text);
}
.yl-state p {
  max-width: 48ch;
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.yl-state__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yl-space-2);
  justify-content: center;
  margin-top: var(--yl-space-1);
}
.yl-state--error .yl-state__icon {
  color: var(--yl-color-danger);
  background: var(--el-color-danger-light-9);
}
</style>
