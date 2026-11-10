# 农惠达 API 设计

设计阶段：开发起步阶段。版本：`initial-development-three-services-2026-11-05`。本文完整列出 54 个目标操作、52 个路径和 79 个数据模型，可独立阅读。所有接口均为目标设计；现有路由存在不代表三服务或对应规则已经实现。

Web 目标采用 React + MUI，后续移动端采用 Flutter + Material。两端通过统一网关复用身份、业务权限和接口；移动端可通过系统浏览器打开沙箱 checkoutUrl，付款结果仍由服务端确认。

## 1. 服务边界与访问地址

| 服务 | 应用 | 内部地址 | 独立逻辑库 | 外部 / 内部操作 |
| --- | --- | --- | --- | --- |
| S01 用户服务 | `agrimart-user-service` | `http://agrimart-user-service:8081` | `agrimart_user` | 8 / 2 |
| S02 农资买卖服务 | `agrimart-trade-service` | `http://agrimart-trade-service:8082` | `agrimart_trade` | 31 / 4 |
| S03 支付服务 | `agrimart-payment-service` | `http://agrimart-payment-service:8083` | `agrimart_payment` | 5 / 4 |

外部开发基址：`http://localhost:8080/api`。下文外部接口已展示完整 /api 前缀；内部接口使用所属服务的内网地址，路径不含 /api。网关转发外部请求时移除 /api 前缀。

S01 管理账号、会话、角色与商家准入；S02 管理商品、库存、订单、履约、退款申请审核与评价；S03 管理沙箱支付、退款资金执行、通道验签与结果核对。每个服务仅访问自己的数据库，跨服务不建外键或共享事务。

### 1.1 网关路由顺序

| 优先级 | 匹配范围 | 处理 |
| --- | --- | --- |
| 0 | `/internal/** 及 /api/internal/**` | 禁止公网访问 |
| 10 | `/api/orders/{id}/payments、/api/payments/**、/api/payment-callbacks/**` | S03 支付服务 |
| 20 | `/api/users/**、/api/merchant-applications/**、/api/admin/merchant-applications/**` | S01 用户服务 |
| 30 | `/api/products/**、/api/merchant/**、/api/admin/products/**、/api/checkouts/**、/api/orders/**、/api/refunds/**、/api/reviews/**` | S02 农资买卖服务 |

支付入口 /api/orders/{id}/payments 必须优先于通用订单路由匹配。网关、注册与配置中心属于基础设施，不计入三个业务微服务。

## 2. 通用协议

### 2.1 请求、身份与权限

- 外部受保护操作使用 `Authorization: Bearer <accessToken>`。令牌由 S01 签发，有效期 2 小时，退出后吊销；当前阶段不设计刷新令牌。
- 游客只能调用明确标记为游客可访问的接口。农户与商家均可作为买家；商家经营还必须通过入驻审核。
- 内部操作使用 `X-Service-Token: <serviceCredential>`，并按调用方及接口授权，配合内网隔离与 HTTPS。服务凭据与用户令牌不能互换；不接受客户端伪造的身份头。
- 无有效登录返回 401，角色或经营权限不足返回 403；他人私有资源按 404 处理，不泄露资源存在性。
- 请求体通常使用 application/json；支付通道回调是例外，保留原始请求并按适配协议处理。

### 2.2 字段、分页与金额

- 字段使用 camelCase；ID 为正整数，最大为 `9007199254740991`。
- 金额按人民币元传输为两位小数字符串，服务端使用十进制计算；客户端不能提交支付金额、退款金额或修改成交快照。
- 时间使用带时区的 ISO 8601，界面按 Asia/Shanghai 展示。无值可选字段省略；商品尚无评价时 rating 为 null。
- 必填文本去除首尾空白后不得为空，密码不裁剪；字段长度及范围按模型约束执行。

| 参数 | 位置 | 必填 | 类型 | 约束 |
| --- | --- | --- | --- | --- |
| page | query | 否 | `integer` | 最小值 1；最大值 1000000；默认 `1`；页码从 1 开始。 |
| size | query | 否 | `integer` | 最小值 1；最大值 100；默认 `10`；每页最多 100 条。 |
| Idempotency-Key | header | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

### 2.3 幂等、异步与一致性

携带 Idempotency-Key 的操作按认证主体、操作和目标资源隔离。同一键与相同规范化请求返回原业务结果，同键不同请求返回 409。记录至少保留 7 天；业务号和永久唯一约束不随缓存过期消失。重放创建结果可能是旧快照，最新状态通过查询取得。

内部命令以 commandId、事实以 eventId 去重，并校验业务号和单调版本。先持久化本服务状态与待办，再调用远端；接收去重和本地副作用同事务提交。超时保留明确未知状态，通过重投及定期查询恢复。

HTTP 202 只表示命令已接受，不能据此认为支付已关闭、退款已完成或库存已经释放；必须查询最终状态。浏览器跳转与客户端声称付款都不能证明资金成功。

### 2.4 统一响应与错误

同步成功使用 HTTP 200，异步接受使用 HTTP 202，响应中的成功业务码均为 200。普通成功体为 `{code:200,message,data}`。错误使用真实 HTTP 状态，data 为 null，并提供稳定 errorCode 与 traceId。供应商回调按通道原始协议应答，不使用 Result 包装。

| HTTP 状态 | 含义 |
| --- | --- |
| 400 | 参数或请求不合法 |
| 401 | 用户或服务凭据缺失、无效或过期 |
| 403 | 角色、经营能力或内部调用范围不足 |
| 404 | 资源不存在或不属于当前主体 |
| 409 | 状态、库存、业务号、唯一性或幂等冲突 |
| 500 | 内部异常，不能暴露实现细节 |
| 503 | 依赖、支付通道未配置或暂时不可用 |

| errorCode | 含义 |
| --- | --- |
| `VALIDATION_ERROR` | 输入校验失败 |
| `UNAUTHENTICATED` | 认证失败 |
| `FORBIDDEN` | 权限不足 |
| `RESOURCE_NOT_FOUND` | 资源不可访问 |
| `ACCOUNT_CONFLICT` | 账号唯一性冲突 |
| `STATE_CONFLICT` | 业务状态冲突 |
| `STOCK_INSUFFICIENT` | 库存不足 |
| `AMOUNT_LIMIT_EXCEEDED` | 金额超过上限 |
| `MERCHANT_NOT_APPROVED` | 商家未获经营权限 |
| `PRODUCT_UNAVAILABLE` | 商品不可购买 |
| `DUPLICATE_REVIEW` | 重复评价 |
| `IDEMPOTENCY_CONFLICT` | 幂等键与请求不一致 |
| `PAYMENT_ALREADY_SUCCEEDED` | 付款已成功，不能当作未支付取消 |
| `PAYMENT_PROVIDER_NOT_CONFIGURED` | 支付供应商未配置 |
| `PROVIDER_UNAVAILABLE` | 支付通道暂时不可用 |
| `CALLBACK_INVALID` | 通道通知无效 |
| `INTERNAL_ERROR` | 内部异常 |
| `DEPENDENCY_UNAVAILABLE` | 内部依赖暂时不可用 |

## 3. 接口总览

外部操作使用 S01-E01 等本文编号，内部操作保留 U01、T01、P01 等调用编号。每个操作同时给出 operationId，供实现与联调识别。

### S01 用户服务

| 编号 | 功能 | 方法 | 地址 | 范围 |
| --- | --- | --- | --- | --- |
| `S01-E01` | 注册农户账号 | POST | `/api/users/register` | 外部 |
| `S01-E02` | 登录 | POST | `/api/users/login` | 外部 |
| `S01-E03` | 退出登录 | POST | `/api/users/logout` | 外部 |
| `S01-E04` | 查询本人资料 | GET | `/api/users/me` | 外部 |
| `S01-E05` | 申请商家入驻 | POST | `/api/merchant-applications` | 外部 |
| `S01-E06` | 查询本人最新入驻申请 | GET | `/api/merchant-applications/me` | 外部 |
| `S01-E07` | 查询商家入驻申请 | GET | `/api/admin/merchant-applications` | 外部 |
| `S01-E08` | 审核商家入驻 | POST | `/api/admin/merchant-applications/{applicationId}/decision` | 外部 |
| `U01` | 校验用户令牌 | POST | `/internal/auth/introspections` | 内部 |
| `U02` | 查询商家经营资格 | GET | `/internal/merchants/{merchantId}` | 内部 |

### S02 农资买卖服务

| 编号 | 功能 | 方法 | 地址 | 范围 |
| --- | --- | --- | --- | --- |
| `S02-E01` | 搜索公开商品 | GET | `/api/products` | 外部 |
| `S02-E02` | 创建商品 | POST | `/api/products` | 外部 |
| `S02-E03` | 查询公开商品详情 | GET | `/api/products/{id}` | 外部 |
| `S02-E04` | 更新商品资料 | PUT | `/api/products/{id}` | 外部 |
| `S02-E05` | 查询商家公开商品 | GET | `/api/products/merchant/{merchantId}` | 外部 |
| `S02-E06` | 上架或下架商品 | PUT | `/api/products/{id}/status` | 外部 |
| `S02-E07` | 调整可用库存 | PUT | `/api/products/{id}/stock` | 外部 |
| `S02-E08` | 查询本人管理商品 | GET | `/api/merchant/products` | 外部 |
| `S02-E09` | 查询本人商品管理详情 | GET | `/api/merchant/products/{id}` | 外部 |
| `S02-E10` | 查询平台商品 | GET | `/api/admin/products` | 外部 |
| `S02-E11` | 设置或解除平台禁售 | PUT | `/api/admin/products/{id}/sale-restriction` | 外部 |
| `S02-E12` | 提交结算并按商家拆单 | POST | `/api/checkouts` | 外部 |
| `S02-E13` | 查询本人结算批次 | GET | `/api/checkouts/{checkoutNo}` | 外部 |
| `S02-E14` | 查询本人购买订单 | GET | `/api/orders/me` | 外部 |
| `S02-E15` | 查询订单详情 | GET | `/api/orders/{id}` | 外部 |
| `S02-E16` | 按订单号查询订单 | GET | `/api/orders/no/{orderNo}` | 外部 |
| `S02-E17` | 取消待支付订单 | POST | `/api/orders/{id}/cancel` | 外部 |
| `S02-E18` | 确认收货并完成订单 | POST | `/api/orders/{id}/receipt` | 外部 |
| `S02-E19` | 查询本人销售订单 | GET | `/api/merchant/orders` | 外部 |
| `S02-E20` | 发货 | POST | `/api/merchant/orders/{id}/shipment` | 外部 |
| `S02-E21` | 申请发货前全额退款 | POST | `/api/orders/{id}/refunds` | 外部 |
| `S02-E22` | 查询退款详情 | GET | `/api/refunds/{refundId}` | 外部 |
| `S02-E23` | 查询本人退款 | GET | `/api/refunds/me` | 外部 |
| `S02-E24` | 查询本人商家退款 | GET | `/api/merchant/refunds` | 外部 |
| `S02-E25` | 审核全额退款申请 | POST | `/api/merchant/refunds/{refundId}/decision` | 外部 |
| `S02-E26` | 重试异常退款 | POST | `/api/merchant/refunds/{refundId}/retry` | 外部 |
| `S02-E27` | 按订单项评价商品 | POST | `/api/reviews` | 外部 |
| `S02-E28` | 查询公开商品评价 | GET | `/api/reviews/product/{productId}` | 外部 |
| `S02-E29` | 查询本人评价 | GET | `/api/reviews/me` | 外部 |
| `S02-E30` | 查询本人商品评价 | GET | `/api/merchant/reviews` | 外部 |
| `S02-E31` | 回复本人商品评价 | POST | `/api/reviews/{id}/reply` | 外部 |
| `T01` | 授权订单支付 | POST | `/internal/orders/{orderId}/payment-authorizations` | 内部 |
| `T02` | 归并可信支付或关单事实 | POST | `/internal/orders/{orderId}/payment-events` | 内部 |
| `T03` | 读取退款执行授权 | GET | `/internal/refunds/{refundNo}/execution-authorization` | 内部 |
| `T04` | 归并退款执行事实 | POST | `/internal/refunds/{refundNo}/execution-events` | 内部 |

