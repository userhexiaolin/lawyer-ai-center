<!-- Aside 渚ц竟鏍? -->
<script setup lang="ts">
import type { ConversationItem } from 'vue-element-plus-x/types/Conversations';
import type { ChatSessionVo } from '@/api/session/types';
import { useRoute, useRouter } from 'vue-router';
import { get_session } from '@/api';
import logo from '@/assets/images/logo.png';
import Collapse from '@/layouts/components/Header/components/Collapse.vue';
import { useDesignStore, useUserStore } from '@/stores';
import { useSessionStore } from '@/stores/modules/session';
import { messageTitle } from '@/utils/messagePresentation';

import { moreNavigation, navigationActive, primaryNavigation } from './navigation';

const userStore = useUserStore();
const navigationIcons: Record<string, string> = {
  chat: 'ChatDotRound',
  contractReview: 'DocumentChecked',
  drafting: 'EditPen',
  templateManagement: 'Files',
  myCases: 'FolderOpened',
  factSearch: 'Search',
  notifications: 'Bell',
  appMarket: 'Grid',
  mediaWorkbench: 'Picture',
  help: 'QuestionFilled',
};

const route = useRoute();
const router = useRouter();
const designStore = useDesignStore();
function closeNavigation() {
  designStore.setCollapse(true);
  designStore.isSafeAreaHover = false;
  document.documentElement.style.setProperty('--sidebar-left-container-default-width', '0px');
}

const sessionStore = useSessionStore();

// Report IDs share the :id parameter name but are never chat-session identifiers.
const sessionId = computed(() =>
  route.name === 'chatWithId' && typeof route.params.id === 'string' ? route.params.id : undefined,
);
const conversationsList = computed(() => sessionStore.sessionList.map(item => ({ ...item, displayTitle: messageTitle(item.sessionTitle || '未命名会话') })));
const loadMoreLoading = computed(() => sessionStore.isLoadingMore);
const active = ref<string | undefined>();

const searchKeyword = computed({
  get: () => sessionStore.searchKeyword,
  set: (val) => {
    handleSearch(val);
  },
});

// 鎼滅储闃叉姈瀹氭椂鍣?
let searchTimer: ReturnType<typeof setTimeout> | null = null;

// 鎼滅储澶勭悊鍑芥暟锛堝甫闃叉姈锛?
function handleSearch(keyword: string) {
  if (!userStore.token)
    return;
  if (searchTimer) {
    clearTimeout(searchTimer);
  }
  searchTimer = setTimeout(() => {
    if (keyword.trim()) {
      sessionStore.searchSessions(keyword.trim());
    }
    else {
      sessionStore.clearSearch();
    }
  }, 300);
}

// 娓呴櫎鎼滅储
function handleClearSearch() {
  sessionStore.clearSearch();
}

onMounted(async () => {
  if (!userStore.token)
    return;
  // 鑾峰彇浼氳瘽鍒楄〃
  await sessionStore.requestSessionList();

  // 楂樹寒鏈€鏂颁細璇?
  if (conversationsList.value.length > 0 && sessionId.value) {
    const currentSessionRes = await get_session(`${sessionId.value}`);
    // 閫氳繃 ID 鏌ヨ璇︽儏锛岃缃綋鍓嶄細璇?(鍥犱负鏈夊垎椤?
    sessionStore.setCurrentSession(currentSessionRes.data);
  }
});

watch(
  () => sessionStore.currentSession,
  (newValue) => {
    active.value = newValue ? `${newValue.id}` : undefined;
  },
);

// 鍒涘缓浼氳瘽
function handleCreatChat() {
  // 鍒涘缓浼氳瘽, 璺宠浆鍒伴粯璁よ亰澶?
  sessionStore.createSessionBtn();
}

function handleChange(item: ConversationItem<ChatSessionVo>) {
  sessionStore.setCurrentSession(item);
  router.replace({
    name: 'chatWithId',
    params: {
      id: item.id,
    },
  });
}

// 澶勭悊缁勪欢瑙﹀彂鐨勫姞杞芥洿澶氫簨浠?
async function handleLoadMore() {
  if (!sessionStore.hasMore)
    return; // 鏃犳洿澶氭暟鎹椂涓嶅姞杞?
  await sessionStore.loadMoreSessions();
}

