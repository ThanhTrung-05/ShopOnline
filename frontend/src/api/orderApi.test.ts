import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

import apiClient from './axios';
import { orderApi } from './orderApi';

describe('orderApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
  });

  it('creates an order from the authenticated customer cart and shipping selection', () => {
    const request = { addressId: 42, shippingMethod: 'STANDARD' as const };

    orderApi.create(request);

    expect(apiClient.post).toHaveBeenCalledWith('/customers/me/orders', request);
  });

  it('lists the authenticated customer orders', () => {
    orderApi.list();

    expect(apiClient.get).toHaveBeenCalledWith('/customers/me/orders');
  });

  it('gets an owned order status by order number', () => {
    orderApi.getStatus('ORD-2026-001');

    expect(apiClient.get).toHaveBeenCalledWith(
      '/customers/me/orders/ORD-2026-001/status',
    );
  });

  it('gets owned order details with an encoded order number', () => {
    orderApi.getDetails('ORD/2026 001');

    expect(apiClient.get).toHaveBeenCalledWith(
      '/customers/me/orders/ORD%2F2026%20001',
    );
  });
});
