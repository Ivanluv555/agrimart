# 贡献指南 Contributing Guide

欢迎参与 AgriMart（农惠达）。项目面向农业场景，目标是连接农户、农资商家与农机拥有者，探索农资采购、订单履约、评价和农机共享。目前以学习与演示为主要开发场景，贡献时请先阅读 [行为准则](CODE_OF_CONDUCT.md) 和 [安全政策](SECURITY.md)。

Welcome to AgriMart. The project focuses on agricultural use cases, aiming to connect farmers, agricultural supply merchants, and machinery owners through procurement, order fulfillment, reviews, and shared machinery. Development currently centers on learning and demonstrations. Before contributing, read the [code of conduct](CODE_OF_CONDUCT.md) and [security policy](SECURITY.md).

## 先了解当前实现 Understand the Current Implementation

当前是 React 前端与 Spring Boot 单体后端组成的前后端分离应用。后端包含用户、商品、订单和评价四个业务模块；购物车保存在前端内存中。开发起步阶段的目标是用户、农资买卖、支付三个微服务，以及统一网关和注册配置中心；当前原型尚未完成拆分。农机共享、真实资金和物流接口留待后续。

The application currently has a separate React frontend and a monolithic Spring Boot backend. The backend contains four business modules: users, products, orders, and reviews. The shopping cart is held in frontend memory. The initial development target is three microservices for users, agricultural trading, and payments, with a unified gateway and registration/configuration center. The prototype has not yet been split. Shared machinery, real funds, and carrier integrations remain future work.

目标 Web 使用 React + MUI，后续移动端使用 Flutter + Material，两端统一 Material 设计规范。现有 Ant Design 页面需要后续迁移，Flutter 工程尚未创建。

The target Web client uses React with MUI, and the future mobile client uses Flutter with Material components. Both follow a shared Material design system. Existing Ant Design pages still require migration, and the Flutter project has not been created.

- `agriapp/`：现有 React、TypeScript、Ant Design 原型，使用 Vite 和 npm；目标迁移为 MUI。

  `agriapp/`: The current React, TypeScript, and Ant Design prototype, using Vite and npm; the target component library is MUI.

- `agridemo/`：Spring Boot、Spring Data JPA 后端，使用 Maven Wrapper。

  `agridemo/`: The Spring Boot and Spring Data JPA backend, using Maven Wrapper.

- `agridemo/compose.yaml`：本地 MySQL 8.0 开发数据库。

  `agridemo/compose.yaml`: The local MySQL 8.0 development database.

- `docs/`：架构、API、数据库、验收与编码规范文档。

  `docs/`: Architecture, API, database, acceptance, and coding standards documentation.

- `agridemo/docs/`：选题与调研资料，包含尚未实现的方案。

  `agridemo/docs/`: Project proposal and research materials, including designs that have not been implemented.

- `agrimart-server/`：当前为空，没有构建或启动入口。

  `agrimart-server/`: Currently empty, with no build or startup entry point.

依赖与环境要求以 `package.json`、`package-lock.json`、`pom.xml` 和实际配置为准。旧 README 中的 Java 17、Spring Boot 3.2.5、预置测试账号和农机实体等描述与当前源码存在差异。提交前请核对源码，并参考 [项目文档索引](docs/00-项目文档索引.md) 和 [编码规范](docs/08-项目结构与编码规范.md)。

Use `package.json`, `package-lock.json`, `pom.xml`, and the actual configuration as the source of truth for dependencies and environment requirements. Older README descriptions of Java 17, Spring Boot 3.2.5, predefined test accounts, and machinery entities differ from the current source code. Before submitting changes, check the source and consult the [project documentation index](docs/00-项目文档索引.md) and [coding standards](docs/08-项目结构与编码规范.md).

## 适合参与的工作 Ways to Contribute

- 修正前后端响应码、分页结构、评价字段等接口契约差异。

  Resolve frontend/backend API contract differences, including response codes, pagination structures, and review fields.

- 完善服务端身份认证、角色与资源归属校验，以及注册角色限制。

  Improve server-side authentication, role and resource ownership checks, and registration role restrictions.

