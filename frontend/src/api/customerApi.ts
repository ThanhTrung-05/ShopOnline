import apiClient from './axios';
import type { ApiResponse } from '../types';

export interface CustomerProfile {
  customerId: number;
  email: string;
  fullName: string;
  phone: string | null;
  role: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateProfileRequest {
  fullName: string | null;
  phone: string | null;
}

export const customerApi = {
  getProfile: () => apiClient.get<ApiResponse<CustomerProfile>>('/customers/me'),

  updateProfile: (request: UpdateProfileRequest) =>
    apiClient.patch<ApiResponse<CustomerProfile>>('/customers/me', request),
};
