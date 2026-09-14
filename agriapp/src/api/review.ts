import request from '../utils/request';
import type { ApiResponse, PageResult, Review, ReviewCreateForm } from '../types';

// 创建评价
export const createReview = (
  userId: number,
  data: ReviewCreateForm
): Promise<ApiResponse<Review>> => {
  return request.post('/reviews', data, { params: { userId } });
};

// 获取商品评价列表
export const getProductReviews = (
  productId: number,
  page: number = 1,
  size: number = 10
): Promise<ApiResponse<PageResult<Review>>> => {
  return request.get(`/reviews/product/${productId}`, {
    params: { page, size },
  });
};

// 获取用户评价列表
export const getUserReviews = (
  userId: number,
  page: number = 1,
  size: number = 10
): Promise<ApiResponse<PageResult<Review>>> => {
  return request.get(`/reviews/user/${userId}`, {
    params: { page, size },
  });
};

// 商家回复评价
export const replyReview = (id: number, reply: string): Promise<ApiResponse<void>> => {
  return request.post(`/reviews/${id}/reply`, null, { params: { reply } });
};
