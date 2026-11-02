# 个人案件：任务、检查项与材料归档

沿用现有 `lawyer_case`、`CaseAccess`、`lawyer_case_file`、会话关联及关键日期提醒。PC 入口仍是「我的案件」 `/my-cases?id={caseId}`；详情新增「任务与检查项」「材料」Tab。移动端仍是 `/pages/cases/index?id={caseId}`，新增只读任务进度、工时和材料入口。

## 部署

1. 在已部署原个人案件 SQL 的数据库上执行 `docs/script/sql/update/2026-10-17-case-tasks.sql`。
2. 部署后端与 PC 资源；移动端本次源码同时位于当前工作区的 `scripts/development/mobile` 和现有 `lawyer-ai-center-mobile/ruoyi-uniapp` 项目。
3. 继续使用现有 OSS 上传配置及访问策略。移动端原生/小程序下载使用经归属校验后取得的新签名地址，并用文档打开菜单查看、保存或分享。

本次没有执行持久数据库迁移、重启应用或部署服务。MySQL 自动测试仅创建连接局部 TEMPORARY 表，关闭连接即清除。

## 数据与归属

- `lawyer_case_task`：`case_id` 引用原案件，增加任务标题、状态、截止时间、排序及累计 `work_minutes`。创建时由服务端绑定租户、用户。
- `lawyer_case_checklist_item`：从所属任务继承访问权限；保存勾选状态与 `checked_at`，取消勾选会清空时间。
- `lawyer_case_task_time`：保留每笔分钟数、备注与请求标识，更新累计工时与明细插入在同一事务内完成。
- 所有任务写操作锁定原案件行后锁定任务行，与原案件删除/关联操作保持锁顺序。每个案件最多 200 个任务，每个任务最多 100 个检查项。
- 案件删除仍采用原逻辑删除，已有任务明细作为历史记录保留；原案件不可访问时，其任务、检查项和材料也不可访问。原文件和会话继续按原规则保留。
- **任务登记工时**独立于现有「开始/停止工作计时」统计。任务面板显示所有任务手动登记分钟总和，不自动与计时器重复相加；两类值在详情分别明确标注。
- 材料统一来自原 `lawyer_case_file`，关联 `sys_oss` 获取真实上传时间；对用户、租户、原文件上传人再次校验。原案件详情 `files` 与新材料接口使用同一查询，旧关联无需迁移。未绑定案件的会话附件不会自动解析 URL 加入归档，可通过已有绑定接口显式归档。

## API

为保持与原模块一致，使用 `/lawyer/case` 前缀。业务响应仍是 `{code,msg,data}`，业务错误可能以 HTTP 200 + 非 200 `code` 返回；客户端必须检查 `code`。ID 始终按字符串处理。

| 方法 / 路径 | 说明 |
| --- | --- |
| GET `/lawyer/case/{id}/tasks` | `data={tasks,doneTasks,totalTasks,checkedItems,totalItems,workMinutes}`；任务按 `sortOrder,id` 排列，每个任务包含 `checklist` |
| POST `/lawyer/case/{id}/tasks` | `{title,dueAt?}`；`dueAt` 为上海时间 `yyyy-MM-dd HH:mm:ss`，初始状态 todo |
| PATCH `/lawyer/case/task/{taskId}` | `{title?,status?,dueAt?,clearDueAt?,sortOrder?}`；缺省字段保留，`clearDueAt:true` 清空截止时间；状态 todo/doing/done |
| POST `/lawyer/case/task/{taskId}/checklist` | `{content}`；返回已保存检查项 |
| POST `/lawyer/case/task/{taskId}/checklist/{itemId}/toggle` | 推荐 `{checked:true或false}`，按目标状态幂等保存；不传 body/checked 时兼容翻转行为 |
| POST `/lawyer/case/task/{taskId}/time` | `{minutes,note?,requestId?}`；每笔 1～1440 整数分钟；返回更新后的任务 |
| GET `/lawyer/case/{id}/files` | 统一材料列表，含 `ossId,originalName,fileName,fileSuffix,url,uploadedAt,linkedAt,downloadUrl` |
| GET `/lawyer/case/{id}/files/{ossId}` | 单个材料及新签名 URL，打开前刷新，避免使用过期地址 |
| GET `/lawyer/case/{id}/files/{ossId}/download` | 鉴权二进制附件；经案件、文件关联与上传人校验后复用现有 OSS 流式下载 |
| POST `/lawyer/case/{id}/files` | **原有接口** `{ossId}`，绑定当前用户上传的文件 |
| DELETE `/lawyer/case/{id}/files/{ossId}` | **原有接口**解除材料关联，保留原文件 |