### S03 支付服务

| 编号 | 功能 | 方法 | 地址 | 范围 |
| --- | --- | --- | --- | --- |
| `S03-E01` | 创建或复用沙箱支付 | POST | `/api/orders/{id}/payments` | 外部 |
| `S03-E02` | 查询本人支付记录 | GET | `/api/payments/{paymentNo}` | 外部 |
| `S03-E03` | 向通道核对支付结果 | POST | `/api/payments/{paymentNo}/sync` | 外部 |
| `S03-E04` | 接收沙箱支付回调 | POST | `/api/payment-callbacks/{providerCode}/payments` | 外部 |
| `S03-E05` | 接收沙箱退款回调 | POST | `/api/payment-callbacks/{providerCode}/refunds` | 外部 |
| `P01` | 接受关闭支付命令 | POST | `/internal/payments/{paymentNo}/close` | 内部 |
| `P02` | 接受或重试全额退款执行 | POST | `/internal/refunds` | 内部 |
| `P03` | 查询权威支付状态 | GET | `/internal/payments/{paymentNo}` | 内部 |
| `P04` | 查询权威退款执行状态 | GET | `/internal/refunds/{refundNo}` | 内部 |

## 4. 外部接口详情

### 4.1 S01 用户服务外部接口

#### S01-E01 注册农户账号

`POST /api/users/register`

- 服务：S01 用户服务。
- Controller：`UserController.register`。
- operationId：`UserController_register`。
- 访问权限：游客。
- 实现状态：现有路径，目标行为及服务归属需调整。

仅创建 ACTIVE/FARMER，不接受 role。注册不自动登录；手机号和用户名唯一。

路径、查询与专用请求头参数：无；认证要求仍按上述权限执行。

请求体：必填；`application/json` → `RegisterDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultUserVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S01-E02 登录

`POST /api/users/login`

- 服务：S01 用户服务。
- Controller：`UserController.login`。
- operationId：`UserController_login`。
- 访问权限：游客。
- 实现状态：现有路径，目标行为及服务归属需调整。

校验账号、密码与 ACTIVE 状态，签发两小时可撤销令牌。错误凭据统一返回 401。

路径、查询与专用请求头参数：无；认证要求仍按上述权限执行。

请求体：必填；`application/json` → `LoginDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultAuthVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S01-E03 退出登录

`POST /api/users/logout`

- 服务：S01 用户服务。
- Controller：`UserController.logout`。
- operationId：`UserController_logout`。
- 访问权限：已登录用户。
- 实现状态：待新增。

吊销当前令牌。客户端退出后删除本地认证信息。

路径、查询与专用请求头参数：无；认证要求仍按上述权限执行。

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultVoid` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S01-E04 查询本人资料

`GET /api/users/me`

- 服务：S01 用户服务。
- Controller：`UserController.getMe`。
- operationId：`UserController_getMe`。
- 访问权限：已登录用户。
- 实现状态：待新增。

S01 返回当前用户本人资料。网关和业务服务通过 U01 验证不透明令牌；角色以用户服务的最新状态为准，不信任客户端身份头。

路径、查询与专用请求头参数：无；认证要求仍按上述权限执行。

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultUserVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S01-E05 申请商家入驻

`POST /api/merchant-applications`

- 服务：S01 用户服务。
- Controller：`MerchantApplicationController.apply`。
- operationId：`MerchantApplicationController_apply`。
- 访问权限：农户。
- 实现状态：待新增。

ACTIVE/FARMER 可申请；每人最多一条待审核申请。驳回后可重新申请；已批准的商家不能重复申请。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：必填；`application/json` → `MerchantApplicationDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultMerchantApplicationVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S01-E06 查询本人最新入驻申请

`GET /api/merchant-applications/me`

- 服务：S01 用户服务。
- Controller：`MerchantApplicationController.getLatestApplication`。
- operationId：`MerchantApplicationController_getLatestApplication`。
- 访问权限：农户、商家。
- 实现状态：待新增。

无申请时返回 404；可查看审核原因，但不能读取其他申请人的联系方式。

路径、查询与专用请求头参数：无；认证要求仍按上述权限执行。

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultMerchantApplicationVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S01-E07 查询商家入驻申请

`GET /api/admin/merchant-applications`

- 服务：S01 用户服务。
- Controller：`AdminMerchantApplicationController.listApplications`。
- operationId：`AdminMerchantApplicationController_listApplications`。
- 访问权限：管理员。
- 实现状态：待新增。

管理员可分页查询并按状态筛选；申请创建时间倒序，相同时间按 ID 倒序。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `page` | 查询 | 否 | `integer` | 最小值 1；最大值 1000000；默认 `1`；页码从 1 开始。 |
| `size` | 查询 | 否 | `integer` | 最小值 1；最大值 100；默认 `10`；每页最多 100 条。 |
| `status` | 查询 | 否 | `ApplicationStatus` | ；申请审核状态。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultPageMerchantApplicationVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S01-E08 审核商家入驻

`POST /api/admin/merchant-applications/{applicationId}/decision`

- 服务：S01 用户服务。
- Controller：`AdminMerchantApplicationController.decideApplication`。
- operationId：`AdminMerchantApplicationController_decideApplication`。
- 访问权限：管理员。
- 实现状态：待新增。

只能审核 PENDING。通过后同一事务更新申请并将账号角色改为 MERCHANT；拒绝须说明原因。不得审核自己的申请。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `applicationId` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：必填；`application/json` → `DecisionDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultMerchantApplicationVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

### 4.2 S02 农资买卖服务外部接口

#### S02-E01 搜索公开商品

`GET /api/products`

- 服务：S02 农资买卖服务。
- Controller：`ProductController.searchProducts`。
- operationId：`ProductController_searchProducts`。
- 访问权限：游客。
- 实现状态：现有路径，目标行为及服务归属需调整。

仅展示经营权限有效、未受平台禁售且 ON_SALE 的商品。库存为零仍可展示，但不能下单。按创建时间和 ID 倒序。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `page` | 查询 | 否 | `integer` | 最小值 1；最大值 1000000；默认 `1`；页码从 1 开始。 |
| `size` | 查询 | 否 | `integer` | 最小值 1；最大值 100；默认 `10`；每页最多 100 条。 |
| `category` | 查询 | 否 | `ProductCategory` | ；按分类筛选。 |
| `keyword` | 查询 | 否 | `string` | 最少字符 0；最多字符 100；商品名称或描述关键词。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultPageProductVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E02 创建商品

`POST /api/products`

- 服务：S02 农资买卖服务。
- Controller：`ProductController.createProduct`。
- operationId：`ProductController_createProduct`。
- 访问权限：商家。
- 实现状态：现有路径，目标行为及服务归属需调整。

已批准商家创建自己的商品，不接受 merchantId；初始 OFF_SALE，价格至少 0.01。stock 为初始可用库存。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：必填；`application/json` → `ProductCreateDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultMerchantProductVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E03 查询公开商品详情

`GET /api/products/{id}`

- 服务：S02 农资买卖服务。
- Controller：`ProductController.getProductById`。
- operationId：`ProductController_getProductById`。
- 访问权限：游客。
- 实现状态：现有路径，目标行为及服务归属需调整。

可见性规则与商品搜索一致；下架、禁售或无效商家的商品返回 404。商家管理详情使用 /merchant/products/{id}。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `id` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultProductVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E04 更新商品资料

`PUT /api/products/{id}`

- 服务：S02 农资买卖服务。
- Controller：`ProductController.updateProduct`。
- operationId：`ProductController_updateProduct`。
- 访问权限：商家。
- 实现状态：待新增。

仅所属商家可操作。必填字段完整替换，未提供的可选字段清空；不可修改商家、库存、销量或历史订单快照。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `id` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：必填；`application/json` → `ProductUpdateDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultMerchantProductVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E05 查询商家公开商品

`GET /api/products/merchant/{merchantId}`

- 服务：S02 农资买卖服务。
- Controller：`ProductController.getMerchantStoreProducts`。
- operationId：`ProductController_getMerchantStoreProducts`。
- 访问权限：游客。
- 实现状态：现有路径，目标行为及服务归属需调整。

只返回指定商家的公开可见商品，不是商家管理列表。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `merchantId` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |
| `page` | 查询 | 否 | `integer` | 最小值 1；最大值 1000000；默认 `1`；页码从 1 开始。 |
| `size` | 查询 | 否 | `integer` | 最小值 1；最大值 100；默认 `10`；每页最多 100 条。 |
| `category` | 查询 | 否 | `ProductCategory` | ；按分类筛选。 |
| `keyword` | 查询 | 否 | `string` | 最少字符 0；最多字符 100；商品名称或描述关键词。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultPageProductVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E06 上架或下架商品

`PUT /api/products/{id}/status`

- 服务：S02 农资买卖服务。
- Controller：`ProductController.updateProductStatus`。
- operationId：`ProductController_updateProductStatus`。
- 访问权限：商家。
- 实现状态：现有路径，目标行为及服务归属需调整。

仅所属商家可操作；使用 JSON status，不再接收 Query。平台禁售时禁止上架。下架不取消已有订单。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `id` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：必填；`application/json` → `ProductStatusDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultMerchantProductVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E07 调整可用库存

`PUT /api/products/{id}/stock`

- 服务：S02 农资买卖服务。
- Controller：`ProductController.adjustStock`。
- operationId：`ProductController_adjustStock`。
- 访问权限：商家。
- 实现状态：现有路径，目标行为及服务归属需调整。