- 改进库存并发控制、订单状态流转、取消幂等和评价规则。

  Improve inventory concurrency control, order state transitions, cancellation idempotency, and review rules.

- 改善中文界面、移动端体验、无障碍交互、错误反馈和文档。

  Improve the Chinese interface, mobile experience, accessibility, error feedback, and documentation.

- 在明确需求与验收标准后扩展农机预约等农业业务。

  Extend agricultural workflows such as machinery booking once requirements and acceptance criteria are defined.

较大的功能、架构调整或依赖替换请先通过 Issue 说明场景、范围和验收方式，再着手实现。小型修复和文档改进可直接提交 Pull Request。

For larger features, architectural changes, or dependency replacements, first open an issue describing the use case, scope, and acceptance approach before starting implementation. Small fixes and documentation improvements may be submitted directly as pull requests.

## 准备本地环境 Set Up the Local Environment

需要 Git、JDK 25、Docker Compose，以及满足锁文件中 Vite/Oxlint 要求的 Node.js（当前为 `^20.19.0 || >=22.12.0`）和 npm。后端当前声明 Spring Boot 4.1.1，使用仓库自带的 Maven Wrapper，无须依赖全局 Maven。

You need Git, JDK 25, Docker Compose, npm, and a Node.js version meeting the Vite/Oxlint requirements in the lockfile, currently `^20.19.0 || >=22.12.0`. The backend currently declares Spring Boot 4.1.1 and uses the repository's Maven Wrapper, so a global Maven installation is not required.

请在隔离的本地环境中开发，只使用虚构数据。Compose 当前将数据库端口发布到主机，后端也没有完整认证授权；启动前确认开发主机的网络访问范围，具体边界见 [安全政策](SECURITY.md)。

Develop in an isolated local environment using only fictional data. Compose currently publishes the database port to the host, and the backend does not implement complete authentication and authorization. Check the development host's network exposure before starting the services. See the [security policy](SECURITY.md) for the specific boundaries.

### 后端与数据库 Backend and Database

从仓库根目录执行：

Run the following from the repository root:

```bash
cd agridemo
docker compose up -d mysql
```

等待数据库就绪后执行：

Once the database is ready, run:

```bash
./mvnw spring-boot:run
```

Windows 使用 `mvnw.cmd`。首次执行需要下载 Maven 和依赖。Compose 默认连接为 `localhost:3307/agri`，本地配置位于 `agridemo/src/main/resources/application.yaml`；不要把真实数据库密码写进这些受版本控制的文件。

On Windows, use `mvnw.cmd`. The first run needs to download Maven and dependencies. The default Compose connection is `localhost:3307/agri`, and local application configuration is in `agridemo/src/main/resources/application.yaml`. Do not put real database passwords in these version-controlled files.

后端 API 基址为 `http://localhost:8080/api`，Swagger UI 为 `http://localhost:8080/api/swagger-ui.html`。当前源码没有预置演示账号初始化器，请通过注册接口创建虚构账号，不要依赖旧 README 中的默认账号。

The backend API base URL is `http://localhost:8080/api`, and Swagger UI is available at `http://localhost:8080/api/swagger-ui.html`. The current source code has no initializer for predefined demo accounts. Create fictional accounts through the registration API rather than relying on default accounts listed in older README files.

### 前端 Frontend

另开终端，从仓库根目录执行：

Open another terminal and run the following from the repository root:

```bash
cd agriapp
npm ci
```

在 `agriapp/.env.local` 中设置公开的开发配置：

Set the following public development configuration in `agriapp/.env.local`:

```dotenv
VITE_API_BASE_URL=/api
```

随后启动：

Then start the development server:

```bash
npm run dev
```

访问 `http://localhost:5173`。使用 `/api` 可通过现有 Vite 代理访问后端；Axios 当前默认的绝对地址会绕过代理，可能引发跨域问题。`VITE_` 变量会进入客户端代码，不能用于保存秘密。