工时请求标识为最多 64 位字母、数字、下划线、连字符。同一任务、同一标识、同一参数重试不会重复累计；同一标识更换参数返回 409。PC 在登记弹窗打开时生成标识，失败后原样重试。未提供标识的调用，每个成功请求视为一笔新工时。

## curl / Postman 验证

以下 Bash 示例中，将令牌、现有个人案件 ID 换成真实值；Postman 使用相同方法、路径、Headers 与 JSON Body。Windows 可使用 Git Bash，或在 Postman 中直接配置。

```bash
BASE=http://127.0.0.1:6039
TOKEN='当前用户的访问令牌'
CLIENT='e5cd7e4891bf95d1d19206ce24a7b32e'
CASE_ID='个人案件ID'

curl -sS "$BASE/lawyer/case/$CASE_ID/tasks" \
  -H "Authorization: Bearer $TOKEN" -H "clientid: $CLIENT"

curl -sS -X POST "$BASE/lawyer/case/$CASE_ID/tasks" \
  -H "Authorization: Bearer $TOKEN" -H "clientid: $CLIENT" -H 'Content-Type: application/json' \
  --data '{"title":"庭前证据准备","dueAt":"2026-10-20 09:00:00"}'
# 将返回的 data.id 填入 TASK_ID，检查项返回的 data.id 填入 ITEM_ID。
TASK_ID='任务ID'
curl -sS -X POST "$BASE/lawyer/case/task/$TASK_ID/checklist" \
  -H "Authorization: Bearer $TOKEN" -H "clientid: $CLIENT" -H 'Content-Type: application/json' \
  --data '{"content":"核对合同原件与证据目录"}'
ITEM_ID='检查项ID'
curl -sS -X POST "$BASE/lawyer/case/task/$TASK_ID/checklist/$ITEM_ID/toggle" \
  -H "Authorization: Bearer $TOKEN" -H "clientid: $CLIENT" -H 'Content-Type: application/json' \
  --data '{"checked":true}'
curl -sS -X PATCH "$BASE/lawyer/case/task/$TASK_ID" \
  -H "Authorization: Bearer $TOKEN" -H "clientid: $CLIENT" -H 'Content-Type: application/json' \
  --data '{"status":"doing"}'
curl -sS -X POST "$BASE/lawyer/case/task/$TASK_ID/time" \
  -H "Authorization: Bearer $TOKEN" -H "clientid: $CLIENT" -H 'Content-Type: application/json' \
  --data '{"minutes":30,"note":"证据整理","requestId":"verify-time-1"}'
# 重复上一条请求，workMinutes 应仍为30；换 requestId、登记15分钟后累计45。
curl -sS "$BASE/lawyer/case/$CASE_ID/tasks" \
  -H "Authorization: Bearer $TOKEN" -H "clientid: $CLIENT"

# 上传并绑定（示例文件使用工作区已有测试材料）
curl -sS -X POST "$BASE/resource/oss/upload" \
  -H "Authorization: Bearer $TOKEN" -H "clientid: $CLIENT" \
  -F 'file=@ruoyi-web/tests/fixtures/sample-contract.docx'
OSS_ID='上传返回的ossId'
curl -sS -X POST "$BASE/lawyer/case/$CASE_ID/files" \
  -H "Authorization: Bearer $TOKEN" -H "clientid: $CLIENT" -H 'Content-Type: application/json' \
  --data "{\"ossId\":\"$OSS_ID\"}"
curl -sS "$BASE/lawyer/case/$CASE_ID/files" \
  -H "Authorization: Bearer $TOKEN" -H "clientid: $CLIENT"
curl -sS "$BASE/lawyer/case/$CASE_ID/files/$OSS_ID/download" \
  -H "Authorization: Bearer $TOKEN" -H "clientid: $CLIENT" -o case-material.docx
```

