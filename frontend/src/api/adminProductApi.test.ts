import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

import apiClient from './axios';
import { adminProductApi, type ProductRequest } from './adminProductApi';

const request: ProductRequest = {
  productName: 'Gạo thơm',
  productSlug: 'gao-thom',
  categoryId: 3,
  description: 'Gạo thơm cao cấp',
  price: 120000,
  imageUrl: 'https://example.com/rice.jpg',
  status: 'ACTIVE',
  initialQuantity: 20,
};

describe('adminProductApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('uses the backend admin product list endpoint', () => {
    adminProductApi.list(2, 20, 3, 'gạo');

    expect(apiClient.get).toHaveBeenCalledWith('/products/admin/products', {
      params: { page: 2, size: 20, categoryId: 3, search: 'gạo' },
    });
  });

  it('creates a product with the ProductRequest contract', () => {
    adminProductApi.create(request);

    expect(apiClient.post).toHaveBeenCalledWith('/products', request);
    expect(apiClient.post).not.toHaveBeenCalledWith(
      '/products',
      expect.objectContaining({ name: expect.anything(), slug: expect.anything() }),
    );
  });

  it('updates a product at the expected endpoint', () => {
    adminProductApi.update(9, request);

    expect(apiClient.put).toHaveBeenCalledWith('/products/9', request);
  });

  it('deletes a product at the expected endpoint', () => {
    adminProductApi.delete(9);

    expect(apiClient.delete).toHaveBeenCalledWith('/products/9');
  });
});
