import apiClient from './axios';
import type { ApiResponse } from '../types';
import type { OrderStatus } from './orderApi';

export interface OperationsOrder {
  orderNumber: string;
  status: OrderStatus;
  updatedAt: string;
}

export interface UpdateOperationsOrderStatusRequest {
  status: OrderStatus;
}

export const operationsOrderApi = {
  list: () =>
    apiClient.get<ApiResponse<OperationsOrder[]>>('/operations/orders'),

  updateStatus: (orderNumber: string, status: OrderStatus) =>
    apiClient.patch<ApiResponse<OperationsOrder>>(
      `/operations/orders/${encodeURIComponent(orderNumber)}/status`,
      { status } satisfies UpdateOperationsOrderStatusRequest,
    ),
};
