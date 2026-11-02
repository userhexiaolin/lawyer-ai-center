# 检索报告后端 MVP（Alpha §3.4、§15.1 / R3～R6）

## 部署

1. 执行 `docs/script/sql/update/2026-10-17-legal-search-report.sql`，新增 `legal_search_report` 和 `legal_search_report_item` 两张表。
2. 编译并部署 `ruoyi-chat` 所在的后端应用。无需前端变更或外部裁判文书库。
3. 沿用当前 OSS/MinIO 配置及 `sys_oss` 记录方式。生成 Word 使用现有 POI 依赖；下载需要登录。

知识库片段捕获沿用上一轮引用元数据结构；若尚未执行 `docs/script/sql/update/2026-10-17-legal-citation-source.sql`，应先执行该迁移（其中的 ALTER 仅执行一次）。

迁移使用 `CREATE TABLE IF NOT EXISTS`，不更改现有业务表。`tenant_id` 在 SQL 中显式参与个人归属检查，管理员也不能通过这些接口访问其他用户报告。删除报告为 `del_flag=2`，条目随报告失去可访问性。旧版生成文件遵循现有 OSS 保留策略，不在编辑或逻辑删除报告时自动清理历史文件。

## API 与返回值

所有 JSON 接口沿用 `{code,msg,data}`；分页沿用 `{code,msg,rows,total}`。业务拒绝通常使用响应体的 `code`，不应只凭 HTTP 状态码判断成功。长整型 ID 返回字符串，客户端应保留字符串，避免 JavaScript 精度丢失。

| 方法 | 路径 | 请求 / 返回 |
|---|---|---|
| POST | `/legal/report` | `{title}` → `data.id` 等报告字段 |
| GET | `/legal/report/list?pageNum=1&pageSize=20` | 仅当前用户报告，分页大小 1～100 |
| GET | `/legal/report/{id}` | `data.report` + `data.items`，条目按 `sortOrder,id` 排序 |
| PUT | `/legal/report/{id}` | `{title?,background?,conclusion?}`；null/缺省保留，空背景或结论可清空 |
| DELETE | `/legal/report/{id}` | 逻辑删除 |
| POST | `/legal/report/{id}/items` | 下述条目输入 → `data.item` + `data.duplicate` |
| PUT | `/legal/report/{id}/items/order` | `{itemIds:[...]}`，必须是全部当前条目的无重复排列 |
| DELETE | `/legal/report/{id}/items/{itemId}` | 仅删除该报告自己的条目 |
| POST | `/legal/report/{id}/generate` | `data:{reportId,status:"generated",fileUrl:"/legal/report/{id}/download"}` |
| GET | `/legal/report/{id}/download` | 归属检查后，经现有 OSS 下载 Word 附件 |

`fileUrl` 是相对 API 路径，下载时仍须携带 Bearer token 和当前客户端 `clientid`。不返回可绕过报告归属检查的裸 OSS 地址。

所有 ID 路径接口先校验报告 `tenant_id/user_id/del_flag`，失败返回业务码 404；没有“知道 reportId 即可访问”的例外。排序也拒绝其他报告条目和缺失/重复条目，整个操作回滚。

## 条目与快照

```json
{
  "itemType": "law",
  "refId": "可选来源标识",
  "sectionKey": "可选段落标识",
  "snapshotTitle": "测试法规第十二条",
  "snapshotContent": "用户粘贴文本或对话/审查引用的正文快照",
  "lawName": "测试法规",
  "articleNo": "第12条",
  "lawStatus": "repealed",
  "sourceUrl": "https://example.org/law"
}
```

