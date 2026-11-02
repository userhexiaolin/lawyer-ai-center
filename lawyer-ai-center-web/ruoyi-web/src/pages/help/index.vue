<script setup lang="ts">
import AppPageHeader from '@/components/AppPageHeader.vue';
import KnowledgeBoundary from '@/components/KnowledgeBoundary.vue';
</script>

<template>
  <main class="help-page yl-page yl-page--reading">
    <AppPageHeader
      title="帮助与数据来源"
      description="了解内容覆盖范围、来源与更新方式，明确每一次检索的使用边界。"
      eyebrow="使用帮助"
    />
    <article class="yl-panel help-article">
      <KnowledgeBoundary />
      <h2>个人版内容包 v1</h2>
      <p>
        2026年10月16日整理的本地内容包共857片：官方条文823片、实务整理34片。数量为内容包清单数，当前账号可访问的检索范围以页面所选知识库为准。系统不承诺覆盖全部现行法规或裁判文书。
      </p>
      <ul>
        <li>民法典合同编526条、总则核心55条。</li>
        <li>合同编通则司法解释69条。</li>
        <li>劳动合同法98条、劳动争议调解仲裁法54条、劳动争议司法解释二21条。</li>
        <li>合同审查风险点30项、高频案由要点4项，整理内容待律师抽检。</li>
      </ul>
      <p>
        当前内容包没有正式裁判文书类案，不能据此声称“全网案例检索”。民间借贷利率专项司法解释、地区指引和人工审核类案摘要仍待内容运营补充。
      </p>
      <h2>来源、更新与使用范围</h2>
      <p>
        条文来源为全国人大、最高人民法院及政府机关公开发布的法律法规文本；这些法律法规及其官方译文属于著作权法第五条规定的文件类别。网页排版、第三方解读、数据库编排和案例摘要不因此获得授权，不纳入自动转载范围。整理版仅为本项目内部试用内容，未经律师审核不作为权威结论。
      </p>
      <p>
        下载/整理日期：2026年10月16日；清单核对日期：2026年10月18日。下载日期不代表逐条现行性已经人工复核；请核查后续修改、施行时间与适用范围。
      </p>
      <ul>
        <li>
          <a href="https://flk.npc.gov.cn/" target="_blank" rel="noopener noreferrer">国家法律法规数据库</a>
        </li>
        <li>
          <a
            href="https://ipc.court.gov.cn/zh-cn/news/view-371.html"
            target="_blank"
            rel="noopener noreferrer"
          >民法典公开原文</a>
        </li>
        <li>
          <a
            href="https://www.court.gov.cn/fabu/xiangqing/419382.html"
            target="_blank"
            rel="noopener noreferrer"
          >合同编通则司法解释</a>
        </li>
      </ul>
      <h2>没有检索结果时</h2>
      <p>
        请调整关键词、筛选条件或所选知识库，必要时到权威来源进一步查证。无命中只表示当前范围未检出，不代表相关法规或案例不存在。系统不补造案号。
      </p>
      <router-link to="/fact-search">
        返回案情检索
      </router-link>
    </article>
  </main>
</template>

<style scoped>
.help-page {
  min-width: 0;
}
.help-page h1 {
  font-size: 26px;
}
.help-page h2 {
  font-size: 18px;
  margin-top: 24px;
}
.help-page p,
.help-page li {
  color: var(--el-text-color-secondary);
}
a {
  color: var(--yl-color-brand-strong);
}
.help-article {
  line-height: 1.9;
}
.help-article ul {
  padding-left: 24px;
}
.help-article li {
  margin: 8px 0;
}
.help-article a {
  color: var(--yl-color-brand-strong);
}
</style>
