import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell } from '@phosphor-icons/react';
import toast from 'react-hot-toast';
import type { CustomerNotification } from '../api/notificationApi';
import { useNotificationStore } from '../store/notificationStore';

export const NOTIFICATION_POLL_INTERVAL_MS = 30_000;

type NotificationBellProps = {
  identityKey: string;
};

const formatDateTime = (value: string) => new Date(value).toLocaleString('vi-VN');

export default function NotificationBell({ identityKey }: NotificationBellProps) {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const notifications = useNotificationStore((state) => state.notifications);
  const unreadCount = useNotificationStore((state) => state.unreadCount);
  const isLoading = useNotificationStore((state) => state.isLoading);
  const error = useNotificationStore((state) => state.error);
  const markRead = useNotificationStore((state) => state.markRead);
  const seenNotificationIds = useRef<Set<number>>(new Set());
  const initialPollComplete = useRef(false);

  useEffect(() => {
    let active = true;
    useNotificationStore.getState().clearLocal();
    seenNotificationIds.current.clear();
    initialPollComplete.current = false;

    const poll = async () => {
      const loaded = await useNotificationStore.getState().refresh();
      if (!active) {
        return;
      }

      if (initialPollComplete.current) {
        loaded
          .filter((notification) => (
            !notification.read && !seenNotificationIds.current.has(notification.id)
          ))
          .forEach((notification) => {
            toast.success(notification.message, { id: `notification-${notification.id}` });
          });
      }

      seenNotificationIds.current = new Set(loaded.map((notification) => notification.id));
      initialPollComplete.current = true;
    };

    void poll();
    const intervalId = window.setInterval(() => void poll(), NOTIFICATION_POLL_INTERVAL_MS);

    return () => {
      active = false;
      window.clearInterval(intervalId);
      useNotificationStore.getState().clearLocal();
    };
  }, [identityKey]);

  const openNotification = async (notification: CustomerNotification) => {
    if (!notification.read) {
      try {
        await markRead(notification.id);
      } catch {
        toast.error('Không thể đánh dấu thông báo đã đọc.');
      }
    }

    setOpen(false);
    navigate(`/orders/status?orderNumber=${encodeURIComponent(notification.orderNumber)}`);
  };

  return (
    <div className="notification-shell">
      <button
        type="button"
        className="notification-bell"
        aria-label={`Thông báo, ${unreadCount} chưa đọc`}
        aria-expanded={open}
        aria-controls="customer-notification-panel"
        onClick={() => setOpen((current) => !current)}
      >
        <Bell size={19} weight={open ? 'fill' : 'bold'} aria-hidden="true" />
        {unreadCount > 0 && (
          <span className="notification-count-badge" aria-label={`${unreadCount} thông báo chưa đọc`}>
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <section
          id="customer-notification-panel"
          className="notification-panel"
          aria-label="Thông báo đơn hàng"
        >
          <div className="notification-panel-heading">
            <strong>Thông báo</strong>
            <span>{unreadCount} chưa đọc</span>
          </div>

          {isLoading && notifications.length === 0 ? (
            <p className="notification-state">Đang tải thông báo...</p>
          ) : error && notifications.length === 0 ? (
            <p className="notification-state notification-state-error">{error}</p>
          ) : notifications.length === 0 ? (
            <p className="notification-state">Chưa có thông báo.</p>
          ) : (
            <ul className="notification-list">
              {notifications.map((notification) => (
                <li key={notification.id}>
                  <button
                    type="button"
                    className={`notification-item ${notification.read ? '' : 'unread'}`}
                    onClick={() => void openNotification(notification)}
                  >
                    <span className="notification-message">{notification.message}</span>
                    <span className="notification-meta">
                      <span>{notification.orderNumber}</span>
                      <time dateTime={notification.createdAt}>
                        {formatDateTime(notification.createdAt)}
                      </time>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </div>
  );
}
