# 阶段4指令56：案情检索相关性评估

本次只调整案情检索的召回词、候选排序及范围/空状态提示，不接入外部案例库，不修改全局 RAG、权限、计费、引用或报告接口。默认仍是 `type=all/status=all`，按 ID 检索前四个可访问知识库；用户可以手动换库。

## 规则与边界

- 使用模型抽取的 `causeHints/keywords/lawsHints`，保留原词；根据借贷、劳动、买卖、租赁的明确要素补充少量领域词，并从争议中补充本金、利息、维修等词。借贷不默认补“利息”，仅当输入涉及计息时补充。最多32个词，SQL参数绑定。
- 全文匹配不再提前结束精确词召回；全文与精确词各最多120条。精确词在 SQL 中先按独立词覆盖数排序再截断，合并原有混合检索候选后统一排序，最终最多30条。未扩大用户可访问库范围。
- 排序分数 = 原始分数截断至 `[0,1]` 后乘 `0.12` + 词重合（标题/法规元数据每词 `0.18`、正文每词 `0.10`，总计最多 `1.2`）+ 领域重合（每领域词 `0.15`，每领域最多 `0.60`）+ 争议匹配（最多 `3.2`）+ 明确请求的法规名/条号匹配（最多 `0.45`）。只在单一领域案情下，对标题明确属于其他领域的结果每领域扣 `0.8`；混合案情不执行该惩罚。相同分数保持召回顺序。
- 争议匹配只在该领域且输入出现相应触发词时生效，每组最多加分一次：到期还本1.2、计息0.9、逾期利息0.8、工资1.0、劳动解除1.0、交付0.9、买卖解除1.2、价款返还1.2、维修1.4、租金减免1.0。匹配表达式用于召回（例如“期限返还借款”“恢复原状”），不按法规条号白名单置顶。
- 排序是相关性规则，不代表法律结论或法规效力判断；`all` 不会悄悄删除失效法规。来源元数据不修改，抽取提示不转换为引用。
- 空结果说明仅是所选库未命中，提示调整案由、筛选或库；可访问库超过四个时明确告知未覆盖其余库。
- SQL 召回上限从30提高至120，仍在既有四库范围内执行；这是小范围有界优化，尚未做大数据量负载验证。

## 固定数据与复现

输入及人工评分标准固定在 `scripts/development/fact-search-evaluation.cases.json`，覆盖借贷本金、借贷利息、劳动、买卖、租赁。评分只看返回原文与案情的关系，保留每个 top5 原文和逐项判定；不将“返回了五条”视为相关。

在同一个本地正式目标库 `ruoyi-ai-lawyer`、同一个普通用户、相同可访问库和默认筛选下，分别调用旧版6039与候选6038的真实要素抽取/检索接口。旧版 JAR 保留原样；无知识库资料导入、编辑、迁移。会话与配额为真实 API 行为，正式对比的十个新会话合计消耗十次额度；另外保留首轮候选的五个新会话结果于 `attempt1/`，该轮消耗五次额度。首轮已消除劳动混入，但仍有同领域偏题，据此补强了上述争议匹配。模型抽取可能波动，原始 `elements/query` 一并留档以供核对。

PowerShell（在仓库根目录，Node 18+；普通用户账号 JSON 包含 `username/password`，勿提交该文件）：

```powershell
$env:FACT_EVAL_ACCOUNT_FILE='本地私有普通用户账号.json'
$env:FACT_EVAL_OUTPUT_DIR=Join-Path $PWD 'scripts/development/fact-search-relevance-evidence-rerun'
$env:FACT_EVAL_BASE_URL='http://127.0.0.1:6039' # 保留的优化前版本
node scripts/development/evaluate-fact-search-live.cjs before
$env:FACT_EVAL_BASE_URL='http://127.0.0.1:6038' # 本次候选版本，同一数据源
node scripts/development/evaluate-fact-search-live.cjs after
node scripts/development/verify-fact-search-regressions.cjs
./scripts/development/Replay-FactSearchRules.ps1 -ReleaseJar '最终候选完整JAR路径' -EvidenceDir $env:FACT_EVAL_OUTPUT_DIR
```

脚本禁止覆盖已有同名阶段证据，重跑请换新目录。首次运行会建立五个会话，复用会话的回归不额外扣费。回归脚本会创建并删除自己建立的测试报告，保存实际下载的 Word。其空状态断言适用于本次没有案例号元数据的测试库；其他环境请准备明确无匹配的类型/库组合。

构建与测试（`ruoyi-ai` 目录，Java17/Maven依赖就绪）：

```powershell
mvn -o -pl ruoyi-modules/ruoyi-chat -am test '-Dtest=FactSearchRelevanceTest,FactSearchKnowledgeTest,FactSearchServiceTest,SearchReportSourcesTest,SearchReportServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false'
mvn -o -pl ruoyi-admin -am package -DskipTests
```

候选服务用 `scripts/deploy/Start-Stage4Service.ps1` 在6038启动，须先把构建 JAR 复制为独立不可覆盖的发布文件；不要在运行中覆盖目标 JAR。此前6039仍保持优化前版本，发布新版本需要沿既有部署流程执行。这份评估不等于6039已升级。

## 可复查交付

实际结果见仓库根目录 `scripts/development/fact-search-relevance-evidence/comparison.md`，同目录保留 `before/after.json`、每条完整响应、规则回放、回归和构建日志、候选 JAR 版本信息。固定五条样本只支持本次样本改善结论，不推断全库召回率。
