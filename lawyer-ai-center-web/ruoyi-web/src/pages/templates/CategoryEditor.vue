<script setup lang="ts">
import type { FormInstance } from 'element-plus';
import type { TemplateCategory } from '@/api/document/templateManagement';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onScopeDispose, ref } from 'vue';
import { saveCategory } from '@/api/document/templateManagement';
import { useUserStore } from '@/stores';
import { useUnsavedEditor } from './useUnsavedEditor';
import { failureMessage, hasTemplatePermission, inputError } from './workflow';

const props = defineProps<{ initial?: TemplateCategory; permissions: string[] }>();
const emit = defineEmits<{ saved: [] }>();
const open = defineModel<boolean>({ required: true });
const user = useUserStore();
const form = ref<TemplateCategory>({
  name: '',
  sortOrder: 0,
  status: '0',
  builtin: false,
  remark: '',
  ...props.initial,
});
const formRef = ref<FormInstance>();
const saving = ref(false);
const confirming = ref(false);
const busy = computed(() => saving.value || confirming.value);
const error = ref('');
const canWrite = computed(() =>
  hasTemplatePermission(props.permissions, 'category', form.value.id ? 'edit' : 'add'),
);
const { close, saved } = useUnsavedEditor(open, busy, () => JSON.stringify(form.value));
let alive = true;
onScopeDispose(() => {
  alive = false;
  if (confirming.value)
    ElMessageBox.close();
});
async function save() {
  if (busy.value || !canWrite.value)
    return;
  const token = user.token;
  if (!(await formRef.value?.validate().catch(() => false)))
    return;
  error.value = inputError(form.value);
  if (error.value || busy.value)
    return;
  if (props.initial && props.initial.status !== form.value.status) {
    confirming.value = true;
    try {
      await ElMessageBox.confirm(
        form.value.status === '1'
          ? '停用后，该分类不再供新模板和新的文书起草选择，已有模板及文书保留。'
          : '启用后，该分类可用于新模板和文书起草。',
        '确认修改分类使用状态？',
        { confirmButtonText: '确认保存', cancelButtonText: '继续编辑', type: 'warning' },
      );
    }
    catch {
      return;
    }
    finally {
      confirming.value = false;
    }
  }
  if (!alive || user.token !== token || busy.value || !canWrite.value)
    return;
  saving.value = true;
  try {
    await saveCategory({ ...form.value, name: form.value.name.trim() });
    if (user.token !== token)
      return;
    saved();
    ElMessage.success('分类已保存');
    emit('saved');
  }
  catch (reason) {
    if (user.token === token)
      error.value = `${failureMessage(reason)} 本次修改尚未保存，输入已保留。`;
  }
  finally {
    saving.value = false;
  }
}
</script>

<template>
  <el-drawer
    :model-value="open"
    :title="form.id ? '编辑分类' : '新增分类'"
    size="min(520px, 100vw)"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    :before-close="close"
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <el-form
      ref="formRef"
      :model="form"
      label-position="top"
      :disabled="saving || !canWrite"
      @submit.prevent="save"
    >
      <el-form-item
        label="分类名称"
        prop="name"
        :rules="[{ required: true, whitespace: true, message: '请输入分类名称', trigger: 'blur' }]"
      >
        <el-input v-model="form.name" :maxlength="50" :disabled="form.builtin" autofocus />
        <p v-if="form.builtin" class="yl-safety-note">
          内置分类关联专用起草流程，名称不可修改。
        </p>
      </el-form-item>
      <el-form-item label="排序号">
        <el-input-number v-model="form.sortOrder" :min="0" :max="999999" :precision="0" />
      </el-form-item>
      <el-form-item label="使用状态">
        <el-radio-group v-model="form.status">
          <el-radio value="0">
            启用
          </el-radio><el-radio value="1">
            停用
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="4"
          :maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="close">
        取消
      </el-button>
      <el-button type="primary" :loading="saving" :disabled="!canWrite" @click="save">
        保存分类
      </el-button>
    </template>
  </el-drawer>
</template>
