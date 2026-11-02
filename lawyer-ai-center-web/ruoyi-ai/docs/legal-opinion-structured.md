# 法律意见书六章输出（Alpha §6.3）

PC 入口为「文书起草 → 法律意见书（六章分析）」，路由 `/legal-opinion`（`legalOpinion`）。应用市场中的「法律意见书助手」也进入该专用页面，与普通聊天区分。

## 部署与配置

1. 执行 `docs/script/sql/update/2026-10-17-legal-opinion.sql`。脚本按租户复制已启用「文书起草助手」的模型及知识库绑定，不使用硬编码模型 ID；重复执行不会覆盖管理员配置。
2. 如果某租户没有已启用的文书起草助手，脚本不会创建不可用记录；在智能体管理中创建名称精确为「法律意见书助手」的智能体，选择可用聊天模型、按需绑定知识库并启用。本次未向持久数据库写入该迁移。
3. 部署后端及 PC 前端。`AgentServiceImpl.toVo` 为此名称的智能体补入版本化系统提示词，管理员前言保留，其他助手不受影响。
4. 推荐不配置 MCP/技能，让专用助手直接生成；迁移创建的新助手默认为空工具集。已有同名助手的工具及模型配置由管理员维护。

不增加 AiJob 表。用户原始案情、分析重点、材料文字快照与回复沿用个人 `chat_session` / `chat_message` 保存；案情变化创建新会话，同一输入的结构补全在原会话进行。

## 输入与固定输出

案情必填，限30000字；分析重点选填，限2000字；材料文字限30000字。支持选择 PDF、DOCX、TXT、MD，文件非空且不超过10MB。PDF/DOCX 复用已有正文读取接口；TXT/MD 在浏览器读取。页面展示可编辑的材料正文供核对，实际送给助手及会话保存的是文字快照，原文件不另行归档。扫描 PDF 无可提取正文时提示粘贴文字。

JSON 契约如下，`content` 为各章实际正文，可包含 Markdown 子标题和列表，`citations` 复用指令42的元数据对象：

```json
{
  "title": "法律意见书",
  "sections": [
    { "key": "timeline", "content": "已知日期及事件；陈述、材料记载、待证事实分别说明", "citations": [] },
    { "key": "issues", "content": "争议焦点及缺失信息", "citations": [] },
    { "key": "analysis", "content": "按焦点：依据 → 事实匹配 → 结论", "citations": [] },
    { "key": "risks", "content": "证据、程序、时效、执行与可行性", "citations": [] },
    { "key": "strategy", "content": "行动步骤与对方视角", "citations": [] },
    { "key": "references", "content": "知识库暂无匹配，建议进一步检索", "citations": [] }
  ]
}
```

实际展示及导出标题统一为：1. 案件事实时间线、2. 争议焦点、3. 法律分析、4. 风险与可行性、5. 策略建议、6. 引用清单。前后端都验证六个 key 唯一、正文非空，按固定顺序重排，并汇总各章引用到第六章。拒绝截断 JSON、重复/缺失章节、无编号引用及危险来源协议。页面等待流式 `done` 后验证；结构失败自动补全一次，仍失败保留可展开原始结果并禁止导出。断流不视作完成。

引用标签复用 `LegalCitation`：法规名+条号或案号、失效/修改标记、「详情 → 外链 → 暂无原文」跳转与加入检索报告。没有依据时提示进一步检索，引用数组为空，不从正文推断案号。Word 使用 POI，包含六章正文、日期、元数据、时效提示及原文超链接；Markdown 与复制内容保留可读引用及来源。

## API 与归属

