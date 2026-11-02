<script setup lang="ts">
import { onScopeDispose, reactive, watch } from 'vue';
import { inboxApi } from '@/api/notifications';
import AppPageHeader from '@/components/AppPageHeader.vue';
import AppState from '@/components/AppState.vue';
import { useUserStore } from '@/stores';
import { createInboxWorkflow, initialInboxState } from './workflow';

const user = useUserStore();
const state = reactive(initialInboxState());
const inbox = createInboxWorkflow(state, inboxApi);
watch(
  () => user.token,
  (token) => {
    inbox.reset();
    if (token)
      void inbox.load();
  },
  { immediate: true, flush: 'sync' },
);
onScopeDispose(() => inbox.reset());
</script>

<template>
  <main class="notifications yl-page">
    <AppPageHeader
      title="个人通知"
      description="集中查看案件关键日期、开庭与任务提醒，及时回到对应案件。"
      eyebrow="工作提醒"
    >
      <template #actions>
        <el-button :loading="state.busy" :disabled="!!state.reading.length" @click="inbox.load()">
          刷新通知
        </el-button>
      </template><template #meta>
        <el-tag type="info" effect="plain">
          未读 {{ state.unread }} 条
        </el-tag>
      </template>
    </AppPageHeader>
    <p class="hint">
      未来 7 天内的提醒由后台定期写入，离线时也会保留。历史提醒中的日期以正文为准。
    </p>
    <el-alert v-if="state.error" :title="state.error" type="error" :closable="false" show-icon />
    <AppState
      v-if="!state.items.length && !state.busy && !state.error"
      title="当前没有新通知"
      description="临近开庭、关键日期或任务到期时，提醒会集中显示在这里。"
    />
    <div v-loading="state.busy && !state.items.length" class="notice-list" aria-live="polite">
      <article
        v-for="notice in state.items"
        :key="notice.id"
        class="notice"
        :class="{ unread: !notice.readAt }"
      >
        <div class="notice-heading">
          <h2>{{ notice.title }}</h2>
          <el-tag :type="notice.readAt ? 'info' : 'warning'">
            {{ notice.readAt ? '已读' : '未读' }}
          </el-tag>
        </div>
        <p class="content">
          {{ notice.content }}
        </p>
        <time>{{ notice.createdAt }}</time>
        <div class="actions">
          <router-link v-if="notice.caseId" :to="{ name: 'myCases', query: { id: notice.caseId } }">
            查看案件
          </router-link><el-button
            v-if="!notice.readAt"
            size="small"
            :disabled="state.busy"
            :loading="state.reading.includes(notice.id)"
            @click="inbox.read(notice)"
          >
            标为已读
          </el-button>
        </div>
      </article>
    </div>
    <el-button v-if="state.nextId" :loading="state.busy" @click="inbox.load(true)">
      加载更早通知
    </el-button>
  </main>
</template>

<style scoped>
.notifications {
  min-width: 0;
}
.notifications header,
.notice-heading,
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: space-between;
}
.notifications h1 {
  margin: 0;
  font-size: 24px;
}
.notifications header p,
.hint,
time {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.hint {
  line-height: 1.7;
}
.notice-list {
  min-height: 60px;
}
.notice {
  background: var(--yl-color-surface);
  border: 1px solid var(--el-border-color);
  border-radius: var(--yl-radius-panel);
  margin: 16px 0;
  padding: 18px;
}
.notice.unread {
  border-left: 4px solid var(--el-color-primary);
}
.notice h2 {
  font-size: 16px;
  margin: 0;
  overflow-wrap: anywhere;
}
.notice-heading .el-tag {
  flex-shrink: 0;
}
.content {
  white-space: pre-wrap;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.actions {
  justify-content: flex-start;
  margin-top: 12px;
}
.actions a {
  color: var(--yl-color-brand-strong);
}
@media (max-width: 600px) {
  .notifications {
    padding: 14px;
  }
  .notifications header {
    align-items: flex-start;
  }
  .notice {
    background: var(--yl-color-surface);
    padding: 12px;
  }
}
.notice-heading {
  flex-wrap: wrap;
}
.actions {
  flex-wrap: wrap;
}
.content {
  margin: 12px 0;
}
</style>
