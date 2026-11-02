# SQL 初始化与升级说明

[中文项目说明](../../../../README.md) · [English project guide](../../../../README-en.md)

## 全新安装：一份完整 SQL

**唯一全量入口是 [ruoyi-ai.sql](ruoyi-ai.sql)。** 它在同一个连接中创建 `ruoyi-ai-lawyer` 主库和 `snail_job` 调度库，并嵌入当前律师业务迁移、菜单权限、字典与内置模板类型。新安装不用逐个执行 `bootstrap/`、`update/` 或根目录其他 SQL，也不用再执行 `snail_job_mysql.sql`。

要求 MySQL **8.0.30+**、UTF-8 / `utf8mb4`，不适用于 MySQL 5.7、MariaDB 或其他 SQL 方言。`CREATE DATABASE` 需要初始化账户具备相应权限；应用运行时另用最小权限账户。

两个数据库名称必须均不存在。脚本不含 `DROP DATABASE` / `DROP TABLE`，也不使用 `SOURCE` 引入外部文件；若数据库已存在，必须在首个错误处停止。**禁止 `--force`，禁止选择“忽略错误继续执行”。** 全量初始化不是已有数据库的升级工具。

## 管理员密码

公开脚本默认 `@YULVXING_ADMIN_BCRYPT = NULL`，管理员 `admin` 会以锁定状态建立，密码为空且不可用于登录。没有通用初始密码。模型、厂商、四个法律助手和自助注册默认停用，调度库没有登录账户或接入令牌。

从仓库根目录运行：

```bash
python -m pip install bcrypt
python scripts/open_source/configure_admin.py
```

Python 要求 3.10+。密码输入不回显，长度至少 12 个字符、至多 72 个 UTF-8 字节；工具使用 BCrypt cost 12。结果为 `tmp/yulvxing-init.local.sql`，仅含散列，不含明文，且被 Git 忽略。它是**完整脚本的私有副本，只执行这一份即可**。工具拒绝覆盖已存在副本或公开原版；如需再次准备，用 `--output` 指定另一个私有 `*.local.sql` 路径。

也可在公开文件顶部 `INSTALLER_BCRYPT_HASH` 标记所在行填写自己的有效 BCrypt 散列（cost 10～16），不要写明文密码，不要把编辑后的私有副本提交。

Linux / macOS，从仓库根目录：

```bash
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p < tmp/yulvxing-init.local.sql
```

Windows PowerShell，从仓库根目录：

```powershell
cmd /c 'mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p < tmp\yulvxing-init.local.sql'
```

由 `cmd` 进行文件重定向可避免 PowerShell 文本管道改变 SQL 中文/提示词的编码。数据库密码通过 `-p` 交互输入，不放进命令行。

只有在**命令退出码 0**，并输出 `YULVXING_DATABASE_INITIALIZATION_COMPLETE` 时，才认为 SQL 导入成功。这不等于模型、存储、Redis 或整个应用已验收。

### 如果已执行公开原版而管理员锁定

不要为解锁而重跑全量文件。使用本机密码准备工具生成自己的散列，在受控的数据库会话中，仅针对初始化账户更新；示例中的占位符必须替换为您自己生成的散列：

```sql
USE `ruoyi-ai-lawyer`;
SET @own_bcrypt = '<YOUR_OWN_BCRYPT_HASH>';
UPDATE sys_user
SET password=@own_bcrypt, status='0'
WHERE user_id=1 AND user_name='admin' AND status='1' AND COALESCE(password,'')=''
  AND @own_bcrypt REGEXP '^[$]2[aby][$]1[0-6][$][./A-Za-z0-9]{53}$';
```

应确认仅影响 1 行。该恢复步骤只适用于空密码、锁定的初始化管理员，不用于修改已有用户密码或绕过已有账户管理。保护并及时清理本地散列副本，不公开凭据。

## 初始化数据边界

保留系统需要的字典、菜单、公开客户端标识、默认租户和管理员关联、普通用户角色及其原有权限、工作流节点类型和模板类型。普通用户角色维持“仅本人”数据范围，未授予系统管理员或模板写入权限。

不导入真实用户联系方式、测试账户、会话/消息、案件、证据、上传文件、存储账户、操作/登录日志、社交绑定、生产订单、工作流执行或示例短剧数据。计费方案为停用配置，不意味着启用收款。四个法律助手关联停用示例模型；部署者必须配置自己的模型后再启用。可选的外部付费视频模型样例未纳入初始化。

