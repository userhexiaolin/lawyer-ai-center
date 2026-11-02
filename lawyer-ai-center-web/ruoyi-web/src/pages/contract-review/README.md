# 合同审查文件流程

## 阶段4指令43：issues 协议

2026-10-18 来源回填续验：后端从本次实际注入的检索命中按法规名+条号回填来源；最终响应与历史消息中的 lawRefs 保留 fragmentId、有效 sourceUrl/url，标记 sourceOrigin=retrieval_backfill。审查JSON在模型完成并回填后发送，普通对话仍保持流式。统一LegalCitation按详情→http(s)→友好提示处理；无来源灰显“暂无原文”。实现及验收见 `ruoyi-ai/docs/review-source-backfill.md` 和 `scripts/development/review-sources-evidence/`。

`result.ts` 优先解析 `contractType/summary/issues`；只有未提供 `issues` 时才映射旧 `risks`，混合输入中的空 `issues: []` 也具有优先级。新旧结果统一提供 `issues` 给 PC/移动 UI。旧别名保留给历史调用方；风险分析和原文定位使用 `problem/quote`。

| 旧字段 | 统一字段 |
| --- | --- |
| risks | issues |
| level / title | riskLevel / clauseTitle |
| clause / analysis | quote / problem |
| position | location.positionText |
| suggestion | suggestion |
| legalBasis | lawRefs（其中 type=case 的对象归入 caseRefs） |

支持标准驼峰和手册蛇形字段，保留完整 JSON、Markdown 围栏/前后说明提取和尾逗号清洗；截断或非法风险项仍进入文本降级，不静默丢弃。缺失的可选字段归一为空字符串、数组或 location 对象，不推测原文页码或引用。

Word `ContractReviewReport` 同样优先 issues，表格含原文摘录、建议、法规名+条号和类案。最低协议夹具为 `tests/fixtures/sample-review-issues.json`，实际组件预览为 `tests/contract-review-results.html`。连续十次实模脚本：`scripts/development/instruction43-live.cjs`（从工作区根目录运行，先 `--prepare`、启动隔离服务，再 `--run`；`--audit` 只读核对 Prompt）。原始回复、每次完整 issues、统计及失败摘要写在 `scripts/development/instruction43-evidence/`，与旧十次基线分别保存。

## 基于合同生成租金催缴函（指令39）

上传并核对合同正文后，点击工作台下方“基于本合同生成文书”，选择“租金催缴函”→提取字段→核对/修改字段→填写自行核实的欠费区间、欠款总额、付款期限和落款日期→生成→编辑预览→复制或导出UTF-8 TXT。不必先消耗一次合同审查额度。

`CollectionLetterPanel.vue` 复用上传/正文读取结果、`GET /agent/agent/agentOptions`、`POST /system/session` 和 `POST /chat/send` SSE，不新增不受鉴权保护的接口。提取使用单独CHAT会话与“文书起草助手”的管理端配置，重复相同输入重试复用会话，继承首次有效消息扣次规则。原合同审查会话不因文书提取而消费审查次数。

`collection-letter.ts` 包含固定JSON结构、强约束提取Prompt、逐字原文验证、租金催缴函模板与事实段一致性检查。管理端可配置文书起草助手的模型和systemPrompt；本轮提取用户Prompt要求固定JSON，生成完全采用本地确定性模板，不请求模型润色。未在原文中核实的字段留空提示，未载明欠款信息不推算。字段或正文改变会使旧结果失效；字段修改后重新生成；预览固定事实段被改写时阻止复制/导出。可编辑其他补充文字，须由使用者人工核对。

当前仅内置租金催缴函；识别为物业合同会阻止套用。TXT下载不是正式签署或发送。没有新增移动端完整版。测试文件 `tests/fixtures/collection-lease-complete.docx`、`collection-lease-missing.docx`，均为虚构数据。测试：`node --test tests/collection-letter.test.cjs`。

## 入口与操作

登录 PC 用户端，打开 `/contract-review`，选择测试文件，核对正文，填写可选审查重点，再点击“开始审查”。

