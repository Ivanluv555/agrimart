# 农惠达 - 前端应用

## 开发起步阶段目标

目标架构是用户管理、农资买卖、支付三个独立业务微服务，配套统一网关、注册与配置中心及三个独立逻辑数据库。此目录仍是待迁移原型的一部分，以下旧使用说明不能视为三服务已交付；功能与版本以源码和最新设计为准。详见 [项目文档索引](../docs/00-项目文档索引.md)。

农资电商与农机共享平台的前端应用，基于 React + TypeScript + Ant Design 构建。

## 目标组件体系

Web 目标改用 MUI（Material UI），后续 Flutter 移动端使用对应的 Material 组件，统一主题与交互规范。现有代码和依赖仍为 Ant Design，本次只更新设计，没有完成组件库迁移；下表描述现有原型。

## 技术栈（现有原型）

- **框架**: React 19 + TypeScript 6
- **构建工具**: Vite 8
- **UI 组件库**: Ant Design 6
- **路由**: React Router DOM 7
- **HTTP 客户端**: Axios
- **状态管理**: React Context API
- **日期处理**: Day.js

## 功能模块

### 用户模块
- 用户注册/登录
- 角色选择（农户、商家、农机拥有者）
- 个人信息管理

### 商品模块
- 商品列表展示
- 商品分类筛选（化肥、种子、农药、农具）
- 商品搜索
- 商品详情查看
- 商品评价展示

### 购物车模块
- 添加商品到购物车
- 购物车商品管理
- 数量调整
- 商品删除

### 订单模块
- 订单创建
- 订单列表查看
- 订单详情
- 订单状态流转
- 订单取消
- 商品评价

### 评价模块
- 评价商品
- 查看商品评价
- 商家回复评价

## 项目结构

```
src/
├── api/                 # API 接口
│   ├── product.ts      # 商品接口
│   ├── order.ts        # 订单接口
│   ├── review.ts       # 评价接口
│   └── user.ts         # 用户接口
├── components/          # 通用组件
│   └── MainLayout.tsx  # 主布局组件
├── contexts/           # 上下文
│   ├── AuthContext.tsx # 认证上下文
│   └── CartContext.tsx # 购物车上下文
├── pages/              # 页面组件
│   ├── Home.tsx        # 首页
│   ├── Login.tsx       # 登录注册
│   ├── ProductList.tsx # 商品列表
│   ├── ProductDetail.tsx # 商品详情
│   ├── Cart.tsx        # 购物车
│   ├── Checkout.tsx    # 结算页面
│   ├── OrderList.tsx   # 订单列表
│   └── OrderDetail.tsx # 订单详情
├── types/              # 类型定义
│   └── index.ts        # 全局类型
├── utils/              # 工具函数
│   ├── request.ts      # HTTP 请求封装
│   ├── constants.ts    # 常量定义
│   └── format.ts       # 格式化函数
├── App.tsx             # 应用根组件
└── main.tsx            # 应用入口
```

## 快速开始

### 前置要求

- Node.js 满足锁文件中的 Vite/Oxlint 要求（当前为 `^20.19.0 || >=22.12.0`）
- npm 或 yarn

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev
```

应用将在 http://localhost:5173 启动

### 构建生产版本

```bash
npm run build
```

### 预览生产构建

```bash
npm run preview
```

## 配置

### 环境变量

在项目根目录创建 `.env` 文件：

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

## API 代理配置

开发环境下，Vite 会将 `/api` 请求代理到后端服务（默认 http://localhost:8080）

配置位置：`vite.config.ts`

```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
}
```

## 测试账号

当前原型没有预置账号初始化器，请通过注册接口创建虚构账号，不依赖固定用户名和密码。

## 主要功能说明

### 认证流程

1. 用户通过登录页面输入用户名和密码
2. 登录成功后，用户信息保存到 localStorage 和 Context
3. 后续请求会自动携带认证信息
4. 未登录用户访问受保护页面会自动跳转到登录页

### 购物车流程

1. 用户在商品列表或详情页添加商品到购物车
2. 购物车数据保存在 Context 中
3. 用户可以在购物车页面调整数量或删除商品
4. 点击结算跳转到订单确认页面

### 订单流程

1. 用户填写收货信息并提交订单
2. 订单创建成功后跳转到订单详情页
3. 根据用户角色显示不同的操作按钮
4. 农户可以：支付、确认收货、取消订单、评价
5. 商家可以：确认发货

### 订单状态流转

```
待支付 → 已支付 → 已发货 → 已送达 → 已完成
   ↓
 已取消
```

## 代码规范

### 组件规范

- 使用函数组件 + Hooks
- 组件文件使用 PascalCase 命名
- 每个组件一个文件
- 导出使用 `export default`

### 样式规范

- 使用 Ant Design 组件的内置样式
- 自定义样式使用内联样式对象
- 响应式使用 Ant Design 的 Grid 系统

### API 调用规范

- 所有 API 调用封装在 `api/` 目录
- 使用 async/await 处理异步
- 统一的错误处理
- 使用 TypeScript 类型约束

## 性能优化

- 使用 React.memo 优化组件渲染
- 路由懒加载
- 图片懒加载
- API 请求缓存

## 浏览器支持

- Chrome（推荐）
- Firefox
- Safari
- Edge

## 开发计划

- [ ] 农机预约功能
- [ ] 优惠券系统
- [ ] 价格行情展示
- [ ] 农技知识库
- [ ] 消息通知
- [ ] 地图展示

## 许可证

本项目采用根目录的 [MIT License](../LICENSE)，允许商业使用，需保留版权与许可声明。当前应用以学习演示为主要开发场景，生产使用前需完成安全与可靠性加固，详见 [安全政策](../SECURITY.md)。

贡献请阅读 [贡献指南](../CONTRIBUTING.md) 和 [行为准则](../CODE_OF_CONDUCT.md)。
