> 2026-10-18正式部署更新：本地原库 ruoyi-ai-lawyer 已执行阶段4与issues Prompt迁移，6039已发布当前JAR并通过普通用户冒烟。详见 [部署清单](../stage4-deployment.md) 与 web/scripts/development/stage4-deploy-evidence/deploy-smoke.md。以下未发布说明保留为历史记录；远程生产未执行。

# 法律专业智能体预置记录

2026-10-18 指令43：合同审查官当前源码协议已升级为 `contractType/summary/issues`。完整约束与 few-shot 见 [contract-review-schema.md](contract-review-schema.md)，配置在 `legal-agents.json`，数据库迁移为 `../script/sql/update/2026-10-18-contract-review-issues.sql`。旧 `risks` 仅作为历史输入兼容；解析后 UI 和 Word 均消费 `issues`。本次实际验收使用隔离库与 6084 服务，原 6039 服务未发布。以下为此前部署记录。

最新完整 Prompt 更新及格式测试见 [prompt-update.md](prompt-update.md)。

2026-10-15 已通过本地管理接口实际创建，后端地址 `http://127.0.0.1:6039`，租户 `000000`。未操作远程演示环境。

| ID | 名称 | 状态 |
| --- | --- | --- |
| 2 | 法律咨询助手 | 启用（status=0） |
| 3 | 合同审查官 | 启用（status=0） |
| 4 | 文书起草助手 | 启用（status=0） |

三个智能体均绑定现有 chat 模型 `Qwen3.6-35B-A3B-FP8`：

- modelId：`2110631969010016257`
- modelName：`/models/Qwen/Qwen3.6-35B-A3B-FP8`
- providerCode：`vllm`
- enableThinking：`0`
- 知识库、MCP 工具和技能绑定：空列表
- 名称、描述、当前完整 systemPrompt：见同目录 `legal-agents.json`

## 已执行验证

1. 管理员读取 `/agent/agent/queryList`，逐字段核对三个智能体的名称、描述、模型、Prompt、状态及绑定列表。
2. 读取 `/agent/agent/agentOptions`，三个智能体均在启用选项中。
3. 逐一通过 `/system/session` 创建测试会话，使用对应 `agentId` 调用 `/chat/send`，三个智能体的问候测试均返回非空 `content` 和 `done` 事件。
4. 创建临时普通用户（仅 `user` 角色、无智能体管理权限）进行验证：应用市场选项返回三个智能体；管理列表返回业务码 403；创建会话并使用智能体 4 对话成功。测试结束已删除临时会话和临时账号。
5. `ruoyi-web` 执行 `pnpm build`，TypeScript 检查及 Vite 生产构建通过。构建仍有项目既有的 KaTeX 字体路径、`:deep` 写法及大文件分包警告。
6. 检查 PC 选择链路：`/app-market` 按名称/描述筛选，选择智能体后写入 agent store 并跳转聊天页，聊天请求携带 `agentId`，后端使用智能体绑定模型。

验证使用真实 HTTP/SSE 接口；管理端和 PC 页面仍可按下面步骤进行浏览器确认。

## 页面确认步骤

1. 使用租户 `000000` 的管理员登录管理端，打开“智能体管理”。搜索上述三个名称，确认状态为启用；编辑查看绑定模型、描述及完整 Prompt。
2. 使用同租户普通用户登录 PC 端，打开 `/app-market`，选择“智能体”分类，分别搜索三个名称。
3. 确认卡片标题显示名称、副标题显示描述；点击卡片进入聊天，发送问候并确认收到回复。
4. 浏览器网络面板确认 `/agent/agent/agentOptions` 返回 ID 2、3、4；创建会话后 `/chat/send` 携带对应 `agentId` 与后端返回的 `sessionId`。

## 已知限制

- 完整专业 Prompt 已按需求写入并通过格式测试；测试范围不包含法律分析质量审核。
- 现有 DeepSeek Flash 的两条模型配置实测返回模型服务鉴权错误，因此本次选择了验证成功的 Qwen 模型，未修改全局模型配置。
- 现有后端 Supervisor 使用 LAST 响应策略，直接结束可能返回空文本。完整 Prompt 更新时已修复无工具、技能、知识库绑定智能体的调用路径，直接将配置作为 SystemMessage 传递给模型。后续添加外部能力绑定会进入既有 Supervisor 编排，需要重新验证 Prompt 传播。

## 在其他环境创建

`legal-agents.json` 是本次实际使用的配置清单，不会自动执行数据库迁移。迁移环境时先从 `/system/model/modelList` 选取实际可用的 chat 模型，替换清单中的 `modelId`，再使用对应租户管理员通过 `POST /agent/agent` 逐条创建。先按名称查询避免重复；已有正式 Prompt 时不要被占位内容覆盖。请求使用正常登录的 Authorization 和对应 clientid。