只创建当前代码需要的关系型数据库结构；不包含对象存储文件、向量索引、Neo4j 数据、商业法律数据库、模型权重或第三方服务额度。`snail_job` 结构已包含但应用调度默认关闭；如以后启用，须单独配置调度服务、账户与独立接入令牌。

## 中途失败与恢复

MySQL 的 DDL 会隐式提交，整份脚本不能依靠一个事务回滚。失败后保留错误输出，确认本次已经创建的库、表和数据范围，修复原因后在新的隔离空库环境重试。只有确认是本次新建、没有业务数据的安装库，才可由维护者清理后重建；不要把清理动作套用到已有部署。

遇到重名数据库时，先确认是否已安装或与其他服务共用，不要为了让脚本通过而删除原库。如果确需自定义库名，需同时调整文件中所有明确的 `CREATE DATABASE` / `USE` 和应用/调度数据源，并重新验证。

## 已有数据库升级

已有数据库应先备份、验证恢复，并根据已应用版本选择尚未执行的**增量迁移**。根目录 `lawyer-*.sql` 存在先后依赖，不能只按整个目录的文件名排序；`update/*-rollback.sql` 也不是安装迁移。

全新安装的执行顺序由 [build_init_sql.py](../../../../scripts/open_source/build_init_sql.py) 中 `MIGRATIONS` 明确维护，基线已包含的历史变更在 `BASELINE` 中分类。对已有环境，应结合实际结构、迁移账本和脚本内说明判断，不能直接把这份新安装序列当作通用线上升级器。原有阅卷等受控迁移工具的备份、校验和权限要求继续适用。

`2026-10-30-open-source-missing-tables.sql` 根据当前四个 Java 实体补齐旧导出漏建的 `adi_user`、`chat_config`、`knowledge_graph_instance`、`knowledge_graph_segment`；只新增结构，不导入账户或业务记录。若现网已存在这些表，先比较字段定义，不应把 `IF NOT EXISTS` 当作结构一致性校验。

`snail_job_mysql.sql` 仅供**尚未存在调度库**时独立初始化，已去除删库及通用账户/令牌。完整导入 `ruoyi-ai.sql` 后无需执行它。

## 维护者生成与验证

维护源为 [bootstrap/schema.sql](bootstrap/schema.sql)、[bootstrap/seed.sql](bootstrap/seed.sql)、[bootstrap/snail-schema.sql](bootstrap/snail-schema.sql) 及显式列出的增量文件。基线是经过数据剔除的历史结构，不是在线数据库导出；不要重新导出生产数据覆盖它。

```bash
# 从仓库根目录构建：不连接数据库
python scripts/open_source/build_init_sql.py
# 检查生成文件是否与所有输入一致
python scripts/open_source/build_init_sql.py --check
```

[bootstrap/manifest.json](bootstrap/manifest.json) 记录输入 SHA-256、顺序、已合并变更、排除的回滚文件及产物 SHA-256。哈希按 UTF-8 / LF 规范化，避免 Windows 换行差异。新增未分类迁移会使生成失败，必须明确纳入顺序或说明已合并原因；不要手工只改生成文件。

历史 [`workflow_nodes_config.sql`](../../../ruoyi-modules/ruoyi-aiflow/src/main/resources/sql/workflow_nodes_config.sql) 是搜索节点的单项配置示例，相关模板已包含在基线，不作为额外安装迁移执行。

验证器额外需要 `python -m pip install bcrypt PyMySQL`（仅验证工具依赖）。在已启动的独立临时 MySQL 实例上执行真实导入核验：

```text
python scripts/open_source/verify_init_sql.py --mysql <mysql客户端绝对路径> --port <非3306测试端口> --data-dir <本仓库tmp下的独立数据目录>
```

工具先验证服务器实际数据目录和端口，再创建随机命名的测试库，验证公开管理员锁定、安装者自选密码、中文、实体表、空业务数据、菜单关联和重复执行保护，最后只移除其创建的随机测试库。该工具不连接默认 3306，不启动或停止现有数据库。默认本地证据为 `tmp/open-source-mysql/validation.json`；公开验收摘要见[开源准备记录](../../../../docs/open-source/PREPARATION-REPORT.md)。
