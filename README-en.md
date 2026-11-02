<div align="center">
  <a href="https://www.qjsec.com">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="lawyer-ai-center-web/docs/open-source/assets/qianjin-logo.png">
      <img src="lawyer-ai-center-web/docs/open-source/assets/qianjin-logo-light.png" alt="Jiangxi Qianjin System Engineering Co., Ltd. logo" width="360">
    </picture>
  </a>

  <h1>YuLvXing · 与律行</h1>
  <p>An AI-assisted workspace for everyday legal work</p>
  <p>Cases and materials · Contract review · Drafting · Research · Source verification</p>
  <p><strong>Maintained and open-sourced by 江西前进系统工程有限公司</strong><br>Jiangxi Qianjin System Engineering Co., Ltd.</p>
  <p><a href="README.md">简体中文</a> · <a href="https://www.qjsec.com">Company website</a> · <a href="NOTICE">Acknowledgements</a> · <a href="LICENSE">MIT License</a></p>
</div>

> **Contact Qianjin: +86 792 2199711 · [jjqianjin@163.com](mailto:jjqianjin@163.com) · [www.qjsec.com](https://www.qjsec.com)**
>
> Contact us about software customization, systems integration, local deployment and project collaboration. Scope, pricing and delivery commitments are subject to a separate agreement.

## Why we are sharing this project

YuLvXing adapts established open-source projects to legal workflows. We have benefited greatly from their authors' work. By sharing our changes, we hope to contribute back, help developers understand the project, and introduce **江西前进系统工程有限公司 (Jiangxi Qianjin System Engineering Co., Ltd.)** to teams with related needs. The Chinese name is the company's legal name; the English rendering here is descriptive.

At the time of this initial open-source preparation, the project had generated no revenue. **This does not remove licensing, copyright, data protection or other legal obligations, and does not impose a non-commercial-only restriction.** You are welcome to study, modify, deploy and contribute under the applicable licenses.

## What is in the codebase

| Workflow | Implemented areas | Boundaries |
| --- | --- | --- |
| Case workspace | Case details, linked materials, task checklists, reminders and time records | Operators configure accounts, permissions and backups |
| Material review | Uploads and versions, parsing jobs, source anchors, fact and timeline review | Scans, OCR and parsing quality depend on the input and runtime |
| Bank statement review | Extraction, pending items, manual change records and export | Amounts, duplicate transactions and aggregation rules require human verification |
| Contract review | Model-assisted issues, suggested edits and available citations | Suggestions do not constitute final legal review |
| Drafting and templates | Drafting, template types and source files, legal opinions | Users verify facts, formatting, sources and applicable law |
| Research and knowledge | Fact-based searches, knowledge retrieval, research reports and citation metadata | No commercial legal database, subscription or third-party data license is included |
| Administration | Models, providers, assistants, roles, menus, storage and system settings | Operators supply external accounts, credentials and service fees |

This is an evolving engineering project. Plans in the repository are not promises of implemented or accepted functionality. See the [preparation report](lawyer-ai-center-web/docs/open-source/PREPARATION-REPORT.md) for the actual verification scope. This repository includes the web user frontend, administration frontend and backend; the adjacent mobile project is not included.

## Stack and repository layout

| Component | Stack and requirements |
| --- | --- |
| Backend `ruoyi-ai/` | Java 17+, Maven 3.9+, Spring Boot 3.5.8, MyBatis-Plus, Sa-Token, LangChain4j |
| Database and cache | MySQL **8.0.30+**, Redis; `utf8mb4` SQL, not for MySQL 5.7 or MariaDB |
| User frontend `ruoyi-web/` | Vue 3, TypeScript, Vite, Element Plus, Pinia; Node.js ≥22.13.0, pnpm **11.0.9** |
| Administration `ruoyi-admin/` | Vue 3, Vben, Ant Design Vue; Node.js ≥20.10.0, pnpm **10.14.0** |
| Feature-dependent services | Your own model service and object storage; a vector service when using vector retrieval |

```text
.
├── ruoyi-ai/                 Backend and database scripts
│   └── docs/script/sql/
│       ├── ruoyi-ai.sql      The ONLY complete SQL needed for a fresh installation
│       ├── bootstrap/        Reviewed baselines and build manifest for maintainers
│       └── update/           Incremental migrations for existing installations
├── ruoyi-web/                Web user frontend
├── ruoyi-admin/              Administration frontend and shared packages
├── docs/open-source/         Disclaimer, release checklist and verification evidence
├── scripts/open_source/      Bootstrap generation, password setup and verification tools
├── LICENSE                   MIT license for the project's own code
└── NOTICE                    Upstream attribution and ownership notices
```

## Run from source

Start in the **repository root** after downloading or cloning this repository. Use an isolated local environment for the first run. Each frontend retains its own pnpm version and lockfile; do not create another dependency setup at the root.

### 1. Initialize the database with one SQL file

The complete bootstrap is **[`ruoyi-ai/docs/script/sql/ruoyi-ai.sql`](ruoyi-ai/docs/script/sql/ruoyi-ai.sql)**. It includes the current main schema, legal-workflow migrations, menus, dictionaries, template types and the `snail_job` scheduler schema. **A fresh installation does not require any other SQL file.**

It creates `ruoyi-ai-lawyer` and `snail_job`. Both database names must be absent. It does not drop existing databases or tables, and fails if a database already exists. Use a client that stops on the first error; **never use `--force`**. Existing installations must follow the [SQL guide](ruoyi-ai/docs/script/sql/README.md) instead of rerunning the bootstrap. MySQL DDL does not provide a transaction spanning this entire file.

The public bootstrap has **no shared administrator password**: `admin` is locked by default. Choose a private password locally and prepare a complete local copy:

```bash
# Python 3.10+; bcrypt is only needed by the local password preparation tool
python -m pip install bcrypt
python scripts/open_source/configure_admin.py
```

The tool prompts without echoing the password (12+ characters) and writes only its BCrypt hash to `tmp/yulvxing-init.local.sql`, never the plaintext. **Execute only this generated file; do not execute the public original first.** Alternatively, put your own BCrypt hash into the variable marked `INSTALLER_BCRYPT_HASH` at the top of the public SQL; BCrypt cost 10–16 is accepted.

Linux / macOS:

```bash
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p < tmp/yulvxing-init.local.sql
```

Windows PowerShell (raw redirection through `cmd` preserves the SQL's UTF-8 bytes):

```powershell
cmd /c 'mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p < tmp\yulvxing-init.local.sql'
```

Proceed only after exit code 0 and `YULVXING_DATABASE_INITIALIZATION_COMPLETE`. The local copy contains a password hash and is Git-ignored; do not publish it. Importing the public original still creates the schema but leaves `admin` locked; see the [SQL guide](ruoyi-ai/docs/script/sql/README.md) for recovery.

No real cases, conversations, source documents, commercial knowledge base or usable API credentials are included. The example model and all four legal assistants start disabled. The scheduler has no preconfigured users, access tokens or running jobs. Database initialization alone does not configure models, file storage or the complete application.

### 2. Configure and start the backend

Use the existing environment-variable entry points in [`application-prod.yml`](ruoyi-ai/ruoyi-admin/src/main/resources/application-prod.yml). **Spring Boot does not automatically load a root `.env` file**; inject variables through your shell, container or process manager:

| Variable | Value |
| --- | --- |
| `DB_URL` | For example, `jdbc:mysql://127.0.0.1:3306/ruoyi-ai-lawyer?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai` |
| `DB_USERNAME` / `DB_PASSWORD` | Your application database user and password; avoid root at runtime |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Your Redis endpoint and credentials |
| `REDIS_SSL` | Match your service; production defaults to `true`, so explicitly use `false` for a local service without TLS |
| `JWT_SECRET` | A strong, newly generated random secret |
| `MONITOR_USERNAME` / `MONITOR_PASSWORD` | Your own values; placeholders still require values although the monitoring client is disabled |
| `UPLOAD_PATH` | An absolute, persistent directory writable by the service account |
| `WEB_ALLOWED_ORIGINS` | Actual frontend origins, comma-separated, e.g. `http://127.0.0.1:5137,http://127.0.0.1:5666` |

```bash
cd ruoyi-ai
# Packaging only; release testing is described in the checklist
mvn -Pprod -pl ruoyi-admin -am package -DskipTests
java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=prod
```

The default backend port is `6039`. Update frontend proxies if you change it. An optional development configuration template is provided in [`application-dev.yml.example`](lawyer-ai-center-web/docs/open-source/config/application-dev.yml.example); keep actual development credentials private.

### 3. Start the two frontends

Use two terminals, each starting at the repository root. The commands below use `npx` to obtain the specified **pnpm tool**; dependency installation is performed by pnpm.

User frontend:

```bash
cd ruoyi-web
cp .env.development.example .env.development
npx --yes pnpm@11.0.9 install --frozen-lockfile
npx --yes pnpm@11.0.9 dev --host 127.0.0.1 --port 5137
```

Administration frontend:

```bash
cd ruoyi-admin
cp apps/web-antd/.env.example apps/web-antd/.env
cp apps/web-antd/.env.development.example apps/web-antd/.env.development
npx --yes pnpm@10.14.0 install --frozen-lockfile
npx --yes pnpm@10.14.0 dev:antd
```

In PowerShell, replace `cp` with `Copy-Item`. Open the user frontend at `http://127.0.0.1:5137` and administration at `http://localhost:5666`. Log in as `admin` with the password you selected. Both example API configurations target `127.0.0.1:6039`.

For production, create each `.env.production` from its corresponding `.env.production.example`, retain the administration `.env`, then run `pnpm build` and `pnpm build:antd` respectively. Deploy with a reverse proxy, HTTPS, backups and least privilege. Historical upstream READMEs, demos and container references remain for provenance; **they are not YuLvXing release services or binary images**.

### 4. Configure models, storage and assistants

1. Configure your own provider, actual model ID, endpoint and credentials in model management. The disabled `configure-your-model` example is not callable; local models also require a running service.
2. Bind working models and authorized knowledge sources to `法律咨询助手` (legal consultation), `合同审查官` (contract review), `文书起草助手` (drafting) and `法律意见书助手` (legal opinion), then enable each assistant. See [model configuration](ruoyi-ai/docs/model-configuration.md).
3. Configure your own object storage, bucket and access policy. Uploading, parsing and file export may be unavailable until storage is configured. Configure vector services and embedding models when using RAG.
4. Verify login, case creation, uploading, parsing, human review, drafting and export using your own non-sensitive test materials before accepting the deployment.

Local deployment defaults to `LAWYER_BILLING_ENABLED=false`, with charging and quota deductions disabled. Model providers may still charge for usage. Billing code and tables do not imply project revenue, live payments or an approved commercial service. See [local billing mode](ruoyi-ai/docs/local-deployment-billing.md). Sending sensitive material to external models still requires authorization and the server's outbound policy checks.

## Acknowledgements and origins

**Our sincere thanks to [ruoyi-ai](https://github.com/ageerle/ruoyi-ai), its author [ageerle](https://github.com/ageerle) and contributors; and to [RuoYi](https://github.com/yangzongzhuan/RuoYi), its author [yangzongzhuan](https://github.com/yangzongzhuan) and contributors.** Their shared work provides important foundations for this project.

We also thank RuoYi-Vue-Plus, the RuoYi AI user and administration frontends, ruoyi-element-ai / HeJiaYue520, Vben, and the authors of the other dependencies. Sources, ownership and license copies are recorded in [NOTICE](NOTICE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

YuLvXing is a derivative maintained by Jiangxi Qianjin System Engineering Co., Ltd., **not an official release of those upstream projects**. Upstream authors do not thereby endorse, warrant or take responsibility for our changes, operations, services or AI output. Report YuLvXing-specific issues to **this repository** or Qianjin, rather than asking upstream authors to support our derivative.

## Licensing, disclaimer and data protection

New code that the company is entitled to license is provided under the **[MIT License](LICENSE)**. Upstream code, third-party components and non-code materials retain their respective licenses and ownership. MIT allows use, modification and commercial redistribution subject to retaining the relevant copyright and license notices. The company presentation in this README adds no mandatory promotional-link condition to the code license. Company names, logos, trademarks, third-party content and data are not automatically licensed merely because the code is open source.

To the maximum extent permitted by applicable law, the software is supplied as is, without warranties of accuracy, fitness, non-infringement, availability or security. **A disclaimer does not excuse infringement or exclude liability that cannot lawfully be excluded.** This project and its AI output are work aids, not formal advice for a particular case, professional representation or a guarantee of results. They do not replace independent legal judgment and human review.

Users must verify facts, amounts, deadlines, the current validity of law and original sources, and obtain the rights needed for case materials, personal information, confidential information, models, databases and document assets. Do not send sensitive material externally without authorization. See the [full disclaimer](lawyer-ai-center-web/docs/open-source/DISCLAIMER.md) and [security policy](SECURITY.md). No claim of zero infringement risk is made.

## Contributing and releasing

Reproducible issues, documentation fixes and pull requests are welcome; read [CONTRIBUTING.md](CONTRIBUTING.md). Never post real case materials, credentials or personal information in issues, commits, logs or screenshots. Report security vulnerabilities and rights concerns privately by email.

Before the first public release, maintainers must complete the [release checklist](lawyer-ai-center-web/docs/open-source/RELEASE-CHECKLIST.md), particularly secret scanning of Git history, asset authorization, dependency licensing and actual deployment acceptance. SQL and tooling verification is not a complete software, security or legal audit.

## Contact Jiangxi Qianjin System Engineering Co., Ltd.

| Channel | Public company information |
| --- | --- |
| Website | **[https://www.qjsec.com](https://www.qjsec.com)** |
| Technical hotline / project enquiries | **+86 792 2199711** |
| After-sales service | **+86 792 2199719** |
| Company email / open-source and rights enquiries | **[jjqianjin@163.com](mailto:jjqianjin@163.com)** |
| Address | 11F, Building A, Huishang Business Center, 211 Xingye Avenue, Balihu, Jiujiang, Jiangxi, China (江西省九江市八里湖兴业大道211号徽商商务中心A座11楼) |

<img src="lawyer-ai-center-web/docs/open-source/assets/qianjin-wechat.jpg" alt="Qianjin official WeChat public account QR code" width="160">

Contact details and the WeChat QR code were verified against the [company website](https://www.qjsec.com) on 2026-10-30. Refer to that website for changes. Follow this repository, share your experience, and introduce Qianjin to teams that may benefit from our services.
