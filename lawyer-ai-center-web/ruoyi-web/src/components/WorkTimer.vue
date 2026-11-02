<script setup lang="ts">
import { computed, onMounted, onScopeDispose, ref, watch } from 'vue';
import { lawyerRequest } from '@/api/lawyer';
import { useUserStore } from '@/stores';

const props = defineProps<{ caseId?: string; sessionId?: string }>();
const emit = defineEmits<{ statusChange: [status: string] }>();
interface Entry {
  id: string;
  caseId?: string;
  sessionId?: string;
  startTime: string;
  endTime?: string;
  durationSeconds: number;
  description?: string;
}
interface Summary {
  active: Entry | null;
  rows: Entry[];
  total: number;
  page: number;
  daySeconds: number;
  weekSeconds: number;
  totalSeconds: number;
  serverNow: string;
}
const state = ref<Summary>();
const rows = ref<Entry[]>([]);
const page = ref(1);
const busy = ref(false);
const loading = ref(false);
const error = ref('');
const description = ref('');
const now = ref(Date.now());
let offset = 0;
let version = 0;
let disposed = false;
const user = useUserStore();
function duration(seconds: number) {
  const s = Math.max(0, Math.floor(seconds));
  return `${Math.floor(s / 3600)}时${Math.floor((s % 3600) / 60)}分${s % 60}秒`;
}
const elapsed = computed(() =>
  state.value?.active
    ? duration((now.value + offset - Date.parse(state.value.active.startTime)) / 1000)
    : '未在计时',
);
watch([() => !!state.value?.active, error], ([active, message]) => {
  emit('statusChange', message ? '计时状态待恢复' : active ? '计时中' : '');
}, { immediate: true });
async function load(append = false, background = false) {
  if (loading.value)
    return;
  loading.value = true;
  error.value = '';
  const v = version;
  const p = append ? page.value + 1 : 1;
  try {
    const q = new URLSearchParams({ pageNum: String(p) });
    if (props.caseId)
      q.set('caseId', props.caseId);
    const r = await lawyerRequest<{ data: Summary }>(`/lawyer/time?${q}`);
    if (disposed || v !== version)
      return;
    state.value = r.data;
    offset = Date.parse(r.data.serverNow) - Date.now();
    if (background && page.value > 1) {
      rows.value = rows.value.map(old => r.data.rows.find(n => n.id === old.id) || old);
    }
    else {
      const combined = append ? [...rows.value, ...r.data.rows] : r.data.rows;
      rows.value = [...new Map(combined.map(r => [r.id, r])).values()];
      page.value = p;
    }
  }
  catch (e) {
    if (v === version)
      error.value = (e as Error).message;
  }
  finally {
    loading.value = false;
    if (v !== version && !disposed)
      void load();
  }
}
async function action() {
  if (busy.value || loading.value)
    return;
  busy.value = true;
  error.value = '';
  try {
    if (state.value?.active) {
      await lawyerRequest(`/lawyer/time/${state.value.active.id}/stop`, 'POST', {});
    }
    else {
      await lawyerRequest('/lawyer/time/start', 'POST', {
        caseId: props.caseId || undefined,
        sessionId: props.sessionId || undefined,
        description: description.value,
      });
    }
    await load();
  }
  catch (e) {
    error.value = `${(e as Error).message}；请刷新状态后重试，服务端会保留计时。`;
  }
  finally {
    busy.value = false;
  }
}
function date(s: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(s));
}
watch(
  () => [props.caseId, props.sessionId, user.token],
  () => {
    version++;
    state.value = undefined;
    rows.value = [];
    page.value = 1;
    description.value = '';
    void load();
  },
);
let tick: ReturnType<typeof setInterval>, poll: ReturnType<typeof setInterval>;
onMounted(() => {
  void load();
  tick = setInterval(() => {
    now.value = Date.now();
  }, 1000);
  poll = setInterval(() => {
    if (document.visibilityState === 'visible')
      void load(false, true);
  }, 30000);
});
onScopeDispose(() => {
  disposed = true;
  version++;
  clearInterval(tick);
  clearInterval(poll);
});
</script>

<template>
  <section class="timer">
    <div class="top">
      <b>工作计时 · {{ elapsed }}</b><el-button :loading="loading" :disabled="busy" text @click="load()">
        恢复 / 刷新
      </el-button>
    </div>
    <p v-if="state?.active">
      正在记录：{{ state.active.description || '未填写备注' }}
      <router-link
        v-if="state.active.caseId"
        :to="{ path: '/my-cases', query: { id: state.active.caseId } }"
      >
        所属案件 ↗
      </router-link><router-link v-if="state.active.sessionId" :to="`/chat/${state.active.sessionId}`">
        所属会话 ↗
      </router-link>
    </p>
    <el-input
      v-if="!state?.active"
      v-model="description"
      maxlength="500"
      placeholder="工作内容备注（选填）"
    /><el-button type="primary" :loading="busy" :disabled="loading || !state" @click="action">
      {{ state?.active ? '停止计时' : '开始计时' }}
    </el-button>
    <p v-if="error" class="error">
      {{ error }}
    </p>
    <p v-if="state">
      今日 {{ duration(state.daySeconds) }} · 本周 {{ duration(state.weekSeconds) }} ·
      {{ caseId ? '本案件' : '累计' }} {{ duration(state.totalSeconds) }}
    </p>
    <details>
      <summary>计时历史（{{ state?.total || 0 }}条）</summary>
      <p v-for="r in rows" :key="r.id">
        {{ date(r.startTime) }} · {{ r.endTime ? duration(r.durationSeconds) : '进行中' }} ·
        {{ r.description || '工作记录' }}
        <router-link v-if="r.caseId" :to="{ path: '/my-cases', query: { id: r.caseId } }">
          案件 ↗
        </router-link>
      </p>
      <p v-if="!rows.length">
        暂无计时，可从当前案件或会话开始。
      </p>
      <el-button v-if="rows.length < (state?.total || 0)" :loading="loading" @click="load(true)">
        加载更多
      </el-button>
    </details>
  </section>
</template>

<style scoped>
.timer {
  width: 100%;
  box-sizing: border-box;
  padding: 16px;
  margin: 12px 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  font-size: 14px;
}
.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.timer .el-input {
  max-width: 350px;
  margin-right: 12px;
}
.timer p {
  margin: 10px 0;
}
.error {
  color: var(--yl-color-danger);
}
.timer a {
  margin-left: 8px;
}
</style>
