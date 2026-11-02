# 完整 System Prompt 更新与验证

2026-10-15，通过本地后端 `http://127.0.0.1:6039` 的正常管理员接口，更新租户 `000000` 下三个既有智能体。完整用户提供的 Prompt 原文保存在 `legal-agents.json`，实际写入各自 `systemPrompt` 字段。

| ID | 智能体 | Prompt 字符数 | 建议温度 | 温度实际状态 |
| --- | --- | --- | --- | --- |
| 2 | 法律咨询助手 | 503 | 0.3 | 当前系统不支持设置 |
| 3 | 合同审查官 | 1139 | 0.25 | 当前系统不支持设置 |
| 4 | 文书起草助手 | 579 | 0.4 | 当前系统不支持设置 |

三个智能体保持启用，模型仍为实测可用的 `Qwen3.6-35B-A3B-FP8`，知识库、工具和技能绑定仍为空。

## 温度能力检查

检查了管理端智能体表单/API 类型、AgentBo/Agent/AgentVo、模型管理 ChatModelBo 及模型调用实现，当前均没有可配置的 temperature 字段。以上温度仅为建议值，尚未生效。建议值单独记录在 `prompt-smoke-cases.json` 的 recommendedTemperature 中，不作为后端更新请求的参数。

## Prompt 传递修复

初次更新后的真实 SSE 测试发现，法律咨询返回空内容、合同审查返回错误。现有 Supervisor 会将问题交给固定角色的子 Agent，并可能在 LAST 响应策略下丢弃直接结束时的回答；将专业 Prompt 保存进数据库不足以确保模型按其执行。

`ChatServiceFacade.handleAgentChat` 已增加无工具、技能及知识库绑定智能体的直接模型调用路径，沿用现有 SystemMessage、历史消息、SSE 流式响应及消息保存逻辑。现有外部能力绑定仍使用原编排。本地 IDE 调试服务已加载经测试的类更新；重新启动服务时使用编译后的当前代码即可。

## 已执行验证

- 使用 `PUT /agent/agent` 更新 ID 2、3、4，随后通过管理编辑页使用的 `GET /agent/agent/{id}` 回读，逐字比对 Prompt 内容及 SHA-256。
- 后端编译通过，执行 `ChatServiceFacadePromptTest` 和 `ChatServiceFacadeTerminalTest`：5 项测试通过，0 失败、0 错误。
- 使用三个真实 `agentId` 分别创建后端会话并调用 `/chat/send`。均返回非空内容和 done，无 error 事件。测试会话均已删除。
- 法律咨询：验证【结论摘要】【法律分析】【风险提示】【行动建议】【需要补充的信息】五部分。
- 合同审查：直接用 JSON 解析器解析完整返回正文，验证所有顶层字段、basicInfo 字段、风险项字段、风险枚举，以及严重程度从高到低排序；无额外解释文本和 Markdown 代码围栏。
- 文书起草：验证授权委托书、张三、李四、2026年10月15日、2026年11月15日均出现在正文中。

这些是配置、调用链和输出格式验证，没有对生成的法律观点进行专业质量审查。

## 复核方法

1. 管理员登录租户 `000000`，进入“智能体管理”，分别编辑三个智能体。核对完整 Prompt 与 `legal-agents.json` 一致；合同审查官的 JSON 模板应完整显示。
2. PC 用户进入 `/app-market` 选择对应智能体，创建新会话；使用 `prompt-smoke-cases.json` 中的三个 question 测试。
3. 法律咨询检查五部分结构，合同审查复制完整正文直接做 JSON 解析，文书起草检查姓名、日期与缺失信息说明。
4. 本地后端回归命令：`mvn -o -pl ruoyi-modules/ruoyi-chat -am -Dtest=ChatServiceFacadePromptTest,ChatServiceFacadeTerminalTest -Dsurefire.failIfNoSpecifiedTests=false test`。Windows 受限环境下可用 JAVA_TOOL_OPTIONS 设置工作区内 java.io.tmpdir，供 Mockito 使用。

## 本次实际读取的 Prompt 摘要

- ID 2（法律咨询助手）：SHA-256 `227099a647cc99b371dbf256eede9d4e8536156a756065059d846a02e7f9cb7f`。
- ID 3（合同审查官）：SHA-256 `1694db25e14d3a34333b4d0550d36f84da159f9b124a09689810867315ffa80f`。
- ID 4（文书起草助手）：SHA-256 `1d544d36ff5b17930b1165cb3cbfc6c1d8c229f5c190b4499d4578da06d24885`。
