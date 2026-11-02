<script setup lang="ts">
import AppPageHeader from '@/components/AppPageHeader.vue';
import KnowledgeBoundary from '@/components/KnowledgeBoundary.vue';
import LawyerUsage from '@/components/LawyerUsage.vue';
import WorkTimer from '@/components/WorkTimer.vue';
import { useUserStore } from '@/stores';

const user = useUserStore();
</script>

<template>
  <main class="profile yl-page yl-page--reading">
    <AppPageHeader
      title="个人中心"
      eyebrow="账号与工作记录"
      description="查看当前账号与工作计时，了解产品的数据和使用边界。"
    />
    <section class="profile-identity yl-panel" aria-label="当前账号">
      <el-avatar :src="user.userInfo?.avatar" :size="56">
        {{ (user.userInfo?.nickName || '我').slice(0, 1) }}
      </el-avatar>
      <div>
        <h2>{{ user.userInfo?.nickName || '我的账号' }}</h2>
        <p>当前登录账号</p>
      </div>
      <router-link to="/my-cases">
        <el-button>进入我的案件</el-button>
      </router-link>
    </section>
    <div class="profile-sections">
      <section class="yl-panel">
        <h2>工作计时</h2>
        <p>按当前任务管理计时，查看已记录的工作时长。</p>
        <WorkTimer />
      </section>
      <section class="yl-panel">
        <h2>常用入口</h2>
        <div class="profile-links">
          <router-link to="/notifications">
            个人通知 <span aria-hidden="true">↗</span>
          </router-link><router-link to="/search-reports">
            我的检索报告 <span aria-hidden="true">↗</span>
          </router-link><router-link to="/help">
            帮助与数据来源 <span aria-hidden="true">↗</span>
          </router-link>
        </div>
        <LawyerUsage />
      </section>
    </div>
    <section class="profile-boundary yl-panel">
      <h2>数据与使用边界</h2>
      <KnowledgeBoundary />
    </section>
  </main>
</template>

<style scoped>
.profile-identity {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 24px;
}
.profile-identity h2 {
  margin: 0;
  font-size: 18px;
}
.profile-identity p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--yl-color-text-muted);
}
.profile-identity > a {
  margin-left: auto;
}
.profile-sections {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.profile-sections h2,
.profile-boundary h2 {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
}
.profile-sections p {
  font-size: 13px;
  color: var(--yl-color-text-muted);
  line-height: 1.7;
}
.profile-links {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.profile-links a {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  text-decoration: none;
  border-bottom: 1px solid var(--yl-color-border);
}
.profile-boundary {
  margin-top: 20px;
}
@media (max-width: 767px) {
  .profile-identity {
    flex-wrap: wrap;
  }
  .profile-identity > a {
    margin-left: 0;
  }
  .profile-sections {
    grid-template-columns: 1fr;
  }
}
</style>