仅所属商家可操作。JSON delta 为非零增量且必须说明原因；不能使可用库存为负，也不能直接改预留库存。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `id` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：必填；`application/json` → `StockAdjustmentDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultMerchantProductVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E08 查询本人管理商品

`GET /api/merchant/products`

- 服务：S02 农资买卖服务。
- Controller：`MerchantProductController.listOwnProducts`。
- operationId：`MerchantProductController_listOwnProducts`。
- 访问权限：商家。
- 实现状态：待新增。

服务端限定当前商家，包含上下架及禁售商品，不接受 merchantId 作为授权依据。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `page` | 查询 | 否 | `integer` | 最小值 1；最大值 1000000；默认 `1`；页码从 1 开始。 |
| `size` | 查询 | 否 | `integer` | 最小值 1；最大值 100；默认 `10`；每页最多 100 条。 |
| `category` | 查询 | 否 | `ProductCategory` | ；按分类筛选。 |
| `keyword` | 查询 | 否 | `string` | 最少字符 0；最多字符 100；商品名称或描述关键词。 |
| `status` | 查询 | 否 | `string` | 允许值：`"ON_SALE"`、`"OFF_SALE"`；商品上下架状态。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultPageMerchantProductVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E09 查询本人商品管理详情

`GET /api/merchant/products/{id}`

- 服务：S02 农资买卖服务。
- Controller：`MerchantProductController.getOwnProduct`。
- operationId：`MerchantProductController_getOwnProduct`。
- 访问权限：商家。
- 实现状态：待新增。

仅所属商家可以查看预留库存及禁售状态。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `id` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultMerchantProductVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E10 查询平台商品

`GET /api/admin/products`

- 服务：S02 农资买卖服务。
- Controller：`AdminProductController.listProducts`。
- operationId：`AdminProductController_listProducts`。
- 访问权限：管理员。
- 实现状态：待新增。

管理员可筛选商家与上下架状态，查看禁售状态；此权限不允许代替商家调库存。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `page` | 查询 | 否 | `integer` | 最小值 1；最大值 1000000；默认 `1`；页码从 1 开始。 |
| `size` | 查询 | 否 | `integer` | 最小值 1；最大值 100；默认 `10`；每页最多 100 条。 |
| `category` | 查询 | 否 | `ProductCategory` | ；按分类筛选。 |
| `keyword` | 查询 | 否 | `string` | 最少字符 0；最多字符 100；商品名称或描述关键词。 |
| `merchantId` | 查询 | 否 | `integer(int64)` | 最小值 1；最大值 9007199254740991；商品所属商家。 |
| `status` | 查询 | 否 | `string` | 允许值：`"ON_SALE"`、`"OFF_SALE"`；商品上下架状态。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultPageMerchantProductVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E11 设置或解除平台禁售

`PUT /api/admin/products/{id}/sale-restriction`

- 服务：S02 农资买卖服务。
- Controller：`AdminProductController.setSaleRestriction`。
- operationId：`AdminProductController_setSaleRestriction`。
- 访问权限：管理员。
- 实现状态：待新增。

restricted=true 时强制下架并阻止商家重新上架；解除限制不自动上架。必须记录原因，已有订单仍应履约。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `id` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：必填；`application/json` → `SaleRestrictionDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultMerchantProductVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E12 提交结算并按商家拆单

`POST /api/checkouts`

- 服务：S02 农资买卖服务。
- Controller：`CheckoutController.createCheckout`。
- operationId：`CheckoutController_createCheckout`。
- 访问权限：农户、商家。
- 实现状态：待新增。

S02 先经 S01 确认买家身份及各商家经营状态，再在 agrimart_trade 本地事务内合并商品行、按商家拆单并预留库存；任一失败全部回滚。最多 50 行，每种合并数量 1～9999，拒绝自购，单笔上限 99999999.99 元。事务不包含远程用户查询或支付调用；经营状态与身份取本次可信快照。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：必填；`application/json` → `CheckoutDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultCheckoutVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E13 查询本人结算批次

`GET /api/checkouts/{checkoutNo}`

- 服务：S02 农资买卖服务。
- Controller：`CheckoutController.getCheckout`。
- operationId：`CheckoutController_getCheckout`。
- 访问权限：农户、商家。
- 实现状态：待新增。

仅买家可查看；返回批次内订单的最新状态。checkoutNo 不是合并支付或合并退款的交易号。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `checkoutNo` | 路径 | 是 | `string` | 最少字符 1；最多字符 64 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultCheckoutVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E14 查询本人购买订单

`GET /api/orders/me`

- 服务：S02 农资买卖服务。
- Controller：`OrderController.listMyOrders`。
- operationId：`OrderController_listMyOrders`。
- 访问权限：农户、商家。
- 实现状态：待新增。

FARMER 与 MERCHANT 均可查询自己的购买订单，身份来自认证上下文。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `page` | 查询 | 否 | `integer` | 最小值 1；最大值 1000000；默认 `1`；页码从 1 开始。 |
| `size` | 查询 | 否 | `integer` | 最小值 1；最大值 100；默认 `10`；每页最多 100 条。 |
| `status` | 查询 | 否 | `OrderStatus` | ；按订单状态筛选。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultPageOrderVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E15 查询订单详情

`GET /api/orders/{id}`

- 服务：S02 农资买卖服务。
- Controller：`OrderController.getOrderById`。
- operationId：`OrderController_getOrderById`。
- 访问权限：农户、商家。
- 实现状态：现有路径，目标行为及服务归属需调整。

仅订单买家或订单所属商家可访问；其他主体统一 404。不得泄露其他商家的拆单及收货信息。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `id` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultOrderVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E16 按订单号查询订单

`GET /api/orders/no/{orderNo}`

- 服务：S02 农资买卖服务。
- Controller：`OrderController.getOrderByOrderNo`。
- operationId：`OrderController_getOrderByOrderNo`。
- 访问权限：农户、商家。
- 实现状态：现有路径，目标行为及服务归属需调整。

与订单 ID 查询使用相同归属校验，订单号不可作为授权凭据。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `orderNo` | 路径 | 是 | `string` | 最少字符 1；最多字符 64 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultOrderVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E17 取消待支付订单

`POST /api/orders/{id}/cancel`

- 服务：S02 农资买卖服务。
- Controller：`OrderController.cancelOrder`。
- operationId：`OrderController_cancelOrder`。
- 访问权限：农户、商家。
- 实现状态：现有路径，目标行为及服务归属需调整。

由 S02 校验买家并先提交 CLOSING。没有绑定支付业务号时本地取消并释放预留；否则持久化调用 P01 的任务，由 S03 串行查询并关闭。202 仅代表接受，不证明通道关闭或库存已释放。S02 收到可信支付事实后才决定 CANCELLED、PAID 或补偿退款。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `id` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：必填；`application/json` → `CancelDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 202 | `application/json` → `ResultOrderVo` | 已接受，返回当前状态；必须查询最终结果。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`、`503`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E18 确认收货并完成订单

`POST /api/orders/{id}/receipt`

- 服务：S02 农资买卖服务。
- Controller：`OrderController.confirmReceipt`。
- operationId：`OrderController_confirmReceipt`。
- 访问权限：农户、商家。
- 实现状态：待新增。

仅买家可将 SHIPPED 转为 COMPLETED。没有自动收货或独立 DELIVERED 步骤；重复相同确认不重复产生副作用。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `id` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultOrderVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E19 查询本人销售订单

`GET /api/merchant/orders`

- 服务：S02 农资买卖服务。
- Controller：`MerchantOrderController.listSalesOrders`。
- operationId：`MerchantOrderController_listSalesOrders`。
- 访问权限：商家。
- 实现状态：待新增。

只返回当前商家的拆分订单；不可用买家订单列表替代。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `page` | 查询 | 否 | `integer` | 最小值 1；最大值 1000000；默认 `1`；页码从 1 开始。 |
| `size` | 查询 | 否 | `integer` | 最小值 1；最大值 100；默认 `10`；每页最多 100 条。 |
| `status` | 查询 | 否 | `OrderStatus` | ；按订单状态筛选。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultPageOrderVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E20 发货

`POST /api/merchant/orders/{id}/shipment`

- 服务：S02 农资买卖服务。
- Controller：`MerchantOrderController.shipOrder`。
- operationId：`MerchantOrderController_shipOrder`。
- 访问权限：商家。
- 实现状态：待新增。

仅所属商家可将 PAID 转为 SHIPPED，须填写承运方与运单号；存在退款审核或执行时拒绝。整单发货，不拆包。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `id` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：必填；`application/json` → `ShipmentDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultOrderVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E21 申请发货前全额退款

`POST /api/orders/{id}/refunds`

- 服务：S02 农资买卖服务。
- Controller：`RefundController.requestRefund`。
- operationId：`RefundController_requestRefund`。
- 访问权限：农户、商家。
- 实现状态：待新增。

S02 仅允许订单买家将 PAID 原子转为 REFUNDING，创建 PENDING_REVIEW 退款申请并阻止发货；金额来自订单实付快照，不从客户端接收。申请与订单属于同一农资数据库，不触碰支付数据库。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `id` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：必填；`application/json` → `RefundCreateDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultRefundVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E22 查询退款详情

`GET /api/refunds/{refundId}`

- 服务：S02 农资买卖服务。
- Controller：`RefundController.getRefund`。
- operationId：`RefundController_getRefund`。
- 访问权限：农户、商家。
- 实现状态：待新增。

由 S02 返回本人订单的退款申请与最后同步的执行结果投影。资金状态以 S03 为准；投影可能短暂滞后，S02 通过结果通知及 P04 定期核对恢复，禁止跨库查询。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `refundId` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultRefundVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E23 查询本人退款

`GET /api/refunds/me`

- 服务：S02 农资买卖服务。
- Controller：`RefundController.listMyRefunds`。
- operationId：`RefundController_listMyRefunds`。
- 访问权限：农户、商家。
- 实现状态：待新增。

由 S02 查询买家本人的退款申请与执行状态投影，包含系统补偿；按创建时间及 ID 倒序。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `page` | 查询 | 否 | `integer` | 最小值 1；最大值 1000000；默认 `1`；页码从 1 开始。 |
| `size` | 查询 | 否 | `integer` | 最小值 1；最大值 100；默认 `10`；每页最多 100 条。 |
| `status` | 查询 | 否 | `RefundStatus` | ；按退款状态筛选。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultPageRefundVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E24 查询本人商家退款

`GET /api/merchant/refunds`

- 服务：S02 农资买卖服务。
- Controller：`MerchantRefundController.listOwnRefunds`。
- operationId：`MerchantRefundController_listOwnRefunds`。
- 访问权限：商家。
- 实现状态：待新增。

由 S02 查询本人商家订单的退款申请与执行状态投影。系统补偿只读，不接受商家重新审核。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `page` | 查询 | 否 | `integer` | 最小值 1；最大值 1000000；默认 `1`；页码从 1 开始。 |
| `size` | 查询 | 否 | `integer` | 最小值 1；最大值 100；默认 `10`；每页最多 100 条。 |
| `status` | 查询 | 否 | `RefundStatus` | ；按退款状态筛选。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultPageRefundVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E25 审核全额退款申请

`POST /api/merchant/refunds/{refundId}/decision`

- 服务：S02 农资买卖服务。
- Controller：`MerchantRefundController.decideRefund`。
- operationId：`MerchantRefundController_decideRefund`。
- 访问权限：商家。
- 实现状态：待新增。

S02 仅允许所属商家审核待审核申请。拒绝须说明原因并本地恢复 PAID；同意后先提交审核结果和持久化 P02 执行任务，投递成功不等于退款成功。S03 独立执行退款并通过 T04 回传事实，S02 才本地更新订单与库存。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `refundId` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：必填；`application/json` → `DecisionDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultRefundVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E26 重试异常退款

