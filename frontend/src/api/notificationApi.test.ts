import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./axios', () => ({
  default: {
    get: vi.fn(),
    patch: vi.fn(),
  },
}));

import apiClient from './axios';
import { notificationApi } from './notificationApi';

describe('notificationApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.patch).mockReset();
  });

  it('lists notifications for the authenticated customer', () => {
    notificationApi.list();

    expect(apiClient.get).toHaveBeenCalledWith('/customers/me/notifications');
  });

  it('gets the authenticated customer unread count', () => {
    notificationApi.getUnreadCount();

    expect(apiClient.get).toHaveBeenCalledWith('/customers/me/notifications/unread-count');
  });

  it('marks an owned notification read', () => {
    notificationApi.markRead(42);

    expect(apiClient.patch).toHaveBeenCalledWith('/customers/me/notifications/42/read');
  });
});
