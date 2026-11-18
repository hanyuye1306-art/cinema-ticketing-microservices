# 前台注册与邮箱找回密码

前台入口：登录页 `/login`、注册页 `/register`、找回密码页 `/forgot-password`。

## 使用流程

- 注册：填写用户名、本人邮箱、密码和确认密码，完成现有拼图验证码后创建普通用户（角色固定为 3），成功后返回登录并带入用户名。
- 找回密码：输入用户名和注册时绑定的邮箱，完成拼图，获取邮件中的 6 位验证码，然后设置新密码。
- 新增接口由 `gouyan-auth` 提供，已有 Gateway `/sysUser/**` 路由可直接转发，无需修改网关或数据库结构。
- 邮件未启用或 SMTP 配置错误时页面明确显示错误，不会显示“密码已重置”。用户名/邮箱不匹配时使用统一的条件式提示，避免泄露账号是否存在。

| 接口 | 方法 | 请求字段 |
| --- | --- | --- |
| `/sysUser/register` | POST | userName、email、password、confirmPassword、sex、captchaVerification |
| `/sysUser/password/code` | POST | userName、email、captchaVerification |
| `/sysUser/password/reset` | POST | userName、email、code、password、confirmPassword |

## 配置真实 SMTP

### Windows QQ 邮箱本机配置

在项目根目录执行：

```powershell
powershell -NoProfile -File scripts/configure-mail.ps1
```

在本机窗口输入发件 QQ 邮箱和 SMTP 授权码（不是 QQ 登录密码）。工具会检查 `smtp.qq.com:465` 的 TLS 连接、服务器身份和 SMTP 认证，不发送邮件；验证成功后将凭据使用 Windows 当前用户/当前机器的 DPAPI 加密，保存到已被 Git 忽略的 `.runtime/smtp-credential.xml`，非敏感设置保存在 `.runtime/smtp-settings.json`。

`start-all.sh` 的 Windows 启动器会在启动认证服务时自动加载这套本机配置；macOS 继续使用下文中的环境变量，不需要复制 Windows 凭据文件。当前认证服务已经加载并启用经验证的 QQ SMTP 配置。

显式指定 `SMTP_ENABLED=false` 可关闭邮件服务；提供完整的 SMTP 环境配置时优先使用环境配置。凭据不能复制到另一台机器或另一 Windows 用户下解密，需要重新运行配置工具。

发件邮箱与账号绑定的收件邮箱可以不同。找回密码时必须填写该账号当前绑定的邮箱；请先在“个人中心—个人资料”确认邮箱属于本人且能正常收信，系统不会自动将发件邮箱写入用户资料。

### 通用环境变量配置

默认关闭邮件服务，正常登录和注册不依赖 SMTP。请在启动认证服务的终端设置环境变量，随后重启认证服务（9002）；已经运行的进程不会读取后来新增的变量。`start-all.sh` 遇到已监听的端口会复用服务，因此仅再次执行启动脚本不能更新配置。

不要把邮箱授权码提交到 Git，也不要把授权码贴入聊天。Spring 不会自动加载任意 `.env` 文件；下面示例直接设置进程环境变量。

PowerShell（587 / STARTTLS 示例）：

```powershell
$env:SMTP_ENABLED = 'true'
$env:SMTP_HOST = 'smtp.example.com'
$env:SMTP_PORT = '587'
$env:SMTP_USERNAME = 'your-mailbox@example.com'
$env:SMTP_PASSWORD = '在本机填写邮箱服务商的SMTP授权码'
$env:SMTP_FROM = 'your-mailbox@example.com'
$env:SMTP_AUTH = 'true'
$env:SMTP_STARTTLS = 'true'
$env:SMTP_SSL = 'false'
```

macOS / Git Bash：

```bash
export SMTP_ENABLED=true
export SMTP_HOST=smtp.example.com
export SMTP_PORT=587
export SMTP_USERNAME=your-mailbox@example.com
export SMTP_PASSWORD='在本机填写邮箱服务商的SMTP授权码'
export SMTP_FROM=your-mailbox@example.com
export SMTP_AUTH=true
export SMTP_STARTTLS=true
export SMTP_SSL=false
```

若服务商使用 465 / SSL，按其文档设置 `SMTP_PORT=465`、`SMTP_SSL=true`、`SMTP_STARTTLS=false`。`SMTP_FROM` 缺省使用 `SMTP_USERNAME`。连接、读取和写入超时均为 3 秒。

停止原认证服务后，可从 `gouyan-cloud/gouyan-auth` 目录启动：

```bash
mvn org.springframework.boot:spring-boot-maven-plugin:2.3.2.RELEASE:run
```

沿用项目的 JDK 11 和 `MYSQL_PASSWORD` 等既有环境配置。新增邮件依赖后，应先在 `gouyan-cloud` 执行 `mvn install -DskipTests` 并重启认证服务，避免旧 Maven 进程持有过时的依赖。若其他微服务仍使用旧框架，也应在维护窗口重新启动，以应用用户接口访问控制。

