# 个人版：持久化站内提醒

提醒复用现有 `sys_notice` 通知公告表，不建立平行消息表，不通过对话消息伪装提醒。原 `/lawyer/case/upcoming` 日历查询保持可用，通知页面读取已经入库的通知；页面刷新不会重新生成通知。

## 触发与去重

- 正式默认：应用启动30秒后首次执行，此后每次扫描完成后间隔5分钟（`Asia/Shanghai`）。使用 Spring 定时任务，不依赖浏览器在线或 SnailJob 服务。
- 扫描真实字段 `lawyer_case.next_hearing_date`（开庭 / 关键日期）、`limitation_date`（诉讼时效）和 `lawyer_case_task.due_at`。当前工程没有单独的 `important_dates` 列，本次沿用正式案件模型。
- 范围是今天至第7天（含边界）；仅进行中、未删除的案件，且收件人账号有效、未删除。任务仅 `todo/doing`，并通过租户、用户与案件三项关联校验。今天已过的任务时间也补一次提醒，覆盖当天短暂停机；更早的历史日期不补发。
- 开庭和时效按 `case-date:案件ID:日期` 去重，同一案件同一天合并成一条（正文同时列出两种日期）。每天重扫不重复发；改到不同日期会产生新提醒，旧通知作为历史保留。
- 任务按 `case-task:任务ID:截止年月日时分秒` 去重；同一任务相同截止时间只发一次，改期后产生新提醒。不同任务各有一条，正文包含任务标题与时间。
- 数据库唯一索引 `(tenant_id, recipient_user_id, reminder_key)` 保证多实例或同时扫描也不刷屏。重复发送不覆盖正文、已读状态或创建时间。改回曾提醒过的同一日期/截止时间不再新发。
- 正文使用固定日期时间，避免历史通知里的“明日”随着时间变化而误导。提前一周生成后，本阶段不会每日重复催告。

配置可通过环境变量覆盖：`LAWYER_REMINDERS_ENABLED`、`LAWYER_REMINDERS_DELAY_MS`、`LAWYER_REMINDERS_INITIAL_DELAY_MS`。本次候选验收使用1秒间隔/1秒首次延迟以复现多轮扫描；主服务使用上述默认值。

## 数据与访问

迁移文件：`docs/script/sql/update/2026-10-18-personal-reminders.sql`，在 case-tasks 之后执行，加入收件人、业务键、关联案件、已读时间和两个索引。每个 ALTER 都先检查当前列/索引，支持中断后继续执行。已有公告的收件人为 NULL。

`SysNoticeServiceImpl` 的公告列表、详情、修改、删除均限制 `recipient_user_id IS NULL`。个人提醒不调用公告发布接口，也不调用 `publishAll`，因此不会广播案件信息。

- `GET /system/notice/inbox?limit=20&beforeId=...`：仅返回登录用户当前租户的个人通知，游标分页最多50条，附未读总数。
- `PUT /system/notice/inbox/{id}/read`：仅当前收件人可标为已读，重复提交幂等；其他用户/租户返回404。
- 不接受请求中的收件人或租户作为查询依据，全部取自登录态。PC `/notifications` 列表和头部/侧栏的“通知”入口复用这些接口；通知与已读状态均从服务器恢复，切换账号立即清空页面中的个人数据。
- 通知是历史快照。案件后来删除、结案或任务完成，不删除已发送通知；关联案件不可用时由原案件权限接口提示。未来未发提醒则由扫描条件排除。

## 迁移与发布

已把新 SQL 纳入 `scripts/deploy/Stage4Migration.java` 的统一迁移顺序、结构校验和备份/演练流程，不修改历史已执行 SQL 的校验和。使用既有 `stage4-deploy.cjs`，依次 `backup --local`、`rehearse --local`、`migrate --local`。环境变量与备份原则沿用 `docs/stage4-deployment.md`（以仓库实际部署文档及 runner 参数为准）。

先迁移，再发布包含新收件人隔离条件的后端；部署所有实例后开启扫描。回滚先设置 `LAWYER_REMINDERS_ENABLED=false` 并停新扫描任务，保留新增列/索引和历史数据；涉及个人消息时不要直接切回未加个人隔离条件的旧公告管理代码。无需把演练库的任何业务数据迁回原库。

## 可复现验收

仓库根目录，私有账号文件不提交：

```powershell
$env:REMINDER_USER_A='普通用户A的私有账号JSON路径'
$env:REMINDER_USER_B='普通用户B的私有账号JSON路径'
$env:REMINDER_EVIDENCE_DIR=Join-Path $PWD 'scripts/development/personal-reminders-evidence-rerun'
$env:REMINDER_BASE_URL='http://127.0.0.1:6039'
node scripts/development/verify-personal-reminders.cjs seed
# 等待后台扫描；使用候选加速配置时只需数秒，默认最多约5分钟。
node scripts/development/verify-personal-reminders.cjs verify
node scripts/development/render-personal-reminders.cjs smoke-6039.json
```

脚本建立两位普通用户的明日开庭及同日时效、待办任务，并验证已结案/已完成不通知、其他用户不可见不可标读、刷新和已读持久化、多次扫描相同ID保持。不会调用 upcoming 或临时扫描接口；合成案件和通知保留供复查。默认脚本等待约48秒，正式默认5分钟间隔时先等待一轮或设置候选加速配置。

后端测试包含通知 MySQL 临时表的持久化、用户/租户隔离、去重、游标分页，以及公告接口不能访问个人通知；另有扫描边界、同日合并和原任务回归。临时表仅在测试连接中存在，不向真实通知表插入测试数据。

```powershell
# ruoyi-ai 目录；workspace 参数为仓库根目录绝对路径
mvn -o -pl ruoyi-admin -am package '-Dtest=CaseReminderTest,CaseReminderDeliveryTest,PersonalNoticeMysqlTest,NoticeAnnouncementPrivacyTest,CaseTaskMysqlTest,CaseTaskControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dpersonal.notice.mysql=true' '-Dpersonal.notice.workspace=仓库根目录' '-Dcase.tasks.mysql=true' '-Dcase.tasks.workspace=仓库根目录'
# 仓库根目录
node --test ruoyi-web/tests/personal-notifications.test.cjs ruoyi-web/tests/case-tasks.test.cjs
```

当前 Work 设置禁用本地浏览器，浏览器工具返回 `Local browser use is disabled in Work settings`。实际验收记录在 `scripts/development/personal-reminders-evidence/`：真实 API、后台日志、模板 SSR 渲染证据、类型检查与测试；SSR 不当作浏览器截图或纯 UI 验收。App 推送、短信、本机系统推送均未实现。