`POST /api/merchant/refunds/{refundId}/retry`

- 服务：S02 农资买卖服务。
- Controller：`MerchantRefundController.retryRefund`。
- operationId：`MerchantRefundController_retryRefund`。
- 访问权限：商家。
- 实现状态：待新增。

S02 校验所属商家及已批准且 FAILED/UNKNOWN 的申请，持久化重试同一 refundNo 的 P02 任务。S03 先查询通道、再决定是否重试；不创建新退款。系统补偿由内部任务重试。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `refundId` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 202 | `application/json` → `ResultRefundVo` | 已接受，返回当前状态；必须查询最终结果。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`、`503`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E27 按订单项评价商品

`POST /api/reviews`

- 服务：S02 农资买卖服务。
- Controller：`ReviewController.createReview`。
- operationId：`ReviewController_createReview`。
- 访问权限：农户、商家。
- 实现状态：现有路径，目标行为及服务归属需调整。

仅已完成订单买家可提交。通过 orderItemId 推导订单与商品，不再接受 userId/orderId/productId。每个订单项只能评价一次，数据库唯一约束保护并发。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：必填；`application/json` → `ReviewCreateDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultReviewVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E28 查询公开商品评价

`GET /api/reviews/product/{productId}`

- 服务：S02 农资买卖服务。
- Controller：`ReviewController.getProductReviews`。
- operationId：`ReviewController_getProductReviews`。
- 访问权限：游客。
- 实现状态：现有路径，目标行为及服务归属需调整。

商品必须公开可见；评价作者只显示脱敏名称，不返回手机号、地址或订单号。按创建时间和 ID 倒序。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `productId` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |
| `page` | 查询 | 否 | `integer` | 最小值 1；最大值 1000000；默认 `1`；页码从 1 开始。 |
| `size` | 查询 | 否 | `integer` | 最小值 1；最大值 100；默认 `10`；每页最多 100 条。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultPageReviewVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E29 查询本人评价

`GET /api/reviews/me`

- 服务：S02 农资买卖服务。
- Controller：`ReviewController.listMyReviews`。
- operationId：`ReviewController_listMyReviews`。
- 访问权限：农户、商家。
- 实现状态：待新增。

仅查询本人购买产生的评价，不接受其他用户 ID。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `page` | 查询 | 否 | `integer` | 最小值 1；最大值 1000000；默认 `1`；页码从 1 开始。 |
| `size` | 查询 | 否 | `integer` | 最小值 1；最大值 100；默认 `10`；每页最多 100 条。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultPageReviewVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E30 查询本人商品评价

`GET /api/merchant/reviews`

- 服务：S02 农资买卖服务。
- Controller：`MerchantReviewController.listOwnProductReviews`。
- operationId：`MerchantReviewController_listOwnProductReviews`。
- 访问权限：商家。
- 实现状态：待新增。

包含本人上下架商品的评价，支持按 productId 筛选；不能修改买家评分。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `page` | 查询 | 否 | `integer` | 最小值 1；最大值 1000000；默认 `1`；页码从 1 开始。 |
| `size` | 查询 | 否 | `integer` | 最小值 1；最大值 100；默认 `10`；每页最多 100 条。 |
| `productId` | 查询 | 否 | `integer(int64)` | 最小值 1；最大值 9007199254740991；限本人商品。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultPageReviewVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S02-E31 回复本人商品评价

`POST /api/reviews/{id}/reply`

- 服务：S02 农资买卖服务。
- Controller：`ReviewController.replyReview`。
- operationId：`ReviewController_replyReview`。
- 访问权限：商家。
- 实现状态：现有路径，目标行为及服务归属需调整。

仅所属商家可回复一次，JSON reply 替代 Query。相同内容重试返回原回复，不同内容再次提交返回 409。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `id` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：必填；`application/json` → `ReviewReplyDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultReviewVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

### 4.3 S03 支付服务外部接口

#### S03-E01 创建或复用沙箱支付

`POST /api/orders/{id}/payments`

- 服务：S03 支付服务。
- Controller：`PaymentController.createPayment`。
- operationId：`PaymentController_createPayment`。
- 访问权限：农户、商家。
- 实现状态：待新增。

由网关优先路由至 S03。S03 验证买家身份，先创建或复用按 orderId 唯一的 INITIATING 支付记录，再调用 S02 T01 获取有效订单授权及服务端金额，最后调用沙箱。S03 按 paymentNo 串行协调创建和关闭，不得在关闭门闩生效后新建通道交易。供应商缺失返回 503，不延长订单有效期。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `id` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultPaymentVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`、`503`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S03-E02 查询本人支付记录

`GET /api/payments/{paymentNo}`

- 服务：S03 支付服务。
- Controller：`PaymentController.getPayment`。
- operationId：`PaymentController_getPayment`。
- 访问权限：农户、商家。
- 实现状态：待新增。

只返回买家本人记录的本地最新状态；不会仅凭浏览器跳转标记成功。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `paymentNo` | 路径 | 是 | `string` | 最少字符 1；最多字符 64 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultPaymentVo` | 操作成功；同步成功业务码为 200。 |

失败状态：`400`、`401`、`403`、`404`、`500`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S03-E03 向通道核对支付结果

`POST /api/payments/{paymentNo}/sync`

- 服务：S03 支付服务。
- Controller：`PaymentController.syncPayment`。
- operationId：`PaymentController_syncPayment`。
- 访问权限：农户、商家。
- 实现状态：待新增。

仅买家可请求。服务器查询通道并按同一状态机归并；未知结果保留 UNKNOWN 并重试，不盲目新建支付。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `paymentNo` | 路径 | 是 | `string` | 最少字符 1；最多字符 64 |
| `Idempotency-Key` | 请求头 | 是 | `string` | 最少字符 16；最多字符 128；格式约束 `^[A-Za-z0-9_-]+$`；按主体与操作隔离。相同键和规范化请求返回原结果；相同键不同请求返回 409；记录至少保留 7 天，业务唯一约束永久防重复。 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 202 | `application/json` → `ResultPaymentVo` | 已接受，返回当前状态；必须查询最终结果。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`500`、`503`，响应体为 `ErrorResult`；含义按通用错误规则执行。

#### S03-E04 接收沙箱支付回调

`POST /api/payment-callbacks/{providerCode}/payments`

- 服务：S03 支付服务。
- Controller：`PaymentCallbackController.receivePaymentEvent`。
- operationId：`PaymentCallbackController_receivePaymentEvent`。
- 访问权限：通过验签的支付通道。
- 实现状态：待新增，供应商适配协议待补齐。

此处仅定义适配入口，不虚构通用供应商回调格式。适配器读取原始请求并验证供应商签名、商户、环境、金额、币种、交易关联及事件唯一性，转换为内部事件后幂等持久化。供应商选型后必须补齐请求字段、签名算法、证书来源、应答内容与重试规则。验签失败不得更新业务状态。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `providerCode` | 路径 | 是 | `string` | 最少字符 1；最多字符 40；格式约束 `^[a-z0-9_-]+$` |

请求体：必填；`application/json` → `object`；`application/x-www-form-urlencoded` → `object<string,string>`。

保留原始请求字节；具体媒体类型与编码由选定供应商确定。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `text/plain` → `string`；`application/json` → `object` | 按通道协议返回成功应答，仅在验签和持久化成功或已幂等处理后确认；不使用 Result 包装。 |
| 400 | `text/plain` → `string`；`application/json` → `object` | 无效事件；具体失败应答需由适配器按通道协议确定。 |
| 503 | `text/plain` → `string`；`application/json` → `object` | 暂时无法持久化或处理，不能错误确认成功。 |

#### S03-E05 接收沙箱退款回调

`POST /api/payment-callbacks/{providerCode}/refunds`

- 服务：S03 支付服务。
- Controller：`PaymentCallbackController.receiveRefundEvent`。
- operationId：`PaymentCallbackController_receiveRefundEvent`。
- 访问权限：通过验签的支付通道。
- 实现状态：待新增，供应商适配协议待补齐。

此处仅定义适配入口，不虚构通用供应商回调格式。适配器读取原始请求并验证供应商签名、商户、环境、金额、币种、交易关联及事件唯一性，转换为内部事件后幂等持久化。供应商选型后必须补齐请求字段、签名算法、证书来源、应答内容与重试规则。验签失败不得更新业务状态。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `providerCode` | 路径 | 是 | `string` | 最少字符 1；最多字符 40；格式约束 `^[a-z0-9_-]+$` |

请求体：必填；`application/json` → `object`；`application/x-www-form-urlencoded` → `object<string,string>`。

保留原始请求字节；具体媒体类型与编码由选定供应商确定。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `text/plain` → `string`；`application/json` → `object` | 按通道协议返回成功应答，仅在验签和持久化成功或已幂等处理后确认；不使用 Result 包装。 |
| 400 | `text/plain` → `string`；`application/json` → `object` | 无效事件；具体失败应答需由适配器按通道协议确定。 |
| 503 | `text/plain` → `string`；`application/json` → `object` | 暂时无法持久化或处理，不能错误确认成功。 |

## 5. 内部接口详情

内部接口仅用于已授权服务之间的通信，网关必须阻断公网访问。接口的接受应答与最终业务事实分离；事件应答成功前必须先持久化。

### U01 校验用户令牌

`POST /internal/auth/introspections`

- 服务：S01 用户服务。
- Controller：`InternalAuthController.introspect`。
- operationId：`InternalAuthController_introspect`。
- 访问权限：仅允许调用方：gateway、S02、S03。
- 实现状态：待新增。

网关、农资和支付服务使用本身服务凭据查询不透明令牌。无效令牌返回 active=false；用户服务不可用时调用方拒绝受保护操作，不信任公网 X-User-Id 或角色头。

内部基址：`http://agrimart-user-service:8081`。身份通过服务凭据校验，不能使用公网网关调用。

路径、查询与专用请求头参数：无；认证要求仍按上述权限执行。

请求体：必填；`application/json` → `TokenIntrospectionDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultAuthContextVo` | 服务内操作成功，code=200；事件已处理时幂等确认。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`503`，响应体为 `ErrorResult`；含义按通用错误规则执行。

### U02 查询商家经营资格

`GET /internal/merchants/{merchantId}`

