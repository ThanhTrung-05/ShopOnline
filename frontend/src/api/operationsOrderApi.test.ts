import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./axios', () => ({
  default: {
    get: vi.fn(),
    patch: vi.fn(),
  },
}));

import apiClient from './axios';
import { operationsOrderApi } from './operationsOrderApi';

describe('operationsOrderApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.patch).mockReset();
  });

  it('lists operational orders', () => {
    operationsOrderApi.list();

    expect(apiClient.get).toHaveBeenCalledWith('/operations/orders');
  });

  it('updates status using only an encoded order number and target status', () => {
    operationsOrderApi.updateStatus('ORD/2026 001', 'SHIPPING');

    expect(apiClient.patch).toHaveBeenCalledWith(
      '/operations/orders/ORD%2F2026%20001/status',
      { status: 'SHIPPING' },
    );
  });
});
