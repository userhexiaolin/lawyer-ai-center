<script setup lang="ts">
import type { Hit, SearchInput } from './workflow';
import { ElMessage } from 'element-plus';
import { computed, defineAsyncComponent, onScopeDispose, reactive, ref, watch } from 'vue';
import { factSearchApi } from '@/api/fact-search';
import { lawyerRequest } from '@/api/lawyer';
import { readMediaApiResult } from '@/api/media/response';
import { create_session } from '@/api/session';
import AppPageHeader from '@/components/AppPageHeader.vue';
import AppState from '@/components/AppState.vue';
import KnowledgeBoundary from '@/components/KnowledgeBoundary.vue';
import LegalCitation from '@/components/LegalCitation.vue';
import { useUserStore } from '@/stores';
import { createSearchWorkflow, initialSearchState } from './workflow';

const AddToReportDialog = defineAsyncComponent(() => import('@/components/AddToReportDialog.vue'));
const user = useUserStore();
const state = reactive(initialSearchState());
const input = reactive<SearchInput>({
  text: '',
  knowledgeIds: [],
  type: 'all',
  status: 'all',
  keyword: '',
  dateFrom: '',
  dateTo: '',
  region: '',
  topic: '',
});
const libraries = ref<{ id: string; name: string }[]>([]);
const loadingLibraries = ref(false);
const libraryError = ref('');
const selected = ref<Hit | null>(null);
const detail = ref<Hit | null>(null);
const detailLoading = ref(false);
const detailError = ref('');
const labels = {
  causeHints: '案由提示',
  keywords: '关键词',
  disputes: '争议焦点',
  parties: '当事人',
  lawsHints: '法规提示',
};
const workflow = createSearchWorkflow(state, {
  create: async title =>
    (await readMediaApiResult<unknown>(create_session({ sessionTitle: title }))).data,
  search: factSearchApi.search,
});
const changed = computed(() => !!state.result && state.fingerprint !== JSON.stringify(input));
let epoch = 0;
let detailEpoch = 0;
let disposed = false;
async function loadLibraries() {
  const version = ++epoch;
  const token = user.token;
  if (!token)
    return;
  loadingLibraries.value = true;
  libraryError.value = '';
  try {
    const { data } = await factSearchApi.libraries();
    if (!disposed && epoch === version && user.token === token)
      libraries.value = data;
  }
  catch (e) {
    if (!disposed && epoch === version && user.token === token)
      libraryError.value = (e as Error).message;
  }
  finally {
    if (!disposed && epoch === version)
      loadingLibraries.value = false;
  }
}
async function search() {
  if (!user.token) {
    user.ensureLogin('/fact-search');
    return;
  }
  await workflow.search({ ...input, knowledgeIds: [...input.knowledgeIds] });
  if (!disposed && state.error)
    ElMessage.error(state.error);
}
async function open(hit: Hit) {
  detail.value = hit;
  detailLoading.value = true;
  detailError.value = '';
  const version = ++detailEpoch;
  const token = user.token;
  try {
    const { data } = await lawyerRequest<{ data: Hit }>(
      `/legal/citations/fragments/${encodeURIComponent(hit.id)}`,
    );
    if (!disposed && version === detailEpoch && user.token === token)
      detail.value = { ...hit, ...data };
  }
  catch (e) {
    if (!disposed && version === detailEpoch && user.token === token)
      detailError.value = (e as Error).message;
  }
  finally {
    if (!disposed && version === detailEpoch)
      detailLoading.value = false;
  }
}
watch(
  () => user.token,
  () => {
    epoch++;
    detailEpoch++;
    workflow.reset();
    libraries.value = [];
    libraryError.value = '';
    selected.value = null;
    detail.value = null;
    Object.assign(input, {
      text: '',
      knowledgeIds: [],
      type: 'all',
      status: 'all',
      keyword: '',
      dateFrom: '',
      dateTo: '',
      region: '',
      topic: '',
    });
    void loadLibraries();
  },
  { immediate: true, flush: 'sync' },
);
onScopeDispose(() => {
  disposed = true;
  epoch++;
  detailEpoch++;
  workflow.dispose();
});
</script>

