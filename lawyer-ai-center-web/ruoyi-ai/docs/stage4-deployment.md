# 阶段4正式迁移与主服务发布（指令53）

本清单用于明确指定的原库/正式目标库。不要运行全量 `ruoyi-ai.sql`，不要把 `codex_stage4_*` 或其他演练库的业务数据导入正式库。所有命令从 web 工作区执行；Java迁移器由入口脚本自动切换到 `ruoyi-ai`。

## 迁移清单与顺序

先核对前置结构：`lawyer-legal-metadata.sql` 的 `law_name/article_no/validity_status`、`2026-07-20-knowledge-fragment-fid.sql` 的 `fid`、`lawyer-cases.sql` 的案件/材料表和 `chat_session.case_id`，以及法律智能体种子。前置的一次性 ALTER 不可盲目重复运行；缺失时由 DBA 按列/索引核对补齐，迁移器会在任何阶段4变更前拒绝缺少前置结构的目标。

| 顺序 | `docs/script/sql/update/`实际文件 | 效果 |
| --- | --- | --- |
| 1 | `2026-10-17-legal-citation-source.sql` | `knowledge_attach.case_no/court/source_url`，逐列检查后添加，保留原值 |
| 2 | `2026-10-17-legal-search-report.sql` | `legal_search_report`、`legal_search_report_item` |
| 3 | `2026-10-17-case-tasks.sql` | `lawyer_case_task`、`lawyer_case_checklist_item`、`lawyer_case_task_time` |
| 4 | `2026-10-17-legal-opinion.sql` | 从目标库自身的起草智能体补充意见书助手，保留已存在配置；没有新意见书表，沿用现有会话/消息 |
| 5 | `2026-10-18-contract-review-issues.sql` | 租户000000合同审查官的 issues Prompt；不改模型或知识库绑定 |
| 6 | `2026-10-18-personal-reminders.sql` | 扩展现有 `sys_notice` 的收件人、去重键、关联案件和已读时间；逐列/索引判重 |

迁移器使用数据库命名锁和 `stage4_schema_migration` 版本表，记录文件名、SHA-256、执行人及时间。重复运行跳过已执行的相同文件；若已登记文件内容变化，则拒绝执行，应新增版本而非改写账本。首次部署此清单前已手工执行的脚本可按实际结构接管：来源列逐列判重，建表 IF NOT EXISTS，意见书 NOT EXISTS，Prompt 是确定性更新。现有不兼容列长、缺列或索引会报错，不能把表存在当作迁移成功。

## 环境、备份、执行

需要 JDK17、MySQL Connector/J、Node22、匹配的 mysqldump；本机提供了默认 JDK/驱动路径，可用 `STAGE4_JAVA` / `STAGE4_JDBC_DRIVER` 覆盖。正式环境使用密钥管理或当前进程环境提供凭据，不把密码写入命令行、日志或版本库。

```powershell
$env:STAGE4_EXPECTED_DATABASE='团队明确指定的目标库名'
$env:STAGE4_OPERATOR='执行人 / 工单'
$env:STAGE4_EVIDENCE_DIR=Join-Path (Get-Location) 'scripts/development/stage4-deploy-evidence/本次唯一运行编号'
$env:STAGE4_JDBC_URL='jdbc:mysql://目标主机:3306/目标库名?useUnicode=true&characterEncoding=utf8&serverTimezone=GMT%2B8'
# STAGE4_DB_USER / STAGE4_DB_PASSWORD 由当前进程环境或密钥管理注入。
$env:STAGE4_MYSQLDUMP='匹配MySQL版本的mysqldump.exe完整路径'
$env:STAGE4_BACKUP_DIR=Join-Path (Get-Location) 'ruoyi-ai/.codex-runtime/stage4-backups'
node ruoyi-ai/scripts/deploy/stage4-deploy.cjs audit
node ruoyi-ai/scripts/deploy/stage4-deploy.cjs backup
node ruoyi-ai/scripts/deploy/stage4-deploy.cjs rehearse
node ruoyi-ai/scripts/deploy/stage4-deploy.cjs migrate
node ruoyi-ai/scripts/deploy/stage4-deploy.cjs migrate # 相同版本安全跳过
node ruoyi-ai/scripts/deploy/stage4-deploy.cjs audit
# 每一步检查 $LASTEXITCODE，失败即停止后续步骤。
```

本地正式开发库可以给上述命令加 `--local`，此选项只从 `application-dev.yml` 读取主数据源到子进程内存，并强制主机为127.0.0.1或localhost；仍必须显式设置 EXPECTED_DATABASE，连接库名不一致立即拒绝。没有选取隔离库配置的入口。远程生产不使用 `--local`。

`backup` 对原目标库使用单事务完整逻辑备份，包含结构、数据、触发器、存储过程和事件；`backup.json` 保存库名、文件位置、大小、SHA-256。迁移前再次校验备份哈希。备份含用户数据，保留在受限运维目录，禁止提交或发布；`.codex-runtime` 已被Git忽略。正式生产还应按团队备份策略验证在另一恢复库能导入，并在维护窗口停止写入或确保表均为InnoDB。演练仅将原库必要结构及法律智能体配置单向复制到新命名的演练库，迁移两次并直接重跑来源列及个人通知SQL各两次；演练库保留供复查，不复制用户/案件/消息/报告业务数据，不向原库回写。

## 发布、候选验证与切换