## 校验与访问控制

- 拼图凭据由服务端调用现有 `CaptchaService.verification` 二次校验，不能只伪造前端的成功事件。
- 验证码由安全随机数生成，绑定账号和数据库中的邮箱，服务端仅存加盐摘要；不通过 API 返回或记录验证码。
- 有效期 10 分钟，60 秒内不能重发；每账号 10 分钟最多 5 次发送申请，每来源地址最多 20 次申请。失败的重发不替换已生效的旧验证码。
- 每个验证码最多验证 5 次；成功后立即消费，过期、重放、邮箱不匹配和并发重复提交均不能再次修改密码。
- 密码沿用项目现有 BCrypt 存储。专用更新语句仅修改指定用户且邮箱仍匹配时的密码和盐，不改变角色、资料或其他用户数据。
- 普通用户注册接口不接收角色或用户 ID。原用户管理接口不再整段匿名放行；管理员可管理用户，普通用户仅能访问和修改自己，不能通过资料修改提升角色。
- 请求参数校验异常不再在应用错误日志中打印被拒绝的密码或验证码。

## 部署范围与限制

当前验证码和频率记录保存在单个认证服务的有界内存中，重启后失效。项目目前只有一个认证实例；增加认证实例前需将验证码、消费操作、频控和图形验证码缓存迁移到共享存储，不能仅复制实例。

来源地址使用服务端看到的 `remoteAddr`，不信任外部随意传入的转发头；经本机 Gateway 转发时，多个客户端可能共享同一频率额度。

沿用现有 JWT 的一小时过期策略；其他设备已签发的令牌不会因为此次密码重置立即失效。需要立即撤销所有会话时，应另行扩展统一的令牌撤销或凭据版本机制。

已有账号必须已绑定且能够访问邮箱才能自助找回；否则需由管理员线下核实身份。注册时收集的邮箱尚未另外执行激活邮件确认。

## 本次验证（Windows）

- 前台 `npm run build` 通过，保留原有资源体积提示。
- `mvn -pl gouyan-auth -am test`：22 项测试全部通过。
- 服务测试覆盖错误图形凭据、普通角色注册、验证码过期/重放、错误次数、邮箱/账号绑定、重发冷却、SMTP 失败、频控、并发消费、接口参数及用户越权防护。
- 使用仅监听本机的临时 SMTP 接收器实际接收 UTF-8 中文邮件，并用邮件中的验证码完成服务级重置测试；用户数据库操作由测试替身隔离，没有修改真实账号密码。
- 浏览器验证登录/注册/找回入口、注册必填、找回必填和邮箱格式、拼图弹窗及关闭后可重新操作；未代替真实用户完成验证码或重置真实账号。
- 最新认证服务实际验证：管理员可查询用户，匿名查询被拒绝；SMTP 未配置时明确报错。
- `bash scripts/smoke-test.sh` 全部通过，包含 Eureka 双节点、登录、业务查询、6 次 Feign、双实例 Ribbon 和两个前端 HTTP 检查。
- 尚未验证真实邮箱收信，也未实际运行 macOS；配置 SMTP 后需要用户完成一次真实邮箱的完整找回流程。

本次重要原文件备份位于 `.runtime/account-flow-backup`；验证日志位于 `.runtime/account-tests.log`、`.runtime/account-frontend-build.log`、`.runtime/account-smoke.log`。这些本地文件不提交到仓库。

## 用户资料保存修复

- 密码只写、不返回的规则保持不变。创建用户仍要求密码；更新用户允许省略密码或留空，并完整保留原密码哈希与盐；明确提供新密码时才执行 BCrypt 编码。
- 后台“修改用户”的密码框改为选填的新密码；资料编辑不再强制补填邮箱或手机。修改自己用户名时服务端返回新 JWT，两个前端先同步令牌，再继续请求。
- 前台增加邮箱/手机号编辑，正确处理新用户的空头像和空资料，稳定保留未替换的头像，上传失败不提交资料；重试保存复用已经上传的头像，避免重复上传和图片列表累积。
- 保存后同步 sessionStorage 和前台导航头像，显示服务端具体错误，而不是只显示通用“修改失败”。
- 新增 6 项后端回归测试，认证模块合计 28 项通过；前台 `npm run test:profile` 的 4 个场景覆盖两端保存、头像、重试和令牌更新。
- 两个前端生产构建通过；通过实际 Gateway 对当前用户执行不含密码、内容不变的资料保存，并回读确认资料不变、原密码仍可登录、响应不暴露密码或盐。完整 smoke 回归通过。
- QQ SMTP 已用实际加密保存的凭据验证认证通过；未发送测试邮件，真实收信仍需通过找回密码页面由账号本人确认。

资料保存修复前的工作区备份位于 `.runtime/profile-mail-backup`。相关日志：`.runtime/profile-mail-tests.log`、`.runtime/gouyan-user-profile-build.log`、`.runtime/gouyan-ui-profile-build.log`、`.runtime/profile-mail-smoke.log`。