// 鍙抽敭鑿滃崟
function handleMenuCommand(command: string, item: ConversationItem<ChatSessionVo>) {
  switch (command) {
    case 'delete':
      ElMessageBox.confirm('删除后，聊天记录将不可恢复。', '确定删除对话？', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
        cancelButtonClass: 'el-button--info',
        roundButton: true,
        autofocus: false,
      })
        .then(async () => {
          // 鍒犻櫎浼氳瘽
          await sessionStore.deleteSessions([item.id!]);

          // 妫€鏌ュ垹闄ょ殑鏄惁涓哄綋鍓嶉€変腑浼氳瘽锛岃嫢鏄垯杩斿洖榛樿椤?
          nextTick(() => {
            if (item.id === active.value) {
              // 濡傛灉鍒犻櫎褰撳墠浼氳瘽锛岃繑鍥炲埌榛樿椤甸潰
              sessionStore.createSessionBtn();
            }
          });
        })
        .catch(() => {
          // 鍙栨秷鍒犻櫎
        });
      break;
    case 'rename':
      ElMessageBox.prompt('', '编辑对话名称', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputErrorMessage: '请输入对话名称',
        confirmButtonClass: 'el-button--primary',
        cancelButtonClass: 'el-button--info',
        roundButton: true,
        inputValue: messageTitle(item.sessionTitle || ''), // 璁剧疆榛樿鍊?
        autofocus: false,
        inputValidator: (value) => {
          if (!value) {
            return false;
          }
          return true;
        },
      }).then(({ value }) => {
        sessionStore
          .updateSession({
            id: item.id!,
            sessionTitle: value,
            sessionContent: item.sessionContent,
          })
          .then(() => {
            ElMessage({
              type: 'success',
              message: '修改成功',
            });
            nextTick(() => {
              // 濡傛灉鏄綋鍓嶄細璇濓紝鍒欐洿鏂板綋鍓嶉€変腑浼氳瘽淇℃伅
              if (sessionStore.currentSession?.id === item.id) {
                sessionStore.setCurrentSession({
                  ...item,
                  sessionTitle: value,
                });
              }
            });
          });
      });
      break;
    default:
      break;
  }
}
onScopeDispose(() => {
  if (searchTimer)
    clearTimeout(searchTimer);
});
</script>

<template>
  <button
    v-if="!designStore.isCollapse"
    class="sidebar-backdrop"
    type="button"
    aria-label="关闭导航菜单"
    @click="closeNavigation"
  />
  <div
    id="primary-sidebar"
    class="aside-container"
    :class="{
      'aside-container-suspended': designStore.isSafeAreaHover,
      'aside-container-collapse': designStore.isCollapse,
      // 鎶樺彔涓旀湭婵€娲绘偓鍋滄椂娣诲姞 no-delay 绫?
      'no-delay': designStore.isCollapse && !designStore.hasActivatedHover,
    }"
    @keydown.esc="closeNavigation"
  >
    <div class="aside-wrapper">
      <div v-if="!designStore.isCollapse" class="aside-header">
        <button
          type="button"
          class="sidebar-brand"
          aria-label="与律行，返回新对话"
          @click="handleCreatChat"
        >
          <el-image :src="logo" alt="与律行" fit="contain" class="logo-img" />
          <span class="brand-copy"><strong class="logo-text">与律行</strong><small>专业法律工作台</small></span>
        </button>
        <Collapse class="ml-auto" />
      </div>

      <div class="aside-body">
        <!-- 鎼滅储妗? -->
        <div class="search-wrapper">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索历史对话"
            aria-label="搜索历史对话"
            clearable
            class="search-input"
            @clear="handleClearSearch"
          >
            <template #prefix>
              <el-icon>
                <Search />
              </el-icon>
            </template>
          </el-input>
        </div>

        <!-- 鍒嗗壊绾? -->
        <nav aria-label="办案入口" class="primary-navigation">
          <template v-for="item in primaryNavigation" :key="item.name">
            <button
              v-if="item.name === 'chat'"
              type="button"
              class="workbench-entry nav-button"
              :aria-current="navigationActive(item.name, String(route.name)) ? 'page' : undefined"
              :class="{ active: navigationActive(item.name, String(route.name)) }"
              @click="handleCreatChat"
            >
              <el-icon aria-hidden="true">
                <component :is="navigationIcons[item.name]" />
              </el-icon><span>{{ item.label }}</span><kbd>Ctrl K</kbd>
            </button>
            <router-link
              v-else
              :to="{ name: item.name }"
              class="workbench-entry"
              :aria-current="navigationActive(item.name, String(route.name)) ? 'page' : undefined"
              :class="{ active: navigationActive(item.name, String(route.name)) }"
            >
              <el-icon aria-hidden="true">
                <component :is="navigationIcons[item.name]" />
              </el-icon><span>{{ item.label }}</span>
            </router-link>
          </template>
        </nav>
        <details class="more-navigation">
          <summary>更多</summary>
          <router-link
            v-for="item in moreNavigation"
            :key="item.name"
            :to="{ name: item.name }"
            class="workbench-entry"
            :aria-current="route.name === item.name ? 'page' : undefined"
            :class="{ active: route.name === item.name }"
          >
            <el-icon aria-hidden="true">
              <component :is="navigationIcons[item.name]" />
            </el-icon><span>{{ item.label }}</span>
          </router-link>
        </details>
        <div class="divider" />
        <h2 class="history-heading">
          最近对话
        </h2>
        <div class="aside-content">
          <div v-if="conversationsList.length > 0" class="conversations-wrap overflow-hidden">
            <Conversations
              v-model:active="active"
              :items="conversationsList"
              :label-max-width="200"
              :show-tooltip="true"
              :tooltip-offset="60"
              show-built-in-menu
              groupable
              row-key="id"
              label-key="displayTitle"
              tooltip-placement="right"
              :load-more="handleLoadMore"
              :load-more-loading="loadMoreLoading"
              :items-style="{
                marginLeft: '8px',
                userSelect: 'none',
                borderRadius: '10px',
                padding: '8px 12px',
              }"
              :items-active-style="{
                backgroundColor: 'var(--yl-color-surface)',
                boxShadow: '0 1px 2px rgba(0, 0, 0, 0.05)',
                color: 'var(--yl-color-text)',
              }"
              :items-hover-style="{
                backgroundColor: 'var(--el-fill-color-light)',
              }"
              @menu-command="handleMenuCommand"
              @change="handleChange"
            />
          </div>

          <div v-else class="history-empty">
            <el-icon aria-hidden="true">
              <ChatLineSquare />
            </el-icon>
            <p>
              {{
                userStore.token
                  ? sessionStore.searchKeyword
                    ? '未找到匹配的对话'
                    : '暂无历史对话'
                  : '登录后查看历史对话'
              }}
            </p>
            <el-button v-if="sessionStore.searchKeyword" text @click="handleClearSearch">
              清除搜索
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.sidebar-backdrop {
  display: none;
}
.aside-container {
  position: absolute;
  inset: 0 auto 0 0;
  z-index: 30;
  box-sizing: border-box;
  width: var(--sidebar-default-width);
  height: 100%;
  background: var(--sidebar-background-color);
  border-right: 1px solid var(--yl-color-border);
}
.aside-wrapper {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.aside-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 76px;
  padding: 0 16px;
}
.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 0;
  text-align: left;
  color: var(--yl-color-text);
  background: transparent;
  border: 0;
  cursor: pointer;
}
.logo-img {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  border-radius: 8px;
}
.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.logo-text {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
.brand-copy small {
  font-size: 10px;
  letter-spacing: 0.05em;
  color: var(--el-text-color-secondary);
}
.aside-body {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  padding-bottom: 12px;
}
.search-wrapper {
  padding: 8px 16px 16px;
}
.primary-navigation {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0 12px;
}
.workbench-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 40px;
  padding: 0 12px;
  font: inherit;
  font-size: 14px;
  font-weight: 400;
  color: var(--yl-color-text-muted);
  text-decoration: none;
  border: 0;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  box-sizing: border-box;
}
.workbench-entry .el-icon {
  font-size: 18px;
  flex-shrink: 0;
}
.workbench-entry:hover {
  background: var(--el-fill-color-light);
  color: var(--yl-color-text);
}
.workbench-entry.active {
  background: var(--el-color-primary-light-9);
  color: var(--yl-color-brand-strong);
  font-weight: 600;
}
.nav-button {
  width: 100%;
  text-align: left;
}
.nav-button kbd {
  margin-left: auto;
  font-family: inherit;
  font-size: 10px;
  letter-spacing: 0;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}
