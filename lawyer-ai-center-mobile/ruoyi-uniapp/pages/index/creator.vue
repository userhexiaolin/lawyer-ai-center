<template>


  <div class="page">
    <!-- 顶部图片 -->
    <div class="top-image">
      <image src="/static/assistant/bg01.png" mode="aspectFill" class="header-image" />
    </div>

    <!-- 搜索框 -->
    <div class="search-section">
      <div class="search-container">
        <div class="search-box">
          <input type="text" placeholder="搜索你想要的~" class="search-input" placeholder-style="color:#606266;font-size:16px;" />
        </div>
        <div class="search-btn">
          <!-- 搜索图标 -->
          <svg class="search-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="11" cy="11" r="8" stroke="#666" stroke-width="2"/>
            <path d="m21 21-4.35-4.35" stroke="#666" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
      </div>
    </div>

    <!-- 内容区域 -->
    <div class="content-area">
      <!-- 分类区块：按数据渲染 -->
      <div class="category-section" v-for="group in groups" :key="group.key">
        <div class="category-header">
          <div class="category-title">
            <span class="title-text">{{ group.title }}</span>
            <div class="title-icon"></div>
          </div>
          <div class="view-all-btn">
            <span class="view-all-text">全部</span>
            <div class="arrow-icon"></div>
          </div>
        </div>

        <div class="cards-grid">
          <div
            v-for="card in group.cards"
            :key="card.key"
            class="feature-card"
            :class="card.cls"
            @click="goChat(card)"
          >
            <div class="card-content">
              <h3 class="card-title">{{ card.title }}</h3>
              <p class="card-subtitle">{{ card.subtitle }}</p>
            </div>
            <div class="card-icon"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  components: {},
  props: {},
  data() {
    return {
      groups: [
        {
          key: 'work',
          title: 'AI工作',
          cards: [
            { key: 'short-video', title: '短视频', subtitle: '视频大纲、口播稿等', cls: 'card-pink',
              systemPrompt: '你是短视频脚本专家，请根据用户需求生成视频大纲、口播稿等。',
              inputText: '帮我写一个关于健康饮食的60秒短视频口播稿' },
            { key: 'summary', title: '概括总结', subtitle: '生成各场景下总结', cls: 'card-purple',
              systemPrompt: '你是总结专家，请根据用户提供的内容生成各场景下的总结。',
              inputText: '请帮我概括总结：今天会议讨论了产品上线时间、分工和风险点' },
            { key: 'translate', title: '万能翻译', subtitle: '公证号朋友圈文章', cls: 'card-blue',
              systemPrompt: '你是翻译专家，请将用户提供的公众号、朋友圈、文章等内容做翻译。',
              inputText: '请把这段话翻译成英文：人工智能正在改变我们的生活方式' },
            { key: 'weekly', title: '周报', subtitle: '一键式汇报工作成果', cls: 'card-yellow',
              systemPrompt: '你是周报撰写专家，请根据用户描述的工作成果一键生成周报。',
              inputText: '本周完成了登录模块开发和列表页联调，请生成周报' },
          ],
        },
        {
          key: 'study',
          title: 'AI学习',
          cards: [
            { key: 'essay', title: '作文', subtitle: '学生阶段写作内容', cls: 'card-yellow',
              systemPrompt: '你是作文辅导老师，请根据用户需求生成学生阶段的写作内容。',
              inputText: '请写一篇关于"秋天的校园"的600字作文' },
            { key: 'paper', title: '论文', subtitle: '一键生成论文内容', cls: 'card-green',
              systemPrompt: '你是论文写作助手，请根据用户主题生成论文内容结构与段落。',
              inputText: '请围绕"人工智能在教育中的应用"生成论文大纲' },
            { key: 'poem', title: '诗歌情诗', subtitle: '一键生成精美诗词', cls: 'card-pink',
              systemPrompt: '你是诗人，请根据用户需求生成精美的诗歌或情诗。',
              inputText: '请写一首关于春天的现代诗' },
            { key: 'quiz', title: '学科出题', subtitle: '一键生成各学科题目', cls: 'card-blue',
              systemPrompt: '你是出题专家，请根据用户需求一键生成各学科题目。',
              inputText: '请生成5道初中数学一元一次方程练习题' },
          ],
        },
      ],
    };
  },
  methods: {
    // 点击创作卡片：带系统提示词 + 默认提问，进入对话页自动发起对话
    goChat(card) {
      const url = `/pages/index/index?newSession=1&systemPrompt=${encodeURIComponent(card.systemPrompt)}&inputText=${encodeURIComponent(card.inputText)}&autoSend=true&appName=${encodeURIComponent(card.title)}`;
      uni.navigateTo({ url });
    },
  },
};
</script>

