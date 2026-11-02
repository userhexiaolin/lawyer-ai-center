<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores';
import { useCaseTasks } from '@/pages/cases/useCaseTasks';
import type { CaseTask } from '@/api/lawyer/tasks';
const props = defineProps<{ caseId: string; disabled?: boolean }>();
const emit = defineEmits<{ busy: [value: boolean] }>(), user = useUserStore();
const { tasks, totals, loading, busy, error, load, create, status, addCheck, toggle, time } = useCaseTasks(
  () => props.caseId, () => user.token, () => !!props.disabled, (text, failed) => failed ? ElMessage.error(text) : ElMessage.success(text), value => emit('busy', value),
);
const title = ref(''), dueAt = ref(''), checks = ref<Record<string, string>>({}), timeTask = ref('');
const entry = reactive({ minutes: 30, note: '', requestId: '' });
const statuses: Record<CaseTask['status'], string> = { todo: '待办', doing: '进行中', done: '已完成' };
function openTime(id: string) { timeTask.value = id; Object.assign(entry, { minutes: 30, note: '', requestId: crypto.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}` }); }
watch(() => [props.caseId, user.token], () => { title.value = ''; dueAt.value = ''; checks.value = {}; timeTask.value = ''; });
</script>
<template>
  <section class="case-tasks" v-loading="loading">
    <div class="task-summary"><strong>任务 {{ totals.done }} / {{ totals.total }} 已完成</strong><span>检查项 {{ totals.checked }} / {{ totals.checks }}</span><strong>任务登记工时 {{ totals.minutes }} 分钟</strong><el-button link :disabled="busy || disabled" @click="load">刷新</el-button></div>
    <p class="hint">这里累计手动登记的任务工时，上方「工作计时」单独统计。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form label-position="top" :disabled="busy || disabled || loading" @submit.prevent>
      <el-form-item label="任务标题"><el-input v-model="title" maxlength="200" show-word-limit placeholder="例如：准备证据目录" /></el-form-item>
      <el-form-item label="截止时间（可选）"><el-date-picker v-model="dueAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择截止时间" clearable /></el-form-item>
      <el-button type="primary" :disabled="busy || disabled || loading || totals.total >= 200" @click="create(title, dueAt, () => { title = ''; dueAt = ''; })">新增任务</el-button>
    </el-form>
    <el-empty v-if="!tasks.length && !loading" description="暂无任务，添加任务后维护检查项" :image-size="60" />
    <article v-for="task in tasks" :key="task.id" class="task-card">
      <h3>{{ task.title }}</h3>
      <div class="task-actions"><el-select :model-value="task.status" :disabled="busy || disabled" :aria-label="`${task.title}状态`" @update:model-value="value => status(task.id, value)"><el-option v-for="(label, value) in statuses" :key="value" :value="value" :label="label" /></el-select><el-button :disabled="busy || disabled" @click="openTime(task.id)">登记工时</el-button><span>{{ task.workMinutes }} 分钟</span></div>
      <p v-if="task.dueAt" class="hint">截止：{{ task.dueAt.replace('T', ' ') }}</p>
      <ul class="task-checks"><li v-for="item in task.checklist" :key="item.id"><el-checkbox :model-value="item.checked" :disabled="busy || disabled" @update:model-value="toggle(task.id, item.id, !!$event)"><span :class="{ checked: item.checked }">{{ item.content }}</span></el-checkbox><small v-if="item.checkedAt">{{ item.checkedAt.replace('T', ' ') }}</small></li></ul>
      <div class="task-check-add"><el-input v-model="checks[task.id]" :disabled="busy || disabled" maxlength="1000" placeholder="输入检查项" :aria-label="`${task.title}新检查项`" @keyup.enter="addCheck(task.id, checks[task.id] || '', () => checks[task.id] = '')" /><el-button :disabled="busy || disabled || task.checklist.length >= 100" @click="addCheck(task.id, checks[task.id] || '', () => checks[task.id] = '')">添加检查项</el-button></div>
    </article>
    <el-dialog :model-value="!!timeTask" title="登记任务工时" width="min(480px, 94vw)" :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy" append-to-body @close="timeTask = ''">
      <el-form label-position="top" :disabled="busy"><el-form-item label="分钟数（1～1440）" required><el-input-number v-model="entry.minutes" :min="1" :max="1440" :precision="0" /></el-form-item><el-form-item label="工作备注"><el-input v-model="entry.note" maxlength="1000" type="textarea" :rows="3" /></el-form-item></el-form>
      <template #footer><el-button :disabled="busy" @click="timeTask = ''">取消</el-button><el-button type="primary" :loading="busy" :disabled="busy" @click="time(timeTask, entry.minutes, entry.note, entry.requestId, () => timeTask = '')">保存工时</el-button></template>
    </el-dialog>
  </section>
</template>
<style scoped>
.task-summary,.task-actions,.task-check-add{display:flex;gap:10px;align-items:center;flex-wrap:wrap}.task-summary{line-height:1.8}.hint{font-size:12px;color:var(--el-text-color-secondary);line-height:1.6}.case-tasks>.el-form{padding:14px 0}.task-card{padding:16px 0;border-top:1px solid var(--el-border-color-lighter)}.task-card h3{margin:0 0 12px;font-size:16px;overflow-wrap:anywhere}.task-actions .el-select{width:115px}.task-checks{padding:0;list-style:none;margin:12px 0}.task-checks li{margin:8px 0}.task-checks .el-checkbox{height:auto;max-width:100%;align-items:flex-start}.task-checks :deep(.el-checkbox__label){white-space:normal;overflow-wrap:anywhere;line-height:1.6}.task-checks :deep(.el-checkbox__input){margin-top:4px}.task-checks small{display:block;color:var(--el-text-color-secondary);margin-left:24px}.checked{text-decoration:line-through}.task-check-add{flex-wrap:nowrap}.task-check-add .el-input{min-width:0}
</style>