- `itemType`：`case_full/case_section/law/viewpoint/custom_text/chart`。
- 普通快照必须提供 `snapshotTitle/snapshotContent`。`refId` 是来源标识，不触发对任意会话、案件或 URL 的读取；站内 ID、对话引用等可随快照一并保存。
- 知识库片段输入可改为 `{"itemType":"law","fragmentId":"实际 fid 或旧片段数据库 id","sectionKey":"第十二条"}`。添加时校验知识库权限，再从原文和元数据生成快照；该模式中的客户端正文、法规身份及状态不能覆盖知识库数据。
- `law` 的 `lawName/articleNo` 都是可选元数据。两者具备时保存 SHA-256 去重键，并有数据库唯一索引；缺少任意一项时保留已知字段，不猜测缺项、不自动去重。同一报告同法同条重复添加返回原条目和 `duplicate:true`，保留首条快照，不覆盖其状态。
- 去重规范化包括书名号、空白、全角字符以及数字条号的“第/条”包装，例如 `《测试法规》+第１２条` 与 `测试法规+12` 去重。中文数字与阿拉伯数字间不做语义转换。无名称/条号时不猜测法条身份，不自动去重。
- `lawStatus` 使用 `effective/amended/repealed/unknown`；法规缺省为 `unknown`，其他类型为空。
- 每份最多 200 条，每条标题最多 500 字、正文最多 100000 字，报告条目正文合计最多 1000000 字。报告标题最多 200 字，背景和结论分别最多 50000 字。为保证合法 DOCX，拒绝 XML 不支持的控制字符。
- `chart` 在本期按文字快照导出，不抓取图片、运行图表脚本或调用外部检索服务。

Word 包含标题与北京时间封面、背景、排序后的全部快照、结论；法规列出时效代码和中文含义，失效/修改/未知状态有醒目标记。添加后的知识库原文或引用来源即使变动，生成仍使用保存的快照。仅在添加知识库条目时读取知识库，生成不重新检索、不调用模型。

新增条目、删除条目、实际修改正文/标题或排序后报告回到 `draft`，下载旧生成结果返回业务码 409，需重新生成。重复添加法规和完全相同的编辑/排序不会使报告失效。

Word、临时文件、上传或持久化失败均有明确业务错误；原有草稿和条目保留。若上传成功而报告事务回滚，会补偿删除本次新上传文件；临时文件在生成结束后清理。所有写操作以报告行锁串行化，生成只在上传与状态保存成功后变为 `generated`。

## curl 验证

使用 Bash/Git Bash，准备两个不同用户的有效 token；所有 ID 均使用 JSON 字符串。

```bash
export SEARCH_REPORT_BASE='http://127.0.0.1:6040'
export SEARCH_REPORT_CLIENT_ID='登录所用的clientid'
export SEARCH_REPORT_TOKEN_A='用户A的token'
export SEARCH_REPORT_TOKEN_B='用户B的token'
bash scripts/development/verify-search-report.curl.sh
```

脚本依次创建、修改、添加法规/类案/自定义文本、验证法规去重、全量排序、删除条目、生成和下载 `search-report.docx`，然后用 B 的 token 检查 A 的详情、编辑、删除、条目增删排序、生成和下载均被拒绝。默认保留本次创建的报告；设置 `SEARCH_REPORT_CLEANUP=1` 可在验证结束后逻辑删除本次创建的报告。脚本需要 `curl/jq`，并检查下载文件的 ZIP 标识；有 `unzip` 时进一步验证 DOCX 压缩包。

单步示例（把占位 ID 替换为创建接口返回的字符串）：

```bash
curl -sS "$SEARCH_REPORT_BASE/legal/report" \
  -H "Authorization: Bearer $SEARCH_REPORT_TOKEN_A" -H "clientid: $SEARCH_REPORT_CLIENT_ID" \
  -H 'Content-Type: application/json' -d '{"title":"我的检索报告"}'

curl -sS "$SEARCH_REPORT_BASE/legal/report/REPORT_ID/items" \
  -H "Authorization: Bearer $SEARCH_REPORT_TOKEN_A" -H "clientid: $SEARCH_REPORT_CLIENT_ID" \
  -H 'Content-Type: application/json' \
  -d '{"itemType":"law","fragmentId":"可访问的知识库fid"}'

curl -sS -X PUT "$SEARCH_REPORT_BASE/legal/report/REPORT_ID/items/order" \
  -H "Authorization: Bearer $SEARCH_REPORT_TOKEN_A" -H "clientid: $SEARCH_REPORT_CLIENT_ID" \
  -H 'Content-Type: application/json' -d '{"itemIds":["ITEM_ID_2","ITEM_ID_1"]}'

curl -sS -X POST "$SEARCH_REPORT_BASE/legal/report/REPORT_ID/generate" \
  -H "Authorization: Bearer $SEARCH_REPORT_TOKEN_A" -H "clientid: $SEARCH_REPORT_CLIENT_ID"

curl -sS "$SEARCH_REPORT_BASE/legal/report/REPORT_ID/download" \
  -H "Authorization: Bearer $SEARCH_REPORT_TOKEN_A" -H "clientid: $SEARCH_REPORT_CLIENT_ID" -o report.docx
```

