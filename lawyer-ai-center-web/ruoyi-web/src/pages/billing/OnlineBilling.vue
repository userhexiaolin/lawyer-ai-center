<script setup lang="ts">
import type { BillingAccount, BillingOrder, BillingPlan } from '@/api/lawyer/billing';
import QRCode from 'qrcode';
import { onMounted, onScopeDispose, ref, watch } from 'vue';
import {
  billingAccount,
  billingOrder,
  billingOrders,
  billingPlans,
  billingStatus,
  closeBillingOrder,
  createBillingOrder,
} from '@/api/lawyer/billing';
import AppPageHeader from '@/components/AppPageHeader.vue';
import { useUserStore } from '@/stores';

const user = useUserStore();
const plans = ref<BillingPlan[]>([]);
const account = ref<BillingAccount>();
const orders = ref<BillingOrder[]>([]);
const selected = ref<BillingOrder>();
const qr = ref('');
const error = ref('');
const busy = ref(false);
const enabled = ref(false);
const page = ref(1);
let timer: ReturnType<typeof setTimeout> | undefined;
let disposed = false;
let generation = 0;
let attempts = 0;
const keys = new Map<string, string>();
function current(token: string | undefined, version: number) {
  return !disposed && user.token === token && generation === version;
}
async function refresh() {
  const token = user.token;
  const version = generation;
  error.value = '';
  try {
    const [catalog, quota, list] = await Promise.all([
      billingPlans(),
      billingAccount(),
      billingOrders(page.value),
    ]);
    if (!current(token, version))
      return;
    plans.value = catalog.data.plans;
    enabled.value = catalog.data.purchasingEnabled;
    account.value = quota.data;
    orders.value = list.data;
  }
  catch (e) {
    if (current(token, version))
      error.value = (e as Error).message;
  }
}
async function display(order: BillingOrder) {
  if (order.status !== 'PENDING')
    keys.clear();
  selected.value = order;
  qr.value = '';
  if (order.status === 'PENDING' && order.payUrl) {
    const value = await QRCode.toDataURL(order.payUrl, { width: 220, margin: 2 });
    if (selected.value?.orderNo === order.orderNo && !disposed)
      qr.value = value;
  }
}
function stop() {
  if (timer)
    clearTimeout(timer);
  timer = undefined;
}
function poll() {
  stop();
  if (!enabled.value || selected.value?.status !== 'PENDING' || attempts++ >= 180 || disposed)
    return;
  timer = setTimeout(() => {
    void check(true);
  }, 5000);
}
async function check(automatic = false) {
  const no = selected.value?.orderNo;
  if (!no || busy.value)
    return;
  const token = user.token;
  const version = generation;
  try {
    const response = await billingOrder(no, true);
    if (!current(token, version))
      return;
    await display(response.data);
    if (response.data.status === 'PAID') {
      stop();
      await refresh();
    }
    else if (response.data.status === 'PENDING') {
      poll();
    }
    else {
      stop();
      await refresh();
    }
  }
  catch (e) {
    if (current(token, version)) {
      error.value = (e as Error).message;
      if (automatic)
        stop();
    }
  }
}
async function buy(plan: BillingPlan) {
  if (busy.value || !enabled.value)
    return;
  busy.value = true;
  stop();
  error.value = '';
  const token = user.token;
  const version = generation;
  try {
    let key = keys.get(plan.code);
    if (!key) {
      key = crypto.randomUUID();
      keys.set(plan.code, key);
    }
    const response = await createBillingOrder(plan.code, key);
    if (!current(token, version))
      return;
    await display(response.data);
    attempts = 0;
    await refresh();
  }
  catch (e) {
    if (current(token, version))
      error.value = (e as Error).message;
  }
  finally {
    busy.value = false;
    if (current(token, version))
      poll();
  }
}
async function cancel() {
  if (!selected.value || busy.value)
    return;
  busy.value = true;
  stop();
  const token = user.token;
  const version = generation;
  try {
    const response = await closeBillingOrder(selected.value.orderNo);
    if (!current(token, version))
      return;
    await display(response.data);
    if (response.data.status !== 'PENDING')
      keys.clear();
    await refresh();
  }
  catch (e) {
    if (current(token, version))
      error.value = (e as Error).message;
  }
  finally {
    busy.value = false;
  }
}
async function open(order: BillingOrder) {
  stop();
  await display(order);
  attempts = 0;
  if (enabled.value)
    await check();
}
async function changePage(delta: number) {
  page.value = Math.max(1, page.value + delta);
  await refresh();
}
watch(
  () => user.token,
  () => {
    generation++;
    stop();
    plans.value = [];
    orders.value = [];
    selected.value = undefined;
    account.value = undefined;
    qr.value = '';
    keys.clear();
  },
  { flush: 'sync' },
);
onMounted(refresh);
onScopeDispose(() => {
  disposed = true;
  generation++;
  stop();
});
</script>