Visit `http://localhost:5173`. Using `/api` routes backend requests through the existing Vite proxy. The absolute URL currently used by Axios by default bypasses this proxy and may cause cross-origin issues. `VITE_` variables are included in client code and must not contain secrets.

## 编码与接口约定 Coding and API Conventions

- 延续当前结构：Java 公共设施放 `core/`，业务放 `module/<领域>/`，按 Controller、Service、Repository 分层；前端请求封装放 `src/api/`，类型放 `src/types/`。

  Follow the current structure: shared Java infrastructure belongs in `core/`, and business code belongs in `module/<domain>/`, separated into Controller, Service, and Repository layers. Frontend request wrappers belong in `src/api/`, and types belong in `src/types/`.

- 沿用现有 Java 4 空格、TypeScript/TSX 2 空格缩进和 UTF-8 编码，避免把无关的整文件格式化混入修复。

  Keep the existing four-space Java indentation, two-space TypeScript/TSX indentation, and UTF-8 encoding. Avoid mixing unrelated whole-file formatting changes into a fix.

- 后端通过 DTO 接收输入、VO 返回数据，使用 Bean Validation；多表写入明确事务边界。金额使用 `BigDecimal`，价格与总额由服务端计算。

  The backend accepts input through DTOs, returns data through VOs, and uses Bean Validation. Define transaction boundaries for writes spanning multiple tables. Use `BigDecimal` for monetary amounts, and calculate prices and totals on the server.

- 当前后端成功业务码为 `200`，分页为 `records/total/page/size/totalPages`，页码从 1 开始。前端仍有契约差异；修复或调整契约时同步修改两端、API 文档和验收说明。

  The backend's current business success code is `200`. Pagination uses `records/total/page/size/totalPages`, with page numbering starting at 1. Contract differences remain in the frontend. When fixing or changing the contract, update both sides, the API documentation, and acceptance instructions together.

- 权限必须由服务端验证；客户端传入的 `userId`、`merchantId`、角色及页面按钮不能作为授权依据。

  Permissions must be checked on the server. Client-supplied `userId`, `merchantId`, roles, and page buttons cannot establish authorization.

- 涉及库存、订单或评价的改动，应覆盖异常、重复请求与并发情形，不能仅凭存在 `@Transactional` 判定正确。

  Changes involving inventory, orders, or reviews should cover errors, repeated requests, and concurrency. The presence of `@Transactional` alone does not establish correctness.

- 前端使用函数组件和 Hooks，补齐类型、加载态、空态和失败反馈；不在新增代码中扩散 `any`。

  Use functional components and Hooks in the frontend. Provide types, loading states, empty states, and failure feedback. Do not introduce further uses of `any` in new code.

- 依赖变更同步更新 `package.json` 与 `package-lock.json`。不得提交依赖目录、构建产物、日志、真实业务数据、凭据或本地环境文件；不要假定所有 `.env.*` 文件都已被忽略。

  Update `package.json` and `package-lock.json` together when changing dependencies. Do not commit dependency directories, build output, logs, real business data, credentials, or local environment files. Do not assume that all `.env.*` files are already ignored.

## 提交前验证 Verify Before Submitting

前端改动在 `agriapp/` 执行：

For frontend changes, run the following in `agriapp/`:

```bash
npm run lint
npm run build
```

后端改动在 `agridemo/` 执行：

For backend changes, run the following in `agridemo/`:

```bash
./mvnw test
```

现有后端测试仅加载 Spring 上下文，使用当前配置时需要可访问的 MySQL；仓库尚无前端自动化测试脚本。涉及业务行为的修复应补充能够证明问题已解决的测试或可复现验收步骤。自动化检查通过不等于购买流程或权限边界已通过验收。

The existing backend test only loads the Spring context and requires an accessible MySQL instance with the current configuration. The repository has no frontend automated test script yet. Fixes to business behavior should include tests or reproducible acceptance steps that demonstrate the issue is resolved. Passing automated checks does not establish that the purchase workflow or permission boundaries have passed acceptance checks.

