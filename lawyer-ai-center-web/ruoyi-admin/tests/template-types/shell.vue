<script setup lang="ts">
import type { Component } from 'vue';
defineProps<{
  page: Component;
  provider: Component;
  locale: object;
  theme: object;
}>();
</script>
<template>
  <component :is="provider" :locale="locale" :theme="theme">
    <aside class="fixture-nav">
      <strong>与律行</strong><span>管理工作台</span>
      <nav>
        <div>模板管理</div>
        <div>模板列表</div>
        <div class="active">模板类型</div>
      </nav>
      <small>隔离 UI 验收 · 合成数据</small>
    </aside>
    <div class="fixture-workspace">
      <div class="fixture-topbar">
        模板管理 / 模板类型<span>测试管理员</span>
      </div>
      <main><component :is="page" /></main>
    </div>
  </component>
</template>
