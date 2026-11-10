# RabbitMQ 与资金链消息

使用 Spring Boot 管理的 `spring-boot-starter-amqp`、`spring.rabbitmq.*`、`RabbitTemplate`、`@RabbitListener`，没有引入 Spring Cloud Stream、分布式事务或自建连接池。Boot 4.1 的重试次数配置是 `max-retries`，当前设置为 2，即首次处理加两次重试。[Spring Boot 配置参考](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)、[Spring AMQP 确认与退回说明](https://docs.spring.io/spring-amqp/reference/amqp/template.html)。

## 本地启动

```bash
docker compose up -d --wait
mvn clean verify
```

RabbitMQ AMQP 地址为 `localhost:5672`，管理台为 http://localhost:15672，开发账号为 `agrimart / agrimart_dev`，vhost 为 `/agrimart`。数据存入独立的 `rabbitmq-data` 卷。已有 MySQL 数据卷保持不变，新增表由各服务自己的 Flyway 迁移创建。

启动交易服务：

```bash
MESSAGING_ENABLED=true RECONCILIATION_ENABLED=true \
  java -jar agrimart-trade-service/target/agrimart-trade-service-0.1.0-SNAPSHOT.jar
```

启动支付服务：

```bash
MESSAGING_ENABLED=true PAYMENT_PROVIDER_CONFIGURED=true \
SANDBOX_CALLBACK_SECRET=local-sandbox-secret-change-me \
  java -jar agrimart-payment-service/target/agrimart-payment-service-0.1.0-SNAPSHOT.jar
```

网关、用户服务沿用 README 启动方式。用户库也有 Outbox/Inbox，但当前没有用户事件生产者或消费者，因此不需要启用用户服务的消息开关。生产部署应为服务分配自己的 RabbitMQ 账号和 vhost 权限；Compose 的共用账号只用于本地开发。

| 配置 | 默认值 | 用途 |
| --- | --- | --- |
| `MESSAGING_ENABLED` | `false` | 同时启用该服务监听器、拓扑声明、Outbox relay |
| `RECONCILIATION_ENABLED` | `false` | 交易服务独立启用 P03/P04 对账，与 MQ 开关无关 |
| `RABBITMQ_HOST / PORT` | `localhost / 5672` | 原生 Spring RabbitMQ 连接配置 |
| `RABBITMQ_USERNAME / PASSWORD` | `agrimart / agrimart_dev` | 开发凭据 |
| `RABBITMQ_VHOST` | `/agrimart` | 服务连接的 vhost |
| `SANDBOX_CALLBACK_SECRET` | 空 | 空值时拒绝所有沙箱回调 |
| `TRADE_SERVICE_URL` | `http://localhost:8082` | 支付服务读取 T01/T03 授权 |
| `PAYMENT_SERVICE_URL` | `http://localhost:8083` | 交易服务读取 P03/P04 权威状态 |
| `SERVICE_TOKEN` | `dev-service-token` | 内部 HTTP 的 `X-Service-Token` |
| `agrimart.messaging.relay-delay` | `1000` 毫秒 | Outbox 扫描间隔 |
| `agrimart.reconciliation.delay` | `30000` 毫秒 | 每批最多 50 个已绑定订单的对账间隔 |

不开 MQ 时，写流程仍会写入 Outbox，消息会等待后续投递，不能把异步中的订单当作已完成。H2 测试显式关闭消息处理，不建立 broker 连接；对账也默认关闭，不访问其他服务。启用消息及对账时使用两个原生调度线程，避免 broker 确认等待阻塞 HTTP 对账。

## 事件与队列

`agrimart-common` 的 `FundsEvent` 是不可变 v1 协议，包含 `schemaVersion / eventId / kind / aggregateId / version / occurredAt`，以及订单号关联、支付号、可选退款号、十进制金额字符串、币种、状态、通道流水和完成时间。消息最多 16 KiB；不序列化实体、商品明细或订单快照。时间统一为 UTC。

v1 允许增加未知字段，禁止修改已有字段语义；破坏性修改必须使用新版本契约及路由。消费端拒绝未知 schema、非法聚合键、金额和状态。消息是完整资金状态事实，允许跨版本恢复；旧版本不回滚新状态，不要求全局顺序。

| 类型 / routing key | 聚合键 | 消费队列 |
| --- | --- | --- |
| `PAYMENT_STATE / payment.state.v1` | paymentNo | `agrimart.trade.funds.v1` |
| `REFUND_STATE / refund.state.v1` | refundNo | `agrimart.trade.funds.v1` |
| `CLOSE_PAYMENT / payment.close.v1` | paymentNo | `agrimart.payment.commands.v1` |
| `EXECUTE_REFUND / refund.execute.v1` | refundNo | `agrimart.payment.commands.v1` |

交换机为持久化 topic `agrimart.funds.v1`。两个服务有各自的 quorum 队列；同一服务的多个实例才共享队列竞争消费。未来其他服务订阅事实时应绑定自己的队列。

每个消费队列有独立的 `.dlq`，经 `agrimart.funds.dlx.v1` 投递。quorum 源队列配置 `at-least-once` 死信转发和 `reject-publish`。容器只在业务事务提交、监听器正常返回后自动 ack。失败由 Spring 原生重试处理，耗尽后 reject，禁止无限 requeue。

## 本地事务及恢复

三个业务库都有自己的 `mq_outbox / mq_inbox / mq_inbox_stream`。公共模块提供 JDBC 基础设施，不共享 JPA 实体，也不跨库联表。

1. 业务状态与 Outbox 在一个本地事务中提交。`append` 强制 `MANDATORY`，禁止脱离业务事务孤立写消息。
2. relay 在业务事务外每批取 50 条，使用带随机令牌的 60 秒租约认领。多实例可以重复扫描，但同一租约只能被一个实例取得。
3. 消息持久化、mandatory 发送，收到正向 publisher confirm 且没有 `basic.return` 后才标记 `sent_at`。
4. broker 不可用、nack、无路由或确认超时均保留 Outbox，退避 1～300 秒重试，并记录尝试次数和错误日志。失败发送不会静默丢弃。
5. relay 崩溃后租约到期恢复。已发送但未记录确认时可能重复发送，这是至少一次投递。
6. 消费者按服务 + 消息类型 + 聚合键加本地锁，再按 eventId 去重、按 version 归并；Inbox、业务变更及后续 Outbox 一起提交。首次并发建立锁行的唯一键冲突需要重试整个事务。

`mq_inbox_stream` 的聚合版本和退款号永久唯一约束应长期保留。可以按运维保留期归档已确认 Outbox 和旧 Inbox，但不能删除未发送记录，也不能通过清空 Inbox 解决毒消息。

## 已接通的资金链

- 创建支付先读取 T01，绑定 paymentNo，核对买家和订单，并采用交易服务金额；客户端金额只能作一致性校验。未配置供应商仍返回 503。
- 同一订单生成稳定的支付号，本地支付创建与关单使用相同聚合锁。T01 已成功但支付本地事务回滚时，关单命令可保存 `payment_close_tombstones`，阻止后续重试重开支付；P03 也能读取这类已关闭状态。
- 已验签支付成功回调更新支付权威记录，并写 `PAYMENT_STATE` Outbox。交易端核对绑定支付号、金额、币种和版本，再更新订单。
- 未绑定支付的待支付订单可以直接取消；已绑定支付则进入 `CLOSING` 并写关单命令。支付服务在自己的库内处理，返回资金事实。只有确认 `CLOSED`，交易端才变为 `CANCELLED`。
- 已成功支付的记录不会被关单覆盖。取消或关单后的迟到成功会使订单进入 `REFUNDING`，原子创建补偿授权和固定退款号 `R + paymentNo` 的退款命令。
- 支付服务消费退款命令后通过 T03 读取交易服务授权，再核对本库成功支付、金额和关联标识，按同一 refundNo 创建执行记录，等待退款回调。回调成功后发送退款事实，订单最终进入 `REFUNDED`。

当前供应商是项目内的本地 sandbox 协议。关单由本地状态模拟，退款进入 `PROCESSING` 后由签名回调确认结果；没有调用真实银行或第三方支付通道。接真实通道时，适配器必须验供应商签名、查询权威资金状态、先查后重试，并把外部调用结果归并到现有本地状态机；不能把“请求已受理”当作资金已经成功或已关闭。

这里完成的是消息基础和系统补偿资金链。规划中的普通买家退款申请、商家审核、发货、库存返还及其余缺失操作仍需对应业务模型；没有把它们冒充为已完成，也没有开放无需审核的普通退款入口。

## 沙箱回调

只支持 `/api/payment-callbacks/sandbox/payments` 和 `/api/payment-callbacks/sandbox/refunds`。网关保持原有公开回调路由，业务服务自行验签，不使用买家登录令牌代替供应商签名。

回调头：`X-Sandbox-Timestamp` 为当前 Unix 秒，`X-Sandbox-Signature` 为十六进制 HMAC-SHA256，签名输入为 `timestamp + '.' + 原始 JSON 字节`，密钥为 `SANDBOX_CALLBACK_SECRET`。时间窗口为前后 300 秒。

使用当前订单实际支付号和金额：

```bash
SANDBOX_CALLBACK_SECRET=local-sandbox-secret-change-me \
  python3 scripts/sandbox_callback.py payments P实际支付号 12.30

SANDBOX_CALLBACK_SECRET=local-sandbox-secret-change-me \
  python3 scripts/sandbox_callback.py refunds RP实际支付号 12.30
```

重复成功回调必须保持相同供应商流水号；金额冲突、流水冲突、未知记录和无效签名都不会被当作成功处理。

## P03/P04 与运维

交易服务轮询本库已绑定支付的订单，按 ID 游标每批最多 50 条，遍历完后从头继续，包含已取消订单以识别迟到支付。

- P03：`GET /internal/payments/{paymentNo}`，当前返回可直接归并的 v1 支付事实。比本地新的版本通过同一个 Inbox 和状态机恢复。
- P04：`GET /internal/refunds/{refundNo}`，返回 `RefundState`。更新退款执行投影。
- `CLOSING` 但权威状态仍未终结、或 P03 还没有支付记录时，重新写入关单命令。
- 本库已有补偿授权但 P04 尚无执行记录时，重建同一个 refundNo 的命令。
- 网络或单个订单失败会记录标识和异常，下一轮继续；不能因为 MQ 已确认投递就关闭对账。

内部读接口继续要求 `X-Service-Token`。保留现有内部写接口兼容旧调用方，但本次资金链的状态通知、关单、退款执行都使用 Outbox 消息。

应监控未发送 Outbox 数量及最老记录年龄、`attempts / last_error`、两条 DLQ 深度、长期 `CLOSING / REFUNDING` 订单及对账错误。DLQ 恢复先修复数据或消费代码，再将原始 body、messageId 重投到原 routing key；不要更换 eventId 或清空 Inbox。重复重投由 Inbox 保证幂等。

## 验证

普通验证：`mvn clean verify`。H2 覆盖事务回滚、聚合幂等和乱序、租约恢复、确认/nack/return、取消与迟到支付、同号退款、原始字节验签，以及无需 MQ 的 P03/P04 修复。

真实 broker 验收使用独立的本地端口与 vhost，按需运行，不参与默认测试：

```bash
docker run --rm -d --name agrimart-rabbitmq-check \
  -p 127.0.0.1:5673:5672 \
  -e RABBITMQ_DEFAULT_USER=agrimart \
  -e RABBITMQ_DEFAULT_PASS=agrimart_dev \
  -e RABBITMQ_DEFAULT_VHOST=/agrimart-check rabbitmq:4.2-management

docker exec agrimart-rabbitmq-check rabbitmq-diagnostics -q ping
AGRIMART_RABBIT_INTEGRATION=true mvn -pl agrimart-payment-service -am \
  -Dtest=RabbitFundsBrokerIT -Dsurefire.failIfNoSpecifiedTests=false test

docker stop agrimart-rabbitmq-check
```

验收复用生产 `application.yml` 的 Rabbit 配置，验证 Outbox 真正发送、消费提交、确认回写，以及无效 JSON 恰好处理三次后进入本服务 DLQ。

2026-11-10 验证记录：

- `mvn clean verify`：220 个测试通过，失败、错误和跳过均为 0，四个应用 JAR 已生成。
- 显式 broker 验收：`RabbitFundsBrokerIT` 通过，确认真实投递、事务消费，以及毒消息处理三次后进入 DLQ。
- 在独立 MySQL 8.4 容器中，用户、交易、支付服务分别迁移到 V2、V4、V3，并通过 Hibernate `validate`。
- 独立端口上的交易/支付应用通过真实 HTTP + RabbitMQ 联调：普通支付和重复回调、关单不覆盖成功、关单后的迟到支付进入同号补偿退款并最终 `REFUNDED`、T01 后支付库回滚窗口的关闭标记，以及伪造金额拒绝后采用可信金额重试。
- 临时应用和容器在验收结束后关闭；未修改现有开发数据库或数据卷。
