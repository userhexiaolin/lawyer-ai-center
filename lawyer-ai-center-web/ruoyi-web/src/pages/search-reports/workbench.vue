<script setup lang="ts">
import { useEventListener } from '@vueuse/core';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ref, watch } from 'vue';
import { onBeforeRouteLeave, useRoute } from 'vue-router';
import AppPageHeader from '@/components/AppPageHeader.vue';
import KnowledgeBoundary from '@/components/KnowledgeBoundary.vue';
import { useUserStore } from '@/stores';
import { itemTypeLabels, lawStatusLabel } from './helpers';
import { useWorkbench } from './useWorkbench';
import './reports.css';

const route = useRoute();
const user = useUserStore();
const {
  report,
  items,
  busy,
  error,
  edit,
  dirty,
  load,
  save,
  move,
  remove,
  add,
  generate,
  download,
} = useWorkbench(
  String(route.params.id),
  () => user.token,
  (text, failed) => (failed ? ElMessage.error(text) : ElMessage.success(text)),
);
const adding = ref(false);
const itemTitle = ref('');
const itemContent = ref('');
watch(
  () => user.token,
  () => {
    adding.value = false;
    itemTitle.value = '';
    itemContent.value = '';
  },
  { flush: 'sync' },
);
function custom() {
  itemTitle.value = '';
  itemContent.value = '';
  adding.value = true;
}
async function confirmRemove(id: string) {
  if (busy.value)
    return;
  const token = user.token;
  try {
    await ElMessageBox.confirm('从报告中删除此条目？', '删除条目', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  }
  catch {
    return;
  }
  if (token === user.token)
    await remove(id);
}
onBeforeRouteLeave(async () => {
  if (busy.value && busy.value !== 'load') {
    ElMessage.info('操作进行中，请稍候');
    return false;
  }
  if (!dirty.value)
    return true;
  try {
    await ElMessageBox.confirm('背景、结论或标题尚未保存，确定离开？', '未保存的修改', {
      confirmButtonText: '放弃并离开',
      cancelButtonText: '继续编辑',
      type: 'warning',
    });
    return true;
  }
  catch {
    return false;
  }
});
useEventListener(window, 'beforeunload', (event: BeforeUnloadEvent) => {
  if (dirty.value || (busy.value && busy.value !== 'load')) {
    event.preventDefault();
    event.returnValue = '';
  }
});
</script>

<template>
  <section class="report-page yl-page">
    <KnowledgeBoundary />
    <router-link class="report-back" :to="{ name: 'searchReports' }">
      ← 我的检索报告
    </router-link>
    <AppPageHeader
      title="检索报告工作台"
      description="按当前顺序保存条目与文本快照；生成时同步保存标题、背景和结论。"
      eyebrow="法律检索"
    >
      <template #actions>
        <div v-if="report" class="report-actions">
          <el-tag :type="report.status === 'generated' && !dirty ? 'success' : 'info'">
            {{ report.status === 'generated' && !dirty ? '已生成' : '草稿' }}
          </el-tag>
          <el-button :disabled="!!busy" :loading="busy === 'save'" @click="save">
            保存
          </el-button>
          <el-button
            type="primary"
            :disabled="!!busy"
            :loading="busy === 'generate'"
            @click="generate"
          >
            {{ busy === 'generate' ? '生成中…' : '生成并下载 Word' }}
          </el-button>
          <el-button
            v-if="report.status === 'generated'"
            :disabled="!!busy || dirty"
            :loading="busy === 'download'"
            @click="download"
          >
            下载 Word
          </el-button>
        </div>
      </template>
    </AppPageHeader>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-button v-if="error" class="report-retry" :disabled="!!busy || dirty" @click="load">
      重新加载
    </el-button>
    <div v-if="!report" v-loading="busy === 'load'" class="report-panel report-placeholder">
      <el-empty v-if="!busy" description="报告暂不可用，请重新加载或返回列表" />
    </div>
    <template v-else>
      <el-alert
        v-if="dirty"
        title="有未保存的修改；生成报告时会自动保存。"
        type="info"
        :closable="false"
      />
      <el-form label-position="top" :disabled="!!busy" class="report-panel">
        <el-form-item label="报告标题" required>
          <el-input v-model="edit.title" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="检索背景">
          <el-input
            v-model="edit.background"
            type="textarea"
            :rows="4"
            maxlength="50000"
            show-word-limit
            placeholder="说明争议事实、检索目的与范围"
          />
        </el-form-item>
      </el-form>
      <section v-loading="busy === 'order' || busy === 'remove'" class="report-panel">
        <div class="report-section-heading">
          <h2>
            报告条目 <small>{{ items.length }} / 200</small>
          </h2>
          <el-button :disabled="!!busy || items.length >= 200" @click="custom">
            添加自定义文本
          </el-button>
        </div>
        <el-empty v-if="!items.length" description="请先添加条目">
          <p class="report-muted">
            可添加自定义文本，或在对话 / 审查结果的法规、类案引用旁点击「加入检索报告」。
          </p>
          <el-button type="primary" :disabled="!!busy" @click="custom">
            添加自定义文本
          </el-button>
        </el-empty>
        <ol v-else class="report-items">
          <li v-for="(item, index) in items" :key="item.id" class="report-item">
            <div class="report-item-heading">
              <div class="report-item-title">
                <span class="report-item-number">{{ index + 1 }}</span>
                <h3>{{ item.snapshotTitle }}</h3>
              </div>
              <div class="report-actions">
                <el-button
                  size="small"
                  :aria-label="`上移第${index + 1}条`"
                  :disabled="!!busy || index === 0"
                  @click="move(index, -1)"
                >
                  上移
                </el-button>
                <el-button
                  size="small"
                  :aria-label="`下移第${index + 1}条`"
                  :disabled="!!busy || index === items.length - 1"
                  @click="move(index, 1)"
                >
                  下移
                </el-button>
                <el-button
                  size="small"
                  type="danger"
                  plain
                  :disabled="!!busy"
                  @click="confirmRemove(item.id)"
                >
                  删除
                </el-button>
              </div>
            </div>
            <div class="report-item-tags">
              <el-tag size="small" type="info">
                {{ itemTypeLabels[item.itemType] || item.itemType }}
              </el-tag>
              <el-tag
                v-if="item.itemType === 'law'"
                size="small"
                :type="
                  item.lawStatus === 'repealed'
                    ? 'danger'
                    : item.lawStatus === 'effective'
                      ? 'success'
                      : 'warning'
                "
                :effect="item.lawStatus === 'repealed' ? 'dark' : 'light'"
              >
                {{ lawStatusLabel(item.lawStatus) }}
              </el-tag>
              <span v-if="item.lawName">{{ item.lawName }} {{ item.articleNo }}</span>
            </div>
            <details class="report-item-content">
              <summary>查看文本快照</summary>
              <pre>{{ item.snapshotContent }}</pre>
            </details>
          </li>
        </ol>
      </section>
      <el-form label-position="top" :disabled="!!busy" class="report-panel">
        <el-form-item label="检索结论">
          <el-input
            v-model="edit.conclusion"
            type="textarea"
            :rows="6"
            maxlength="50000"
            show-word-limit
            placeholder="整理检索结论与适用建议"
          />
        </el-form-item>
      </el-form>
    </template>
    <el-dialog
      v-model="adding"
      title="添加自定义文本"
      width="min(640px, 94vw)"
      :close-on-click-modal="!busy"
      :close-on-press-escape="!busy"
      :show-close="!busy"
      append-to-body
    >
      <el-form label-position="top" :disabled="!!busy" @submit.prevent>
        <el-form-item label="条目标题" required>
          <el-input v-model="itemTitle" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="正文快照" required>
          <el-input
            v-model="itemContent"
            type="textarea"
            :rows="10"
            maxlength="100000"
            show-word-limit
            placeholder="粘贴需收录到报告的原文或自定义内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="!!busy" @click="adding = false">
          取消
        </el-button><el-button
          type="primary"
          :disabled="!!busy"
          :loading="busy === 'add'"
          @click="add(itemTitle, itemContent, () => (adding = false))"
        >
          加入报告
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
