# 法规与类案引用（Alpha §16 / §18.4 K1～K3）

## 数据约定

```ts
type LegalCitation =
  | { type: 'law'; lawName: string; articleNo: string; status?: string; sourceUrl?: string; fragmentId?: string }
  | { type: 'case'; caseNo: string; title?: string; court?: string; sourceUrl?: string; fragmentId?: string };
```

法规必须提供名称和条款号，类案必须有真实案号。禁止用前端占位值补齐案号。`status` 使用 `effective/repealed/amended/unknown`；兼容原有 `有效/已失效/已修订`。未知或未提供状态也提示“时效待核实”。过期日期已到的法规按 `repealed` 组装。

检索接口仍返回 `legalMetadata` 和旧的文字 `citation`，新增 `reference: LegalCitation`。`fragmentId` 使用检索结果 `id`（向量 fid，旧数据可使用片段数据库 id），始终作为字符串传输。

对话及意见书采用下列引用块，可在正文相应段落后出现多次。完整引用块被渲染为可点击标签，流式未完成或无效的块保留为原文。

````text
分析正文……
```legal-citations
[{"type":"law","lawName":"测试法规（仅验收使用）","articleNo":"第12条","status":"repealed","fragmentId":"实际可访问的片段fid"}]
```
````

合同审查保留原有 JSON 根结构，直接在 `risks[].legalBasis` 放引用对象；兼容已有字符串，也兼容 `risks[].citations` 对象数组。报告导出保留法规编号、状态和来源地址。

## 跳转与权限

1. 有 `fragmentId`：PC 详情抽屉，移动端原文弹层。请求 `GET /legal/citations/fragments/{fragmentId}`。
2. 没有片段、有 HTTP(S) `sourceUrl`：PC/H5 新窗口；小程序/App 打开原文 WebView。
3. 都没有：提示“暂无原文，仅展示引用信息”。

片段接口先限制可读知识库，再校验知识库访问权；无权、删除或未知片段不返回正文。加载失败可重试，不越过片段优先级打开外链。移动端非 H5 的外部来源域名需符合现有宿主 WebView 域名配置。

## 元数据链路与部署

先执行 `docs/script/sql/update/2026-10-17-legal-citation-source.sql`，为 `knowledge_attach` 增加 `case_no/court/source_url`，再部署后端。管理端“法律元数据”表单可维护这三个字段，类案标题使用附件名称。

附件元数据经 `LegalMetadataService` 组装 `reference`；检索缓存复制、纯文本检索接口、RAG `TextSegment` 的正文及 metadata 均保留引用。SSE 普通聊天、智能体/监督者和移动端 WebSocket 均注入引用约定。无命中时向模型明确提供“知识库暂无匹配”并禁止编造案号，前端不生成案号。管理端空检索结果也显示该提示。

移动端源码补丁位于当前仓库 `scripts/development/mobile/`，沿用现有 `write-mobile.cjs` 同步流程；本次验证在工作区临时副本叠加补丁，不直接修改兄弟移动端项目。

## 改动文件

- PC：`ruoyi-web/src/utils/legalCitation.ts`、`src/components/LegalCitation.vue`、`src/components/LegalAnswer.vue`、`src/pages/chat/layouts/chatWithId/index.vue`、`src/pages/contract-review/result.ts`、`src/pages/contract-review/ReviewResults.vue`、自动生成的 `types/components.d.ts`。
- 管理端：`ruoyi-admin/apps/web-antd/src/utils/legal-citation.ts`、`src/api/knowledge/attach/model.d.ts`、`src/views/knowledge/attach/attach-modal.vue`、`src/views/knowledge/info/detail/components/RetrievalTest.vue`。
- 移动端补丁：`scripts/development/mobile/common/legal-citation.js`、`common/review-result.js`、`components/legal-citation.vue`、`components/review-message.vue`、`components/risk-result-card.vue`、`pages/legal/source.vue`、`pages.json`、`tests/review-display.test.cjs`。
- 后端（`ruoyi-ai/ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/`）：`domain/vo/knowledge/LegalReference.java`、`KnowledgeRetrievalVo.java`、`KnowledgeAttachVo.java`、`domain/entity/knowledge/KnowledgeAttach.java`、`domain/bo/knowledge/KnowledgeAttachBo.java`、`service/knowledge/LegalMetadataService.java`、`service/knowledge/retriever/CustomVectorRetriever.java`、`service/retrieval/impl/KnowledgeRetrievalServiceImpl.java`、`service/chat/impl/ChatServiceFacade.java`、`service/chat/ContractReviewReport.java`、`websocket/chat/MpChatWebSocketHandler.java`、`controller/knowledge/LegalCitationController.java`。
- 测试：`ruoyi-web/tests/legal-citation.test.cjs`、`tests/fixtures/legal-citation-preview.html`、`tests/fixtures/legal-citation-preview.ts`、`tests/fixtures/legal-citation-preview.vue`；后端 `LegalReferenceTest.java`、`LegalCitationControllerTest.java`、`LegalCitationRetrieverTest.java`、`ChatServiceFacadePromptTest.java`、`ContractReviewReportTest.java`。
- 数据库迁移与本文档：`ruoyi-ai/docs/script/sql/update/2026-10-17-legal-citation-source.sql`、`ruoyi-ai/docs/legal-citations-alpha16.md`。

## 验证步骤

1. `node --test ruoyi-web/tests/legal-citation.test.cjs ruoyi-web/tests/contract-review-result.test.cjs`：检查法规名+条号、真实输入案号的保留、失效/修改/未知状态、跳转优先级、非法来源协议、流式兼容、审查对象保留、实际移动端点击函数。
2. `cd ruoyi-web; pnpm build`：PC 类型检查及生产构建。
3. 在 `ruoyi-ai` 执行 Maven：`mvn -o -pl ruoyi-modules/ruoyi-chat -am test -Dtest=LegalReferenceTest,LegalMetadataServiceTest,LegalCitationRetrieverTest,LegalCitationControllerTest,ChatServiceFacadePromptTest,MpChatContextTest,KnowledgeAccessTest,ContractReviewReportTest -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false -DskipTests=false`。
4. 用现有移动端完整源码叠加补丁，运行 `tests/review-display.test.cjs`，再使用 HBuilderX CLI 构建 H5 和微信小程序。补丁目录自身不含完整项目依赖，不能单独作为完整移动端项目运行。
5. 手工验收：将一份测试法规附件设置名称、条号、`已失效`，检索它并检查红色标记；分别以“片段+来源地址”“仅来源地址”“都没有”三个对象验证详情、外链和提示；在合同风险与意见书检查同样行为；查询空库确认“知识库暂无匹配”且没有新造案号；用无权账号打开片段确认不返回正文。

自动验证结果见工作区 `scripts/development/legal-citation-*-tests.log` 与 `legal-citation-*-build.log`；实际环境的数据库迁移、真实模型输出与小程序来源域名仍需部署后联调。

本次已通过前端 22 项测试、移动端 5 项原有展示回归、后端 21 项针对性测试、PC 类型检查及最终生产构建、移动 H5/微信小程序构建。管理端全量类型检查仍有 89 项既有错误，本次涉及文件未报告类型错误，不能记作全量通过。

本地模拟验收页：启动 PC Vite 开发服务后访问 `/tests/fixtures/legal-citation-preview.html`，所有请求与外链均由页面模拟，可检验三种点击路径与失效标记。本次内置浏览器访问本机端口超时，未完成浏览器视觉验收；不能据此宣称真实接口、真实模型和视觉联调已通过。
