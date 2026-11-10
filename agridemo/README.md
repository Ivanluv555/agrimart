# 农惠达 - 农资电商平台 Demo

## 开发起步阶段目标

目标架构是用户管理、农资买卖、支付三个独立业务微服务，配套统一网关、注册与配置中心及三个独立逻辑数据库。此目录仍是待迁移原型的一部分，以下旧使用说明不能视为三服务已交付；功能与版本以源码和最新设计为准。详见 [项目文档索引](../docs/00-项目文档索引.md)。

## 项目简介

AgriMart（农惠达）是一个基于 Spring Boot 的农资电商单体应用，提供农资商品在线采购、订单管理、评价系统等核心功能。

### 技术栈

- **后端框架**: Spring Boot 4.1.1
- **数据库**: MySQL 8.0
- **ORM**: Spring Data JPA
- **API 文档**: SpringDoc OpenAPI 3
- **容器化**: Docker Compose

### 核心功能模块

1. **用户模块**: 用户注册、登录、信息管理
2. **商品模块**: 商品CRUD、分类筛选、搜索
3. **订单模块**: 下单、支付、物流跟踪、订单状态管理
4. **评价模块**: 商品评价、评分、商家回复
5. **农机模块**: 农机信息管理、预约功能（实体已创建）

### 业务角色

- **农户（FARMER）**: 购买农资、租赁农机
- **商家（MERCHANT）**: 发布商品、管理订单
- **农机拥有者（MACHINE_OWNER）**: 发布农机、接受预约
- **管理员（ADMIN）**: 平台管理、审核

## 快速开始

### 环境要求

- JDK 25
- Maven 3.8+
- Docker & Docker Compose

### 启动步骤

1. **启动数据库**

```bash
docker-compose up -d
```

2. **编译运行**

```bash
./mvnw clean package
./mvnw spring-boot:run
```

或者直接运行：

```bash
./mvnw spring-boot:run
```

3. **访问应用**

- API 基础路径: http://localhost:8080/api
- Swagger UI: http://localhost:8080/api/swagger-ui.html
- API 文档: http://localhost:8080/api/api-docs

### 测试账号

当前原型没有预置账号初始化器，请通过注册接口创建虚构账号，不依赖固定用户名和密码。

## 数据库配置

### 连接信息

- **主机**: localhost:3307
- **数据库**: agri
- **用户名**: agri
- **密码**: agripassword

### 数据表说明

- `users`: 用户表
- `products`: 商品表
- `orders`: 订单表
- `order_items`: 订单明细表
- `reviews`: 评价表
- `machinery`: 农机表
- `machinery_bookings`: 农机预约表

## API 接口示例

### 用户注册

```http
POST /api/users/register
Content-Type: application/json

{
  "username": "testuser",
  "password": "password123",
  "realName": "测试用户",
  "phone": "13800138000",
  "role": "FARMER"
}
```

### 用户登录

```http
POST /api/users/login
Content-Type: application/json

{
  "username": "farmer",
  "password": "farmer123"
}
```

### 商品搜索

```http
GET /api/products?category=FERTILIZER&keyword=复合肥&page=1&size=10
```

### 创建订单

```http
POST /api/orders?userId=3
Content-Type: application/json

{
  "items": [
    {
      "productId": 1,
      "quantity": 2
    }
  ],
  "shippingAddress": "重庆市北碚区柳荫镇",
  "receiverName": "张农民",
  "receiverPhone": "13800138000"
}
```

### 商品评价

```http
POST /api/reviews?userId=3
Content-Type: application/json

{
  "orderId": 1,
  "productId": 1,
  "rating": 5,
  "content": "商品质量很好，物流很快！"
}
```

## 项目结构

```
src/main/java/edu/swu/agridemo/
├── common/                 # 通用类（统一返回、分页结果）
├── config/                 # 配置类（JPA、OpenAPI、Security）
├── controller/             # 控制器层
├── dto/                    # 数据传输对象
├── entity/                 # 实体类
├── enums/                  # 枚举类
├── exception/              # 异常处理
├── init/                   # 数据初始化
├── repository/             # 数据访问层
└── service/                # 业务逻辑层
```

## 业务流程

### 商品购买流程

1. 用户浏览商品列表，支持按分类、关键词搜索
2. 查看商品详情，包括价格、库存、使用指南
3. 添加商品到订单，填写收货信息
4. 提交订单，系统自动扣减库存
5. 订单状态流转：待支付 → 已支付 → 已发货 → 已送达 → 已完成
6. 完成后可进行评价

### 评价流程

1. 用户对已完成订单进行评价
2. 填写评分（1-5星）和评价内容
3. 系统更新商品平均评分
4. 商家可回复评价

## 扩展功能（已预留实体）

- **农机共享**: Machinery 和 MachineryBooking 实体已创建
- **时段管理**: 支持按日期范围预约
- **冲突检测**: 防止同一农机时段重复预约

## 开发说明

### 添加新功能

1. 在 `entity` 包创建实体类
2. 在 `repository` 包创建 Repository 接口
3. 在 `service` 包实现业务逻辑
4. 在 `controller` 包暴露 API 接口
5. 使用 Swagger 注解完善 API 文档

### 日志配置

应用已配置详细日志，包括：
- SQL 语句打印
- 参数绑定追踪
- 业务逻辑日志

### 事务管理

关键业务方法已添加 `@Transactional` 注解，确保数据一致性。

## 生产环境部署建议

1. 修改 `application.yaml` 中的数据库密码
2. 调整 JPA 的 `ddl-auto` 为 `validate`
3. 配置日志级别为 `INFO` 或 `WARN`
4. 启用 Spring Security 完整认证授权
5. 添加 Redis 缓存热点数据
6. 使用 Nginx 反向代理

## 许可证

本项目采用根目录的 [MIT License](../LICENSE)，允许商业使用，需保留版权与许可声明。当前后端以学习演示为主要开发场景，认证授权等能力尚未完善，不建议直接用于生产环境；这是技术成熟度说明，不是许可证的用途限制。详见 [安全政策](../SECURITY.md)。

贡献请阅读 [贡献指南](../CONTRIBUTING.md) 和 [行为准则](../CODE_OF_CONDUCT.md)。
