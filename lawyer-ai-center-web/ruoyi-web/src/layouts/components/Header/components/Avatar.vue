<!-- 头像 -->
<script setup lang="ts">
import { useRouter } from 'vue-router';
import LawyerUsage from '@/components/LawyerUsage.vue';
import Popover from '@/components/Popover/index.vue';
import SvgIcon from '@/components/SvgIcon/index.vue';
import { useUserStore } from '@/stores';
import { useSessionStore } from '@/stores/modules/session';

const userStore = useUserStore();
const router = useRouter();
const sessionStore = useSessionStore();
const src = computed(() => userStore.userInfo?.avatar || '');

/* 弹出面板 开始 */
const popoverStyle = ref({
  width: '200px',
  padding: '4px',
  height: 'fit-content',
});
const popoverRef = ref();

// 弹出面板内容
const popoverList = ref([
  {
    key: '1',
    title: '收藏夹',
    icon: 'book-mark-fill',
  },
  {
    key: '2',
    title: '个人中心',
    icon: 'settings-4-fill',
  },
  {
    key: '3',
    divider: true,
  },
  {
    key: '4',
    title: '退出登录',
    icon: 'logout-box-r-line',
  },
]);

// 点击
function handleClick(item: any) {
  switch (item.key) {
    case '1':
      ElMessage.warning('暂未开放');
      console.log('点击了收藏夹');
      break;
    case '2':
      router.push('/profile');
      break;
    case '4':
      popoverRef.value?.hide?.();
      ElMessageBox.confirm('退出登录不会丢失任何数据，你仍可以登录此账号。', '确认退出登录？', {
        confirmButtonText: '确认退出',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
        cancelButtonClass: 'el-button--info',
        roundButton: true,
        autofocus: false,
      })
        .then(async () => {
          // 在这里执行退出方法
          await userStore.logout();
          // 清空回话列表并回到默认页
          await sessionStore.requestSessionList(1, true);
          await sessionStore.createSessionBtn();
          ElMessage({
            type: 'success',
            message: '退出成功',
          });
        })
        .catch(() => {
          // ElMessage({
          //   type: 'info',
          //   message: '取消',
          // });
        });
      break;
    default:
      break;
  }
}

/* 弹出面板 结束 */
</script>

<template>
  <div class="avatar-container">
    <LawyerUsage v-if="userStore.token" />
    <Popover
      ref="popoverRef"
      placement="bottom-end"
      trigger="clickTarget"
      :trigger-style="{ cursor: 'pointer' }"
      popover-class="popover-content"
      :popover-style="popoverStyle"
    >
      <!-- 触发元素插槽 -->
      <template #trigger>
        <button type="button" class="avatar-trigger" aria-label="打开账号菜单">
          <el-avatar :src="src" :size="30" shape="circle">
            {{ (userStore.userInfo?.nickName || '我').slice(0, 1) }}
          </el-avatar>
        </button>
      </template>

      <div class="popover-content-box shadow-lg">
        <div v-for="item in popoverList" :key="item.key" class="popover-content-box-items h-full">
          <button
            v-if="!item.divider"
            type="button"
            class="popover-content-box-item flex items-center h-full gap-8px p-8px pl-10px pr-12px rounded-lg hover:cursor-pointer hover:bg-[rgba(0,0,0,.04)]"
            @click="handleClick(item)"
          >
            <SvgIcon :name="item.icon!" size="16" class-name="flex-none" />
            <div class="popover-content-box-item-text font-size-14px text-overflow max-h-120px">
              {{ item.title }}
            </div>
          </button>

          <div v-if="item.divider" class="divder h-1px bg-gray-200 my-4px" />
        </div>
      </div>
    </Popover>
  </div>
</template>

<style scoped lang="scss">
.popover-content {
  width: 520px;
  height: 520px;
}
.popover-content-box {
  padding: 8px;
  background: var(--yl-color-surface);
  border: 1px solid var(--yl-color-border);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgb(0 0 0 / 8%);
}
.avatar-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
}
.popover-content-box-item {
  width: 100%;
  font: inherit;
  text-align: left;
  border: 0;
  background: transparent;
  color: var(--yl-color-text);
}
</style>
