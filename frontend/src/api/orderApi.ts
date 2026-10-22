import apiClient from './axios';
import type { ApiResponse } from '../types';
import type { ShippingMethod } from './shippingApi';
import type { PaymentStatus } from './paymentApi';

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
  vatRate: number;
  vatAmount: number;
  subtotalIncludingVat: number;
}

/** ATS-39/ATS-41: Extended with VAT breakdown, shippingAddress and paymentStatus */
export interface OrderDetails extends OrderStatusDetails {
  orderId: number;
  totalBeforeVat: number;
  totalVatAmount: number;
  shippingFee: number;
  totalAmount: number;
  /** Shipping address snapshot captured at order-creation time */
  shippingAddress: string;
  /** Current payment status from backend; null if no payment record yet */
  paymentStatus: PaymentStatus | null;
  items: OrderItemDetails[];
}

export interface CreateOrderRequest {
  addressId: number;
  shippingMethod: ShippingMethod;
}

/** ATS-39: orderId is now included so the frontend can immediately call the QR API */
export interface CreatedOrderSummary {
  orderId: number;
  orderNumber: string;
  status: OrderStatus;
  totalBeforeVat: number;
  totalVatAmount: number;
  shippingFee: number;
  totalAmount: number;
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

