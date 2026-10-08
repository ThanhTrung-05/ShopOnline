import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./axios', () => ({
  default: {
    post: vi.fn(),
  },
}));

import apiClient from './axios';
import { shippingApi, type PrepareShippingRequest } from './shippingApi';

describe('shippingApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.post).mockReset();
  });

  it('prepares shipping through the authenticated customer endpoint', () => {
    const request: PrepareShippingRequest = {
      addressId: 42,
      shippingMethod: 'STANDARD',
    };

    shippingApi.prepare(request);

    expect(apiClient.post).toHaveBeenCalledWith(
      '/customers/me/shipping/prepare',
      request,
    );
  });
});
