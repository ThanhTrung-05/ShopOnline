import apiClient from './axios';
import type { ApiResponse } from '../types';

export type ShippingMethod = 'STANDARD' | 'EXPRESS';

export type ShippingRegion = 'LOCAL' | 'NEARBY' | 'OTHER';

export interface PrepareShippingRequest {
  addressId: number;
  shippingMethod: ShippingMethod;
}

export interface ShippingPreparation {
  customerId: number;
  addressId: number;
  recipientName: string;
  phone: string;
  line1: string;
  ward: string | null;
  district: string | null;
  province: string;
  shippingMethod: ShippingMethod;
  region: ShippingRegion;
  shippingFee: number;
}

export const shippingApi = {
  prepare: (request: PrepareShippingRequest) =>
    apiClient.post<ApiResponse<ShippingPreparation>>(
      '/customers/me/shipping/prepare',
      request,
    ),
};
