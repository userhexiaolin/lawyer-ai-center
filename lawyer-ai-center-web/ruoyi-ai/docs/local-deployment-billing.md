# 本地部署计费模式

默认 `lawyer.billing.enabled=false`（环境变量 `LAWYER_BILLING_ENABLED=false`）。此开关同时关闭免费次数校验、已购权益扣减、用量账和购买功能。聊天 SSE / WebSocket、合同审查、文书、案情检索共用该开关；会话归属验证仍然有效。无需为新用户充值或制造“无限额度”。

PC 和移动端显示本地部署说明；历史支付实现保存在 OnlineBilling.vue / online-billing.vue，普通路由不会加载。数据表、订单历史保留。未来上线 SaaS 需专门恢复在线入口并验收支付，不能只修改开关即对外收费。

配置修改后重启后端，并重新构建发布 PC 和移动端。零额度验收应比较请求前后的 lawyer_daily_usage、lawyer_billing_ledger 和 chat_session.usage_started，关闭时均不因处理消息新增扣次。