<template>
  <main class="billing yl-page yl-page--reading">
    <AppPageHeader
      title="额度与会员"
      eyebrow="个人账号"
      description="查看权益、套餐与已有订单；购买能力以当前服务配置为准。"
    />
    <el-alert
      v-if="!enabled"
      title="购买功能暂未开放，可查看已有权益与订单。"
      type="info"
      :closable="false"
    />
    <el-alert v-if="error" :title="error" type="warning" :closable="false" /><el-button
      text
      @click="refresh"
    >
      刷新权益与订单
    </el-button>
    <p v-if="account">
      剩余咨询 {{ account.remainingChat }} 次 · 审查 {{ account.remainingReview }} 次 ·
      {{ account.memberActive ? `会员有效至 ${account.memberUntil}` : '未开通有效会员' }}
    </p>
    <div class="plans">
      <section v-for="plan in plans" :key="plan.code">
        <h2>{{ plan.name }}</h2>
        <strong>¥{{ Number(plan.price).toFixed(2) }}</strong>
        <p>咨询 {{ plan.chatCredits }} 次 · 审查 {{ plan.reviewCredits }} 次</p>
        <p>
          次数有效 {{ plan.validDays }} 天<span v-if="plan.memberDays">
            · 会员 {{ plan.memberDays }} 天</span>
        </p>
        <el-button type="primary" :disabled="!enabled" :loading="busy" @click="buy(plan)">
          购买
        </el-button>
      </section>
    </div>
    <el-empty v-if="!plans.length" description="暂无可购买套餐" :image-size="60" />
    <section v-if="selected" class="checkout" aria-live="polite">
      <h2>{{ selected.planName }} · {{ billingStatus[selected.status] }}</h2>
      <p>订单 {{ selected.orderNo }} · ¥{{ Number(selected.amount).toFixed(2) }}</p>
      <template v-if="selected.status === 'PENDING'">
        <img v-if="qr" :src="qr" alt="支付宝付款二维码">
        <p>使用支付宝扫码。支付后以服务器核实结果为准；离开页面不会取消订单。</p>
        <p>支付期限：{{ selected.expiresAt }}</p>
        <el-button :disabled="busy || !enabled" @click="check()">
          我已支付，查询结果
        </el-button><el-button :loading="busy" :disabled="!enabled" @click="cancel">
          取消未付订单
        </el-button>
      </template>
      <p v-else-if="selected.status === 'PAID'">
        支付成功，权益已刷新。
      </p>
    </section>
    <h2>我的订单</h2>
    <el-table :data="orders">
      <el-table-column prop="planName" label="套餐" /><el-table-column label="金额" align="right">
        <template #default="{ row }">
          ¥{{ Number(row.amount).toFixed(2) }}
        </template>
      </el-table-column><el-table-column label="状态">
        <template #default="{ row }">
          {{ billingStatus[row.status as BillingOrder['status']] }}
        </template>
      </el-table-column><el-table-column prop="createdAt" label="创建时间" /><el-table-column label="操作">
        <template #default="{ row }">
          <el-button text @click="open(row)">
            查看
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <p>
      <el-button :disabled="page === 1" @click="changePage(-1)">
        上一页
      </el-button> 第{{ page }}页
      <el-button :disabled="orders.length < 20" @click="changePage(1)">
        下一页
      </el-button>
    </p>
  </main>
</template>

<style scoped>
.billing {
  min-width: 0;
}
.billing > p {
  line-height: 1.8;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.plans {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
  margin: 20px 0;
}
.plans section,
.checkout {
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  padding: 20px;
}
.plans strong {
  font-size: 26px;
}
.plans h2,
.checkout h2 {
  font-size: 17px;
}
.checkout img {
  width: 220px;
  height: 220px;
}
.checkout {
  margin: 20px 0;
  overflow-wrap: anywhere;
}
.checkout p {
  font-size: 13px;
  line-height: 1.8;
}
</style>
