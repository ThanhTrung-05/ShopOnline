import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./axios', () => ({
  default: { get: vi.fn() },
}));

import apiClient from './axios';
import { productApi } from './productApi';

describe('productApi featured products', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('uses the public featured products endpoint', () => {
    productApi.featured();

    expect(apiClient.get).toHaveBeenCalledWith('/products/featured');
  });
});
