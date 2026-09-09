import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../api/notificationApi', () => ({
  notificationApi: {
    list: vi.fn(),
    getUnreadCount: vi.fn(),
    markRead: vi.fn(),
  },
}));

import {
  notificationApi,
  type CustomerNotification,
} from '../api/notificationApi';
import { useNotificationStore } from './notificationStore';

const unreadNotification: CustomerNotification = {
  id: 42,
  orderNumber: 'ORD-20260908-NOTIFY',
  oldStatus: 'PENDING',
  newStatus: 'CONFIRMED',
  message: 'Đơn hàng đã được xác nhận.',
  createdAt: '2026-09-08T02:00:00Z',
  readAt: null,
  read: false,
};

describe('notificationStore', () => {
  beforeEach(() => {
    vi.mocked(notificationApi.list).mockReset();
    vi.mocked(notificationApi.getUnreadCount).mockReset();
    vi.mocked(notificationApi.markRead).mockReset();
    useNotificationStore.getState().clearLocal();
  });

  it('loads the notification list and unread badge count together', async () => {
    vi.mocked(notificationApi.list).mockResolvedValue({
      data: { data: [unreadNotification] },
    } as any);
    vi.mocked(notificationApi.getUnreadCount).mockResolvedValue({
      data: { data: { unreadCount: 1 } },
    } as any);

    const result = await useNotificationStore.getState().refresh();

    expect(result).toEqual([unreadNotification]);
    expect(useNotificationStore.getState().notifications).toEqual([unreadNotification]);
    expect(useNotificationStore.getState().unreadCount).toBe(1);
    expect(useNotificationStore.getState().error).toBeNull();
  });

  it('marks a notification read and updates local unread state', async () => {
    const readNotification = {
      ...unreadNotification,
      read: true,
      readAt: '2026-09-08T03:00:00Z',
    };
    useNotificationStore.setState({
      notifications: [unreadNotification],
      unreadCount: 1,
    });
    vi.mocked(notificationApi.markRead).mockResolvedValue({
      data: { data: readNotification },
    } as any);

    await useNotificationStore.getState().markRead(unreadNotification.id);

    expect(notificationApi.markRead).toHaveBeenCalledWith(unreadNotification.id);
    expect(useNotificationStore.getState().notifications).toEqual([readNotification]);
    expect(useNotificationStore.getState().unreadCount).toBe(0);
  });

  it('clears customer notification state without persisting it locally', () => {
    useNotificationStore.setState({
      notifications: [unreadNotification],
      unreadCount: 1,
      error: 'x',
    });

    useNotificationStore.getState().clearLocal();

    expect(useNotificationStore.getState().notifications).toEqual([]);
    expect(useNotificationStore.getState().unreadCount).toBe(0);
    expect(useNotificationStore.getState().error).toBeNull();
  });
});
