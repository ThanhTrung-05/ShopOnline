import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

import apiClient from './axios';
import { authApi } from './authApi';
import { customerApi } from './customerApi';
import { addressApi, type AddressRequest } from './addressApi';

const address: AddressRequest = {
  recipientName: 'Nguyen Van A',
  phone: '0987654321',
  line1: '123 Le Loi',
  ward: null,
  district: 'Quan 1',
  province: 'TP. Ho Chi Minh',
};

describe('test UI API contracts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('registers a customer and reads the JWT session', () => {
    const request = { email: 'customer@example.com', password: 'SecurePass123', fullName: 'Customer A', phone: null };

    authApi.register(request);
    authApi.session();

    expect(apiClient.post).toHaveBeenCalledWith('/auth/register', request);
    expect(apiClient.get).toHaveBeenCalledWith('/auth/session');
  });

  it('gets and patches the authenticated customer profile', () => {
    customerApi.getProfile();
    customerApi.updateProfile({ fullName: 'Customer B', phone: '0987654321' });

    expect(apiClient.get).toHaveBeenCalledWith('/customers/me');
    expect(apiClient.patch).toHaveBeenCalledWith('/customers/me', {
      fullName: 'Customer B',
      phone: '0987654321',
    });
  });

  it('uses customer-owned address endpoints for CRUD and default selection', () => {
    addressApi.list();
    addressApi.create(address);
    addressApi.update(7, address);
    addressApi.setDefault(7);
    addressApi.remove(7);

    expect(apiClient.get).toHaveBeenCalledWith('/customers/me/addresses');
    expect(apiClient.post).toHaveBeenCalledWith('/customers/me/addresses', address);
    expect(apiClient.put).toHaveBeenCalledWith('/customers/me/addresses/7', address);
    expect(apiClient.patch).toHaveBeenCalledWith('/customers/me/addresses/7/default');
    expect(apiClient.delete).toHaveBeenCalledWith('/customers/me/addresses/7');
  });
});
