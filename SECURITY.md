# 安全政策 Security Policy

AgriMart（农惠达）当前是农资电商教学与演示项目，涉及账号、商品、订单、评价和收货信息。本政策说明当前安全边界及漏洞报告方式；MIT 许可证允许商业使用，并不代表现有实现具备生产安全保障。

AgriMart is currently an educational and demonstration project for agricultural e-commerce, handling accounts, products, orders, reviews, and delivery information. This policy describes its current security boundaries and vulnerability reporting process. The MIT license permits commercial use, but does not mean the current implementation provides production security assurances.

## 维护范围 Maintenance Scope

安全修复面向上游默认分支的最新代码，以及仓库维护的前端、后端和开发配置。当前不提供历史版本的安全回补或固定响应、修复时限。报告请注明提交号；依赖本身的漏洞也可报告其对本项目的实际影响，第三方组件的根本修复由其上游维护。

Security fixes target the latest code on the upstream default branch and the frontend, backend, and development configuration maintained in this repository. Security backports for older versions and fixed response or resolution deadlines are not currently provided. Include the commit identifier in your report. You may also report the practical impact of dependency vulnerabilities on this project; fixes to third-party components themselves are maintained by their respective upstream projects.

## 当前已知边界 Current Known Boundaries

以下结论来自当前源码与配置；修复后应同步更新本节。

The following findings are based on the current source code and configuration. Update this section when fixes are made.

- `SecurityConfig` 对所有请求执行 `permitAll` 并关闭 CSRF；登录只返回用户信息，没有完整的业务令牌签发、验证和服务端访问控制。

  `SecurityConfig` applies `permitAll` to every request and disables CSRF. Login returns only user information; complete application token issuance, validation, and server-side access control are not implemented.

- `userId`、`merchantId` 等由客户端传入，部分操作缺少角色及资源归属校验；注册角色尚无服务端白名单限制。localStorage 中的用户对象及前端角色按钮不能证明调用者身份。

  Parameters such as `userId` and `merchantId` come from the client, and some operations lack role and resource ownership checks. Registration roles are not yet restricted by a server-side allowlist. A user object in localStorage and role-based frontend buttons do not establish the caller's identity.

- 订单状态更新缺少状态转换限制，库存扣减缺少并发保护；这些缺陷可能破坏订单、库存和交易数据的一致性。当前“支付”等操作仅为状态模拟，没有真实支付对接。

  Order status updates lack transition restrictions, and stock deductions lack concurrency protection. These defects may compromise the consistency of order, inventory, and transaction data. Operations such as "payment" currently simulate status changes and do not integrate with a real payment provider.

- Compose 和应用配置包含公开的开发数据库凭据；数据库默认端口映射 `3307:3306` 未限定回环地址。默认配置同时开启 API 文档、SQL 输出及调试日志，并使用 `ddl-auto=update`。

  Compose and application configuration contain publicly known development database credentials. The default database port mapping, `3307:3306`, is not restricted to the loopback address. The default configuration also enables API documentation, SQL output, and debug logging, and uses `ddl-auto=update`.

- 当前没有预置演示账号初始化器；旧 README 中的账号信息不能作为身份认证或安全措施使用。

  There is currently no initializer for predefined demo accounts. Account information in older README files must not be treated as authentication or a security measure.

这些边界意味着当前默认配置不适合直接暴露到公网、处理真实交易或保存真实个人信息。请使用隔离的本地环境和虚构数据。针对上述已知问题的补充影响、绕过方式或新的漏洞仍欢迎报告，不必因为它们属于 Demo 而忽略安全问题。

These boundaries make the current default configuration unsuitable for direct public internet exposure, real transactions, or storage of real personal information. Use an isolated local environment and fictional data. Reports of additional impact, bypasses, or new vulnerabilities related to these known issues remain welcome. Security concerns should not be dismissed because this is a demo.

## 私密报告漏洞 Reporting Vulnerabilities Privately

