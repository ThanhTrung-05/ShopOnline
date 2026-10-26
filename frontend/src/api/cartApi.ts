import apiClient from './axios';
import type { ApiResponse } from '../types';

export interface CartItem {
  cartItemId: number;
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  itemSubtotal: number;
  /** true when the product is INACTIVE or DELETED; checkout will fail for this item */
  unavailable: boolean;
}

export interface Cart {
  items: CartItem[];
  subtotal: number;
  /** true when at least one item has an unavailable product */
  hasUnavailableItems: boolean;
}

export interface AddCartItemRequest {
  productId: number;
  quantity: number;
}

export interface UpdateCartItemQuantityRequest {
  quantity: number;
}

export const emptyCart: Cart = {
  items: [],
  subtotal: 0,
  hasUnavailableItems: false,
};

export const cartApi = {
  get: () => apiClient.get<ApiResponse<Cart>>('/cart'),

  addItem: (request: AddCartItemRequest) =>
    apiClient.post('/cart/items', request),

  updateItemQuantity: (cartItemId: number, request: UpdateCartItemQuantityRequest) =>
    apiClient.put(`/cart/items/${cartItemId}`, request),

  removeItem: (cartItemId: number) =>
    apiClient.delete(`/cart/items/${cartItemId}`),
};
