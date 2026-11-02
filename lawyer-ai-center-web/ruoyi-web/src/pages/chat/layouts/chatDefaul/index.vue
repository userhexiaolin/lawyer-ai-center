<!-- 默认消息列表页 -->
<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { ref } from 'vue';
import CaseAssociation from '@/components/CaseAssociation.vue';
import CaseReminders from '@/components/CaseReminders.vue';
import ChatSender from '@/components/ChatSender/index.vue';
import WelecomeText from '@/components/WelecomeText/index.vue';
import { useUserStore } from '@/stores';
import { useSessionStore } from '@/stores/modules/session';

const userStore = useUserStore();
const sessionStore = useSessionStore();

const caseId = ref('');
const senderValue = ref('');
const senderRef = ref<InstanceType<typeof ChatSender> | null>(null);

const creating = ref(false);
async function handleSubmit(content: string) {
  if (creating.value || !content.trim())
    return;
  creating.value = true;
  try {
    localStorage.setItem('chatContent', content);
    await sessionStore.createSessionList({
      userId: userStore.userInfo?.userId as number,
      sessionContent: content,
      sessionTitle: content.slice(0, 30),
      caseId: caseId.value || undefined,
      remark: content.slice(0, 30),
    });
    senderValue.value = '';
  }
  catch (e) {
    localStorage.removeItem('chatContent');
    senderValue.value = content;
    ElMessage.error((e as Error).message || '会话创建失败，请重试');
  }
  finally {
    creating.value = false;
  }
}
</script>

<template>
  <div class="chat-defaul-wrap">
    <WelecomeText />
    <nav class="chat-shortcuts" aria-label="开始办案任务">
      <router-link to="/contract-review">
        <el-icon aria-hidden="true">
          <DocumentChecked />
        </el-icon><strong>合同审查</strong><span>对照原文，定位风险</span><el-icon class="shortcut-arrow" aria-hidden="true">
          <ArrowRight />
        </el-icon>
      </router-link><router-link to="/drafting">
        <el-icon aria-hidden="true">
          <EditPen />
        </el-icon><strong>文书起草</strong><span>填写要素，编辑定稿</span><el-icon class="shortcut-arrow" aria-hidden="true">
          <ArrowRight />
        </el-icon>
      </router-link><router-link to="/fact-search">
        <el-icon aria-hidden="true">
          <Search />
        </el-icon><strong>案情检索</strong><span>查找依据，保留出处</span><el-icon class="shortcut-arrow" aria-hidden="true">
          <ArrowRight />
        </el-icon>
      </router-link>
    </nav>
    <CaseReminders /><CaseAssociation
      v-if="userStore.token"
      v-model="caseId"
      :disabled="creating"
    />
    <ChatSender ref="senderRef" v-model="senderValue" :loading="creating" @submit="handleSubmit" />
  </div>
</template>

<style scoped lang="scss">
.chat-defaul-wrap {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  box-sizing: border-box;
  max-width: 1000px;
  padding: clamp(24px, 6vh, 64px) var(--yl-page-padding) 24px;
  min-height: 0;
}
.chat-shortcuts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
  margin-bottom: 20px;
}
.chat-shortcuts a {
  position: relative;
  display: grid;
  grid-template-columns: 20px 1fr;
  align-items: center;
  gap: 8px 10px;
  padding: 16px;
  background: var(--yl-color-surface);
  border: 1px solid var(--yl-color-border);
  border-radius: var(--yl-radius-panel);
  text-decoration: none;
  color: var(--yl-color-text);
}
.chat-shortcuts a:hover {
  border-color: var(--yl-color-brand);
  background: var(--el-color-primary-light-9);
}
.chat-shortcuts strong {
  font-size: 14px;
  font-weight: 500;
}
.chat-shortcuts span {
  grid-column: 1/-1;
  font-size: 12px;
  color: var(--yl-color-text-muted);
}
.chat-shortcuts .shortcut-arrow {
  position: absolute;
  right: 12px;
  top: 18px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
@media (max-width: 600px) {
  .chat-shortcuts {
    grid-template-columns: 1fr;
  }
  .chat-shortcuts a {
    grid-template-columns: 20px 90px 1fr;
    padding: 12px;
  }
  .chat-shortcuts span {
    grid-column: auto;
  }
  .chat-shortcuts .shortcut-arrow {
    display: none;
  }
}
</style>