空报告生成返回业务码 400。上传不可用返回 503 且 `msg` 明确说明文件存储不可用，重新 GET 确认草稿与条目仍在。报告不存在、已逻辑删除或属于其他用户时返回 404。

## Postman

可导入 `docs/postman/legal-search-report.postman_collection.json`，填写 `baseUrl/clientId/tokenA/tokenB`。创建及添加请求会自动保存 `reportId/lawItemId/caseItemId/tempItemId`，随后执行排序、删除、生成及跨用户访问断言。“下载 Word”使用 Send and Download 保存附件；最后的逻辑删除请求按需执行。

## 自动测试

后端针对性测试覆盖来源快照、规范化去重、全量排序校验、所有 ID 操作的归属拒绝、上传错误的可读提示、Word 顺序与状态、生成失败回滚、补偿清理以及下载。

```text
mvn -o -pl ruoyi-modules/ruoyi-chat -am test
  -Dtest=SearchReportSourcesTest,SearchReportServiceTest,SearchReportFilesTest,SearchReportControllerTest,SearchReportMysqlTest,LegalCitationControllerTest,LegalReferenceTest,LegalCitationRetrieverTest,ContractReviewReportTest
  -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false -DskipTests=false
  -Dsearch.report.mysql=true -Dsearch.report.workspace=<当前仓库绝对路径>
```

命令在 `ruoyi-ai` 目录运行，上述换行为展示，执行时合为一行。MySQL 测试默认跳过，仅显式启用后从本地开发配置读取连接信息；每个测试使用独立连接上的临时表，关闭连接即释放，不读写同名持久业务表。OSS 使用 mock，生成与解析是真实 POI，DAO 与事务回滚使用真实 MySQL。

## 改动文件

- `src/main/java/org/ruoyi/controller/legal/SearchReportController.java`
- `src/main/java/org/ruoyi/domain/bo/legal/SearchReportInput.java`
- `src/main/java/org/ruoyi/domain/vo/legal/SearchReportView.java`
- `src/main/java/org/ruoyi/service/legal/SearchReportRepository.java`
- `src/main/java/org/ruoyi/service/legal/SearchReportSources.java`
- `src/main/java/org/ruoyi/service/legal/SearchReportService.java`
- `src/main/java/org/ruoyi/service/legal/SearchReportWord.java`
- `src/main/java/org/ruoyi/service/legal/SearchReportFiles.java`
- `src/main/java/org/ruoyi/service/knowledge/LegalCitationService.java` 及原 `LegalCitationController.java`，共用受保护片段读取。
- 测试：`SearchReportSourcesTest`、`SearchReportServiceTest`、`SearchReportFilesTest`、`SearchReportControllerTest`、`SearchReportMysqlTest`，并调整原 `LegalCitationControllerTest` 的构造方式。
- SQL、本文档、Postman 集合及仓库根目录 `scripts/development/verify-search-report.curl.sh`。

Java 路径均相对于 `ruoyi-modules/ruoyi-chat/`。测试日志保存在仓库根目录 `scripts/development/search-report-backend-tests.log`。

## 本次验证结果

- 后端编译及 29 项针对性/回归测试全部通过，失败 0、错误 0、跳过 0；其中包括 4 项真实 MySQL 临时表测试。
- 验证了真实 SQL 的创建、去重、全量排序、删除、逻辑删除、跨用户/跨租户拒绝，以及 POI 导出的快照正文、顺序、法条状态与下载字节。
- 上传失败、结果持久化失败的草稿保留、事务回滚和新文件补偿清理通过测试。
- curl 脚本通过 Bash 语法检查；Postman 集合的 22 个请求体和测试脚本通过语法校验。
- OSS 测试使用 mock；真实 OSS 上传下载及 HTTP 端到端验证须在执行迁移、部署后端后运行所附示例。持久业务表尚未应用本次迁移。
