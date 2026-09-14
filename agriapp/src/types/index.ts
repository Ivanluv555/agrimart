// 用户相关类型
export const UserRole = {
  FARMER: 'FARMER',
  MERCHANT: 'MERCHANT',
  MACHINE_OWNER: 'MACHINE_OWNER',
  ADMIN: 'ADMIN',
} as const;
export type UserRole = (typeof UserRole)[keyof typeof UserRole];

export const UserStatus = {
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE',
  PENDING: 'PENDING',
} as const;
export type UserStatus = (typeof UserStatus)[keyof typeof UserStatus];

export interface User {
  id: number;
  username: string;
  realName: string;
  phone: string;
  email?: string;
  role: UserRole;
  status: UserStatus;
  address?: string;
  avatar?: string;
  description?: string;
  createdAt: string;
}

// 商品相关类型
export const ProductCategory = {
  FERTILIZER: 'FERTILIZER',
  SEED: 'SEED',
  PESTICIDE: 'PESTICIDE',
  AGRICULTURAL_TOOL: 'AGRICULTURAL_TOOL',
} as const;
export type ProductCategory = (typeof ProductCategory)[keyof typeof ProductCategory];

export const ProductStatus = {
  ON_SALE: 'ON_SALE',
  OFF_SALE: 'OFF_SALE',
  OUT_OF_STOCK: 'OUT_OF_STOCK',
  PENDING: 'PENDING',
} as const;
export type ProductStatus = (typeof ProductStatus)[keyof typeof ProductStatus];

export interface Product {
  id: number;
  name: string;
  description: string;
  category: ProductCategory;
  price: number;
  stock: number;
  unit: string;
  brand?: string;
  specification?: string;
  images?: string;
  status: ProductStatus;
  merchantId: number;
  merchantName: string;
  salesCount: number;
  rating?: number;
  usageGuide?: string;
  createdAt: string;
}

// 订单相关类型
export const OrderStatus = {
  PENDING_PAYMENT: 'PENDING_PAYMENT',
  PAID: 'PAID',
  SHIPPED: 'SHIPPED',
  DELIVERED: 'DELIVERED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
  REFUNDING: 'REFUNDING',
  REFUNDED: 'REFUNDED',
} as const;
export type OrderStatus = (typeof OrderStatus)[keyof typeof OrderStatus];

export interface OrderItem {
  id: number;
  productId: number;
  productName: string;
  productImage?: string;
  price: number;
  quantity: number;
  subtotal: number;
}

export interface Order {
  id: number;
  orderNo: string;
  userId: number;
  username: string;
  orderItems: OrderItem[];
  totalAmount: number;
  status: OrderStatus;
  shippingAddress: string;
  receiverName: string;
  receiverPhone: string;
  remark?: string;
  trackingNumber?: string;
  createdAt: string;
}

// 评价相关类型
export interface Review {
  id: number;
  orderId: number;
  productId: number;
  productName: string;
  userId: number;
  username: string;
  rating: number;
  content: string;
  reply?: string;
  images?: string;
  createdAt: string;
}

// API响应类型
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// 表单类型
export interface LoginForm {
  username: string;
  password: string;
}

export interface RegisterForm {
  username: string;
  password: string;
  realName: string;
  phone: string;
  role: UserRole;
  email?: string;
}

export interface OrderCreateForm {
  items: Array<{
    productId: number;
    quantity: number;
  }>;
  shippingAddress: string;
  receiverName: string;
  receiverPhone: string;
  remark?: string;
}

export interface ReviewCreateForm {
  orderId: number;
  productId: number;
  rating: number;
  content: string;
}
