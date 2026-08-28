import apiClient from './axios';
import type { ApiResponse, PageResponse, Product } from '../types';

export interface ProductRequest {
  productName: string;
  productSlug: string;
  categoryId: number;
  description?: string;
  price: number;
  imageUrl?: string;
  status: string;
  initialQuantity: number;
}

export const adminProductApi = {
  /** GET /api/v1/products/admin/products — returns all non-DELETED products (requires ROLE_ADMIN) */
  list: (
    page = 0,
    size = 20,
    categoryId?: number,
    search?: string,
  ) =>
    apiClient.get<ApiResponse<PageResponse<Product>>>('/products/admin/products', {
      params: { page, size, categoryId, search },
    }),

  /** POST /api/v1/products — create new product (requires ROLE_ADMIN) */
  create: (data: ProductRequest) =>
    apiClient.post<ApiResponse<Product>>('/products', data),

  /** PUT /api/v1/products/{id} — update product (requires ROLE_ADMIN) */
  update: (id: number, data: ProductRequest) =>
    apiClient.put<ApiResponse<Product>>(`/products/${id}`, data),

  /** DELETE /api/v1/products/{id} — soft-delete product (requires ROLE_ADMIN) */
  delete: (id: number) =>
    apiClient.delete<ApiResponse<void>>(`/products/${id}`),
};
