import request from '../utils/request';
import type { ApiResponse, User, LoginForm, RegisterForm } from '../types';

// 用户注册
export const register = (data: RegisterForm): Promise<ApiResponse<User>> => {
  return request.post('/users/register', data);
};

// 用户登录
export const login = (data: LoginForm): Promise<ApiResponse<User>> => {
  return request.post('/users/login', data);
};

// 获取用户信息
export const getUserById = (id: number): Promise<ApiResponse<User>> => {
  return request.get(`/users/${id}`);
};
