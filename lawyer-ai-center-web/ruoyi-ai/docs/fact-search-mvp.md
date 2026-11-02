# 轻量案情检索（Alpha §3.7）

入口：PC 左侧菜单「案情检索」，路由 `/fact-search`，名称 `factSearch`，登录后使用。仅检索本系统中当前用户可访问的知识库，不对接外部案例库，也不实现24维外部检索。

## 最小闭环

1. 输入案情（1～12000字），可选知识库、片段类型（全部/法规/类案）、法规时效。
2. 后端复用启用的「法律咨询助手」所绑定的模型，通过现有 `ChatServiceFactory` 调用专门的要素抽取提示词。模型、厂商及凭据由后端配置解析，前端不能指定模型服务地址。
3. 严格解析以下五个数组，模型每组最多8项、每项最多60字。案情文本作为数据处理；提示词禁止编造案号、条号、当事人或法规名称，`lawsHints` 只保留用户明确提及的法规名称。
   后端还会过滤原文没有出现的当事人/法规名称提示；若模型输出原文不存在的常见格式案号，整次抽取降级为原文关键词检索，避免虚构编号出现在要素摘要。

```json
{
  "causeHints": ["民间借贷"],
  "keywords": ["借款", "还款", "转账"],
  "disputes": ["借款返还与证据"],
  "parties": ["张某", "李某"],
  "lawsHints": []
}
```

4. 使用 `keywords + causeHints + lawsHints` 作为检索文本，复用 `KnowledgeRetrievalService` 的混合检索和法律元数据补全。按法规/类案类型、法规时效过滤；SQL召回在限制条数之前应用相同的元数据筛选，向量结果补全元数据后过滤。
5. 模型不可用、输出非法、全部检索词为空或15秒超时，均降级为原案情中的关键词检索，其余要素数组为空。不会用推断的案号补齐结果。
6. 关键词召回优先使用 MySQL FULLTEXT；没有索引或未命中时使用参数化 `LOCATE` 查询，不依赖部署新的全文索引即可工作。缺少向量配置、向量故障时仍尝试 SQL 召回。
7. PC 展示要素摘要、可展开 JSON、命中卡片、原文片段和报告收录。法规/类案引用复用指令42组件，保留名称编号、失效标记及跳转；没有完整元数据的条目显示为普通知识片段。
8. 复用44/45的选择/新建报告弹窗。添加时仅提交真实 `fragmentId`，由现有后端检查归属并抓取全文快照；普通片段以 `viewpoint` 收录，不生成案号。卡片正文最多预览6000字，详情和报告快照读取完整片段。

本期最多同时选择4个知识库，不选时按 ID 顺序检索前4个可访问知识库。列表超过4个时，页面和结果都明确说明此范围。最终返回最多30个片段。模型和知识库任务使用有界线程池；混合检索任务共享23秒等待期限，超时取消等待并尝试SQL检索。

## 额度与权限

- PC 先通过现有 `/system/session` 创建 `案情检索 - ...` 会话，此步不扣次数。
- 通过输入、会话归属及知识库访问校验后，统一调用一次现有 `LawyerUsageService.beginProcessing`，采用 CHAT 类别与会话首次有效处理规则。
- 抽取、混合检索、SQL降级均不走 `/chat/send`，不再消费第二份额度；相同会话重试由已有 `usage_started` 原子标记避免重复扣费。前端保留同输入/筛选的会话 ID，修改输入或筛选则新建会话。
- 处理失败后重试仍复用该会话；已受理并扣除的额度遵循原有对话规则，不另行新增退款或计费体系。
- 不信任客户端 user/tenant。会话必须属于当前用户且使用案情检索标题前缀；知识库集合只取当前用户可访问项，显式请求不可访问 ID 在扣费和模型调用之前拒绝。
- SQL 显式限定租户和已校验的知识库 ID，所有检索词和筛选值使用绑定参数；异步 RAG 使用服务器创建的用户/租户 principal，缓存读取仍由既有权限检查保护。
- 案情、要素和结果保存至已有个人会话消息。原始检索内容、元数据卡片来自真实知识库记录，模型输出的要素只用于查询，不用作法规或案例记录。

无知识库或无匹配时返回空列表和「知识库暂无匹配，可调整关键词或补充知识库资料」。关键词服务失败时明确提示检索暂不可用，避免把故障伪装为确定的无命中。前端对错误响应、结构异常、账号切换和组件销毁均有处理。

## API

`GET /legal/fact-search/libraries`：可访问知识库的 `{id,name}` 列表。

`POST /legal/fact-search`：

```json
{
  "sessionId": "当前用户的案情检索会话ID",
  "text": "张某借给李某30000元，有借条和转账记录，到期仍未还款。",
  "knowledgeIds": [],
  "type": "all",
  "status": "all"
}
```

返回 `R<Result>`，包括：`sessionId`、五组 `elements`、`extractionStatus`（model/fallback）、`retrievalMode`（hybrid/keyword，存在降级时标记keyword）、实际 `query`、`attemptedLibraries`、`hits`、`warnings`、`message`。`hits` 包含真实片段ID、知识库ID、来源名、正文预览、是否截断、统一法规/类案 `reference` 和原法律元数据。

