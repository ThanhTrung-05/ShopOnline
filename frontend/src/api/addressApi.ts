import apiClient from './axios';
import type { ApiResponse } from '../types';

export interface AddressRequest {
  recipientName: string;
  phone: string;
  line1: string;
  ward: string | null;
  district: string | null;
  province: string;
}

export interface Address extends AddressRequest {
  addressId: number;
  isDefault: boolean;
}

const basePath = '/customers/me/addresses';

export const addressApi = {
  list: () => apiClient.get<ApiResponse<Address[]>>(basePath),

  create: (request: AddressRequest) =>
    apiClient.post<ApiResponse<Address>>(basePath, request),

  update: (addressId: number, request: AddressRequest) =>
    apiClient.put<ApiResponse<Address>>(basePath + '/' + addressId, request),

  remove: (addressId: number) => apiClient.delete(basePath + '/' + addressId),

  setDefault: (addressId: number) =>
    apiClient.patch<ApiResponse<Address>>(basePath + '/' + addressId + '/default'),
};
