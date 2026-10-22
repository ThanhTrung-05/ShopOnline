import apiClient from './axios';
import type { ApiResponse } from '../types';

export type PaymentStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'SUCCESS'
  | 'FAILED'
  | 'EXPIRED'
  | 'REFUNDED';

/** Response from GET /api/v1/payments/qr/{orderId} */
export interface QrPaymentInfo {
  paymentId: number;
  orderId: number;
  orderNumber: string;
  /** Grand total amount sourced from backend — must NOT be recomputed on frontend */
  amount: number;
  /** Mock VietQR payload: "orderId|orderNumber|amount" */
  qrCode: string;
  status: PaymentStatus;
  /** ISO-8601 timestamp at which the QR expires */
  qrExpiresAt: string;
}

/** Response from GET /api/v1/payments/{orderId}/status */
export interface PaymentStatusInfo {
  orderId: number;
  status: PaymentStatus;
}

export const paymentApi = {
  /** ATS-39: Generate (or reuse) a QR code for an order in PENDING status. */
  generateQr: (orderId: number) =>
    apiClient.get<ApiResponse<QrPaymentInfo>>(`/payments/qr/${orderId}`),

  /** ATS-39: Poll payment status — served cache-first from Redis. */
  getStatus: (orderId: number) =>
    apiClient.get<ApiResponse<PaymentStatusInfo>>(`/payments/${orderId}/status`),
};
