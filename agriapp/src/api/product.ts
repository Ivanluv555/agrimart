import request from '../utils/request';
import type { ApiResponse, PageResult, Product, ProductCategory } from '../types';

// 搜索商品
export const searchProducts = (params: {
  category?: ProductCategory;
  keyword?: string;
  page?: number;
  size?: number;
}): Promise<ApiResponse<PageResult<Product>>> => {
  return request.get('/products', { params });
};

// 获取商品详情
export const getProductById = (id: number): Promise<ApiResponse<Product>> => {
  return request.get(`/products/${id}`);
};

// 获取商家的商品列表
export const getProductsByMerchant = (
  merchantId: number,
  page: number = 1,
  size: number = 10
): Promise<ApiResponse<PageResult<Product>>> => {
  return request.get(`/products/merchant/${merchantId}`, {
    params: { page, size },
  });
};

// 创建商品（商家）
export const createProduct = (
  merchantId: number,
  data: {
    name: string;
    description: string;
    category: ProductCategory;
    price: number;
    stock: number;
    unit: string;
    brand?: string;
    specification?: string;
    images?: string;
    usageGuide?: string;
  }
): Promise<ApiResponse<Product>> => {
  return request.post('/products', data, { params: { merchantId } });
};

// 更新商品库存
export const updateStock = (id: number, quantity: number): Promise<ApiResponse<void>> => {
  return request.put(`/products/${id}/stock`, null, { params: { quantity } });
};
