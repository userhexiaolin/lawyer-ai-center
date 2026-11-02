# 本地 MinIO 存储

本地服务已启动，项目默认 OSS 已通过管理接口切换到 MinIO，数据库和缓存均已更新，无需改动上传业务代码或重启后端。

| 项目 | 当前值 |
| --- | --- |
| 安装目录 | `D:\Program Files\minio` |
| 数据目录 | `D:\Program Files\minio\data` |
| S3 API | `http://127.0.0.1:9000` |
| 控制台 | `http://127.0.0.1:9001` |
| OSS 配置 | ID `1`，key `minio`，默认状态 `0` |
| 存储桶 | `ruoyi`，私有 |
| endpoint / HTTPS / region | `127.0.0.1:9000` / `N` / `us-east-1` |
| 应用账号 | `lawyer-ai-app`，仅授予 ruoyi 桶读写、删除及分片上传权限 |
| 管理账号 | `lawyer-local-admin` |

项目中实际使用应用账号，不使用 MinIO 管理员账号。随机密码未写入源代码；安装目录 `local-runtime` 下的 `root-credential.xml` 和 `app-credential.xml` 使用 Windows DPAPI 加密，仅当前 Windows 用户可以解密。目录权限限定为当前用户及 SYSTEM。原项目 MinIO 配置已备份为 `project-oss-before.xml`，原默认配置标识备份为 `project-default-before.json`。腾讯云配置仍保留，但不再是默认存储。

## 重启与管理

当前服务在后台运行。电脑重启后，在项目根目录的 PowerShell 中执行：

```powershell
.\scripts\minio\Start-LocalMinio.ps1
```

脚本发现服务已运行时直接返回，否则读取安装目录中的加密凭据并在当前终端运行 MinIO；保持该终端开启，Ctrl+C 可停止。未注册 Windows 开机启动服务。

控制台管理密码可由当前 Windows 用户在本机查看（不要复制进源码或提交）：

```powershell
(Import-Clixml -LiteralPath 'D:\Program Files\minio\local-runtime\root-credential.xml').GetNetworkCredential().Password
```

服务和凭据不会随项目构建发布。若在另一台电脑部署，需要重新初始化该电脑的 MinIO 和项目 OSS 配置。当前地址仅用于本机，跨设备联调时需使用设备可访问的地址。

## 验证

本次通过真实 `/resource/oss/upload` 上传了 DOCX、PDF、PNG、JPG，各返回业务码200；返回的私有签名链接下载HTTP200，SHA-256与原文件一致。去除签名后请求返回403。未登录和错误 clientid 的请求均返回业务码401（此后端错误响应HTTP状态仍为200）。临时普通用户使用 PC clientid 上传DOCX也成功，下载字节一致。测试文件、对象记录和临时用户均已清理。

详细机器验证结果见 `verification.json`，其中不含访问令牌、密钥或签名链接。

人工验证：管理端进入“系统管理 → 对象存储配置”，确认 minio 为默认配置；PC用户端进入 `/contract-review`，上传 `ruoyi-web/tests/fixtures/sample-contract.docx`，确认进度、上传成功、读取正文及开始审查流程。