.divider {
  height: 1px;
  flex-shrink: 0;
  background: var(--yl-color-border);
  margin: 20px 16px 16px;
}
.history-heading {
  margin: 0 24px 10px;
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}
.aside-content {
  display: flex;
  flex: 1;
  min-height: 60px;
  overflow: auto;
  flex-direction: column;
  padding: 0 8px;
}
.conversations-wrap {
  min-height: 0;
  flex: 1;
}
.history-empty {
  padding: 20px 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
  font-size: 12px;
}
.history-empty > .el-icon {
  font-size: 20px;
  margin-bottom: 4px;
}
.history-empty p {
  margin: 8px 0;
}
.more-navigation {
  order: 2;
  flex-shrink: 0;
  padding: 8px 12px 0;
  border-top: 1px solid var(--yl-color-border);
  margin: 12px 0 0;
}
.more-navigation summary {
  cursor: pointer;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.more-navigation .workbench-entry {
  min-height: 34px;
}
.aside-container-collapse {
  top: var(--header-container-default-heigth);
  height: calc(100% - var(--header-container-default-heigth) - 16px);
  max-height: 760px;
  pointer-events: none;
  opacity: 0;
  transform: translateX(-100%);
  border: 1px solid var(--yl-color-border);
  border-radius: var(--yl-radius-panel);
  box-shadow: var(--yl-shadow-overlay);
  transition:
    opacity 0.15s,
    transform 0.15s;
}
.aside-container-collapse:hover,
.aside-container-collapse.aside-container-suspended {
  pointer-events: auto;
  opacity: 1;
  transform: translateX(8px);
}
@media (max-width: 767px) {
  .sidebar-backdrop {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 29;
    border: 0;
    background: var(--el-overlay-color-lighter);
  }
  .aside-container {
    max-width: calc(100vw - 56px);
  }
  .aside-container-collapse {
    visibility: hidden;
  }
}
@media (prefers-reduced-motion: reduce) {
  .aside-container-collapse {
    transition: none;
  }
}
</style>
