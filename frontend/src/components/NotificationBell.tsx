import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, BellSlash, WarningCircle, X } from '@phosphor-icons/react';
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
  const shellRef = useRef<HTMLDivElement>(null);
  const bellRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLElement>(null);
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

  useEffect(() => {
    if (!open) return undefined;

    panelRef.current?.focus();
    const closeFromOutside = (event: PointerEvent) => {
      if (!shellRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        bellRef.current?.focus();
      }
    };
    document.addEventListener('pointerdown', closeFromOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('pointerdown', closeFromOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [open]);

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
    <div ref={shellRef} className="notification-shell">
      <button
        ref={bellRef}
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
          ref={panelRef}
          id="customer-notification-panel"
          className="notification-panel"
          role="region"
          aria-label="Thông báo đơn hàng"
          tabIndex={-1}
        >
          <div className="notification-panel-heading">
            <div><span>Đơn hàng của bạn</span><strong>Thông báo</strong></div>
            <div className="notification-panel-actions"><span>{unreadCount} chưa đọc</span><button className="icon-button" type="button" aria-label="Đóng thông báo" onClick={() => { setOpen(false); bellRef.current?.focus(); }}><X size={18} aria-hidden="true" /></button></div>
          </div>

          {isLoading && notifications.length === 0 ? (
            <div className="notification-state"><span className="spinner spinner-dark" /><p>Đang tải thông báo...</p></div>
          ) : error && notifications.length === 0 ? (
            <div className="notification-state notification-state-error"><WarningCircle size={27} aria-hidden="true" /><p role="alert">{error}</p><button className="btn btn-ghost btn-sm" type="button" onClick={() => void useNotificationStore.getState().refresh()}>Thử lại</button></div>
          ) : notifications.length === 0 ? (
            <div className="notification-state"><BellSlash size={27} aria-hidden="true" /><p>Chưa có thông báo.</p></div>
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