<template>
  <main class="fact-search yl-page">
    <AppPageHeader
      title="案情检索"
      description="提取案情要素，在当前可访问的知识库中查找法规、类案和相关片段。"
      eyebrow="法律检索"
    >
      <template #actions>
        <router-link :to="{ name: 'searchReports' }">
          <el-button>我的检索报告</el-button>
        </router-link>
      </template>
    </AppPageHeader>
    <KnowledgeBoundary />
    <el-form label-position="top" :disabled="state.busy" @submit.prevent="search">
      <el-form-item label="案情描述" required>
        <el-input
          v-model="input.text"
          type="textarea"
          :rows="5"
          maxlength="12000"
          show-word-limit
          placeholder="例如：张某借给李某30000元，有借条和转账记录，约定还款日已过，对方拒不还款。希望查找借款返还及利息相关依据。"
        />
      </el-form-item>
      <div class="filters">
        <el-form-item label="知识库（默认使用前4个，可手动调整）">
          <el-select
            v-model="input.knowledgeIds"
            multiple
            :multiple-limit="4"
            clearable
            :loading="loadingLibraries"
            placeholder="选择知识库，最多4个"
          >
            <el-option
              v-for="library in libraries"
              :key="library.id"
              :label="library.name"
              :value="library.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="片段类型">
          <el-select
            v-model="input.type"
            @change="
              input.status = 'all';
              input.dateFrom = '';
              input.dateTo = '';
            "
          >
            <el-option label="全部片段" value="all" /><el-option
              label="法规"
              value="law"
            /><el-option label="类案" value="case" /><el-option
              label="其他 / 实务整理"
              value="other"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="法规时效">
          <el-select v-model="input.status">
            <el-option label="全部状态" value="all" /><el-option
              label="有效"
              value="effective"
            /><el-option label="已修改" value="amended" /><el-option
              label="已失效"
              value="repealed"
            /><el-option label="时效待核实" value="unknown" />
          </el-select>
        </el-form-item>
      </div>
      <el-alert v-if="libraryError" :title="libraryError" type="warning" :closable="false">
        <el-button link @click="loadLibraries">
          重载知识库
        </el-button>
      </el-alert>
      <div class="filters extra-filters">
        <el-form-item label="关键词（空格分隔，全部匹配）">
          <el-input
            v-model="input.keyword"
            maxlength="100"
            clearable
            placeholder="例如：违约金 调整"
          />
        </el-form-item>
        <el-form-item label="地域">
          <el-input
            v-model="input.region"
            maxlength="80"
            clearable
            placeholder="例如：全国（按元数据精确匹配）"
          />
        </el-form-item>
        <el-form-item label="案由 / 主题关键词">
          <el-input
            v-model="input.topic"
            maxlength="80"
            clearable
            placeholder="在标题与片段中匹配，如：借款"
          />
        </el-form-item>
        <el-form-item label="发布起始日期">
          <el-date-picker
            v-model="input.dateFrom"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="input.type === 'case'"
            placeholder="不限"
          />
        </el-form-item>
        <el-form-item label="发布截止日期">
          <el-date-picker
            v-model="input.dateTo"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="input.type === 'case'"
            placeholder="不限"
          />
        </el-form-item>
        <el-form-item label="法院层级（暂不可用）">
          <el-select disabled placeholder="当前内容包未提供法院层级" />
        </el-form-item>
      </div>
      <p class="quota-note">
        日期按已录入的发布日期筛选，缺失日期的片段不会命中日期条件；类案裁判日期尚未入库，类案模式禁用日期筛选。地域按元数据匹配，主题按标题与正文匹配。
      </p>
      <el-button
        type="primary"
        :loading="state.busy"
        :disabled="state.busy || !input.text.trim()"
        @click="search"
      >
        开始案情检索
      </el-button>
    </el-form>
    <p v-if="state.progress" role="status">
      {{ state.progress }}
    </p>
    <el-alert v-if="state.error" :title="state.error" type="error" :closable="false" />
    <template v-if="state.result">
      <el-alert
        v-if="changed"
        title="输入或筛选已修改，下方为上一次检索结果"
        type="info"
        :closable="false"
      />
      <el-alert
        v-for="warning in state.result.warnings"
        :key="warning"
        :title="warning"
        type="warning"
        :closable="false"
      />
      <section class="elements">
        <h2>
          要素摘要
          <el-tag :type="state.result.extractionStatus === 'fallback' ? 'warning' : 'info'">
            {{ state.result.extractionStatus === 'fallback' ? '关键词降级' : '模型抽取' }}
          </el-tag>
        </h2>
        <dl>
          <div v-for="(label, key) in labels" :key="key">
            <dt>{{ label }}</dt>
            <dd>
              <el-tag v-for="value in state.result.elements[key]" :key="value">
                {{ value }}
              </el-tag><span v-if="!state.result.elements[key].length">未提取</span>
            </dd>
          </div>
        </dl>
        <details>
          <summary>查看要素 JSON</summary>
          <pre>{{ JSON.stringify(state.result.elements, null, 2) }}</pre>
        </details>
      </section>
      <section aria-label="知识库命中列表">
        <h2>{{ state.result.message }}</h2>
        <p>
          已尝试 {{ state.result.attemptedLibraries }} 个知识库 ·
          {{ state.result.retrievalMode === 'hybrid' ? '混合检索' : '关键词检索 / 降级' }}
        </p>
        <el-empty v-if="!state.result.hits.length" :description="state.result.message" />
        <article v-for="hit in state.result.hits" :key="`${hit.knowledgeId}:${hit.id}`">
          <h3>{{ hit.sourceName || '知识库片段' }}</h3>
          <LegalCitation v-if="hit.reference" :citation="hit.reference" />
          <p v-else class="metadata-note">
            {{ hit.legalMetadata?.lawName || '通用知识片段' }}
            {{ hit.legalMetadata?.articleNo || '' }} · 未提供完整法规 / 案号元数据
          </p>
          <p v-if="hit.reference?.type === 'case'">
            案号：{{ hit.reference.caseNo }} · 法院：{{ hit.reference.court || '未录入' }} ·
            裁判日期：未录入
          </p>
          <p v-if="hit.legalMetadata?.region || hit.legalMetadata?.publishDate">
            地区：{{ hit.legalMetadata?.region || '未录入' }} · 发布日期：{{
              hit.legalMetadata?.publishDate || '未录入'
            }}
          </p>
          <pre class="snippet">{{ hit.content }}</pre>
          <p v-if="hit.truncated">
            正文较长，查看片段可阅读完整内容。
          </p>
          <el-button link type="primary" @click="open(hit)">
            查看片段
          </el-button><el-button v-if="!hit.reference" link type="primary" @click="selected = hit">
            加入检索报告
          </el-button>
        </article>
      </section>
    </template>
    <AppState
      v-else-if="!state.busy && !state.error"
      title="从清晰的案情开始检索"
      description="填写当事人、关键事实与争议，按所选知识库查找可核对的内容。"
    />
    <AddToReportDialog
      v-if="selected"
      :fragment-id="selected.id"
      :reference="selected.reference"
      :title="selected.sourceName"
      :content="selected.content"
      @close="selected = null"
    />
    <el-drawer
      :model-value="!!detail"
      title="知识库片段"
      size="min(680px, 95vw)"
      @close="
        detailEpoch++;
        detail = null;
      "
    >
      <p v-if="detailLoading">
        正在加载原文…
      </p>
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" /><template
        v-if="detail"
      >
        <h3>{{ detail.sourceName || '知识库片段' }}</h3>
        <LegalCitation v-if="detail.reference" :citation="detail.reference" />
        <pre class="snippet">{{ detail.content }}</pre>
      </template>
    </el-drawer>
  </main>