<style scoped lang="css">
.page {
  background-color: #f5f5f5;
  min-height: 100vh;
}

.top-image {
  position: relative;
  width: 100%;
  height: 200px;
  overflow: hidden;
}

.header-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.search-section {
  padding: 1rem;
  margin-top: -25px;
  position: relative;
  z-index: 10;
  background-color: white;
  border-radius: 25px 25px 0 0;
}

.search-container {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  position: relative;
  z-index: 10;
}

.search-box {
  flex: 1;
  display: flex;
  align-items: center;
  background-color: white;
  border-radius: 25px;
  padding: 0.75rem 1rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border: 2px solid #333;
}



.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 1rem;
  color: #333;
  background: transparent;
}

.search-input::placeholder {
  color:var(--brand-muted);
}

.search-btn {
  width: 48px;
  height: 48px;
  background-color: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.search-icon {
  width: 32px;
  height: 32px;
}

.scan-icon {
  font-size: 1.5rem;
  color: white;
}

.content-area {
  padding: 0 1rem 1rem 1rem;
  background-color: white;
  position: relative;
}

.category-section {
  margin-bottom: 2rem;
}

.category-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.category-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.title-text {
  font-size: 1.5rem;
  font-weight: bold;
  color: #333;
}

.title-icon {
  width: 1.5rem;
  height: 1.5rem;
  background-color: #9ACD32;
  border-radius: 50%;
  position: relative;
}

.title-icon::after {
  content: '✦';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: white;
  font-size: 0.8rem;
}
.view-all-btn {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.5rem 1rem;
  border: 1px solid #333;
  border-radius: 1.5rem;
  background-color: white;
}

.view-all-text {
  font-size: 0.9rem;
  color: #333;
}

.arrow-icon {
  width: 0.5rem;
  height: 0.5rem;
  border-right: 2px solid #333;
  border-bottom: 2px solid #333;
  transform: rotate(-45deg);
}

.cards-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.feature-card {
  position: relative;
  padding: 1.5rem;
  border-radius: 1rem;
  min-height: 8rem;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
}

.card-content {
  z-index: 2;
}

.card-title {
  font-size: 1.2rem;
  font-weight: bold;
  margin: 0 0 0.5rem 0;
  color: #333;
}

.card-subtitle {
  font-size: 0.9rem;
  margin: 0;
  color: #666;
  line-height: 1.4;
}

.card-icon {
  position: absolute;
  bottom: 1rem;
  right: 1rem;
  width: 3rem;
  height: 3rem;
  border-radius: 0.5rem;
  z-index: 1;
}

/* 卡片背景色 */
.card-pink {
  background: linear-gradient(135deg, #fef0f0 0%, #f4dddd 100%);
}

.card-purple {
  background: linear-gradient(135deg, #f7f1ff 0%, #e8ddf4 100%);
}

.card-blue {
  background: linear-gradient(135deg, #edf5fe 0%, #dae9f8 100%);
}

.card-yellow {
  background: linear-gradient(135deg, #fff5c8 0%, #f7eabe 100%);
}

.card-green {
  background: linear-gradient(135deg, #ebf7e3 0%, #e1eed6 100%);
}
</style>

<style scoped lang="scss">
@import '@/common/form-controls.scss';
.search-input { @include native-input-frame; height:44px; min-height:44px; padding:0; } .search-box { min-height:48px; padding:0 12px; }
</style>
