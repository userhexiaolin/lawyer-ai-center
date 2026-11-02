# 第三方开源来源与致谢 / Third-party notices

与律行基于社区已有成果进行二次开发。**感谢 ruoyi-ai 作者 ageerle、RuoYi 作者 yangzongzhuan 及各项目贡献者。** 本文件记录主要来源和可核实的许可，不替代对应许可证，也不宣称所有依赖均为 MIT。

YuLvXing is a derivative of community projects. We thank their authors and contributors. This inventory records principal origins and available notices; each component retains its own license.

## 主要来源 / Principal upstream projects

下列许可证于 2026-10-30 对照公开上游原文核对。版权行按原文保留，不将公司名称替换为原作者。目录内原有许可证和源文件作者注释保持有效。

| 项目 / Project | 作者与来源 / Attribution and source | 本地许可证副本 / License copy | 关系与依据 / Relationship |
| --- | --- | --- | --- |
| ruoyi-ai | [ageerle 与贡献者](https://github.com/ageerle/ruoyi-ai) | [MIT · Copyright (c) 2026 ruoyi-ai](docs/open-source/licenses/ruoyi-ai-MIT.txt) | 后端直接开发基础；保留 `ruoyi-ai/LICENSE` |
| RuoYi | [yangzongzhuan 与贡献者](https://github.com/yangzongzhuan/RuoYi) | [MIT · Copyright (c) 2018 RuoYi](docs/open-source/licenses/RuoYi-MIT.txt) | 若依生态与基础框架来源；明确致谢，不声称当前工程等同其官方实现 |
| RuoYi-Vue-Plus | [Lion Li / Dromara 社区与贡献者](https://github.com/dromara/RuoYi-Vue-Plus) | [MIT · Copyright (c) 2019 RuoYi-Vue-Plus](docs/open-source/licenses/RuoYi-Vue-Plus-MIT.txt) | 上游 README 列出的框架基础；当前代码仍包含相应作者与框架痕迹 |
| ruoyi-web | [ageerle 与贡献者](https://github.com/ageerle/ruoyi-web) | [MIT · Copyright (c) 2026 ruoyi-ai](docs/open-source/licenses/ruoyi-web-MIT.txt) | 用户端来源，原许可文件名为小写 `license` |
| ruoyi-admin | [ageerle 与贡献者](https://github.com/ageerle/ruoyi-admin) | [MIT · Copyright (c) 2026 ruoyi-ai](docs/open-source/licenses/ruoyi-admin-MIT.txt) | 管理端来源；保留 `ruoyi-admin/LICENSE` |
| ruoyi-element-ai | [HeJiaYue520 与贡献者](https://github.com/element-plus-x/ruoyi-element-ai) | [MIT · Copyright (c) 2025 HeJiaYue520](docs/open-source/licenses/ruoyi-element-ai-MIT.txt) | 用户端 `package.json` 的作者、homepage 与 repository 标明该来源，补充其原始署名 |
| Vue Vben Admin | [Vben 与贡献者](https://github.com/vbenjs/vue-vben-admin) | [MIT · Copyright (c) 2024-present, Vben](docs/open-source/licenses/vue-vben-admin-MIT.txt) | 管理端工作区、共享包和模板基础，补充其原始署名 |

公开核对路径分别为：[ruoyi-ai LICENSE](https://github.com/ageerle/ruoyi-ai/blob/main/LICENSE)、[RuoYi LICENSE](https://github.com/yangzongzhuan/RuoYi/blob/master/LICENSE)、[RuoYi-Vue-Plus LICENSE](https://github.com/dromara/RuoYi-Vue-Plus/blob/5.X/LICENSE)、[ruoyi-web license](https://github.com/ageerle/ruoyi-web/blob/main/license)、[ruoyi-admin LICENSE](https://github.com/ageerle/ruoyi-admin/blob/main/LICENSE)、[ruoyi-element-ai LICENSE](https://github.com/element-plus-x/ruoyi-element-ai/blob/main/LICENSE)、[Vben LICENSE](https://github.com/vbenjs/vue-vben-admin/blob/main/LICENSE)。

这些链接用于溯源，分支可能继续变化。当前本地衍生代码与每个上游的**精确基线提交尚未逐一建立映射**；发布版本时应补记实际来源提交/标签，而不是把上游今天的主分支当作本项目的开发基线。许可证原文复制与来源表核对，不等于已经完成逐文件或完整传递依赖审计。

## 使用和分发义务 / Redistribution

- MIT 代码的使用和商业分发须遵守原文要求，保留相关版权与许可声明。根目录 [LICENSE](LICENSE) 和 [NOTICE](NOTICE) 补充公司贡献及来源，不覆盖目录内与文件内的原有声明。
- 公司仅对其有权许可的贡献作出授权；不能将第三方代码、模板、字体、模型、数据或商标不加区分地重新授权为公司所有。
- 分发源码、构建产物、容器或安装包时，应随附对应许可证、NOTICE 及其他适用声明。其他许可证可能要求额外文本、修改说明或其他履行方式，须按实际组件核实。
- 未盈利、致谢、开源或添加免责声明均不能替代授权，也不能免除侵权或法定责任。
- 欢迎介绍与律行或前进公司，但不强制 MIT 使用者保留推广链接，不以额外“禁止商用”条件改变 MIT 许可。

Retain applicable copyright and license notices for MIT material. Preserve other components' separate obligations when distributing source or binaries. The company's license covers only material it is entitled to license. Acknowledgement, lack of revenue and disclaimers do not excuse infringement or remove statutory obligations. No promotional-link retention or non-commercial restriction is added to MIT code.

## 依赖与非代码素材 / Dependencies and assets

Java 依赖以 [`ruoyi-ai/pom.xml`](ruoyi-ai/pom.xml) 及各子模块 POM 为入口；用户端和管理端分别以各自 `package.json`、[`ruoyi-web/pnpm-lock.yaml`](ruoyi-web/pnpm-lock.yaml) 与 [`ruoyi-admin/pnpm-lock.yaml`](ruoyi-admin/pnpm-lock.yaml) 为准。Vue、Element Plus、Vben、Ant Design Vue、Spring、Sa-Token、LangChain4j、文档解析库和各 SDK 等均保留其各自权利与许可证；本表不是它们完整的版本清单或许可证判定。

本地密码配置工具按需使用 [pyca/bcrypt](https://github.com/pyca/bcrypt)（Apache-2.0），通过包管理器安装，不将其源码或二进制随本仓库复制。隔离 SQL 验证器另按需使用 [PyMySQL](https://github.com/PyMySQL/PyMySQL)（MIT），同样通过包管理器安装，不随本仓库复制。未来如打包工具运行时，应同时核对其实际安装版本和分发义务。

前进公司 LOGO、公众号二维码等见[素材来源](docs/open-source/ASSETS.md)。商业法律数据、外部文档、图片、图标、字体和模型权重不因代码开源而自动获得再分发授权。发布前完成[清单](docs/open-source/RELEASE-CHECKLIST.md)中的完整依赖和素材核查。

## 独立维护与反馈 / Independent maintenance

本项目不是上述上游的官方版本，不暗示赞助、合作、认可或上游作者对衍生项目的担保。与律行的运营、修改和支持由本项目维护者负责安排，问题请在当前仓库提出。安全与权利反馈请联系 **[jjqianjin@163.com](mailto:jjqianjin@163.com)**。

This is not an official upstream release. No endorsement, sponsorship, partnership or upstream warranty is implied. Direct derivative-specific support, security and rights enquiries to this project and the company contact above.