请先访问 [仓库 Security Advisories](https://github.com/Ivanluv555/agrimart/security/advisories)。**如果页面提供 “Report a vulnerability” 按钮**，使用该入口向维护者提交私密报告；本文件不表示仓库已经启用该功能。

First visit the [repository's Security Advisories](https://github.com/Ivanluv555/agrimart/security/advisories). **If the page provides a "Report a vulnerability" button**, use it to submit a private report to the maintainers. This document does not imply that the repository has enabled this feature.

如果入口不可用，本仓库目前没有在文档中公布专用安全邮箱。请在 [项目 Issue](https://github.com/Ivanluv555/agrimart/issues) 中仅发起“请求私密安全联系渠道”的联系请求，不要包含漏洞细节、受影响接口、利用代码、截图或个人数据；待维护者提供私密渠道后再提交详细报告。这一后备方式与 [GitHub 的私密漏洞报告指引](https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/report-privately) 一致。

If that option is unavailable, this repository has not currently documented a dedicated security email address. Open a [project issue](https://github.com/Ivanluv555/agrimart/issues) only to request a private security contact channel. Do not include vulnerability details, affected endpoints, exploit code, screenshots, or personal data. Submit the detailed report after a maintainer provides a private channel. This fallback follows [GitHub's guidance on reporting vulnerabilities privately](https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/report-privately).

不要将未公开漏洞直接提交为公开 Issue、PR 或讨论，也不要向维护者发送仍有效的生产凭据。

Do not disclose unpublished vulnerabilities directly in public issues, pull requests, or discussions. Do not send maintainers active production credentials.

### 报告内容 What to Include in a Report

- 受影响的提交号、组件、依赖版本与运行环境。

  The affected commit identifier, components, dependency versions, and runtime environment.

- 漏洞类型、触发前提和可验证的影响，包括是否涉及越权、个人信息或数据完整性。

  The vulnerability type, prerequisites, and verifiable impact, including whether it involves unauthorized access, personal information, or data integrity.

- 在本地或明确授权环境中的最小复现步骤、请求与响应，以及预期和实际结果。

  Minimal reproduction steps, requests, and responses from a local or explicitly authorized environment, together with expected and actual results.

- 已脱敏的日志、截图或最小验证代码；如有修复建议可一并提供。

  Redacted logs, screenshots, or minimal proof-of-concept code. Include suggested fixes if available.

- 是否已向其他方报告，以及期望的公开署名方式；可选择不署名。

  Whether you have reported the issue to anyone else and how you would like to be credited publicly. You may choose to remain uncredited.

无需提供完整攻击链或大量真实数据。若发现真实凭据或个人信息，停止进一步访问，只记录定位所需的最少信息并私密告知维护者。

You do not need to provide a complete attack chain or large amounts of real data. If you encounter real credentials or personal information, stop accessing further data, record only the minimum information needed to locate the issue, and inform the maintainers privately.

## 处理与披露 Handling and Disclosure

维护者收到私密报告后，应确认影响范围、尝试复现，并与报告者沟通修复及验证方式。修复应包含与影响相匹配的回归验证；需要配置调整、凭据轮换或数据迁移时，应向受影响使用者说明。

After receiving a private report, maintainers should determine the scope of impact, attempt reproduction, and discuss remediation and verification with the reporter. Fixes should include regression checks appropriate to their impact. Affected users should be informed when configuration changes, credential rotation, or data migration are required.

在修复与缓解方案准备好后，维护者与报告者协调披露时间和细节，并按报告者意愿致谢。不要在协调完成前公开可利用细节或敏感数据。当前没有漏洞奖励计划，也不承诺固定的处理时限。

Once fixes and mitigations are ready, maintainers and the reporter should coordinate the timing and details of disclosure and acknowledge the reporter according to their wishes. Do not publish exploitable details or sensitive data before that coordination is complete. There is currently no bug bounty program or guaranteed handling deadline.

本政策不授予对第三方部署的测试权限。请只在自己控制或明确获授权的环境中验证，避免破坏数据、影响服务可用性或访问他人信息。

This policy does not authorize testing of third-party deployments. Verify issues only in environments you control or are explicitly authorized to test, and avoid damaging data, affecting service availability, or accessing other people's information.

## 本地开发与后续加固 Local Development and Further Hardening

- 将应用和数据库限制在可信开发网络，必要时把数据库端口绑定到回环地址；不要复用公开的演示凭据。

  Restrict the application and database to a trusted development network. Bind the database port to the loopback address when needed, and do not reuse publicly known demo credentials.

- 通过环境变量或本地配置覆盖后端凭据，不提交真实账号、令牌、私钥、数据库导出或完整收货信息。`VITE_` 环境变量是客户端公开配置，不可保存秘密。

  Override backend credentials through environment variables or local configuration. Do not commit real accounts, tokens, private keys, database exports, or complete delivery details. `VITE_` environment variables are public client configuration and must not contain secrets.

- 实现可信服务端认证、最小权限、注册角色限制和资源归属校验；根据最终认证方案配置 CSRF、跨域和会话策略。

  Implement trusted server-side authentication, least privilege, registration role restrictions, and resource ownership checks. Configure CSRF, cross-origin, and session policies according to the final authentication design.

- 为订单状态、金额、库存、重复请求与并发操作建立服务端规则及回归验证。

  Establish server-side rules and regression checks for order status, monetary amounts, stock, repeated requests, and concurrent operations.

- 面向真实部署时另行完成 HTTPS、数据库访问限制、可审查迁移、日志脱敏、备份恢复和依赖安全检查；不能只靠更改配置密码替代这些工作。

  Before a real deployment, also establish HTTPS, restricted database access, reviewable migrations, log redaction, backup and recovery, and dependency security checks. Changing configuration passwords alone does not replace this work.

如果真实秘密已经进入 Git 历史，应先吊销或轮换，再协调清理历史。仅删除当前文件不能消除已经泄露的风险。日常贡献与验证流程见 [贡献指南](CONTRIBUTING.md)。

If a real secret has entered Git history, revoke or rotate it first, then coordinate history cleanup. Deleting it from the current file does not eliminate the risk of prior exposure. See the [contribution guide](CONTRIBUTING.md) for everyday contribution and verification procedures.