```powershell
& ruoyi-ai/scripts/deploy/Build-Stage4Release.ps1 -EvidenceDir $env:STAGE4_EVIDENCE_DIR
$release=Get-Content (Join-Path $env:STAGE4_EVIDENCE_DIR 'release.json') -Raw | ConvertFrom-Json
# 生产由现有服务管理器注入相同目标库及Redis/OSS/模型配置；候选服务也必须指向同一目标库。
& ruoyi-ai/scripts/deploy/Start-Stage4Service.ps1 -ReleaseJar $release.jar -EvidenceDir $env:STAGE4_EVIDENCE_DIR -Port 6038
$env:STAGE4_BASE_URL='http://127.0.0.1:6038'
$env:STAGE4_SMOKE_ACCOUNT_FILE=Join-Path (Get-Location) 'ruoyi-ai/.codex-runtime/stage4-smoke-user.json'
node ruoyi-ai/scripts/deploy/deploy-smoke.cjs
# 候选通过后确认6039的唯一PID，明确传入才会停止该进程。
Get-NetTCPConnection -LocalPort 6039 -State Listen | Select-Object OwningProcess
& ruoyi-ai/scripts/deploy/Start-Stage4Service.ps1 -ReleaseJar $release.jar -EvidenceDir $env:STAGE4_EVIDENCE_DIR -Port 6039 -ReplaceProcessId 实际PID
$env:STAGE4_BASE_URL='http://127.0.0.1:6039'
node ruoyi-ai/scripts/deploy/deploy-smoke.cjs
# 核对API写入确实落在显式指定原库，而不是启动了错误数据源。
$env:STAGE4_SMOKE_RECORD_FILE=Join-Path $env:STAGE4_EVIDENCE_DIR 'deploy-smoke-6039.json'
node ruoyi-ai/scripts/deploy/stage4-deploy.cjs verify-smoke
```

启动脚本复用后端标准 Spring 环境配置。**迁移器的 STAGE4_JDBC_URL 等变量不替代服务的 Spring 配置**：远程生产必须由服务管理器设置 `SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL/USERNAME/PASSWORD` 等，或提供受限的外部 Spring 配置。先核对候选启动日志的数据源库名及后台运行配置，再做普通用户冒烟。本次本地JAR内 dev 配置与已核对原库一致。

可以用 `STAGE4_SMOKE_USERNAME/PASSWORD` 提供团队指定的普通用户；仅本地/预发允许在未提供账号时使用当前开放的注册接口创建合成冒烟账号，密码仅写入受限的 `.codex-runtime` 账户文件。生产优先使用预先批准的普通测试账号。冒烟器拒绝 userId=1、管理员角色、全权限，检查实际 `user` 角色。

本地原库如已关闭注册，保持该设置，可执行 `node ruoyi-ai/scripts/deploy/stage4-deploy.cjs create-smoke-user --local`。此入口仅允许本机、固定命名前缀的全新合成账号，仅绑定目标库已有 `user` 角色；不更改注册参数、现有用户或角色权限，需要本地Maven缓存的hutool-crypto/core 5.8.40。已有私有账号文件时拒绝再建。远程生产入口拒绝自动建号，使用团队指定的普通测试账号。

冒烟覆盖：登录 → 建报告/加一条/生成/下载 → 建案件任务/勾选并重读 → 真实知识库检索携带法规名、条号、状态、fragmentId并读详情 → issues夹具Word导出 → 六章意见书夹具历史/Word接口。后两项是合成结构夹具，不宣称重跑真实模型审查/意见书生成；指令50/51实模证据另见此前验收记录。每端口生成脱敏 `deploy-smoke-端口.json` 及测试Word。报告和案件通过原有普通用户API逻辑删除，合成会话、普通测试账号及生成OSS对象按既有保留策略保留。不能删除或覆盖真实客户资料。

`release.json` 保存不可变JAR路径和SHA-256、关键源码哈希；`service-6039.json` 保存实际PID、路径和同一哈希，结合主端口监听者、启动日志及6039冒烟判断实际发布。指令52的正式uniapp源码与三端资源验证独立交付，不会被后端JAR打包或自动发布到手机。

Windows脚本是本地部署入口，不注册开机自启或改写IDE运行配置；生产使用既有systemd/容器/Windows服务管理器，替换同一不可变JAR/镜像后执行同一冒烟器。通过后停止6038候选实例，保持6039主实例运行。

## 回滚与失败恢复

个人提醒开始写入以后，公告管理代码必须持续保留 `recipient_user_id IS NULL` 隔离条件。此时不能直接退回不认识收件人的旧公告管理版本；先关闭 `LAWYER_REMINDERS_ENABLED`，保留带隔离条件的服务版本和已有通知。触发频率、去重与个人消息验收见 `personal-case-reminders.md`。

MySQL DDL会隐式提交，不能依赖事务整体撤销。中途失败保留执行日志与账本，修复原因后按实际列/表续跑；不得清空账本伪造成功。优先回滚到上一个已验证的不可变二进制，保留本次兼容性新增表/列；不要自动DROP新表/列。Prompt/新增意见书配置需要恢复时，按备份中的目标行人工核对恢复，不改模型和知识绑定。需要全库恢复时先停止写入，在新恢复库验证备份，再经团队故障流程切换，避免覆盖迁移后新业务。

本机原6039是IDE调试进程，缺少可证明与其内存完全一致的旧部署JAR，`previous-process.json`只记录旧启动方式。切换前已保留原库备份并要求候选服务通过；如必须回到IDE运行方式，停止新6039后在IDE用原配置启动。正式生产必须先归档上一个可回滚JAR/镜像，不把本机调试进程描述成已有不可变回滚包。

## 本次执行记录

详见 web工作区 `scripts/development/stage4-deploy-evidence/deploy-smoke.md`，包括执行人、时间、原库名、迁移哈希、主服务JAR/PID、普通用户冒烟结果与实际剩余事项。该记录只证明本地正式开发实例，不代表远程生产自动上线。
