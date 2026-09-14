import { ProductCategory, UserRole, OrderStatus } from '../types';

// 商品分类中文映射
export const PRODUCT_CATEGORY_MAP: Record<ProductCategory, string> = {
  [ProductCategory.FERTILIZER]: '化肥',
  [ProductCategory.SEED]: '种子',
  [ProductCategory.PESTICIDE]: '农药',
  [ProductCategory.AGRICULTURAL_TOOL]: '农具',
};

// 用户角色中文映射
export const USER_ROLE_MAP: Record<UserRole, string> = {
  [UserRole.FARMER]: '农户',
  [UserRole.MERCHANT]: '商家',
  [UserRole.MACHINE_OWNER]: '农机拥有者',
  [UserRole.ADMIN]: '管理员',
};

// 订单状态中文映射
export const ORDER_STATUS_MAP: Record<OrderStatus, string> = {
  [OrderStatus.PENDING_PAYMENT]: '待支付',
  [OrderStatus.PAID]: '已支付',
  [OrderStatus.SHIPPED]: '已发货',
  [OrderStatus.DELIVERED]: '已送达',
  [OrderStatus.COMPLETED]: '已完成',
  [OrderStatus.CANCELLED]: '已取消',
  [OrderStatus.REFUNDING]: '退款中',
  [OrderStatus.REFUNDED]: '已退款',
};

// 订单状态颜色映射
export const ORDER_STATUS_COLOR_MAP: Record<OrderStatus, string> = {
  [OrderStatus.PENDING_PAYMENT]: 'orange',
  [OrderStatus.PAID]: 'blue',
  [OrderStatus.SHIPPED]: 'cyan',
  [OrderStatus.DELIVERED]: 'purple',
  [OrderStatus.COMPLETED]: 'green',
  [OrderStatus.CANCELLED]: 'default',
  [OrderStatus.REFUNDING]: 'gold',
  [OrderStatus.REFUNDED]: 'default',
};
