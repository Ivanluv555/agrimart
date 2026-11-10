# agrimart-server

AgriMart 后端由用户、交易、支付三个独立业务服务，以及统一网关和公共协议模块组成。使用 Java 25、Maven 和 Spring Boot 4.1.2。

架构图：[项目架构.excalidraw](./项目架构.excalidraw)，包含运行拓扑、主要类通信、组件与目录位置，以及尚未接通的内部调用。

客户演示：[客户实际演示指南](./客户实际演示指南.md)，包含环境准备、可复制的 API 操作、现场讲解话术、当前功能边界和故障备用方案。

## 目录约定

先按业务分块，再在业务内部按职责分包。Controller、Service、Repository、DTO 和实体各放入自己的子包；测试目录镜像生产代码的包结构。启动类保留在各服务根包，使组件、实体和仓储扫描覆盖整个服务。

```text
edu.swu.agrimart
├── user
│   ├── UserServiceApplication
│   └── account
│       ├── controller  # 账户接口、内部认证接口和异常响应
│       ├── service     # 注册、登录及会话业务
│       ├── repository  # 用户与会话仓储
│       ├── dto         # 注册、登录、身份校验及响应数据
│       ├── entity      # 用户与会话实体
│       └── config      # Argon2 密码编码配置
├── trade
│   ├── TradeServiceApplication
│   ├── product         # controller / service / repository / dto / entity
│   ├── order           # controller / service / repository / dto / entity
│   └── support         # controller / config，服务内共享的异常响应和身份配置
├── payment
│   ├── PaymentServiceApplication
│   └── payment         # controller / service / repository / dto / entity / exception
├── gateway
│   ├── GatewayApplication
│   └── routing         # controller / service
└── common
    ├── api.dto         # 统一成功及错误响应信封
    ├── auth.service    # 统一 RequestAuth 凭据与网关身份校验
    ├── identity.dto    # 跨服务身份协议
    ├── merchant.dto    # 商家资格协议
    └── payment.dto     # 支付授权与支付事件协议
```

业务类、测试类、方法及 DTO 均使用中文 Javadoc；参数、返回值和异常采用标准 `@param`、`@return`、`@throws` 标签。公共模块只包含协议和认证辅助代码，不包含共享 JPA 实体或仓储。

## 版本管理

根 POM 统一继承 `spring-boot-starter-parent:4.1.2`，三个服务和网关继承本项目父 POM。Spring、Hibernate、Jackson、Flyway、MySQL 驱动、H2 和测试组件均使用 Boot 管理的版本，不再重复导入 Boot BOM。MVC 与 HTTP 客户端使用 Boot 4 对应的模块化 Starter，JSON 使用 Jackson 3。

