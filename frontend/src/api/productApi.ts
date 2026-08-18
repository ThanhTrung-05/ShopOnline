import apiClient from './axios';
import type { ApiResponse, PageResponse, Product } from '../types';

/** Detailed product response from GET /api/v1/products/{id} — includes VAT breakdown */
export interface ProductDetail {
  id: number;
  name: string;
  slug: string;
  /** Unit price BEFORE VAT (VND) */
  price: number;
  /** VAT rate in % (e.g. 5 or 10) */
  vatRate: number;
  /** VAT amount = price * vatRate / 100 */
  vatAmount: number;
  /** Total price including VAT = price + vatAmount */
  priceIncludingVat: number;
  imageUrl?: string;
  description?: string;
  categoryId: number;
  categoryName: string;
  inventoryCount: number;
  status: string;
}

export const productApi = {
  list: (
    page = 0,
    size = 20,
    category?: string,
    search?: string,
    minPrice?: number,
    maxPrice?: number,
  ) =>
    apiClient.get<ApiResponse<PageResponse<Product>>>('/products', {
      params: { page, size, category, search, minPrice, maxPrice },
    }),

  detail: (id: number) =>
    apiClient.get<ApiResponse<ProductDetail>>(`/products/${id}`),
};