- 生成沿用 `/chat/send` 和现有个人会话创建接口。
- `GET /system/session/list?opinionOnly=true&pageNum=1&pageSize=10`：按会话 ID 倒序返回当前用户意见书历史。
- `GET /legal/opinion/{sessionId}`：先校验当前用户归属和意见书会话前缀，再用服务器用户 ID 和会话 ID 读取消息；返回原案情输入及最新回复，不把自动补全指令当案情。
- `POST /legal/opinion/export`：`{"sessionId":"...","content":"完整JSON快照","format":"docx"}`（或 `md`）。校验归属、意见书会话及六章结构后导出当前展示快照，返回附件字节与 `Cache-Control: no-store`；不抓取外链。其他人的会话不可读/导出。

前端在账号切换或组件销毁时取消生成/材料请求并清理输入、结果及历史。下载还会核对当前账号和 Word ZIP 文件头，避免把 HTTP200 的业务错误 JSON 存成 Word。修改输入后旧结果不能复制或导出。

## 两个测试案情

均为虚构数据，非客户材料。

**样例1：设备买卖未交付**

甲公司称：2026年1月8日与乙公司签订设备买卖合同，价款30000元，已支付全部价款，约定2026年2月8日交付。乙公司截至2026年3月1日未交付，口头称上游缺货。甲公司保存合同、转账记录及催货微信，合同违约责任和解除条款尚未提供。希望优先追回货款。

分析重点：履行、解除与返还的选择；证据缺口与协商步骤。

**样例2：设计人员用工关系争议**

王某称：2026年3月1日进入某公司从事设计工作，月报酬8000元；公司于2026年5月20日在微信通知其不必再来。王某称尚未签订书面劳动合同，公司称其是项目外包。现有三个月银行流水、部分考勤截图和工作群记录，工资发放方与用工主体是否一致尚不明确，离职补偿未协商。

分析重点：劳动关系认定、报酬核对与程序选择，避免预设关系已成立。

实模测试使用专用六章提示词和以上输入，不接入知识库，用于检查缺少依据时的降级和禁止编造案号。原始 JSON、解析后 Markdown、运行记录放在 `docs/samples/legal-opinion/`；UI 隔离验收使用明确标注的模拟流和失效测试法规，不代表真实知识库命中。

2026-10-17 使用项目配置的内网 `/models/Qwen/Qwen3.6-35B-A3B-FP8` 实测，两次均直接输出完整 JSON，无需结构补全。样例1回复2378字，样例2回复2635字。实际结构如下：

| 章节 | 样例1输出内容 | 样例2输出内容 |
| --- | --- | --- |
| 案件事实时间线 | 订约、付款主张、约定交付、截至3月1日未交付与待证事实 | 入职主张、工作记录、5月20日微信通知与主体不明确事项 |
| 争议焦点 | 解除及返还、违约责任、证据充分性 | 劳动/外包关系、发薪与用工主体、程序选择 |
| 法律分析 | 按焦点列依据、事实匹配、待核实结论 | 按关系认定、主体、程序列依据、事实匹配、待核实结论 |
| 风险与可行性 | 合同条款、付款及催告证据、履行和回收风险 | 用工管理、主体和资料完整性等风险 |
| 策略建议 | 补齐合同与付款材料、沟通路径；包含对方视角 | 整理用工证据、核对主体；包含对方视角 |
| 引用清单 | 知识库暂无匹配，建议进一步检索；`citations: []` | 知识库暂无匹配，建议进一步检索；`citations: []` |

这是结构、来源降级和导出保真的技术验证，没有对上述实模文本的法律判断作正确性背书。使用者仍需核对事实、适用依据及程序意见。`validation.json` 记录 PC 解析校验，`word-validation.json` 记录生产后端解析/导出再打开校验；两者记录原始输出 SHA-256，可确认导出来自同一回复。

## 验证步骤