- 服务：S01 用户服务。
- Controller：`InternalMerchantController.getMerchant`。
- operationId：`InternalMerchantController_getMerchant`。
- 访问权限：仅允许调用方：S02。
- 实现状态：待新增。

仅返回经营状态及店铺名称；S02 下单和经营命令校验资格，不泄露手机号或住址。不存在返回 404，未批准返回 approved=false。

内部基址：`http://agrimart-user-service:8081`。身份通过服务凭据校验，不能使用公网网关调用。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `merchantId` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultMerchantProfileVo` | 服务内操作成功，code=200；事件已处理时幂等确认。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`503`，响应体为 `ErrorResult`；含义按通用错误规则执行。

### T01 授权订单支付

`POST /internal/orders/{orderId}/payment-authorizations`

- 服务：S02 农资买卖服务。
- Controller：`InternalOrderController.authorizePayment`。
- operationId：`InternalOrderController_authorizePayment`。
- 访问权限：仅允许调用方：S03。
- 实现状态：待新增。

S03 先持久化唯一支付记录再调用。本地校验 buyerId、PENDING_PAYMENT、到期时间及未关闭条件，将 paymentNo 唯一绑定到订单，返回服务端金额。进入 CLOSING 后不得签发新授权；远程供应商调用不在本事务内。

内部基址：`http://agrimart-trade-service:8082`。身份通过服务凭据校验，不能使用公网网关调用。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `orderId` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |

请求体：必填；`application/json` → `PaymentAuthorizationDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultPaymentAuthorizationVo` | 服务内操作成功，code=200；事件已处理时幂等确认。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`503`，响应体为 `ErrorResult`；含义按通用错误规则执行。

### T02 归并可信支付或关单事实

`POST /internal/orders/{orderId}/payment-events`

- 服务：S02 农资买卖服务。
- Controller：`InternalOrderController.applyPaymentEvent`。
- operationId：`InternalOrderController_applyPaymentEvent`。
- 访问权限：仅允许调用方：S03。
- 实现状态：待新增。

仅接受 S03。以 eventId 去重并检查 paymentNo、金额、币种、环境与版本；在 agrimart_trade 本地事务内写收件记录、订单和库存流水。已付不回退为关闭；超期或取消后收款创建系统补偿退款及 P02 待办。

内部基址：`http://agrimart-trade-service:8082`。身份通过服务凭据校验，不能使用公网网关调用。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `orderId` | 路径 | 是 | `integer(int64)` | 最小值 1；最大值 9007199254740991 |

请求体：必填；`application/json` → `PaymentEventDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultVoid` | 服务内操作成功，code=200；事件已处理时幂等确认。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`503`，响应体为 `ErrorResult`；含义按通用错误规则执行。

### T03 读取退款执行授权

`GET /internal/refunds/{refundNo}/execution-authorization`

- 服务：S02 农资买卖服务。
- Controller：`InternalRefundController.authorizeExecution`。
- operationId：`InternalRefundController_authorizeExecution`。
- 访问权限：仅允许调用方：S03。
- 实现状态：待新增。

仅 S03 可读。BUYER_REQUEST 必须已经商家同意；LATE_PAYMENT_COMPENSATION 必须有系统资金事实。返回不可变全额退款授权，未批准返回 409，不接受客户端金额。

内部基址：`http://agrimart-trade-service:8082`。身份通过服务凭据校验，不能使用公网网关调用。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `refundNo` | 路径 | 是 | `string` | 最少字符 1；最多字符 64 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultRefundExecutionAuthorizationVo` | 服务内操作成功，code=200；事件已处理时幂等确认。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`503`，响应体为 `ErrorResult`；含义按通用错误规则执行。

### T04 归并退款执行事实

`POST /internal/refunds/{refundNo}/execution-events`

- 服务：S02 农资买卖服务。
- Controller：`InternalRefundController.applyExecutionEvent`。
- operationId：`InternalRefundController_applyExecutionEvent`。
- 访问权限：仅允许调用方：S03。
- 实现状态：待新增。

仅接受 S03。检查退款业务号、支付号、金额及事件版本，以 eventId 去重；成功事实与订单 REFUNDED、库存回补及销量扣回在 S02 同一事务执行。系统补偿仅同步退款结果，不再次回补库存。

内部基址：`http://agrimart-trade-service:8082`。身份通过服务凭据校验，不能使用公网网关调用。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `refundNo` | 路径 | 是 | `string` | 最少字符 1；最多字符 64 |

请求体：必填；`application/json` → `RefundEventDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultVoid` | 服务内操作成功，code=200；事件已处理时幂等确认。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`503`，响应体为 `ErrorResult`；含义按通用错误规则执行。

### P01 接受关闭支付命令

`POST /internal/payments/{paymentNo}/close`

- 服务：S03 支付服务。
- Controller：`InternalPaymentController.closePayment`。
- operationId：`InternalPaymentController_closePayment`。
- 访问权限：仅允许调用方：S02。
- 实现状态：待新增。

仅 S02 发起，以 commandId 去重。S03 持久化关闭门闩并串行协调在途创建、查询及关闭调用；未知结果保留并重试。接受成功不等于关闭成功，最终事实经 T02 回传。

内部基址：`http://agrimart-payment-service:8083`。身份通过服务凭据校验，不能使用公网网关调用。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `paymentNo` | 路径 | 是 | `string` | 最少字符 1；最多字符 64 |

请求体：必填；`application/json` → `ClosePaymentDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 202 | `application/json` → `ResultCommandAcceptedVo` | 已持久化接受，须等待结果事实；不得据此释放库存。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`503`，响应体为 `ErrorResult`；含义按通用错误规则执行。

### P02 接受或重试全额退款执行

`POST /internal/refunds`

- 服务：S03 支付服务。
- Controller：`InternalPaymentController.executeRefund`。
- operationId：`InternalPaymentController_executeRefund`。
- 访问权限：仅允许调用方：S02。
- 实现状态：待新增。

仅 S02 发起。以 commandId 去重并按 refundNo 永久唯一；S03 调用 T03 取得授权，核对本服务已成功支付记录后执行，重试先查通道。执行结果经 T04 回传并保留重投任务。

内部基址：`http://agrimart-payment-service:8083`。身份通过服务凭据校验，不能使用公网网关调用。

路径、查询与专用请求头参数：无；认证要求仍按上述权限执行。

请求体：必填；`application/json` → `ExecuteRefundDto`。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 202 | `application/json` → `ResultCommandAcceptedVo` | 已持久化接受，须等待结果事实；不得据此释放库存。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`503`，响应体为 `ErrorResult`；含义按通用错误规则执行。

### P03 查询权威支付状态

`GET /internal/payments/{paymentNo}`

- 服务：S03 支付服务。
- Controller：`InternalPaymentController.getPaymentState`。
- operationId：`InternalPaymentController_getPaymentState`。
- 访问权限：仅允许调用方：S02。
- 实现状态：待新增。

仅 S02 用于修复丢失通知或核对关闭结果，返回支付服务持久化的权威版本。未知状态不能当作未支付；S02 通过与 T02 相同的去重归并规则更新本地投影。

内部基址：`http://agrimart-payment-service:8083`。身份通过服务凭据校验，不能使用公网网关调用。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `paymentNo` | 路径 | 是 | `string` | 最少字符 1；最多字符 64 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultInternalPaymentStateVo` | 服务内操作成功，code=200；事件已处理时幂等确认。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`503`，响应体为 `ErrorResult`；含义按通用错误规则执行。

### P04 查询权威退款执行状态

`GET /internal/refunds/{refundNo}`

- 服务：S03 支付服务。
- Controller：`InternalPaymentController.getRefundState`。
- operationId：`InternalPaymentController_getRefundState`。
- 访问权限：仅允许调用方：S02。
- 实现状态：待新增。

仅 S02 用于补偿核对，返回 S03 的执行结果及版本；不直接修改农资库存或订单。

内部基址：`http://agrimart-payment-service:8083`。身份通过服务凭据校验，不能使用公网网关调用。

| 参数 | 位置 | 必填 | 类型 | 约束与说明 |
| --- | --- | --- | --- | --- |
| `refundNo` | 路径 | 是 | `string` | 最少字符 1；最多字符 64 |

请求体：无。

| HTTP 状态 | 返回类型 | 结果说明 |
| --- | --- | --- |
| 200 | `application/json` → `ResultInternalRefundStateVo` | 服务内操作成功，code=200；事件已处理时幂等确认。 |

失败状态：`400`、`401`、`403`、`404`、`409`、`503`，响应体为 `ErrorResult`；含义按通用错误规则执行。

## 6. 请求与响应示例

示例数据仅用于说明。下列字段按模型校验，示例不代表接口已实现或业务验收已通过。

### 6.1 登录请求

方法与地址：`POST /api/users/login`；Content-Type 为 application/json。

模型：`LoginDto`。

```json
{
  "username": "demo_farmer",
  "password": "demo_pass_123"
}
```

### 6.2 登录成功响应

