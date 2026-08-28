import apiClient from './axios';
import type { ApiResponse } from '../types';

export interface RegisterCustomerRequest {
  email: string;
  password: string;
  fullName: string;
  phone: string | null;
}

export interface RegisteredCustomer {
  customerId: number;
  email: string;
  fullName: string;
  phone: string | null;
  role: string;
  status: string;
  createdAt: string;
}

export interface SessionData {
  authenticated: boolean;
  subject: string;
  username: string | null;
}

export const authApi = {
  register: (request: RegisterCustomerRequest) =>
    apiClient.post<ApiResponse<RegisteredCustomer>>('/auth/register', request),

  session: () => apiClient.get<ApiResponse<SessionData>>('/auth/session'),
};
