import apiClient from './axios';
import type { ApiResponse } from '../types';

/** Category DTO returned by GET /api/categories */
export interface Category {
  categoryId: number;
  categoryName: string;
  categoryCode: string;
  description?: string;
  /** VAT rate (%) — 5 or 10 */
  vatRate: number;
  status: string;
}

export interface CategoryRequest {
  categoryName: string;
  categoryCode: string;
  description?: string;
  vatRate: number;
  status: string;
}

export const categoryApi = {
  /** GET /api/categories — returns all categories */
  list: () =>
    apiClient.get<ApiResponse<Category[]>>('/categories', { baseURL: '/api' }),
    
  /** POST /api/categories — creates a new category (requires ROLE_ADMIN) */
  create: (data: CategoryRequest) =>
    apiClient.post<ApiResponse<Category>>('/categories', data, { baseURL: '/api' }),
    
  /** PUT /api/categories/{id} — updates an existing category (requires ROLE_ADMIN) */
  update: (id: number, data: CategoryRequest) =>
    apiClient.put<ApiResponse<Category>>(`/categories/${id}`, data, { baseURL: '/api' }),
    
  /** DELETE /api/categories/{id} — deletes a category (requires ROLE_ADMIN) */
  delete: (id: number) =>
    apiClient.delete<ApiResponse<void>>(`/categories/${id}`, { baseURL: '/api' }),
};