测试文件位于 `ruoyi-web/tests/fixtures/`：`sample-contract.docx`、`sample-contract.pdf`、`sample-contract.png`、`sample-contract.jpg`，另有可直接复制的 `sample-contract.txt`。内容仅用于联调。

流程依次执行：

1. `POST /resource/oss/upload`：原生 multipart 上传，携带 Bearer token 和 clientid，显示上传进度，保存返回的 url（作为 fileUrl）与成功状态。
2. `POST /system/session`：由后端绑定当前登录用户，标题为“合同审查 - 文件名 - YYYY-MM-DD”；将数值型会话 ID 保持为字符串，避免 JavaScript 大整数精度丢失。
3. `POST /system/contract-review/content`：读取原始文件字节，返回正文。PDF 使用 PDFBox，DOCX 使用 POI；JPG/PNG 通过合同审查官绑定的模型识别原文。
4. 用户核对或修改正文，填写审查重点。
5. 从 `/agent/agent/agentOptions` 查找启用的“合同审查官”，通过现有 `/chat/send` 及 hook-fetch SSE 发送真实 sessionId、agentId、合同正文、文件地址和说明，展示流式内容和完成状态。

上传、创建会话、读取正文及审查时禁用重复操作。失败时显示提示；重试文件准备会复用已上传的文件地址和已创建会话。读取失败时可手动填写正文继续审查。离开页面会取消请求并忽略迟到回包。SSE 收到 done 且存在正文后标记回复完成，再尝试结构化解析。JSON、Markdown 围栏及带前后说明的 JSON 均支持；解析失败（包括模型截断 JSON）显示“结构化解析失败，已切换为文本模式”，并保留原文。请求错误、中断及空回复仍标记失败。

## 格式及限制

- PDF、DOCX、JPG/JPEG、PNG，单文件不超过10MB。
- PDF 不超过100页，正文不超过60000字；超出限制应拆分合同。
- 扫描 PDF 不会自动做逐页 OCR；可转换为清晰图片，或手工补充正文。
- 图片识别依赖智能体绑定模型的图片能力；本次 Qwen 模型已用真实 PNG/JPG 验证。
- 文件内容接口只读取请求中的文件字节，不抓取任意 URL，不向共享知识库写入内容。
- 结果兼容纯 JSON、完整 JSON Markdown 围栏及前后说明；缺省可选字段使用空值，未知风险等级标记“风险待确认”，不自动降为低风险。字段严重不符时降级为文本模式。
- 工作台支持审查历史分页、通过 `?sessionId=` 回看跨端结果，并重新绑定合同审查官继续追问；未完成上传的本地文件草稿不跨刷新保存。

## 部署与权限

后端增加了 `ContractReviewController`，部署时需包含该类。当前本地调试服务已加载该接口，并纳入原有登录及 clientid 检查；未登录和错误 clientid 请求均实测返回401。

普通用户需要原有会话权限及 `system:oss:upload`。本次通过现有角色管理接口给默认租户普通用户角色补充上传权限，保留原有30项菜单权限；没有授予存储配置管理权限。其他部署环境可通过管理端授权，或应用 `ruoyi-ai/docs/script/sql/contract-review-upload-permission.sql`，之后重新登录以刷新权限缓存。

登录后返回合同工作台时，模型选择组件会跳过聊天页的后台模型请求，避免无关的模型管理权限403打断工作台导航。

## 当前本地存储

已启动本地 MinIO，项目默认 OSS 为 minio，endpoint 为 127.0.0.1:9000，桶为私有 ruoyi，使用应用专用账号。真实 DOCX、PDF、PNG、JPG 上传及签名下载校验通过，普通用户使用 PC clientid 上传也通过；此前示例桶引起的 NoSuchBucket 已解除。启动和管理说明见项目 scripts/minio/README.md。

## 已完成检查

