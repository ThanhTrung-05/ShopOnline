import { describe, expect, it, vi, beforeEach } from 'vitest';

vi.mock('./axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

import apiClient from './axios';
import { cartApi } from './cartApi';

describe('cartApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
    vi.mocked(apiClient.delete).mockReset();
  });

  it('calls GET /cart', () => {
    cartApi.get();

    expect(apiClient.get).toHaveBeenCalledWith('/cart');
  });

  it('calls POST /cart/items', () => {
    cartApi.addItem({ productId: 10, quantity: 2 });

    expect(apiClient.post).toHaveBeenCalledWith('/cart/items', { productId: 10, quantity: 2 });
  });

  it('calls PUT /cart/items/{cartItemId}', () => {
    cartApi.updateItemQuantity(5, { quantity: 3 });

    expect(apiClient.put).toHaveBeenCalledWith('/cart/items/5', { quantity: 3 });
  });

  it('calls DELETE /cart/items/{cartItemId}', () => {
    cartApi.removeItem(5);

    expect(apiClient.delete).toHaveBeenCalledWith('/cart/items/5');
  });
});