现有 Argon2 所需的 `bcprov-jdk18on:1.84` 不由 Boot 4.1.2 管理，因此保留原版本及密码算法，避免破坏已有密码摘要的兼容性。接口文档依赖 `springdoc-openapi` 的版本也由根 POM 单独管理。版本依据：[Boot 4.1.2 发布说明](https://spring.io/blog/2026/10/23/spring-boot-4-1-2-available-now/)、[依赖管理说明](https://docs.spring.io/spring-boot/reference/using/build-systems.html)、[受管理组件列表](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)。

## 开发数据库

三个业务服务默认连接 `compose.yaml` 中的同一台 MySQL 8.4 服务器，宿主机端口为 `3307`。每个服务使用独立逻辑库和账号，账号仅授权访问本服务数据库。

| 服务 | 逻辑库 / 账号 | 本地开发密码 | HTTP 端口 |
| --- | --- | --- | --- |
| 用户 | `agrimart_user` | `agrimart_user_dev` | 8081 |
| 交易 | `agrimart_trade` | `agrimart_trade_dev` | 8082 |
| 支付 | `agrimart_payment` | `agrimart_payment_dev` | 8083 |
| 网关 | 无数据库 | — | 8080 |

```bash
docker compose up -d --wait
mvn clean verify
```

分别在四个终端启动：

```bash
java -jar agrimart-user-service/target/agrimart-user-service-0.1.0-SNAPSHOT.jar
java -jar agrimart-trade-service/target/agrimart-trade-service-0.1.0-SNAPSHOT.jar
java -jar agrimart-payment-service/target/agrimart-payment-service-0.1.0-SNAPSHOT.jar
java -jar agrimart-gateway/target/agrimart-gateway-0.1.0-SNAPSHOT.jar
```

MySQL 首次创建数据卷时运行 `docker/mysql/init/01-databases.sql`，创建三库三账号。已有开发数据卷不会重新执行初始化脚本；保留数据时可手动补建：

```bash
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot' < docker/mysql/init/01-databases.sql
```

该脚本不会删除已有数据库或修改已存在账号的密码。若账号原先使用其他密码，通过 `DB_PASSWORD` 指定实际密码。Compose 中的默认凭据仅供本地开发；其他环境通过环境变量提供自己的凭据。

每个服务启动时使用 Flyway 执行本服务 `src/main/resources/db/migration` 中的迁移，随后由 Hibernate `validate` 校验结构。已修正原脚本中 MySQL 不支持的带时区时间类型，并补充支付表迁移。时间按 UTC 存储。若旧库已应用过原 V1，需先核对已有结构和迁移历史后安排迁移，不要直接清空数据或重置 Flyway 校验记录。

可通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`DB_DRIVER`、`SERVER_PORT` 覆盖连接和端口；修改 `MYSQL_PORT` 后也应为各服务设置相应的 `DB_URL`。网关地址可用 `USER_SERVICE_URL`、`TRADE_SERVICE_URL` 和 `PAYMENT_SERVICE_URL` 覆盖。应用在宿主机运行时使用 `localhost:3307`；若自行将应用容器化并加入同一 Compose 网络，数据库地址应使用 `mysql:3306`。

## 接口文档

三个业务服务使用 `springdoc-openapi` 独立生成 OpenAPI 文档，网关提供统一 Swagger UI：

| 入口 | 地址 |
| --- | --- |
| 统一 Swagger UI | http://localhost:8080/swagger-ui.html |
| 用户服务原始文档 | http://localhost:8081/v3/api-docs |
| 交易服务原始文档 | http://localhost:8082/v3/api-docs |
| 支付服务原始文档 | http://localhost:8083/v3/api-docs |
| 网关适配后的文档 | `http://localhost:8080/docs/user`、`/docs/trade`、`/docs/payment` |

启动三个业务服务和网关后，在 Swagger UI 顶部选择服务。网关使用现有 `agrimart.routes.*` 地址获取对应服务文档，服务未启动时对应文档返回 503，其他服务文档仍可访问。业务服务只提供文档数据，UI 统一由网关提供。

统一 UI 的调试请求使用同源 `/api` 地址，经过网关的路由和认证流程。注册、登录及契约中的公开商品、公开评价查询无需登录，POST 支付和退款回调不要求用户令牌，但下游必须实现供应商验签；回调业务本身尚未实现。其他操作先调用登录接口，将响应中的 `accessToken` 填入右上角 **Authorize**；只填写令牌本身，无需添加 `Bearer ` 前缀。具体业务权限及支付供应商配置仍按现有实现执行。

各服务原始文档保留内部接口；网关文档隐藏 `/internal` 路径，不提供内部接口调试入口。直接调用业务服务的鉴权要求与网关不同，前端联调应使用统一 UI。接口和请求模型由控制器及 DTO 自动生成，当前统一响应中使用的 `Api.Result<?>` 不会自动推断出具体业务响应模型。

根 POM 统一管理 `springdoc-openapi:3.2.0`，与当前 Boot 4.1.x 对应：[官方兼容矩阵](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot)。

## 测试和当前业务范围

现有链路修复和后续业务批次见[契约修复与后续批次](./契约修复与后续批次.md)。商品列表现已分页，例如 `GET /api/products?page=1&size=10`，前端应读取 `data.records`，总数及分页信息位于 `data.total/page/size/totalPages`。交易服务启动时执行新增 V2 迁移，保留商品数据并补齐创建时间及分页索引。

`mvn clean verify` 执行全部模块测试并生成四个可执行应用 JAR。H2 仅为测试依赖；测试资源显式使用独立内存数据库、关闭 Flyway，并通过 `create-drop` 创建测试表，因此单元测试与仓储测试不依赖 Docker。

测试覆盖账户注册、密码及会话、DTO 校验、商品与订单、仓储约束、支付记录、异常响应、网关路由以及网关实际 HTTP 启动。支付事件和关单操作已收归 Service 事务，并验证提交后从仓储重新读取的状态。

当前保留单商家结算和既有接口契约。支付供应商未配置时，支付服务返回 `503/PAYMENT_PROVIDER_NOT_CONFIGURED`。完整退款、真实支付沙箱、多商家拆单、Nacos 及规划文档中的全量业务仍属于后续工作，本次结构重构不代表这些功能已经实现。

## 本次重构验证记录（2026-11-10）

- `mvn verify`：151 个测试通过，失败、错误和跳过均为 0；四个可执行应用 JAR 已生成。
- 84 个 Java 文件按“业务 / 职责”组织，388 个类、方法及构造器声明的 Javadoc 与必需参数、返回标签完整；生产和测试源码均通过 JDK Javadoc 文档校验。生成文档位于各模块 `target/site/all-javadoc`。
- 四个应用产物均包含 Spring Boot 4.1.2，未包含仅供测试使用的 H2；打包后的网关已实际启动并通过 HTTP 健康检查。
- Compose 配置曾通过解析并启动独立临时 MySQL 至健康状态；后续 Docker Desktop 返回 `Docker Desktop is unable to start`，因此三服务连接真实 MySQL、Flyway 实际执行和数据库账号隔离尚未完成端到端验收。
- 验证使用过独立临时项目 `agrimart-refactor-check`，端口为 `13307`。Docker 恢复后可用 `docker compose -p agrimart-refactor-check down -v` 清理该临时项目及其测试卷；不要对原开发项目的数据卷执行此命令。