- 前端流程及结构化解析测试26项通过：上传失败不创建会话、创建失败重试不重复上传、重复提交、手动正文恢复、长ID精度、离开页面取消、SSE错误/中断/空完成、完整 event/data 帧、CRLF及多帧消费，以及 done 后 JSON 截断的文本模式降级、括号/引号转义、风险排序、缺省字段与错误结构。
- 后端测试9项通过：真实DOCX/PDF解析、非法文件拒绝、图片字节传递、System Message路由和流式结束处理。
- 真实内容接口：DOCX、PDF、PNG、JPG均返回测试合同正文，均保留3000金额。
- 真实会话及后端 SSE：已创建会话，合同审查官返回多个内容事件和 done；独立接口测试会话已清理。
- 普通用户角色实测具有上传权限，不具备存储配置编辑权限；未登录及 clientid 不匹配请求被拒绝。
- 早期浏览器测试记录（MinIO 启用前）：显示错误和重试按钮，不继续创建会话。为隔离验证后续 UI 流程，仅模拟了 OSS 成功回包，后续会话、正文、模型请求使用真实后端。此模拟仅存在于独立测试浏览器，没有进入应用源码，也不代表真实上传成功。精简为单项风险的真实模型审查返回完整 JSON，页面显示“审查完成”并展示1张风险卡片；测试账号及其3个会话已通过原有接口清理。

- 前端类型检查及生产构建通过。

## 人工验收步骤

1. 确认本地 MinIO 正在运行，普通用户登录。
2. 上传 sample-contract.docx：观察上传中、进度、上传成功、创建会话、读取正文和待审查状态；网络面板确认上传带 Authorization 和 clientid。
3. 核对正文金额3000及日期，输入“重点审查违约责任和管辖条款”，点击开始审查。
4. 确认发送期间按钮禁用，`/chat/send` 携带启用智能体 ID 和后端创建的 sessionId，右侧逐步出现内容，收到 done 后显示完成。可解析时显示风险徽章、排序后的折叠风险点、原文/位置/分析/建议/法律依据、缺失条款及整体建议；不可解析时显示文本模式提示与原文。
5. 分别上传 PDF、PNG、JPG 复核。尝试错误扩展名、空文件、断网或损坏文件，检查提示与重试；不要把上传失败后的页面当作成功。

前端测试命令：`node --test tests/contract-review.test.cjs tests/contract-review-result.test.cjs`。
后端测试命令：`mvn -o -pl ruoyi-modules/ruoyi-chat -am -Dtest=ContractReviewControllerTest,ChatServiceFacadePromptTest,ChatServiceFacadeTerminalTest -Dsurefire.failIfNoSpecifiedTests=false test`。


## 结构化结果手动测试

启动前端开发服务后，打开 `http://localhost:5173/tests/contract-review-results.html`（端口以 Vite 输出为准）。这是独立测试页，仅引用真实结果组件及本地模拟数据，不加入生产路由，也不调用后端。

1. 默认载入 `tests/fixtures/sample-review-result.json`，原始风险顺序为低、高、中；确认右侧排序为高、中、低，徽章分别为红、橙、蓝，摘要和基本信息显示正常。
2. 点击风险标题展开/收起，检查条款位置、原文、分析、修改建议和法律依据；点击全部展开/全部收起，检查缺失条款与整体建议。
3. 将 JSON 加上 Markdown 围栏或前后说明后点击“应用输入”，应仍结构化显示；点击“模拟流式回复”，流式中显示原文，完成后切换为结构化结果。
4. 点击“模拟文本回复”，或粘贴不完整/格式错误的 JSON 后点击“应用输入”，确认提示“结构化解析失败，已切换为文本模式”且原文完整保留。
5. 把 riskLevel 改为 medium/low，检查整体徽章颜色；清空 risks 与 missingClauses 数组检查空状态。原文含 HTML 时仍按纯文本展示。

相关代码：`result.ts`（解析、归一化、风险排序）、`ReviewResults.vue`（结果区组件）、`workflow.ts`（收集 SSE 正文并判定请求结束）。

本次浏览器验证：排序为高/中/低，实际徽章颜色为红/橙/蓝；单条展开、全部展开、详情字段、文本模式警告和模拟 SSE 完成后的结构化切换均通过。类型检查和生产构建通过。