片段详情继续使用 `GET /legal/citations/fragments/{fragmentId}`，报告收录继续使用 `POST /legal/report/{reportId}/items`，不新增平行的收藏或报告系统。

## 手工验证

1. 启用并配置现有「法律咨询助手」，准备属于当前用户或本租户共享的知识库。向量配置可选；没有向量配置时仍可使用 SQL 文字检索。
2. 输入借贷案情：「张某借给李某30000元，有借条和转账记录，到期仍未还款，希望查找借款返还和利息相关依据。」核对五个要素数组、尝试的知识库数和列表；如果知识库已有包含“借款/还款”的片段，应能召回。
3. 也可输入合同案情：「甲公司支付设备货款50000元，乙公司逾期未交付，催告后仍未履行。希望查找交付义务、违约及解除相关片段。」
4. 将抽取模型临时配置为不可用，确认出现关键词降级提示，仍尝试检索。不要删除正式配置；验证后恢复。
5. 选择无匹配的库或关键词，确认空列表和友好提示；不存在案号时不得生成案例卡片编号。
6. 对法规片段验证法规名+条号、失效/修改标记，点击查看详情；对普通片段验证无案号状态。
7. 点击「加入检索报告」，选择已有或新建报告，保存后从报告工作台确认快照存在。失效法条状态、法条去重继续由44/45处理。
8. 对比单次检索前后额度，确认抽取与检索合计消费一次；同输入重试仍使用相同会话，修改输入后产生新会话。用他人会话/不可访问知识库 ID 请求应被拒绝且不扣费。

请求示例（先按项目登录流程取得 TOKEN 与 CLIENT_ID）：

```bash
curl "$BASE/system/session" -H "Authorization: Bearer $TOKEN" -H "clientid: $CLIENT_ID" -H 'Content-Type: application/json' \
  -d '{"sessionTitle":"案情检索 - 借贷验证"}'

curl "$BASE/legal/fact-search" -H "Authorization: Bearer $TOKEN" -H "clientid: $CLIENT_ID" -H 'Content-Type: application/json' \
  -d '{"sessionId":"上一步返回的ID","text":"张某出借30000元，有借条和转账，约定还款日已过仍未还款。","knowledgeIds":[],"type":"all","status":"all"}'
```

## 自动化与界面验收

- 后端17项通过：新增 `FactSearchServiceTest` 5项、`FactSearchKnowledgeTest` 4项，既有 `LawyerUsageServiceTest` 6项、`KnowledgeRetrievalServiceRegressionTest` 2项。覆盖严格JSON、抽取失败继续查库、真实引用保留、租户/知识库绑定参数、无全文索引降级、权限拒绝先于扣费、原有会话计费幂等。
- 前端36项通过：`fact-search.test.cjs`、`search-report.test.cjs`、`legal-citation.test.cjs`。包括新增搜索工作流8项及普通片段收录用例。
- 应用级 `vue-tsc -p tsconfig.app.json --noEmit` 通过，Vite 生产构建通过（17分59秒）。构建仍有既有 KaTeX 字体路径、`:deep` 和大分包提示。
- 浏览器隔离页 `/tests/fixtures/fact-search-preview.html` 加载真实页面和收录组件，API/片段为明确标注的模拟数据。已操作：借贷检索、JSON展开、新建报告收录普通片段、抽取降级仍显示命中、空库与账号切换清理；未出现白屏。
- 截图：`scripts/development/fact-search-ui.png`、`fact-search-empty-ui.png`。
- 本轮没有把案情发送到真实模型，也未修改持久知识库或数据库。真实服务联调按上面的步骤进行；本期不需要新增 SQL 迁移。

命令（各自项目目录）：

```text
# ruoyi-web
node --test tests/fact-search.test.cjs tests/search-report.test.cjs tests/legal-citation.test.cjs
node_modules/.bin/vue-tsc -p tsconfig.app.json --noEmit
node node_modules/vite/bin/vite.js build

# ruoyi-ai
mvn -o -pl ruoyi-modules/ruoyi-chat -am test -Dtest=FactSearchServiceTest,FactSearchKnowledgeTest,LawyerUsageServiceTest,KnowledgeRetrievalServiceRegressionTest -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false -DskipTests=false
```

## 改动文件

后端（相对 `ruoyi-ai`）：
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/legal/FactElements.java`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/legal/FactSearchModel.java`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/legal/FactSearchKnowledge.java`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/legal/FactSearchService.java`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/controller/legal/FactSearchController.java`
- `ruoyi-modules/ruoyi-chat/src/test/java/org/ruoyi/service/legal/FactSearchServiceTest.java`
- `ruoyi-modules/ruoyi-chat/src/test/java/org/ruoyi/service/legal/FactSearchKnowledgeTest.java`
- `docs/fact-search-mvp.md`

PC（相对 `ruoyi-web`）：
- `src/pages/fact-search/index.vue`、`workflow.ts`
- `src/api/fact-search/index.ts`
- `src/components/AddToReportDialog.vue`（兼容普通知识片段）
- `src/routers/modules/staticRouter.ts`
- `src/layouts/components/Aside/index.vue`、`src/layouts/components/Header/index.vue`
- `tests/fact-search.test.cjs`、`tests/search-report.test.cjs`
- `tests/fixtures/fact-search-preview.{html,ts,vue}`