模型：`ResultAuthVo`。

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "demo_access_token_for_documentation_only",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "user": {
      "id": 1,
      "username": "demo_farmer",
      "realName": "演示用户",
      "phone": "13800000000",
      "role": "FARMER",
      "status": "ACTIVE",
      "createdAt": "2026-11-05T10:00:00+08:00"
    }
  }
}
```

### 6.3 按商家拆单的结算请求

方法与地址：`POST /api/checkouts`。请求头包含 Bearer 令牌、Content-Type: application/json 和 `Idempotency-Key: checkout_demo_20261105_001`。身份、价格和商家归属由服务端确定。

模型：`CheckoutDto`。

```json
{
  "items": [
    {
      "productId": 101,
      "quantity": 2
    },
    {
      "productId": 202,
      "quantity": 1
    }
  ],
  "shippingAddress": "演示村演示农场",
  "receiverName": "演示买家",
  "receiverPhone": "13800000000"
}
```

### 6.4 拒绝退款的审核请求

模型：`DecisionDto`。

```json
{
  "decision": "REJECT",
  "reason": "演示审核原因"
}
```

### 6.5 库存冲突响应

模型：`ErrorResult`。

```json
{
  "code": 409,
  "message": "商品库存不足",
  "data": null,
  "errorCode": "STOCK_INSUFFICIENT",
  "traceId": "demo-trace-20261105-001"
}
```

## 7. 数据模型与字段

以下列出全部 79 个模型。类型名称表示本文内的模型定义；`array<T>` 为 T 类型数组。表中“必填”指对应对象的 required 条件，未标必填的字段在无值时省略。状态相关必填条件同时受各模型的业务说明约束。

对象若标记“不接受未定义字段”，不得额外提交未列出的字段。DecisionDto 的两个变体必须且只能满足一个，不可把拒绝原因要求当成普通可选项。

### Money

人民币元，固定两位小数，禁止二进制浮点计算；单笔金额不超过 99999999.99。

类型：`string`。

约束：格式约束 `^(0|[1-9][0-9]{0,7})\.[0-9]{2}$`。

示例：`"41.00"`。

### ProductCategory

类型：`string`。

约束：允许值：`"FERTILIZER"`、`"SEED"`、`"PESTICIDE"`、`"AGRICULTURAL_TOOL"`。

### OrderStatus

类型：`string`。

约束：允许值：`"PENDING_PAYMENT"`、`"CLOSING"`、`"PAID"`、`"SHIPPED"`、`"COMPLETED"`、`"CANCELLED"`、`"REFUNDING"`、`"REFUNDED"`。

### PaymentStatus

类型：`string`。

约束：允许值：`"INITIATING"`、`"PENDING"`、`"SUCCEEDED"`、`"CLOSED"`、`"FAILED"`、`"UNKNOWN"`。

### RefundStatus

类型：`string`。

约束：允许值：`"PENDING_REVIEW"`、`"REJECTED"`、`"PROCESSING"`、`"SUCCEEDED"`、`"FAILED"`、`"UNKNOWN"`。

### ApplicationStatus

类型：`string`。

约束：允许值：`"PENDING"`、`"APPROVED"`、`"REJECTED"`。

### UserVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `id` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `username` | `string` | 是 | 最少字符 3；最多字符 50 |
| `realName` | `string` | 是 | 最少字符 1；最多字符 50 |
| `phone` | `string` | 是 | 格式约束 `^1[3-9][0-9]{9}$` |
| `email` | `string` | 否 | 最少字符 0；最多字符 100；格式 `email` |
| `role` | `string` | 是 | 允许值：`"FARMER"`、`"MERCHANT"`、`"ADMIN"` |
| `status` | `string` | 是 | 允许值：`"ACTIVE"`、`"INACTIVE"` |
| `createdAt` | `string` | 是 | 格式 `date-time` |

### RegisterDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `username` | `string` | 是 | 最少字符 3；最多字符 50 |
| `password` | `string` | 是 | 最少字符 8；最多字符 72；仅用于输入，不在响应中返回 |
| `realName` | `string` | 是 | 最少字符 1；最多字符 50 |
| `phone` | `string` | 是 | 格式约束 `^1[3-9][0-9]{9}$` |
| `email` | `string` | 否 | 最少字符 0；最多字符 100；格式 `email` |

### LoginDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `username` | `string` | 是 | 最少字符 3；最多字符 50 |
| `password` | `string` | 是 | 最少字符 8；最多字符 72；仅用于输入，不在响应中返回 |

### AuthVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `accessToken` | `string` | 是 | 最少字符 32；最多字符 512 |
| `tokenType` | `string` | 是 | 允许值：`"Bearer"` |
| `expiresIn` | `integer` | 是 | 允许值：`7200` |
| `user` | `UserVo` | 是 | 按字段类型传输 |

### MerchantApplicationDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `shopName` | `string` | 是 | 最少字符 2；最多字符 100 |
| `contactName` | `string` | 是 | 最少字符 1；最多字符 50 |
| `contactPhone` | `string` | 是 | 格式约束 `^1[3-9][0-9]{9}$` |
| `businessDescription` | `string` | 是 | 最少字符 1；最多字符 500 |

### MerchantApplicationVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `id` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `applicantId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `shopName` | `string` | 是 | 最少字符 2；最多字符 100 |
| `contactName` | `string` | 是 | 最少字符 1；最多字符 50 |
| `contactPhone` | `string` | 是 | 格式约束 `^1[3-9][0-9]{9}$` |
| `businessDescription` | `string` | 是 | 最少字符 1；最多字符 500 |
| `status` | `ApplicationStatus` | 是 | 按字段类型传输 |
| `reviewReason` | `string` | 否 | 最少字符 0；最多字符 500 |
| `reviewedAt` | `string` | 否 | 格式 `date-time` |
| `createdAt` | `string` | 是 | 格式 `date-time` |

### DecisionDto

类型：`互斥对象变体`。

变体 1：

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `decision` | `string` | 是 | 允许值：`"APPROVE"` |
| `reason` | `string` | 否 | 最少字符 0；最多字符 500 |

该变体不接受未定义字段。

变体 2：

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `decision` | `string` | 是 | 允许值：`"REJECT"` |
| `reason` | `string` | 是 | 最少字符 1；最多字符 500 |

该变体不接受未定义字段。

### ProductCreateDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `name` | `string` | 是 | 最少字符 1；最多字符 200 |
| `description` | `string` | 否 | 最少字符 0；最多字符 5000 |
| `category` | `ProductCategory` | 是 | 按字段类型传输 |
| `price` | `Money` | 是 | 禁止值：`"0.00"` |
| `unit` | `string` | 是 | 最少字符 1；最多字符 50 |
| `brand` | `string` | 否 | 最少字符 0；最多字符 100 |
| `specification` | `string` | 否 | 最少字符 0；最多字符 100 |
| `images` | `array<string>` | 否 | 最少元素 0；最多元素 5；每个元素：最多字符 500；格式 `uri` |
| `usageGuide` | `string` | 否 | 最少字符 0；最多字符 5000 |
| `stock` | `integer` | 是 | 最小值 0；最大值 1000000 |

### ProductUpdateDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `name` | `string` | 是 | 最少字符 1；最多字符 200 |
| `description` | `string` | 否 | 最少字符 0；最多字符 5000 |
| `category` | `ProductCategory` | 是 | 按字段类型传输 |
| `price` | `Money` | 是 | 禁止值：`"0.00"` |
| `unit` | `string` | 是 | 最少字符 1；最多字符 50 |
| `brand` | `string` | 否 | 最少字符 0；最多字符 100 |
| `specification` | `string` | 否 | 最少字符 0；最多字符 100 |
| `images` | `array<string>` | 否 | 最少元素 0；最多元素 5；每个元素：最多字符 500；格式 `uri` |
| `usageGuide` | `string` | 否 | 最少字符 0；最多字符 5000 |

### ProductStatusDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `status` | `string` | 是 | 允许值：`"ON_SALE"`、`"OFF_SALE"` |

### StockAdjustmentDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `delta` | `integer` | 是 | 最小值 -1000000；最大值 1000000；禁止值：`0` |
| `reason` | `string` | 是 | 最少字符 1；最多字符 500 |

### SaleRestrictionDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `restricted` | `boolean` | 是 | 按字段类型传输 |
| `reason` | `string` | 是 | 最少字符 1；最多字符 500 |

### ProductVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `id` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `name` | `string` | 是 | 最少字符 1；最多字符 200 |
| `description` | `string` | 否 | 最少字符 0；最多字符 5000 |
| `category` | `ProductCategory` | 是 | 按字段类型传输 |
| `price` | `Money` | 是 | 禁止值：`"0.00"` |
| `unit` | `string` | 是 | 最少字符 1；最多字符 50 |
| `brand` | `string` | 否 | 最少字符 0；最多字符 100 |
| `specification` | `string` | 否 | 最少字符 0；最多字符 100 |
| `images` | `array<string>` | 否 | 最少元素 0；最多元素 5；每个元素：最多字符 500；格式 `uri` |
| `usageGuide` | `string` | 否 | 最少字符 0；最多字符 5000 |
| `stock` | `integer` | 是 | 最小值 0；最大值 2147483647 |
| `status` | `string` | 是 | 允许值：`"ON_SALE"`、`"OFF_SALE"` |
| `merchantId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `shopName` | `string` | 是 | 最少字符 2；最多字符 100 |
| `salesCount` | `integer` | 是 | 最小值 0；最大值 2147483647 |
| `rating` | `number / null` | 是 | 最小值 1；最大值 5；允许 null |
| `createdAt` | `string` | 是 | 格式 `date-time` |

### MerchantProductVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `id` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `name` | `string` | 是 | 最少字符 1；最多字符 200 |
| `description` | `string` | 否 | 最少字符 0；最多字符 5000 |
| `category` | `ProductCategory` | 是 | 按字段类型传输 |
| `price` | `Money` | 是 | 禁止值：`"0.00"` |
| `unit` | `string` | 是 | 最少字符 1；最多字符 50 |
| `brand` | `string` | 否 | 最少字符 0；最多字符 100 |
| `specification` | `string` | 否 | 最少字符 0；最多字符 100 |
| `images` | `array<string>` | 否 | 最少元素 0；最多元素 5；每个元素：最多字符 500；格式 `uri` |
| `usageGuide` | `string` | 否 | 最少字符 0；最多字符 5000 |
| `stock` | `integer` | 是 | 最小值 0；最大值 2147483647 |
| `status` | `string` | 是 | 允许值：`"ON_SALE"`、`"OFF_SALE"` |
| `merchantId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `shopName` | `string` | 是 | 最少字符 2；最多字符 100 |
| `salesCount` | `integer` | 是 | 最小值 0；最大值 2147483647 |
| `rating` | `number / null` | 是 | 最小值 1；最大值 5；允许 null |
| `createdAt` | `string` | 是 | 格式 `date-time` |
| `reservedStock` | `integer` | 是 | 最小值 0；最大值 2147483647 |
| `saleRestricted` | `boolean` | 是 | 按字段类型传输 |

### CheckoutItemDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `productId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `quantity` | `integer` | 是 | 最小值 1；最大值 9999 |

### CheckoutDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `items` | `array<CheckoutItemDto>` | 是 | 最少元素 1；最多元素 50 |
| `shippingAddress` | `string` | 是 | 最少字符 1；最多字符 200 |
| `receiverName` | `string` | 是 | 最少字符 1；最多字符 50 |
| `receiverPhone` | `string` | 是 | 格式约束 `^1[3-9][0-9]{9}$` |
| `remark` | `string` | 否 | 最少字符 0；最多字符 500 |

### OrderItemVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `id` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `productId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `productName` | `string` | 是 | 最少字符 1；最多字符 200 |
| `price` | `Money` | 是 | 按字段类型传输 |
| `quantity` | `integer` | 是 | 最小值 1；最大值 9999 |
| `subtotal` | `Money` | 是 | 按字段类型传输 |
| `productImage` | `string` | 否 | 最少字符 0；最多字符 500；格式 `uri` |
| `reviewed` | `boolean` | 是 | 按字段类型传输 |

### ShipmentVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `carrierName` | `string` | 是 | 最少字符 1；最多字符 100 |
| `trackingNumber` | `string` | 是 | 最少字符 1；最多字符 100 |
| `shippedAt` | `string` | 是 | 格式 `date-time` |

### OrderVo

