<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import logoPng from '@/assets/images/logo.png';
import SvgIcon from '@/components/SvgIcon/index.vue';
import { useUserStore } from '@/stores';
import { useLoginFormStore } from '@/stores/modules/loginForm';
import AccountPassword from './components/FormLogin/AccountPassword.vue';
import RegistrationForm from './components/FormLogin/RegistrationForm.vue';
import QrCodeLogin from './components/QrCodeLogin/index.vue';

const userStore = useUserStore();
const loginFromStore = useLoginFormStore();
const loginFormType = computed(() => loginFromStore.LoginFormType);
const visible = defineModel<boolean>('visible');
const isQrMode = ref(false);
watch(visible, (value) => {
  if (value)
    isQrMode.value = false;
});
</script>

<template>
  <el-dialog
    :model-value="!!visible"
    class="yl-login-dialog"
    width="min(480px, calc(100vw - 32px))"
    align-center
    append-to-body
    :style="{ '--el-dialog-padding': '24px', '--el-dialog-border-radius': '12px' }"
    @close="userStore.closeLoginDialog()"
  >
    <template #header>
      <div class="login-brand">
        <img :src="logoPng" alt="与律行"><strong>与律行</strong>
      </div>
    </template>
    <section class="login-content">
      <div class="login-heading">
        <div>
          <p class="welcome-eyebrow">
            {{ loginFormType === 'RegistrationForm' ? '创建新账号' : '欢迎回来' }}
          </p>
          <h2>
            {{
              isQrMode
                ? '扫码登录'
                : loginFormType === 'RegistrationForm'
                  ? '注册与律行'
                  : '登录与律行'
            }}
          </h2>
        </div>
        <button
          class="mode-toggle"
          type="button"
          :aria-label="isQrMode ? '切换到账号登录' : '切换到扫码登录'"
          :title="isQrMode ? '切换到账号登录' : '切换到扫码登录'"
          @click="isQrMode = !isQrMode"
        >
          <SvgIcon :name="isQrMode ? 'zhanghaodenglu' : 'erweimadenglu'" />
        </button>
      </div>
      <p class="welcome-desc">
        {{
          loginFormType === 'RegistrationForm'
            ? '创建账号，开始管理你的法律工作。'
            : '登录后继续处理案件、文书与历史对话。'
        }}
      </p>
      <div v-if="!isQrMode" class="form-box">
        <slot name="form">
          <AccountPassword v-if="loginFormType === 'AccountPassword'" /><RegistrationForm
            v-if="loginFormType === 'RegistrationForm'"
          />
        </slot>
      </div>
      <div v-else class="qr-container">
        <QrCodeLogin />
      </div>
    </section>
  </el-dialog>
</template>

<style scoped>
.login-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--yl-color-text);
}
.login-brand img {
  width: 28px;
  height: 28px;
  object-fit: contain;
}
.login-brand strong {
  font-size: 17px;
  font-weight: 600;
}
.login-content {
  min-width: 0;
  padding: 8px 0 4px;
}
.login-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.welcome-eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--yl-color-brand-strong);
}
.login-heading h2 {
  margin: 0;
  font-size: 24px;
  line-height: 1.4;
  font-weight: 600;
  color: var(--yl-color-text);
}
.welcome-desc {
  margin: 12px 0 24px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--yl-color-text-muted);
}
.mode-toggle {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border: 1px solid var(--yl-color-border);
  border-radius: var(--yl-radius-panel);
  color: var(--yl-color-brand-strong);
  background: var(--el-color-primary-light-9);
  cursor: pointer;
}
.form-box {
  width: 100%;
  min-width: 0;
}
.qr-container {
  display: flex;
  justify-content: center;
  max-width: 100%;
  overflow: auto;
}
</style>