归属验收：用用户 B 的令牌重发 A 的任务 PATCH、检查项新增/勾选、工时登记、材料读取/下载，均应得到 404 业务错误，A 的数据保持不变。使用 A 的另一任务去修改不属于该任务的 `itemId` 也应被拒绝。

## UI 验收

1. PC「我的案件」选择既有案件，确认原关键日期提醒仍可见，进入「任务与检查项」。
2. 新增任务，添加检查项并勾选；刷新页面，勾选和时间应保留；取消勾选后时间清空。
3. 状态切换为 doing/done；登记 30 与 15 分钟、填写备注，案件详情显示任务累计 45 分钟；刷新后仍在。
4. 切到「材料」，上传文件后自动绑定当前 caseId，显示文件名、实际上传时间；刷新后仍可见，能打开与下载；旧有文件关联也应出现。
5. 模拟绑定失败可点击「重试关联已上传文件」，无需重复上传；错误 JSON 不会被当作文件下载。
6. 切换账号或案件时旧任务、材料、表单状态清空，旧响应不能回填当前页面。
7. 移动端案件详情只读显示完成数/总数、检查项和任务工时；点击材料可打开签名地址，原生/小程序下载后用文档菜单查看或保存。

隔离 UI 页面：在 `ruoyi-web` 启动 `node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5197 --strictPort`，访问 `/tests/fixtures/case-tasks-preview.html`。页面使用真实案件组件，API 和上传为模拟，下载为静态测试 Word；不写入真实案件或 OSS。截图位于 `scripts/development/case-tasks-ui.jpg`、`scripts/development/case-materials-ui.jpg`。本次模拟下载文件已与夹具核对哈希后清理。

## 文件清单与验证记录

- SQL：`ruoyi-ai/docs/script/sql/update/2026-10-17-case-tasks.sql`。
- 后端新增：`CaseTaskInput.java`、`CaseTaskView.java`、`CaseTaskService.java`、`CaseTaskController.java`；扩展原 `LawyerCaseService.java`、`LawyerCaseController.java` 的统一材料视图/下载。
- PC：原 `src/pages/cases/index.vue`、`src/api/lawyer/index.ts`；新增 `src/components/{CaseTasks,CaseMaterials}.vue`、`src/pages/cases/useCaseTasks.ts`、`src/api/lawyer/{tasks,materials}.ts`，自动组件声明随构建更新。
- 移动端：原 `pages/cases/index.vue`、`main.js`；新增 `components/case-archive.vue`。`scripts/development/sync-case-tasks-mobile.cjs` 只同步本次三个文件，发现其他目标改动则停止。
- 测试：后端 `CaseTaskMysqlTest`（4）、`CaseTaskControllerTest`（4）及案件安全/提醒/计时回归（9），共17项通过；PC `tests/case-tasks.test.cjs` 8项通过；移动端新归档与原案件/计时共12项通过。
- `vue-tsc -b` 与 Vite PC 生产构建通过。H5、小程序构建通过；保留项目已有字体/大包、原组件 key 与平台兼容性提示。
- PC 隔离 UI 已验证新增任务、检查项勾选/刷新保持、状态修改、30+15分钟累计/刷新保持、材料上传绑定/刷新保持与下载、账号切换后清空私人案件内容。真实 OSS 及移动端真机打开尚待部署环境联调。

在工作区根目录可重跑前端测试：

```powershell
node --test ruoyi-web/tests/case-tasks.test.cjs
node --test scripts/development/mobile/tests/case-archive.test.cjs ..\lawyer-ai-center-mobile\ruoyi-uniapp\tests\cases.test.cjs ..\lawyer-ai-center-mobile\ruoyi-uniapp\tests\timer.test.cjs
```

日志均位于 `scripts/development`：`case-task-backend-tests.log`、`case-task-controller-tests.log`、`case-task-pc-tests.log`、`case-task-pc-typecheck.log`、`case-task-pc-build.log`、`case-task-mobile-tests.log`、`case-task-mobile-{h5,mp-weixin}-build.log`。
