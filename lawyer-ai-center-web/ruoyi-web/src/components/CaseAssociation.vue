<script setup lang="ts">
import { useId } from 'vue';
import { useDraftActivity } from '@/hooks/useDraftWorkspace';
import { useCaseAssociation } from './useCaseAssociation';

const props = withDefaults(
  defineProps<{ modelValue?: string; sessionId?: string; disabled?: boolean }>(),
  { modelValue: '', sessionId: '', disabled: false },
);
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
const labelId = useId();
const { selected, linked, rows, more, busy, loading, restoring, saving, error, associationFailure, retryLabel, newTitle, creating, load, apply, create, retry } = useCaseAssociation(props, value => emit('update:modelValue', value));
useDraftActivity(() => associationFailure.value === 'save' || !!newTitle.value.trim(), () => saving.value);
</script>

<template>
  <section class="association">
    <span :id="labelId">所属案件</span><el-select
      v-model="selected"
      :aria-labelledby="labelId"
      aria-label="所属案件"
      clearable
      filterable
      placeholder="可选：关联案件"
      :disabled="busy || disabled || associationFailure === 'restore'"
      :loading="loading || restoring"
      @change="apply"
    >
      <el-option v-for="r in rows" :key="r.id" :label="r.title" :value="r.id" />
      <el-option v-if="selected && !rows.some(row => row.id === selected)" :value="selected" label="当前关联案件（详情暂不可用）" disabled />
    </el-select><el-button v-if="more" text :loading="busy" @click="load(true)">
      更多案件
    </el-button><el-button text :disabled="busy || disabled || associationFailure === 'restore'" @click="creating = !creating">
      新建并关联
    </el-button><router-link v-if="linked" :to="{ path: '/my-cases', query: { id: linked } }">
      查看案件 ↗
    </router-link>
    <div v-if="creating" class="create">
      <el-input
        v-model="newTitle"
        maxlength="200"
        placeholder="案件标题（必填）"
        aria-label="新建案件标题"
        :disabled="busy || disabled"
        @keyup.enter="create"
      /><el-button :disabled="!newTitle.trim() || disabled" :loading="busy" @click="create">
        创建并关联
      </el-button>
    </div>
    <p v-if="busy" role="status" aria-live="polite" class="association-status">
      {{ saving ? '正在保存案件关联…' : restoring ? '正在恢复案件关联…' : '正在加载案件…' }}
    </p>
    <p v-if="error" class="error" role="alert">
      {{ error }} <el-button text :disabled="busy || disabled" @click="retry">
        {{ retryLabel }}
      </el-button>
    </p>
  </section>
</template>

<style scoped>
.association {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 12px 0;
  font-size: 14px;
}
.association .el-select {
  width: 220px;
}
.create {
  display: flex;
  gap: 8px;
  width: 100%;
}
.error {
  width: 100%;
  color: var(--yl-color-danger);
}
.association-status {
  width: 100%;
  margin: 0;
  color: var(--yl-color-text-muted);
}
</style>
