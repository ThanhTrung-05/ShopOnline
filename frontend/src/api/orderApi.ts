import apiClient from './axios';
import type { ApiResponse } from '../types';
import type { ShippingMethod } from './shippingApi';

export type OrderStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'PAID'
  | 'PAYMENT_FAILED'
  | 'SHIPPING'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED';

export interface OrderStatusDetails {
  orderNumber: string;
  status: OrderStatus;
  createdAt: string;
  updatedAt: string;
}

export interface OrderItemDetails {
  productId: number;
  productName: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
}

export interface OrderDetails extends OrderStatusDetails {
  totalAmount: number;
  items: OrderItemDetails[];
}

export interface CreateOrderRequest {
  addressId: number;
  shippingMethod: ShippingMethod;
}

export interface CreatedOrderSummary {
  orderNumber: string;
  status: OrderStatus;
  totalAmount: number;
  shippingFee: number;
  createdAt: string;
}

export const orderApi = {
  create: (request: CreateOrderRequest) =>
    apiClient.post<ApiResponse<CreatedOrderSummary>>(
      '/customers/me/orders',
      request,
    ),

  list: () =>
    apiClient.get<ApiResponse<OrderStatusDetails[]>>('/customers/me/orders'),

  getStatus: (orderNumber: string) =>
    apiClient.get<ApiResponse<OrderStatusDetails>>(
      `/customers/me/orders/${encodeURIComponent(orderNumber)}/status`,
    ),

  getDetails: (orderNumber: string) =>
    apiClient.get<ApiResponse<OrderDetails>>(
      `/customers/me/orders/${encodeURIComponent(orderNumber)}`,
    ),
};
