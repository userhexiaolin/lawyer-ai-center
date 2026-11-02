# 安全政策 / Security policy

请将本项目的漏洞、疑似泄密或权利问题发送至 **[jjqianjin@163.com](mailto:jjqianjin@163.com)**，邮件主题建议使用 `[与律行安全]` 或 `[YuLvXing Security]`。该地址是公司公开邮箱，请先发送问题概要和安全联系方式；涉及真实材料、密钥或可利用细节时，先协商安全传输方式。

Report vulnerabilities, suspected disclosure and rights concerns to the company email above. Start with a summary and a safe way to contact you; agree on a secure channel before transferring sensitive evidence.

请提供受影响版本或提交、复现条件、影响范围和经过脱敏的证据。不要公开真实案件、个人信息、密码、访问令牌、私钥或完整攻击载荷。普通问题可以在当前仓库提交 Issue；请不要把本项目的衍生实现问题转交给上游作者。

Include the affected version or commit, prerequisites, impact and sanitized reproduction details. Do not disclose live case data, personal information, credentials, private keys or sensitive exploit details in public issues. Direct derivative-specific reports to this project.

## 维护范围 / Maintenance scope

目前没有独立的长期支持或安全响应时限承诺。维护者将根据可复现性、风险和维护能力安排处理；报告漏洞不构成有偿测试、漏洞赏金或对任何系统发起测试的授权。仅在您拥有或明确获授权的环境中复现。

There is no separate LTS policy, guaranteed response time or implied bug bounty. Reporting is not authorization to test systems you do not own or have permission to assess.

## 部署者需配置的边界 / Operator responsibilities

- 公开 SQL 不提供通用密码；管理员需配置自己的 BCrypt 散列。模型、助手、注册和调度默认不开放。
- 真实环境配置、数据库副本、上传目录、日志和本地安装 SQL 不应进入 Git。`.gitignore` 不会清除已经提交的内容或历史秘密。
- 数据库、Redis、存储、JWT、监控与可选加密配置分别使用独立凭据；任何前端 `VITE_*` 值都可能被打包到浏览器，不能承担服务端保密职责。
- 按最小权限部署，限制跨域来源、管理端和基础设施网络暴露，启用 HTTPS、备份与恢复演练。
- 外部模型、MCP、工作流工具和材料外发应满足服务端权限与准出策略；隐藏按钮不能替代服务端鉴权。
- 如真实密钥曾提交或外泄，先在服务商处撤销/轮换，再检查日志与历史；仅删文件或改成占位符并不充分。

The public bootstrap has no shared admin password. Keep runtime settings and data private, use separate credentials, restrict access, and preserve server-side authorization and outbound-data checks. Browser-bundled values are public. If a secret was committed, rotate or revoke it first; deleting a file does not remove it from Git history.

完整的首次发布核对见[发布检查清单](docs/open-source/RELEASE-CHECKLIST.md)。安全配置样例或静态检查不代表完成渗透测试、隐私合规或生产安全认证。