按改动范围参考 [项目验收清单](docs/07-项目验收清单.md) 手工验证；接口改动检查实际请求与响应，界面改动提供脱敏截图。纯文档改动检查内容、路径、链接和命令即可，无须为此启动数据库或应用。

Perform manual verification appropriate to the change using the [project acceptance checklist](docs/07-项目验收清单.md). For API changes, inspect actual requests and responses. For interface changes, provide redacted screenshots. For documentation-only changes, checking content, paths, links, and commands is sufficient; starting the database or application is unnecessary.

从仓库根目录检查待提交内容：

Review the pending changes from the repository root:

```bash
git diff --check
git diff
git status --short
```

如受依赖下载、环境或既有问题阻塞，请在 PR 中写明执行命令、实际结果和未验证部分，不将跳过的检查标记为通过。

If dependency downloads, the environment, or existing issues block verification, state the commands you ran, their actual results, and what remains unverified in the pull request. Do not mark skipped checks as passed.

## Issue & Pull Request

普通问题请在 [项目 Issue](https://github.com/Ivanluv555/agrimart/issues) 中提供版本或提交号、环境、复现步骤、预期与实际行为，以及脱敏日志。功能建议请说明农户、商家或农机拥有者的使用场景。未公开的漏洞不要提交到公开 Issue 或 PR，按 [安全政策](SECURITY.md) 处理。

For ordinary issues, provide the version or commit identifier, environment, reproduction steps, expected and actual behavior, and redacted logs in a [project issue](https://github.com/Ivanluv555/agrimart/issues). Feature requests should explain the use case for farmers, merchants, or machinery owners. Do not report unpublished vulnerabilities in public issues or pull requests; follow the [security policy](SECURITY.md).

从自己的 Fork 创建主题分支，提交聚焦单一目标的改动，再向上游默认分支发起 PR。提交信息清楚说明目的，例如 `fix: 修正商品分页字段映射` 或 `docs: 补充本地启动步骤`，不强制额外的签署流程。

Create a topic branch in your fork, commit changes focused on a single objective, and open a pull request against the upstream default branch. Commit messages should clearly state their purpose, for example `fix: correct product pagination field mapping` or `docs: add local startup steps`. No additional signing process is required.

PR 描述应包含：

The pull request description should include:

- 解决的问题或关联 Issue，以及可观察的行为变化。

  The problem being solved or related issue, and the observable behavior change.

- 主要改动、兼容性影响；涉及表结构时提供数据迁移与回退说明。

  The main changes and compatibility impact. For schema changes, include data migration and rollback instructions.

- 实际运行的检查、手工验证结果及已知限制。

  Checks actually run, manual verification results, and known limitations.

- 与接口、配置、功能范围变化对应的文档更新。

  Documentation updates corresponding to changes in APIs, configuration, or feature scope.

请保留与本次改动无关的已有工作，及时回应评审。维护者会结合功能范围、正确性和维护成本决定是否合并。

Preserve existing work unrelated to your change and respond to reviews promptly. Maintainers decide whether to merge based on feature scope, correctness, and maintenance cost.

## 许可证与署名 License and Attribution

本项目采用 [MIT License](LICENSE)，使用标准 [MIT 许可文本](https://opensource.org/license/mit)。提交贡献意味着你有权提交这些内容，并同意贡献按 MIT 许可证分发；贡献者保留自身版权，不要求转让版权。

This project uses the [MIT License](LICENSE), based on the standard [MIT license text](https://opensource.org/license/mit). By submitting a contribution, you confirm that you have the right to submit it and agree to its distribution under the MIT license. Contributors retain their own copyrights; no copyright transfer is required.

MIT 允许商业使用，但不保证当前 Demo 已满足生产使用要求。第三方代码、依赖、图片和资料仍受各自许可证约束；引入时注明来源、确认授权并保留必要声明，不能将来源不明的材料直接作为项目原创提交。

The MIT license permits commercial use but does not guarantee that the current demo meets production requirements. Third-party code, dependencies, images, and materials remain subject to their own licenses. When introducing them, credit their sources, confirm permission, and retain required notices. Do not submit material of unknown origin as original project work.
