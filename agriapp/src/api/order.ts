import request from '../utils/request';
import type { ApiResponse, PageResult, Order, OrderCreateForm, OrderStatus } from '../types';

// 创建订单
export const createOrder = (
  userId: number,
  data: OrderCreateForm
): Promise<ApiResponse<Order>> => {
  return request.post('/orders', data, { params: { userId } });
};

// 获取订单详情
export const getOrderById = (id: number): Promise<ApiResponse<Order>> => {
  return request.get(`/orders/${id}`);
};

// 根据订单号获取订单
export const getOrderByOrderNo = (orderNo: string): Promise<ApiResponse<Order>> => {
  return request.get(`/orders/no/${orderNo}`);
};

// 获取用户订单列表
export const getUserOrders = (
  userId: number,
  status?: OrderStatus,
  page: number = 1,
  size: number = 10
): Promise<ApiResponse<PageResult<Order>>> => {
  return request.get(`/orders/user/${userId}`, {
    params: { status, page, size },
  });
};

// 更新订单状态
export const updateOrderStatus = (
  id: number,
  status: OrderStatus
): Promise<ApiResponse<void>> => {
  return request.put(`/orders/${id}/status`, null, { params: { status } });
};

// 取消订单
export const cancelOrder = (id: number, userId: number): Promise<ApiResponse<void>> => {
  return request.post(`/orders/${id}/cancel`, null, { params: { userId } });
};
