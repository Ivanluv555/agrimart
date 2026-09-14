# 农惠达 - 项目总览

农资电商与农机共享平台 - 基于微服务架构的农业电商系统

## 项目结构

```
agrimart/
├── agridemo/           # 后端服务（Spring Boot）
│   ├── src/           # 源代码
│   ├── docs/          # 文档
│   └── README.md      # 后端文档
└── agriapp/           # 前端应用（React）
    ├── src/           # 源代码
    └── README.md      # 前端文档
```

## 技术架构

### 后端技术栈

- **框架**: Spring Boot 3.2.5
- **数据库**: MySQL 8.0
- **ORM**: Spring Data JPA
- **API 文档**: SpringDoc OpenAPI 3
- **容器化**: Docker Compose

### 前端技术栈

- **框架**: React 19 + TypeScript 6
- **构建工具**: Vite 8
- **UI 组件**: Ant Design 6
- **路由**: React Router DOM 7
- **HTTP 客户端**: Axios

## 核心功能

### 1. 用户模块
- 用户注册/登录
- 多角色支持（农户、商家、农机拥有者、管理员）
- 用户信息管理

### 2. 商品模块
- 商品CRUD
- 商品分类（化肥、种子、农药、农具）
- 商品搜索与筛选
- 商品评分与销量统计

### 3. 订单模块
- 订单创建
- 订单状态管理
- 订单列表与详情
- 订单取消

### 4. 评价模块
- 用户评价商品
- 商家回复评价
- 评分统计

### 5. 农机模块（实体已创建）
- 农机信息管理
- 农机预约
- 时段管理

## 快速启动

### 后端服务

1. 启动数据库
```bash
cd agridemo
docker-compose up -d
```

2. 启动后端服务
```bash
./mvnw spring-boot:run
```

后端服务运行在: http://localhost:8080
API 文档访问: http://localhost:8080/api/swagger-ui.html

### 前端应用

1. 安装依赖
```bash
cd agriapp
npm install
```

2. 启动开发服务器
```bash
npm run dev
```

前端应用运行在: http://localhost:5173

## 测试账号

| 角色 | 用户名 | 密码 |
|-----|--------|------|
| 管理员 | admin | admin123 |
| 商家 | merchant | merchant123 |
| 农户 | farmer | farmer123 |

## 业务流程

### 商品购买流程

1. 用户浏览商品列表
2. 查看商品详情
3. 添加商品到购物车
4. 填写收货信息
5. 提交订单
6. 订单状态流转：待支付 → 已支付 → 已发货 → 已送达 → 已完成
7. 订单完成后进行评价

### 订单状态流转

```
待支付(PENDING_PAYMENT) 
  ↓ 用户确认支付
已支付(PAID)
  ↓ 商家发货
已发货(SHIPPED)
  ↓ 用户确认收货
已送达(DELIVERED)
  ↓ 用户完成订单
已完成(COMPLETED)

取消分支：
待支付 → 已取消(CANCELLED)
退款分支：
已支付/已发货 → 退款中(REFUNDING) → 已退款(REFUNDED)
```

## API 接口概览

### 用户接口
- POST `/api/users/register` - 用户注册
- POST `/api/users/login` - 用户登录
- GET `/api/users/{id}` - 获取用户信息

### 商品接口
- GET `/api/products` - 搜索商品
- GET `/api/products/{id}` - 获取商品详情
- POST `/api/products` - 创建商品（商家）
- GET `/api/products/merchant/{merchantId}` - 获取商家商品

### 订单接口
- POST `/api/orders` - 创建订单
- GET `/api/orders/{id}` - 获取订单详情
- GET `/api/orders/user/{userId}` - 获取用户订单列表
- PUT `/api/orders/{id}/status` - 更新订单状态
- POST `/api/orders/{id}/cancel` - 取消订单

### 评价接口
- POST `/api/reviews` - 创建评价
- GET `/api/reviews/product/{productId}` - 获取商品评价
- GET `/api/reviews/user/{userId}` - 获取用户评价
- POST `/api/reviews/{id}/reply` - 商家回复评价

## 数据库设计

### 核心表

- `users` - 用户表
- `products` - 商品表
- `orders` - 订单表
- `order_items` - 订单明细表
- `reviews` - 评价表
- `machinery` - 农机表
- `machinery_bookings` - 农机预约表

## 项目特点

### 1. 代码结构清晰
- 前后端分离
- 模块化设计
- 统一的代码风格

### 2. 类型安全
- 后端使用强类型 Java
- 前端使用 TypeScript
- 完整的类型定义

### 3. 用户体验
- 响应式设计
- 友好的错误提示
- 流畅的交互体验

### 4. 可扩展性
- 模块化架构
- 易于添加新功能
- 良好的代码复用

## 开发规范

### 后端规范
- 遵循 RESTful API 设计
- 统一的返回格式
- 完善的异常处理
- 详细的 API 文档

### 前端规范
- 组件化开发
- 统一的状态管理
- 类型安全
- 代码复用

## 未来规划

### 短期目标
- [ ] 完善农机预约功能
- [ ] 添加优惠券系统
- [ ] 实现消息通知
- [ ] 添加支付集成

### 长期目标
- [ ] 迁移到微服务架构
- [ ] 集成 Spring Cloud Alibaba
- [ ] 添加分布式事务（Seata）
- [ ] 实现限流熔断（Sentinel）
- [ ] 链路追踪（SkyWalking）

## 许可证

本项目仅用于学习演示。

## 贡献

欢迎提交 Issue 和 Pull Request。