状态相关字段：PAID 及其后续状态须有 paymentNo/paidAt；SHIPPED/COMPLETED 须有 shipment；COMPLETED 须有 completedAt；CANCELLED 须有 closeReason；REFUNDING 须有 activeRefundId。其他无值字段省略。

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `id` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `orderNo` | `string` | 是 | 最少字符 1；最多字符 50 |
| `checkoutNo` | `string` | 是 | 最少字符 1；最多字符 50 |
| `userId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `merchantId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `shopName` | `string` | 是 | 最少字符 2；最多字符 100 |
| `orderItems` | `array<OrderItemVo>` | 是 | 最少元素 1；最多元素 50 |
| `totalAmount` | `Money` | 是 | 按字段类型传输 |
| `currency` | `string` | 是 | 允许值：`"CNY"` |
| `status` | `OrderStatus` | 是 | 按字段类型传输 |
| `shippingAddress` | `string` | 是 | 最少字符 1；最多字符 200 |
| `receiverName` | `string` | 是 | 最少字符 1；最多字符 50 |
| `receiverPhone` | `string` | 是 | 格式约束 `^1[3-9][0-9]{9}$` |
| `remark` | `string` | 否 | 最少字符 0；最多字符 500 |
| `expiresAt` | `string` | 是 | 格式 `date-time` |
| `createdAt` | `string` | 是 | 格式 `date-time` |
| `paidAt` | `string` | 否 | 格式 `date-time` |
| `completedAt` | `string` | 否 | 格式 `date-time` |
| `shipment` | `ShipmentVo` | 否 | 按字段类型传输 |
| `closeReason` | `string` | 否 | 允许值：`"USER_CANCELLED"`、`"PAYMENT_TIMEOUT"` |
| `paymentNo` | `string` | 否 | 最少字符 1；最多字符 64 |
| `activeRefundId` | `integer(int64)` | 否 | 最小值 1；最大值 9007199254740991 |

### CheckoutVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `checkoutNo` | `string` | 是 | 最少字符 1；最多字符 50 |
| `createdAt` | `string` | 是 | 格式 `date-time` |
| `orders` | `array<OrderVo>` | 是 | 最少元素 1；最多元素 50 |

### CancelDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `reason` | `string` | 是 | 最少字符 1；最多字符 500 |

### ShipmentDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `carrierName` | `string` | 是 | 最少字符 1；最多字符 100 |
| `trackingNumber` | `string` | 是 | 最少字符 1；最多字符 100 |

### PaymentVo

统一业务侧支付模型，不是供应商通知原文。INITIATING/UNKNOWN 可以暂无 checkoutUrl；PENDING 可拉起支付时必须有 checkoutUrl；SUCCEEDED 必须有已核对的 providerTransactionNo 与 paidAt。

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `paymentNo` | `string` | 是 | 最少字符 1；最多字符 64 |
| `orderId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `providerCode` | `string` | 是 | 最少字符 1；最多字符 40；格式约束 `^[a-z0-9_-]+$` |
| `environment` | `string` | 是 | 允许值：`"SANDBOX"` |
| `amount` | `Money` | 是 | 按字段类型传输 |
| `currency` | `string` | 是 | 允许值：`"CNY"` |
| `status` | `PaymentStatus` | 是 | 按字段类型传输 |
| `checkoutUrl` | `string` | 否 | 最少字符 0；最多字符 2000；格式 `uri` |
| `expiresAt` | `string` | 是 | 格式 `date-time` |
| `providerTransactionNo` | `string` | 否 | 最少字符 1；最多字符 128 |
| `paidAt` | `string` | 否 | 格式 `date-time` |
| `createdAt` | `string` | 是 | 格式 `date-time` |

### RefundCreateDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `reason` | `string` | 是 | 最少字符 1；最多字符 500 |

### RefundVo

S02 维护退款申请与 S03 执行结果投影，不跨库联表。SUCCEEDED 必须有 refundedAt；只有 BUYER_REQUEST/PENDING_REVIEW 接受商家审核；系统补偿自动授权。

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `id` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `refundNo` | `string` | 是 | 最少字符 1；最多字符 64 |
| `orderId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `paymentNo` | `string` | 是 | 最少字符 1；最多字符 64 |
| `type` | `string` | 是 | 允许值：`"BUYER_REQUEST"`、`"LATE_PAYMENT_COMPENSATION"` |
| `amount` | `Money` | 是 | 按字段类型传输 |
| `currency` | `string` | 是 | 允许值：`"CNY"` |
| `status` | `RefundStatus` | 是 | 按字段类型传输 |
| `reason` | `string` | 是 | 最少字符 1；最多字符 500 |
| `reviewReason` | `string` | 否 | 最少字符 0；最多字符 500 |
| `providerRefundNo` | `string` | 否 | 最少字符 1；最多字符 128 |
| `failureCode` | `string` | 否 | 最少字符 1；最多字符 100 |
| `createdAt` | `string` | 是 | 格式 `date-time` |
| `reviewedAt` | `string` | 否 | 格式 `date-time` |
| `refundedAt` | `string` | 否 | 格式 `date-time` |

### ReviewCreateDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `orderItemId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `rating` | `integer` | 是 | 最小值 1；最大值 5 |
| `content` | `string` | 否 | 最少字符 0；最多字符 2000 |
| `images` | `array<string>` | 否 | 最少元素 0；最多元素 5；每个元素：最多字符 500；格式 `uri` |

### ReviewReplyDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `reply` | `string` | 是 | 最少字符 1；最多字符 1000 |

### ReviewVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `id` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `productId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `authorDisplayName` | `string` | 是 | 最少字符 1；最多字符 50 |
| `rating` | `integer` | 是 | 最小值 1；最大值 5 |
| `content` | `string` | 否 | 最少字符 0；最多字符 2000 |
| `images` | `array<string>` | 否 | 最少元素 0；最多元素 5；每个元素：最多字符 500；格式 `uri` |
| `merchantReply` | `string` | 否 | 最少字符 0；最多字符 1000 |
| `repliedAt` | `string` | 否 | 格式 `date-time` |
| `createdAt` | `string` | 是 | 格式 `date-time` |

### Void

成功时 data 为 null。

类型：`object / null`。

约束：允许值：`null`；允许 null。

### ErrorResult

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 最小值 400；最大值 599 |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `object / null` | 是 | 允许值：`null`；允许 null |
| `errorCode` | `string` | 是 | 允许值：`"VALIDATION_ERROR"`、`"UNAUTHENTICATED"`、`"FORBIDDEN"`、`"RESOURCE_NOT_FOUND"`、`"ACCOUNT_CONFLICT"`、`"STATE_CONFLICT"`、`"STOCK_INSUFFICIENT"`、`"AMOUNT_LIMIT_EXCEEDED"`、`"MERCHANT_NOT_APPROVED"`、`"PRODUCT_UNAVAILABLE"`、`"DUPLICATE_REVIEW"`、`"IDEMPOTENCY_CONFLICT"`、`"PAYMENT_ALREADY_SUCCEEDED"`、`"PAYMENT_PROVIDER_NOT_CONFIGURED"`、`"PROVIDER_UNAVAILABLE"`、`"CALLBACK_INVALID"`、`"INTERNAL_ERROR"`、`"DEPENDENCY_UNAVAILABLE"` |
| `traceId` | `string` | 是 | 最少字符 1；最多字符 100 |

### ResultUserVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `UserVo` | 是 | 按字段类型传输 |

### ResultAuthVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `AuthVo` | 是 | 按字段类型传输 |

### ResultVoid

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `Void` | 是 | 按字段类型传输 |

### ResultMerchantApplicationVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `MerchantApplicationVo` | 是 | 按字段类型传输 |

### PageMerchantApplicationVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `records` | `array<MerchantApplicationVo>` | 是 | 最少元素 0；最多元素 100 |
| `total` | `integer` | 是 | 最小值 0；最大值 9007199254740991 |
| `page` | `integer` | 是 | 最小值 1；最大值 1000000 |
| `size` | `integer` | 是 | 最小值 1；最大值 100 |
| `totalPages` | `integer` | 是 | 最小值 0；最大值 9007199254740991 |

### ResultPageMerchantApplicationVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `PageMerchantApplicationVo` | 是 | 按字段类型传输 |

### PageProductVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `records` | `array<ProductVo>` | 是 | 最少元素 0；最多元素 100 |
| `total` | `integer` | 是 | 最小值 0；最大值 9007199254740991 |
| `page` | `integer` | 是 | 最小值 1；最大值 1000000 |
| `size` | `integer` | 是 | 最小值 1；最大值 100 |
| `totalPages` | `integer` | 是 | 最小值 0；最大值 9007199254740991 |

### ResultPageProductVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `PageProductVo` | 是 | 按字段类型传输 |

### ResultProductVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `ProductVo` | 是 | 按字段类型传输 |

### ResultMerchantProductVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `MerchantProductVo` | 是 | 按字段类型传输 |

### PageMerchantProductVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `records` | `array<MerchantProductVo>` | 是 | 最少元素 0；最多元素 100 |
| `total` | `integer` | 是 | 最小值 0；最大值 9007199254740991 |
| `page` | `integer` | 是 | 最小值 1；最大值 1000000 |
| `size` | `integer` | 是 | 最小值 1；最大值 100 |
| `totalPages` | `integer` | 是 | 最小值 0；最大值 9007199254740991 |

### ResultPageMerchantProductVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `PageMerchantProductVo` | 是 | 按字段类型传输 |

### ResultCheckoutVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `CheckoutVo` | 是 | 按字段类型传输 |

### PageOrderVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `records` | `array<OrderVo>` | 是 | 最少元素 0；最多元素 100 |
| `total` | `integer` | 是 | 最小值 0；最大值 9007199254740991 |
| `page` | `integer` | 是 | 最小值 1；最大值 1000000 |
| `size` | `integer` | 是 | 最小值 1；最大值 100 |
| `totalPages` | `integer` | 是 | 最小值 0；最大值 9007199254740991 |

### ResultPageOrderVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `PageOrderVo` | 是 | 按字段类型传输 |

### ResultOrderVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `OrderVo` | 是 | 按字段类型传输 |

### ResultPaymentVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `PaymentVo` | 是 | 按字段类型传输 |

### ResultRefundVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `RefundVo` | 是 | 按字段类型传输 |

### PageRefundVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `records` | `array<RefundVo>` | 是 | 最少元素 0；最多元素 100 |
| `total` | `integer` | 是 | 最小值 0；最大值 9007199254740991 |
| `page` | `integer` | 是 | 最小值 1；最大值 1000000 |
| `size` | `integer` | 是 | 最小值 1；最大值 100 |
| `totalPages` | `integer` | 是 | 最小值 0；最大值 9007199254740991 |

### ResultPageRefundVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `PageRefundVo` | 是 | 按字段类型传输 |

### ResultReviewVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `ReviewVo` | 是 | 按字段类型传输 |

### PageReviewVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `records` | `array<ReviewVo>` | 是 | 最少元素 0；最多元素 100 |
| `total` | `integer` | 是 | 最小值 0；最大值 9007199254740991 |
| `page` | `integer` | 是 | 最小值 1；最大值 1000000 |
| `size` | `integer` | 是 | 最小值 1；最大值 100 |
| `totalPages` | `integer` | 是 | 最小值 0；最大值 9007199254740991 |

### ResultPageReviewVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `PageReviewVo` | 是 | 按字段类型传输 |

### TokenIntrospectionDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `accessToken` | `string` | 是 | 最少字符 32；最多字符 512；仅用于输入，不在响应中返回 |

### AuthContextVo

