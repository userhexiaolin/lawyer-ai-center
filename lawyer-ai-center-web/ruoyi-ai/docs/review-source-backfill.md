> 2026-10-18部署更新：来源回填代码已随本地6039主后端发布，原库新增来源列已就绪；普通用户检索引用与真实fragment详情冒烟通过。详见 [阶段4部署记录入口](stage4-deployment.md)。此前隔离环境的实模证据保持原样，本次未重跑审查十次统计。

# 合同审查引用来源回填（2026-10-18）

来源以**后处理回填**为主，不依赖模型复制或猜测 URL/片段 ID。

1. `CustomVectorRetriever` 将权限检查后的命中元数据放入模型上下文，同时保留 law_name、article_no、status、source_url、fragmentId。即使只有 `reference` VO、没有单独 `legalMetadata`，也保留法规名及条号。
2. `ChatServiceFacade` 从本次 `AugmentationResult.contents()` 中保留实际注入的命中，作为请求内引用表。不会再检索一次，不从模型正文提取来源，不使用跨会话缓存。
3. `ContractReviewSources` 按法规名＋条号对齐。支持《》、中华人民共和国前缀及中文/阿拉伯数字条号；不按相似法规名或仅条号模糊匹配。多个同条命中沿用上下文顺序，优先具有可用来源的命中。
4. 原始 `issues/lawRefs`、蛇形字段及旧 `risks/legalBasis` 统一为 issues。已匹配引用从命中回填 fragmentId、sourceUrl/url、标准名称/条号和状态，标记 `sourceOrigin: retrieval_backfill`。模型给出的来源字段先清除；未匹配引用只保留名称/条号，来源为空、状态待核实。不会凭法条名合成 URL。类案只按实际命中的案号对齐。
5. 模型完成后，通过现有 content 事件发出最终 JSON，历史消息保存同一份回填后的内容。审查等待期间仍显示生成状态；普通对话仍沿用原流式行为。截断/错误输出保留既有文本降级和错误记录，不将其伪装成完整审查。
6. PC 审查继续复用 `LegalCitation.vue`，优先详情，其次 http(s) 外链，最后“暂无原文”提示。无来源引用明确灰显并标注“暂无原文”；`url` 别名可直接被统一组件识别。移动端同步统一引用工具的别名兼容。

自动化：`ContractReviewSourcesTest.backfillsMissingSourceIntoLawRefsVoFromMockHits` 明确断言 mock 命中时 lawRefs 带有 fragmentId。另有未命中去除伪来源、错法/错条不误配、旧协议映射、无来源、危险 URL、流式终态及 facade 实际保存 VO 的回归测试。

真实验收脚本：`scripts/development/review-sources-live.cjs`，使用保留的 instruction43 隔离数据库、普通测试账号、实际 `/chat/send` 模型和公共知识库。新 JAR 启动脚本为 `Start-ReviewSourcesValidation.ps1`；PC 实际组件预览为 `/tests/fixtures/review-sources-live.html`，预览服务需设置 `VITE_API_URL=http://127.0.0.1:6084`。同源测试网关仅向本机5198预览提供内存中的临时账号令牌，不落盘。

完整响应、实际原文详情、检索回归及浏览器点击证据保存于 `scripts/development/review-sources-evidence/`。该目录的最终验收记录说明实际来源与通过情况。原阶段4基线与指令43十次统计保持历史记录，不据此倒推旧版本 K2 已通过。现有6039服务不由这些验收脚本部署。