1. 部署并配置助手后，用普通用户从文书起草入口打开专用页。粘贴任一样例和分析重点，另用非空 TXT/DOCX 验证可选材料读取与文字核对。
2. 点击生成，确认生成中不能重复提交；完成后出现全部六章，第三章按焦点给出依据/事实匹配/结论，第五章含对方视角。空库时引用清单提示进一步检索，不出现编造案号。
3. 复制全文、导出 Word/Markdown，打开文件核对六个标题与日期、金额一致。对有元数据的条目检查名称编号、失效标记及来源链接。
4. 刷新页面，从我的历史打开，确认原案情和六章结果恢复。修改分析重点后确认旧结果导出禁用。
5. 换用户请求旧 `sessionId` 的详情和导出，确认拒绝访问。模拟断流、额度错误或重复缺章，确认报错且导出禁用。

自动化命令（在各自项目目录执行）：

```text
# ruoyi-web
node --test tests/legal-opinion.test.cjs tests/legal-citation.test.cjs
node_modules/.bin/vue-tsc -p tsconfig.app.json --noEmit
node node_modules/vite/bin/vite.js build

# ruoyi-ai（本地依赖已缓存，可使用 -o）
mvn -o -pl ruoyi-modules/ruoyi-chat -am test -Dtest=LegalOpinionDocumentTest,LegalOpinionControllerTest,LegalReferenceTest,ChatServiceFacadePromptTest -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false -DskipTests=false
```

UI 隔离验收入口 `/tests/fixtures/legal-opinion-preview.html` 仅用于 Vite 开发服务，加载真实页面组件、模拟 SSE 和个人历史。已检查生成进度、六章、复制、失效标记、输入变化禁用导出、刷新恢复；未通过浏览器原生下载/上传对话框做真实服务端文件联调，导出格式与归属由后端 POI/接口单元测试覆盖。

前端测试18项通过；后端初次12项通过，补充最新回复恢复及成功导出用例后7项通过（含重复运行，合计13个不同后端用例）。完整 `vue-tsc -p tsconfig.app.json --noEmit` 和 Vite 生产构建通过（构建18分22秒；日志有既有 KaTeX 字体路径、`:deep` 和大分包提示）。实模2份原始回复通过同一 PC 解析器，并通过生产后端 Word 生成器重新打开检查；PC/Word 校验记录中的源文件哈希一致。

实模脚本仅用于本项目内网服务，运行前需明确授权；Windows Java 源文件执行必须传入 `'-Dfile.encoding=UTF-8'`，脚本会拒绝其他默认编码，防止中文测试案情失真。脚本不输出密钥、不修改数据库。

## 改动文件

后端：
- `ruoyi-modules/ruoyi-chat/src/main/resources/prompts/legal-opinion-system.txt`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/legal/LegalOpinionDocument.java`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/controller/legal/LegalOpinionController.java`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/agent/impl/AgentServiceImpl.java`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/impl/ChatServiceFacade.java`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/domain/vo/knowledge/LegalReference.java`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/domain/bo/chat/ChatSessionBo.java`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/impl/ChatSessionServiceImpl.java`
- `docs/agent/legal-agents.json`、`docs/script/sql/update/2026-10-17-legal-opinion.sql`
- `ruoyi-modules/ruoyi-chat/src/test/java/org/ruoyi/service/legal/LegalOpinionDocumentTest.java`
- `ruoyi-modules/ruoyi-chat/src/test/java/org/ruoyi/controller/legal/LegalOpinionControllerTest.java`

PC（相对 `ruoyi-web`）：
- `src/pages/legal-opinion/index.vue`、`document.ts`、`workflow.ts`
- `src/pages/drafting/index.vue`、`src/pages/app-market/useApplicationMarket.ts`
- `src/routers/modules/staticRouter.ts`、`src/layouts/components/Header/index.vue`
- `tests/legal-opinion.test.cjs`、`tests/fixtures/legal-opinion-preview.{html,ts,vue}`

辅助：`scripts/development/LegalOpinionModelSmoke.java`（只读配置、指定内网模型的显式实模验收脚本）、`LegalOpinionExportSmoke.java`、`verify-legal-opinion-output.cjs`、本说明及 `docs/samples/legal-opinion/` 测试产物。