active=false 表示令牌无效；active=true 必须返回 subjectId、role、expiresAt。只供网关和服务验证身份，不能用于公开用户资料查询。

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `active` | `boolean` | 是 | 按字段类型传输 |
| `subjectId` | `integer(int64)` | 否 | 最小值 1；最大值 9007199254740991 |
| `role` | `string` | 否 | 允许值：`"FARMER"`、`"MERCHANT"`、`"ADMIN"` |
| `expiresAt` | `string` | 否 | 格式 `date-time` |

### MerchantProfileVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `merchantId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `approved` | `boolean` | 是 | 按字段类型传输 |
| `active` | `boolean` | 是 | 按字段类型传输 |
| `shopName` | `string` | 否 | 最少字符 2；最多字符 100 |

### PaymentAuthorizationDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `paymentNo` | `string` | 是 | 最少字符 1；最多字符 64 |
| `buyerId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |

### PaymentAuthorizationVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `authorizationNo` | `string` | 是 | 最少字符 1；最多字符 64 |
| `paymentNo` | `string` | 是 | 最少字符 1；最多字符 64 |
| `orderId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `orderNo` | `string` | 是 | 最少字符 1；最多字符 50 |
| `buyerId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `merchantId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `amount` | `Money` | 是 | 按字段类型传输 |
| `currency` | `string` | 是 | 允许值：`"CNY"` |
| `expiresAt` | `string` | 是 | 格式 `date-time` |

### PaymentEventDto

SUCCEEDED 必须携带经验证的通道交易号和支付时间；CLOSED 仅表示确认未支付且已关闭。S02 校验已绑定 paymentNo、金额及版本，持久化收件去重后原子更新订单库存。

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `eventId` | `string` | 是 | 最少字符 1；最多字符 100 |
| `paymentNo` | `string` | 是 | 最少字符 1；最多字符 64 |
| `orderId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `version` | `integer(int64)` | 是 | 最小值 1 |
| `status` | `string` | 是 | 允许值：`"SUCCEEDED"`、`"CLOSED"` |
| `amount` | `Money` | 是 | 按字段类型传输 |
| `currency` | `string` | 是 | 允许值：`"CNY"` |
| `environment` | `string` | 是 | 允许值：`"SANDBOX"` |
| `providerTransactionNo` | `string` | 否 | 最少字符 1；最多字符 128 |
| `paidAt` | `string` | 否 | 格式 `date-time` |

### RefundExecutionAuthorizationVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `refundNo` | `string` | 是 | 最少字符 1；最多字符 64 |
| `orderId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `paymentNo` | `string` | 是 | 最少字符 1；最多字符 64 |
| `type` | `string` | 是 | 允许值：`"BUYER_REQUEST"`、`"LATE_PAYMENT_COMPENSATION"` |
| `amount` | `Money` | 是 | 按字段类型传输 |
| `currency` | `string` | 是 | 允许值：`"CNY"` |
| `merchantId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `approvedAt` | `string` | 是 | 格式 `date-time` |

### RefundEventDto

SUCCEEDED 必须携带退款完成时间；资金执行状态由 S03 负责，S02 仅保留投影并执行一次库存副作用。

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `eventId` | `string` | 是 | 最少字符 1；最多字符 100 |
| `refundNo` | `string` | 是 | 最少字符 1；最多字符 64 |
| `paymentNo` | `string` | 是 | 最少字符 1；最多字符 64 |
| `version` | `integer(int64)` | 是 | 最小值 1 |
| `status` | `string` | 是 | 允许值：`"PROCESSING"`、`"SUCCEEDED"`、`"FAILED"`、`"UNKNOWN"` |
| `amount` | `Money` | 是 | 按字段类型传输 |
| `currency` | `string` | 是 | 允许值：`"CNY"` |
| `providerRefundNo` | `string` | 否 | 最少字符 1；最多字符 128 |
| `refundedAt` | `string` | 否 | 格式 `date-time` |
| `failureCode` | `string` | 否 | 最少字符 1；最多字符 100 |

### ClosePaymentDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `commandId` | `string` | 是 | 最少字符 1；最多字符 100 |
| `reason` | `string` | 是 | 允许值：`"USER_CANCELLED"`、`"PAYMENT_TIMEOUT"` |

### ExecuteRefundDto

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `commandId` | `string` | 是 | 最少字符 1；最多字符 100 |
| `refundNo` | `string` | 是 | 最少字符 1；最多字符 64 |

### CommandAcceptedVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `commandId` | `string` | 是 | 最少字符 1；最多字符 100 |
| `state` | `string` | 是 | 允许值：`"ACCEPTED"`、`"ALREADY_APPLIED"` |

### InternalPaymentStateVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `paymentNo` | `string` | 是 | 最少字符 1；最多字符 64 |
| `orderId` | `integer(int64)` | 是 | 最小值 1；最大值 9007199254740991 |
| `providerCode` | `string` | 是 | 最少字符 1；最多字符 40；格式约束 `^[a-z0-9_-]+$` |
| `environment` | `string` | 是 | 允许值：`"SANDBOX"` |
| `amount` | `Money` | 是 | 按字段类型传输 |
| `currency` | `string` | 是 | 允许值：`"CNY"` |
| `status` | `PaymentStatus` | 是 | 按字段类型传输 |
| `checkoutUrl` | `string` | 否 | 最少字符 0；最多字符 2000；格式 `uri` |
| `expiresAt` | `string` | 是 | 格式 `date-time` |
| `providerTransactionNo` | `string` | 否 | 最少字符 1；最多字符 128 |
| `paidAt` | `string` | 否 | 格式 `date-time` |
| `createdAt` | `string` | 是 | 格式 `date-time` |
| `version` | `integer(int64)` | 是 | 最小值 1 |
| `closeRequested` | `boolean` | 是 | 按字段类型传输 |

### InternalRefundStateVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `refundNo` | `string` | 是 | 最少字符 1；最多字符 64 |
| `paymentNo` | `string` | 是 | 最少字符 1；最多字符 64 |
| `status` | `string` | 是 | 允许值：`"PROCESSING"`、`"SUCCEEDED"`、`"FAILED"`、`"UNKNOWN"` |
| `amount` | `Money` | 是 | 按字段类型传输 |
| `currency` | `string` | 是 | 允许值：`"CNY"` |
| `version` | `integer(int64)` | 是 | 最小值 1 |
| `providerRefundNo` | `string` | 否 | 最少字符 1；最多字符 128 |
| `refundedAt` | `string` | 否 | 格式 `date-time` |
| `failureCode` | `string` | 否 | 最少字符 1；最多字符 100 |

### ResultAuthContextVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `AuthContextVo` | 是 | 按字段类型传输 |

### ResultMerchantProfileVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `MerchantProfileVo` | 是 | 按字段类型传输 |

### ResultPaymentAuthorizationVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `PaymentAuthorizationVo` | 是 | 按字段类型传输 |

### ResultRefundExecutionAuthorizationVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `RefundExecutionAuthorizationVo` | 是 | 按字段类型传输 |

### ResultCommandAcceptedVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `CommandAcceptedVo` | 是 | 按字段类型传输 |

### ResultInternalPaymentStateVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `InternalPaymentStateVo` | 是 | 按字段类型传输 |

### ResultInternalRefundStateVo

类型：`object`。对象不接受未定义字段。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `code` | `integer` | 是 | 允许值：`200` |
| `message` | `string` | 是 | 最少字符 1；最多字符 500 |
| `data` | `InternalRefundStateVo` | 是 | 按字段类型传输 |

## 8. 现有接口迁移去向

现有原型包含 19 个操作，其中 14 个保留路径但调整实现及服务归属，5 个由目标接口替代。旧通用状态写入口必须退役，不能绕过新授权、库存与资金规则。下表外部路径均已包含网关 /api 前缀。

| 现有操作 | 迁移方式 | 目标操作 |
| --- | --- | --- |
| GET /api/orders/no/{orderNo} | 保留路径并调整 | S02-E16 GET /api/orders/no/{orderNo} |
| GET /api/orders/user/{userId} | 由目标操作替代 | S02-E14 GET /api/orders/me |
| GET /api/orders/{id} | 保留路径并调整 | S02-E15 GET /api/orders/{id} |
| GET /api/products | 保留路径并调整 | S02-E01 GET /api/products |
| GET /api/products/merchant/{merchantId} | 保留路径并调整 | S02-E05 GET /api/products/merchant/{merchantId} |
| GET /api/products/{id} | 保留路径并调整 | S02-E03 GET /api/products/{id} |
| GET /api/reviews/product/{productId} | 保留路径并调整 | S02-E28 GET /api/reviews/product/{productId} |
| GET /api/reviews/user/{userId} | 由目标操作替代 | S02-E29 GET /api/reviews/me |
| GET /api/users/{id} | 由目标操作替代 | S01-E04 GET /api/users/me |
| POST /api/orders | 由目标操作替代 | S02-E12 POST /api/checkouts |
| POST /api/orders/{id}/cancel | 保留路径并调整 | S02-E17 POST /api/orders/{id}/cancel |
| POST /api/products | 保留路径并调整 | S02-E02 POST /api/products |
| POST /api/reviews | 保留路径并调整 | S02-E27 POST /api/reviews |
| POST /api/reviews/{id}/reply | 保留路径并调整 | S02-E31 POST /api/reviews/{id}/reply |
| POST /api/users/login | 保留路径并调整 | S01-E02 POST /api/users/login |
| POST /api/users/register | 保留路径并调整 | S01-E01 POST /api/users/register |
| PUT /api/orders/{id}/status | 由目标操作替代 | S03-E01 POST /api/orders/{id}/payments；S02-E20 POST /api/merchant/orders/{id}/shipment；S02-E18 POST /api/orders/{id}/receipt；S02-E21 POST /api/orders/{id}/refunds；S02-E17 POST /api/orders/{id}/cancel |
| PUT /api/products/{id}/status | 保留路径并调整 | S02-E06 PUT /api/products/{id}/status |
| PUT /api/products/{id}/stock | 保留路径并调整 | S02-E07 PUT /api/products/{id}/stock |

## 9. 支付适配与验收边界

支付供应商尚未指定。通知字段、验签算法、证书来源、应答及重试协议须选型后补齐；当前只固定业务侧支付、退款和跨服务结果契约。

回调入口只定义请求媒体类型、可信验证责任和接收边界，没有虚构统一的供应商字段。适配器需要补齐原始字段、签名算法、证书来源、应答内容、超时与重试规则。通知验签或持久化失败时不能错误确认成功。

每个服务仅提交自己的本地事务；内部命令和事实持久化重投、业务号唯一、接收去重、单调版本与定期核对。HTTP 接受不等于资金完成，不使用跨服务数据库事务。

实现验收至少覆盖：三服务及网关隔离、用户与商家授权、拆单回滚和并发库存、支付与取消竞态、重复及乱序事实、退款审核与发货互斥、退款执行重试以及结果投影恢复。静态文档完整不表示这些运行场景已经通过。
