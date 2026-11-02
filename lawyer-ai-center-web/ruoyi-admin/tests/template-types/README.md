# 模板类型隔离 UI 验收

挂载真实模板类型页面、抽屉和 Ant 控件；仅测试外壳和 API 使用合成 fixture，不替代生产路由和鉴权。运行 `node_modules/.bin/vite.CMD --config tests/template-types/vite.config.ts`，仅监听127.0.0.1:5197。

URL参数：`scenario=list-error` 首次超时可重试；`save-error` 首存失败；`conflict` 409；`readonly` 只读；`empty` 初始空；`theme=dark` 暗色。

`node tests/template-types/verify.cjs` 运行三个桌面视口、完整任务和异常恢复。结果位于仓库根 `artifacts/template-type-20261026/`。不会写真实数据库或调用模型。
