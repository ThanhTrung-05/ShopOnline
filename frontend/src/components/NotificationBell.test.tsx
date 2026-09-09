import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import toast from 'react-hot-toast';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('react-hot-toast', () => ({
  default: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

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
import NotificationBell, { NOTIFICATION_POLL_INTERVAL_MS } from './NotificationBell';
import { useNotificationStore } from '../store/notificationStore';

const firstNotification: CustomerNotification = {
  id: 1,
  orderNumber: 'ORD/2026 001',
  oldStatus: 'PENDING',
  newStatus: 'CONFIRMED',
  message: 'Đơn hàng ORD/2026 001 đã được xác nhận.',
  createdAt: '2026-09-08T02:00:00Z',
  readAt: null,
  read: false,
};

const secondNotification: CustomerNotification = {
  ...firstNotification,
  id: 2,
  orderNumber: 'ORD-2026-002',
  message: 'Đơn hàng ORD-2026-002 đang được giao.',
  oldStatus: 'CONFIRMED',
  newStatus: 'SHIPPING',
  createdAt: '2026-09-08T03:00:00Z',
};

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname + location.search}</output>;
}

function renderBell() {
  return render(
    <MemoryRouter initialEntries={['/products']}>
      <NotificationBell identityKey="customer-a" />
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe('NotificationBell', () => {
  beforeEach(() => {
    vi.useRealTimers();
    vi.mocked(notificationApi.list).mockReset();
    vi.mocked(notificationApi.getUnreadCount).mockReset();
    vi.mocked(notificationApi.markRead).mockReset();
    vi.mocked(toast.success).mockReset();
    vi.mocked(toast.error).mockReset();
    useNotificationStore.getState().clearLocal();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows the unread badge and notification list', async () => {
    vi.mocked(notificationApi.list).mockResolvedValue({
      data: { data: [firstNotification] },
    } as any);
    vi.mocked(notificationApi.getUnreadCount).mockResolvedValue({
      data: { data: { unreadCount: 1 } },
    } as any);

    renderBell();

    const bell = await screen.findByRole('button', { name: 'Thông báo, 1 chưa đọc' });
    expect(screen.getByLabelText('1 thông báo chưa đọc')).toHaveTextContent('1');
    fireEvent.click(bell);

    expect(screen.getByRole('region', { name: 'Thông báo đơn hàng' })).toBeInTheDocument();
    expect(screen.getByText(firstNotification.message)).toBeInTheDocument();
    expect(toast.success).not.toHaveBeenCalled();
  });

  it('marks an unread item read and navigates to its ATS-34 detail', async () => {
    const readNotification = {
      ...firstNotification,
      read: true,
      readAt: '2026-09-08T04:00:00Z',
    };
    vi.mocked(notificationApi.list).mockResolvedValue({
      data: { data: [firstNotification] },
    } as any);
    vi.mocked(notificationApi.getUnreadCount).mockResolvedValue({
      data: { data: { unreadCount: 1 } },
    } as any);
    vi.mocked(notificationApi.markRead).mockResolvedValue({
      data: { data: readNotification },
    } as any);

    renderBell();

    fireEvent.click(await screen.findByRole('button', { name: 'Thông báo, 1 chưa đọc' }));
    fireEvent.click(screen.getByText(firstNotification.message).closest('button') as HTMLButtonElement);

    await waitFor(() => expect(notificationApi.markRead).toHaveBeenCalledWith(firstNotification.id));
    expect(screen.getByTestId('location')).toHaveTextContent(
      '/orders/status?orderNumber=ORD%2F2026%20001',
    );
    expect(useNotificationStore.getState().unreadCount).toBe(0);
  });

  it('polls every 30 seconds and toasts a newly detected unread notification', async () => {
    vi.useFakeTimers();
    vi.mocked(notificationApi.list)
      .mockResolvedValueOnce({ data: { data: [firstNotification] } } as any)
      .mockResolvedValueOnce({ data: { data: [secondNotification, firstNotification] } } as any);
    vi.mocked(notificationApi.getUnreadCount)
      .mockResolvedValueOnce({ data: { data: { unreadCount: 1 } } } as any)
      .mockResolvedValueOnce({ data: { data: { unreadCount: 2 } } } as any);

    renderBell();
    await act(async () => {});

    expect(notificationApi.list).toHaveBeenCalledTimes(1);
    expect(toast.success).not.toHaveBeenCalled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(NOTIFICATION_POLL_INTERVAL_MS);
    });

    expect(notificationApi.list).toHaveBeenCalledTimes(2);
    expect(toast.success).toHaveBeenCalledWith(
      secondNotification.message,
      { id: `notification-${secondNotification.id}` },
    );
    expect(useNotificationStore.getState().unreadCount).toBe(2);
  });
});
