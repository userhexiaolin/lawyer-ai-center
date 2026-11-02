# 贡献指南 / Contributing

感谢愿意帮助改进与律行的贡献者。请先在**当前仓库**说明问题或变更范围，避免向上游项目请求处理本项目独有的修改。安全和权利问题按 [SECURITY.md](SECURITY.md) 私下反馈。

Please use this repository for YuLvXing-specific issues and changes. Report security and rights concerns privately as described in the security policy.

## 提交前

1. 确保您有权提交代码、文本、图片、字体、模板、测试数据与其他素材；标明外部来源，保留许可证和版权。不要复制来源不明的代码或闭源产品素材。
2. 不提交真实案件、客户资料、身份证号、银行账户、真实合同、登录记录、凭据或商业法律数据库内容。样例应为自行编写的合成数据，并注明其用途。
3. 阅读适用的 `AGENTS.md`。前端变更遵守 `docs/frontend/` 与现有技术栈；不在根目录新增依赖体系。用户端与管理端使用各自指定 pnpm 版本及锁文件。
4. 针对改动执行真实可复现的检查，说明命令、环境与结果；未执行写明“未验证 / NOT_RUN”，不要把构建成功当作业务验收。
5. 新增数据库迁移使用日期文件名，说明前置条件、影响和恢复办法；同步更新全量 SQL 生成器的迁移分类，运行 `python scripts/open_source/build_init_sql.py --check` 及隔离导入验证。

Submit only material you are entitled to contribute, retain provenance and license notices, use synthetic test data, follow repository instructions, and report reproducible validation. Keep incremental migrations and the single-file bootstrap in sync.

## Pull Request 建议说明

- 具体问题、触发条件与修改后行为。
- 修改范围、界面/数据/API/权限的影响，以及需要的迁移或配置。
- 实际执行的验证、未验证项与回滚方式。
- 引入的第三方代码或素材及相应许可证。

Explain the problem, resulting behavior, affected interfaces or data, actual validation, recovery plan and any new third-party material. Avoid unrelated formatting or generated-file churn.

## 贡献许可

除非您事先明确另行约定并经维护者接受，提交供本项目合并的原创贡献按本仓库 MIT 许可证提供；第三方内容仍适用原许可。此说明不是版权转让，不改变您或上游作者的原有权利，也不允许提交您无权许可的材料。

Unless separately agreed before acceptance, original contributions submitted for inclusion are offered under this repository's MIT license. Third-party material retains its original terms. This is not a copyright assignment and does not authorize contributions you have no right to license.
