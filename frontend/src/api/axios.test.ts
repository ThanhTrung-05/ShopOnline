import { describe, expect, it, vi, beforeEach } from 'vitest';

vi.mock('../auth/keycloak', () => ({
  default: {
    authenticated: true,
    token: 'valid-token',
    updateToken: vi.fn(),
    clearToken: vi.fn(),
  },
}));

import keycloak from '../auth/keycloak';
import apiClient from './axios';

describe('axios client refresh/401 handling', () => {
  beforeEach(() => {
    vi.mocked(keycloak.updateToken).mockReset();
    vi.mocked(keycloak.clearToken).mockReset();
  });

  it('calls updateToken(30) before an authenticated request', async () => {
    vi.mocked(keycloak.updateToken).mockResolvedValue(false);

    const config = { headers: new Headers() } as any;
    config.headers.set = vi.fn();

    const interceptor = (apiClient.interceptors.request as any).handlers[0].fulfilled;
    await interceptor(config);

    expect(keycloak.updateToken).toHaveBeenCalledWith(30);
  });

  it('retries exactly once on 401 via force refresh, then gives up without looping', async () => {
    vi.mocked(keycloak.updateToken).mockRejectedValueOnce(new Error('refresh failed'));

    const requestInterceptor = (apiClient.interceptors.response as any).handlers[0].rejected;

    const firstConfig = { _retry: false, headers: { set: vi.fn() } };
    const error = { response: { status: 401 }, config: firstConfig };

    await expect(requestInterceptor(error)).rejects.toBeDefined();
    expect(keycloak.updateToken).toHaveBeenCalledWith(-1);
    expect(keycloak.clearToken).toHaveBeenCalledTimes(1);
  });

  it('does not retry a second time when the config is already marked _retry', async () => {
    const secondConfig = { _retry: true, headers: { set: vi.fn() } };
    const error = { response: { status: 401 }, config: secondConfig };

    const requestInterceptor = (apiClient.interceptors.response as any).handlers[0].rejected;
    await expect(requestInterceptor(error)).rejects.toBe(error);
    expect(keycloak.updateToken).not.toHaveBeenCalled();
  });
});
