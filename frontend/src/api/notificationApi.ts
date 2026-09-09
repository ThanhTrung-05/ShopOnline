import apiClient from './axios';
import type { ApiResponse } from '../types';
import type { OrderStatus } from './orderApi';

export interface CustomerNotification {
  id: number;
  orderNumber: string;
  oldStatus: OrderStatus;
  newStatus: OrderStatus;
  message: string;
  createdAt: string;
  readAt: string | null;
  read: boolean;
}

export interface UnreadNotificationCount {
  unreadCount: number;
}

export const notificationApi = {
  list: () =>
    apiClient.get<ApiResponse<CustomerNotification[]>>('/customers/me/notifications'),

  getUnreadCount: () =>
    apiClient.get<ApiResponse<UnreadNotificationCount>>(
      '/customers/me/notifications/unread-count',
    ),

  markRead: (notificationId: number) =>
    apiClient.patch<ApiResponse<CustomerNotification>>(
      `/customers/me/notifications/${notificationId}/read`,
    ),
};
