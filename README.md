<div align="center">
  <a href="https://www.qjsec.com">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="lawyer-ai-center-web/docs/open-source/assets/qianjin-logo.png">
      <img src="lawyer-ai-center-web/docs/open-source/assets/qianjin-logo-light.png" alt="江西前进系统工程有限公司 LOGO" width="360">
    </picture>
  </a>

  <h1>与律行 · YuLvXing</h1>
  <p>面向律师日常工作的 AI 辅助工作台</p>
  <p>案件与材料管理 · 合同审查 · 文书起草 · 法律检索 · 来源复核</p>
  <p><strong>由江西前进系统工程有限公司维护与开源</strong></p>
  <p><a href="README-en.md">English</a> · <a href="https://www.qjsec.com">前进公司官网</a> · <a href="NOTICE">上游致谢</a> · <a href="LICENSE">MIT 许可证</a></p>
</div>

> **合作与咨询：0792-2199711 · [jjqianjin@163.com](mailto:jjqianjin@163.com) · [www.qjsec.com](https://www.qjsec.com)**
>
> 欢迎联系前进公司，交流软件定制、系统集成、本地部署与项目合作需求。服务范围、费用和交付承诺以双方另行约定为准。

## 为什么开源

与律行基于优秀开源项目进行面向律师工作场景的二次开发。我们从开源社区获得了大量帮助，希望通过公开代码回馈社区，让更多开发者了解项目，也让有实际需求的团队认识**江西前进系统工程有限公司**。

在本次开源准备时，本项目尚未带来盈利。这是项目现状说明，**不是免除许可证、版权、数据保护或其他法定义务的理由，也不是对使用者增加“仅限非商业使用”的限制**。欢迎在遵守相应许可证的前提下学习、修改、部署和参与改进。

## 项目能做什么

| 场景 | 当前代码中的能力 | 使用边界 |
| --- | --- | --- |
| 案件工作台 | 案件信息、材料关联、任务清单、提醒与工时记录 | 部署者应配置账户、权限和备份 |
| 材料阅卷 | 上传与版本、解析任务、来源锚点、事实与时间线复核 | 扫描件、OCR、解析质量受文件和运行环境影响 |
| 银行流水辅助审阅 | 流水提取、待核条目、人工修改记录与导出 | 金额、重复交易、统计口径必须人工核验 |
| 合同审查 | 基于配置模型生成风险点、修改建议和可用引用 | AI 建议不等于律师终审或法律结论 |
| 文书与模板 | 文书起草、模板类型与文件管理、法律意见书 | 使用者核对事实、格式、来源与适用法律 |
| 检索与知识库 | 案情检索、知识库检索、检索报告与引用元数据 | 不附带商业法律数据库、订阅或第三方数据授权 |
| 运行管理 | 模型与厂商、助手、菜单角色、存储与系统配置 | 外部服务账户、API Key 和费用由部署者自行配置 |

这是正在迭代的工程项目。仓库中的规划文档不等于已经实现或通过验收的能力；完整验证范围见[开源准备记录](lawyer-ai-center-web/docs/open-source/PREPARATION-REPORT.md)。本仓库包含网页用户端、管理端和后端，不包含相邻目录中的移动端工程。

## 技术栈与目录

| 部分 | 技术与版本要求 |
| --- | --- |
| 后端 `ruoyi-ai/` | Java 17+、Maven 3.9+、Spring Boot 3.5.8、MyBatis-Plus、Sa-Token、LangChain4j |
| 数据库与缓存 | MySQL **8.0.30+**、Redis；SQL 使用 `utf8mb4`，不适用于 MySQL 5.7 / MariaDB |
| 用户端 `ruoyi-web/` | Vue 3、TypeScript、Vite、Element Plus、Pinia；Node.js ≥22.13.0、pnpm **11.0.9** |
| 管理端 `ruoyi-admin/` | Vue 3、Vben、Ant Design Vue；Node.js ≥20.10.0、pnpm **10.14.0** |
| 按需依赖 | 自有模型服务、对象存储；使用知识库向量检索等能力时配置相应向量服务 |

```text
.
├── ruoyi-ai/                 后端及数据库脚本
│   └── docs/script/sql/
│       ├── ruoyi-ai.sql      全新安装只执行这一份完整 SQL
│       ├── bootstrap/        维护者使用的安全基线与生成清单
│       └── update/           已有数据库的增量迁移，不与全量重复执行
├── ruoyi-web/                网页用户端
├── ruoyi-admin/              管理端及共享前端组件
├── docs/open-source/         免责、授权说明、发布检查与验证记录
├── scripts/open_source/      SQL 生成、管理员密码配置及验证工具
├── LICENSE                   本项目代码的 MIT 许可证
└── NOTICE                    上游项目和权利归属说明
```

## 从源码开始

以下命令从下载后解压或克隆的**仓库根目录**开始。首次运行请使用隔离的本地环境。前后端分别保留自己的 pnpm 锁文件，不在根目录安装另一套依赖。

### 1. 初始化数据库：只执行一个 SQL

完整脚本为 **[`ruoyi-ai/docs/script/sql/ruoyi-ai.sql`](ruoyi-ai/docs/script/sql/ruoyi-ai.sql)**，已包含当前版本的主库结构、律师业务迁移、菜单字典、模板类型及 `snail_job` 调度库结构。**新安装不需要再执行其他 SQL 文件。**

脚本创建 `ruoyi-ai-lawyer` 和 `snail_job` 两个数据库，两个名称必须均不存在。脚本不删除已有数据库或数据表；若名称已存在会报错。使用支持遇错停止的客户端，**禁止 `--force`**。已有环境升级请阅读 [SQL 说明](ruoyi-ai/docs/script/sql/README.md)，不要重跑全量脚本。DDL 不具备整份脚本的事务回滚能力。

公开脚本**没有通用管理员密码**，默认管理员 `admin` 为锁定状态。安装前在本机选择自己的密码，生成一份私有的完整 SQL：

```bash
# Python 3.10+；bcrypt 仅供本地密码准备工具使用，不是应用运行依赖
python -m pip install bcrypt
python scripts/open_source/configure_admin.py
```

工具隐藏输入密码（至少 12 字符），只将 BCrypt 散列写入 `tmp/yulvxing-init.local.sql`，不保存明文。**数据库只需执行生成的这一份文件，无需先执行原版。** 已有 BCrypt 散列也可以直接填入原文件顶部 `INSTALLER_BCRYPT_HASH` 对应变量，要求 BCrypt cost 10～16。

Linux / macOS：

```bash
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p < tmp/yulvxing-init.local.sql
```

Windows PowerShell（由 `cmd` 进行原始文件重定向，避免管道改变中文编码）：

```powershell
cmd /c 'mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p < tmp\yulvxing-init.local.sql'
```

看到 `YULVXING_DATABASE_INITIALIZATION_COMPLETE` 且命令退出码为 0 后，再启动后端。安装私有副本含密码散列，已被 Git 忽略；不要公开或上传。若直接执行公开原版，表结构仍可初始化，但管理员保持锁定；恢复方式见 [SQL 说明](ruoyi-ai/docs/script/sql/README.md)。

SQL 不包含真实案件、聊天记录、材料文件、商业知识库或可用 API 密钥。预置示例模型和四个法律助手均停用；`snail_job` 不预置登录账户、接入令牌或运行任务。数据库初始化完成不代表模型、文件存储或整个应用已配置完成。

### 2. 配置并启动后端

使用现有 [`application-prod.yml`](ruoyi-ai/ruoyi-admin/src/main/resources/application-prod.yml) 的环境变量入口。**Spring Boot 不会自动读取根目录 `.env` 文件**，请通过当前 shell、容器或进程管理器注入：

| 环境变量 | 填写内容 |
| --- | --- |
| `DB_URL` | 例如 `jdbc:mysql://127.0.0.1:3306/ruoyi-ai-lawyer?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai` |
| `DB_USERNAME` / `DB_PASSWORD` | 自己创建的应用数据库账户及密码；运行时避免使用 root |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | 自己的 Redis 地址、端口与凭据 |
| `REDIS_SSL` | 按实际服务填写；生产配置默认 `true`，本地无 TLS 服务需显式设为 `false` |
| `JWT_SECRET` | 自行生成的高强度随机密钥，不复用示例值 |
| `MONITOR_USERNAME` / `MONITOR_PASSWORD` | 自行设置；监控客户端默认关闭，配置占位符仍需提供 |
| `UPLOAD_PATH` | 服务账户可写的持久化绝对目录 |
| `WEB_ALLOWED_ORIGINS` | 实际用户端与管理端来源，逗号分隔，如 `http://127.0.0.1:5137,http://127.0.0.1:5666` |

```bash
cd ruoyi-ai
# 仅打包；发布测试要求见检查清单
mvn -Pprod -pl ruoyi-admin -am package -DskipTests
java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=prod
```

后端默认端口 `6039`。如采用其他端口，同步调整前端代理。开发配置样例见 [`application-dev.yml.example`](lawyer-ai-center-web/docs/open-source/config/application-dev.yml.example)；本机真实开发配置不应提交。

### 3. 启动网页用户端与管理端

分别开两个终端，从仓库根目录执行。下面用 `npx` 获取指定版本的 **pnpm 工具**，依赖安装仍由 pnpm 完成。

用户端：

```bash
cd ruoyi-web
cp .env.development.example .env.development
npx --yes pnpm@11.0.9 install --frozen-lockfile
npx --yes pnpm@11.0.9 dev --host 127.0.0.1 --port 5137
```

管理端：

```bash
cd ruoyi-admin
cp apps/web-antd/.env.example apps/web-antd/.env
cp apps/web-antd/.env.development.example apps/web-antd/.env.development
npx --yes pnpm@10.14.0 install --frozen-lockfile
npx --yes pnpm@10.14.0 dev:antd
```

PowerShell 将上述 `cp` 换为 `Copy-Item`。用户端为 `http://127.0.0.1:5137`，管理端为 `http://localhost:5666`；使用初始化时自行设置的 `admin` 密码登录。管理端代理默认访问 `127.0.0.1:6039`；用户端样例 API 地址也指向该服务。

生产构建分别执行 `pnpm build` 和 `pnpm build:antd`，并先从对应 `.env.production.example` 创建自己的 `.env.production`；管理端还需 `.env`。通过反向代理、HTTPS、备份与最小权限进行部署。子目录保留的上游演示地址、镜像及历史 README 用于来源参考，**不代表与律行版本的发布服务或二进制镜像**。

### 4. 配置模型、存储与助手

1. 在管理端的模型管理中配置自己的厂商、实际模型 ID、地址与凭据。停用示例 `configure-your-model` 不能直接调用；本地模型也需要实际部署。
2. 为 `法律咨询助手`、`合同审查官`、`文书起草助手`、`法律意见书助手` 绑定可用模型，按需配置合法知识来源，再逐一启用。详见[模型配置](ruoyi-ai/docs/model-configuration.md)。
3. 在存储配置中填写自己的对象存储、桶和访问权限；未配置前，上传、材料解析及文件导出可能不可用。使用 RAG 时另行配置向量服务和嵌入模型。
4. 用自建的非敏感测试材料验证登录、案件创建、上传、解析、复核、文书及导出，再按自身场景验收。

本地部署默认 `LAWYER_BILLING_ENABLED=false`，不启用收费与额度扣减；它不影响模型服务商可能收取的费用。订单和计费代码的存在不表示已有盈利、支付上线或合规收费能力，见[本地计费说明](ruoyi-ai/docs/local-deployment-billing.md)。敏感材料向外部模型发送仍须满足服务端准出策略及必要授权。

## 致谢与上游来源

**衷心感谢 [ruoyi-ai](https://github.com/ageerle/ruoyi-ai) 的作者 [ageerle](https://github.com/ageerle) 与所有贡献者，感谢 [RuoYi](https://github.com/yangzongzhuan/RuoYi) 的作者 [yangzongzhuan](https://github.com/yangzongzhuan) 与所有贡献者。** 你们公开分享的成果，为本项目提供了重要基础。

同时感谢 RuoYi-Vue-Plus、RuoYi AI 用户端与管理端、ruoyi-element-ai / HeJiaYue520、Vben 以及项目所依赖的各开源组件作者。具体来源、许可证原文及归属见 [NOTICE](NOTICE) 和[第三方开源说明](THIRD_PARTY_NOTICES.md)。

与律行是江西前进系统工程有限公司维护的衍生项目，**不是上述上游项目的官方版本**。上游作者未因此对本项目的修改、运营、服务或 AI 输出作出认可、担保或背书。与律行问题请在**当前仓库**提出或联系前进公司，不应要求上游作者为本项目负责。

## 许可、免责与数据保护

公司有权许可的新增代码采用 **[MIT License](LICENSE)**；上游代码、第三方组件与非代码素材分别遵守其原有许可证和权利声明。MIT 允许在保留相应版权和许可声明的前提下使用、修改与商业分发；README 中的公司展示不附加“强制保留推广链接”等额外代码许可条件。公司名称、LOGO、商标、第三方材料和数据的权利不因代码开源自动一并授予。

在适用法律允许的最大范围内，软件按现状提供，不保证准确性、适用性、不侵权、持续可用性或安全性。**免责声明不是侵权豁免，也不能排除法律不得免除的责任。** 本项目及 AI 输出仅提供工作辅助，不构成针对具体案件的正式法律意见、执业服务或结果保证，不替代律师独立判断与人工复核。

使用者应核对事实、金额、期限、法条有效性和原始来源，自行取得处理案件材料、个人信息、商业秘密、模型、数据库及文档素材的必要授权；未经授权不得外发敏感材料。完整条款与权利反馈流程见[免责声明](lawyer-ai-center-web/docs/open-source/DISCLAIMER.md)和[安全政策](SECURITY.md)。不提供、也不承诺“零侵权风险”。

## 参与贡献与发布

欢迎提交可复现问题、文档修正和 Pull Request，先阅读[贡献指南](CONTRIBUTING.md)。请勿在 Issue、提交记录、日志或截图中上传真实案件、密钥及个人信息。安全漏洞与权利争议请通过邮箱私下反馈。

首次公开发布前，维护者须完成[开源发布检查清单](lawyer-ai-center-web/docs/open-source/RELEASE-CHECKLIST.md)，特别检查 Git 历史中的秘密、素材授权、依赖许可证与实际部署验收。工具和 SQL 的验证不等于完整软件、安全或法律审计通过。

## 联系江西前进系统工程有限公司

| 方式 | 官网公开信息 |
| --- | --- |
| 官方网站 | **[https://www.qjsec.com](https://www.qjsec.com)** |
| 技术热线 / 项目咨询 | **0792-2199711** |
| 售后报修 | **0792-2199719** |
| 公司邮箱 / 开源与权利反馈 | **[jjqianjin@163.com](mailto:jjqianjin@163.com)** |
| 公司地址 | 江西省九江市八里湖兴业大道211号徽商商务中心A座11楼 |

<img src="lawyer-ai-center-web/docs/open-source/assets/qianjin-wechat.jpg" alt="江西前进系统工程有限公司微信公众号二维码" width="160">

联系方式与公众号二维码来源于[公司官网](https://www.qjsec.com)，核对日期：2026-10-30；如有调整，以官网为准。欢迎关注当前仓库、反馈使用体验，也欢迎将前进公司推荐给有相关需求的团队。
