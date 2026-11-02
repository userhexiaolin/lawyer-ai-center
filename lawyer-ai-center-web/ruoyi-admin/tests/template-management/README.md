# 模板管理隔离 UI 验收

该环境挂载生产 `doc-template/index.vue`、编辑抽屉、上传组件与真实 Ant Design Vue 控件，使用固定合成数据和隔离 API 替身。侧导航为测试外壳，不能当作管理端真实布局/鉴权/模型服务的端到端证明。配置只影响此目录，生产路由、请求封装和鉴权均不改。

从 `ruoyi-admin/` 运行已安装工具（不安装依赖、不改锁文件）：

```powershell
.\node_modules\.bin\vite.CMD --config tests/template-management/vite.config.ts
.\node_modules\.bin\vitest.CMD run --config vitest.template-upload.config.ts
```

默认服务只绑定 `127.0.0.1:5198`。端口占用时可追加 `--port 5199`。

| URL 参数 | 可复现状态 |
|---|---|
| 无 | 五份模板，含已确认、待核对、未解析、仅文字和长名称 |
| `?scenario=list-error` | 首次列表请求失败，重试恢复 |
| `?scenario=save-error` | 首次基础信息保存失败，保留输入，再保存成功 |
| `?scenario=empty` | 真正的初始空列表 |
| `?scenario=readonly` | 只读查询，无新增、编辑、AI、删除和批量选择 |
| `?theme=dark` | 现有管理端暗色 Token 与 Ant 暗色控件 |

手工脚本与结果见仓库根 `artifacts/template-management-20261024/`。三个视口为 1366×768、1440×900、1920×1080。截图使用 `fullPage`，故短视口截图高度可能超过视口高度；实际尺寸和整页横滚检查以 `layout.json` 为准。早期截图是调试过程，最终基线以 `final-list-*.jpg` 为准。

上传、格式转换、模型调用在该环境明确拒绝执行，避免伪装真实服务成功。真实后端联调需使用授权测试租户和合成文件另行运行。