</template>

<style scoped>
.fact-search {
  min-width: 0;
}
.fact-search h1 {
  font-size: 24px;
  margin: 0 0 12px;
}
.fact-search h2 {
  font-size: 18px;
  margin: 20px 0 12px;
}
.fact-search p,
.metadata-note {
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
.fact-search form,
.elements,
.fact-search article {
  border: 1px solid var(--el-border-color);
  border-radius: var(--yl-radius-panel);
  padding: 20px;
  background: var(--el-bg-color);
  margin-bottom: 18px;
}
.filters {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: 16px;
}
.quota-note {
  margin-left: 14px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.elements dl > div {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
}
.elements dt {
  min-width: 70px;
}
.elements dd {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 0;
}
.snippet,
details pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font: inherit;
  line-height: 1.8;
}
.fact-search article .snippet {
  max-height: 280px;
  overflow: auto;
}
.fact-search article h3 {
  margin: 0 0 12px;
  font-size: 16px;
}
@media (max-width: 760px) {
  .fact-search {
    padding: 16px;
  }
  .filters {
    grid-template-columns: 1fr;
  }
  .quota-note {
    display: block;
    margin: 12px 0;
  }
}
@container workspace (max-width:800px) {
  .filters {
    grid-template-columns: 1fr;
  }
}
</style>
