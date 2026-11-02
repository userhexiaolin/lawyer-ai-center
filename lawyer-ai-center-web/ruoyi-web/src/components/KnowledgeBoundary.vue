<template>
  <aside class="knowledge-boundary" aria-label="知识库数据边界">
    本系统知识库为精选/自建内容，非全网裁判文书库；重要结论请人工复核并自行查证权威来源。
    <router-link to="/help">
      查看内容范围与来源说明
    </router-link>
  </aside>
</template>

<style scoped>
.knowledge-boundary {
  margin: 16px 0;
  padding: 12px 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.8;
  background: var(--el-fill-color-light);
}
.knowledge-boundary a {
  margin-left: 8px;
  color: var(--yl-color-brand-strong);
}
</style>
