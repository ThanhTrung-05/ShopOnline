import { create } from 'zustand';
import {
  notificationApi,
  type CustomerNotification,
} from '../api/notificationApi';
import { getApiErrorMessage } from '../utils/apiError';

interface NotificationState {
  notifications: CustomerNotification[];
  unreadCount: number;
  isLoading: boolean;
  error: string | null;
  requestGeneration: number;
  refresh: () => Promise<CustomerNotification[]>;
  markRead: (notificationId: number) => Promise<CustomerNotification>;
  clearLocal: () => void;
}

export const useNotificationStore = create<NotificationState>((set, get) => ({
  notifications: [],
  unreadCount: 0,
  isLoading: false,
  error: null,
  requestGeneration: 0,

  refresh: async () => {
    const generation = get().requestGeneration;
    set({ isLoading: true, error: null });
    try {
      const [notificationsResponse, unreadCountResponse] = await Promise.all([
        notificationApi.list(),
        notificationApi.getUnreadCount(),
      ]);
      const notifications = notificationsResponse.data.data ?? [];
      if (get().requestGeneration === generation) {
        set({
          notifications,
          unreadCount: unreadCountResponse.data.data.unreadCount,
          isLoading: false,
          error: null,
        });
      }
      return notifications;
    } catch (requestError) {
      if (get().requestGeneration === generation) {
        set({
          isLoading: false,
          error: getApiErrorMessage(requestError, 'Không thể tải thông báo.'),
        });
      }
      return get().notifications;
    }
  },

  markRead: async (notificationId) => {
    const existing = get().notifications.find((notification) => notification.id === notificationId);
    const response = await notificationApi.markRead(notificationId);
    const updated = response.data.data;
    set((state) => ({
      notifications: state.notifications.map((notification) => (
        notification.id === notificationId ? updated : notification
      )),
      unreadCount: existing && !existing.read
        ? Math.max(0, state.unreadCount - 1)
        : state.unreadCount,
      error: null,
    }));
    return updated;
  },

  clearLocal: () => set((state) => ({
    notifications: [],
    unreadCount: 0,
    isLoading: false,
    error: null,
    requestGeneration: state.requestGeneration + 1,
  })),
}));
