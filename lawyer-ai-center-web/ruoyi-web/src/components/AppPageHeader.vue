<script setup lang="ts">
defineProps<{
  title: string;
  description?: string;
  eyebrow?: string;
}>();
</script>

<template>
  <header class="yl-page-header">
    <div class="yl-page-header__content">
      <p v-if="eyebrow" class="yl-page-header__eyebrow">
        {{ eyebrow }}
      </p>
      <h1 class="yl-page-header__title">
        {{ title }}
      </h1>
      <p v-if="description" class="yl-page-header__description">
        {{ description }}
      </p>
      <div v-if="$slots.meta" class="yl-page-header__meta">
        <slot name="meta" />
      </div>
    </div>
    <div v-if="$slots.actions" class="yl-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.yl-page-header {
  display: flex;
  gap: var(--yl-space-6);
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--yl-space-6);
}
.yl-page-header__content {
  min-width: 0;
}
.yl-page-header__eyebrow {
  margin: 0 0 var(--yl-space-2);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
  color: var(--yl-color-brand-strong);
  letter-spacing: 0.08em;
}
.yl-page-header__title {
  margin: 0;
  font-size: var(--yl-title-lg);
  font-weight: 600;
  line-height: 1.4;
  color: var(--yl-color-text);
  letter-spacing: -0.025em;
  overflow-wrap: anywhere;
}
.yl-page-header__description {
  max-width: 78ch;
  margin: var(--yl-space-2) 0 0;
  font-size: var(--yl-text-base);
  line-height: 1.7;
  color: var(--yl-color-text-muted);
}
.yl-page-header__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yl-space-3);
  align-items: center;
  margin-top: var(--yl-space-4);
}
.yl-page-header__actions {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: var(--yl-space-2);
  align-items: center;
  justify-content: flex-end;
  max-width: 55%;
  padding-top: var(--yl-space-1);
}

@media (width <= 767px) {
  .yl-page-header {
    flex-direction: column;
    gap: var(--yl-space-4);
  }
  .yl-page-header__actions {
    justify-content: flex-start;
    max-width: 100%;
  }
  .yl-page-header__title {
    font-size: 22px;
  }
}
</style>
