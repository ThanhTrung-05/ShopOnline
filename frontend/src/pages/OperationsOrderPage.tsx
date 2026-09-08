import { useCallback, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import {
  operationsOrderApi,
  type OperationsOrder,
} from '../api/operationsOrderApi';
import type { OrderStatus } from '../api/orderApi';
import { getApiErrorMessage } from '../utils/apiError';

const GENERIC_ERROR = 'Không thể tải danh sách đơn hàng.';

const STATUS_PRESENTATION: Record<OrderStatus, { label: string; className: string }> = {
  PENDING: { label: 'Chờ xử lý', className: 'status-inactive' },
  CONFIRMED: { label: 'Đã xác nhận', className: 'status-info' },
  PAID: { label: 'Đã thanh toán', className: 'status-active' },
  PAYMENT_FAILED: { label: 'Thanh toán thất bại', className: 'status-error' },
  SHIPPING: { label: 'Đang giao hàng', className: 'status-info' },
  DELIVERED: { label: 'Đã giao hàng', className: 'status-active' },
  CANCELLED: { label: 'Đã hủy', className: 'status-error' },
  REFUNDED: { label: 'Đã hoàn tiền', className: 'status-info' },
};

const NEXT_STATUSES: Partial<Record<OrderStatus, OrderStatus[]>> = {
  PENDING: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['SHIPPING', 'CANCELLED'],
  SHIPPING: ['DELIVERED'],
};

const ACTION_LABELS: Partial<Record<OrderStatus, string>> = {
  CONFIRMED: 'Xác nhận',
  SHIPPING: 'Bắt đầu giao hàng',
  DELIVERED: 'Xác nhận đã giao',
  CANCELLED: 'Hủy đơn',
};

const formatDateTime = (value: string) => new Date(value).toLocaleString('vi-VN');

export default function OperationsOrderPage() {
  const [orders, setOrders] = useState<OperationsOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [updatingOrderNumber, setUpdatingOrderNumber] = useState<string | null>(null);

  const loadOrders = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await operationsOrderApi.list();
      setOrders(response.data.data ?? []);
    } catch (requestError) {
      setOrders([]);
      setError(getApiErrorMessage(requestError, GENERIC_ERROR));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  const updateStatus = async (orderNumber: string, status: OrderStatus) => {
    if (updatingOrderNumber !== null) {
      return;
    }

    setUpdatingOrderNumber(orderNumber);
    try {
      await operationsOrderApi.updateStatus(orderNumber, status);
      toast.success('Cập nhật trạng thái đơn hàng thành công.');
      await loadOrders();
    } catch (requestError) {
      toast.error(getApiErrorMessage(
        requestError,
        'Không thể cập nhật trạng thái đơn hàng.',
      ));
    } finally {
      setUpdatingOrderNumber(null);
    }
  };

  return (
    <main className="page">
      <div className="container admin-container">
        <div className="admin-heading">
          <div>
            <span className="eyebrow">Vận hành</span>
            <h1>Quản lý đơn hàng</h1>
            <p>Xác nhận và cập nhật tiến trình giao hàng.</p>
          </div>
          <button
            type="button"
            className="btn btn-ghost"
            disabled={loading || updatingOrderNumber !== null}
            onClick={() => void loadOrders()}
          >
            Làm mới
          </button>
        </div>

        {error && (
          <div className="alert alert-error admin-alert" role="alert">
            <span>{error}</span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void loadOrders()}>
              Thử lại
            </button>
          </div>
        )}

        <section className="table-shell" aria-label="Danh sách đơn hàng vận hành">
          <div className="table-scroll">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Mã đơn hàng</th>
                  <th>Trạng thái</th>
                  <th>Cập nhật gần nhất</th>
                  <th><span className="sr-only">Thao tác</span></th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan={4}>
                      <div className="table-state"><span className="spinner spinner-dark" /> Đang tải...</div>
                    </td>
                  </tr>
                ) : error ? null : orders.length === 0 ? (
                  <tr><td colSpan={4}><div className="table-state">Chưa có đơn hàng.</div></td></tr>
                ) : orders.map((order) => {
                  const presentation = STATUS_PRESENTATION[order.status];
                  const nextStatuses = NEXT_STATUSES[order.status] ?? [];
                  const isUpdating = updatingOrderNumber === order.orderNumber;

                  return (
                    <tr key={order.orderNumber}>
                      <td><strong>{order.orderNumber}</strong></td>
                      <td>
                        <span className={`status-badge ${presentation.className}`}>
                          {presentation.label}
                        </span>
                      </td>
                      <td><time dateTime={order.updatedAt}>{formatDateTime(order.updatedAt)}</time></td>
                      <td>
                        {nextStatuses.length === 0 ? (
                          <span className="muted">Không có thao tác</span>
                        ) : (
                          <div className="table-actions">
                            {nextStatuses.map((status) => (
                              <button
                                type="button"
                                className={status === 'CANCELLED' ? 'btn btn-danger btn-sm' : 'btn btn-primary btn-sm'}
                                key={status}
                                disabled={updatingOrderNumber !== null}
                                onClick={() => void updateStatus(order.orderNumber, status)}
                              >
                                {isUpdating ? 'Đang cập nhật...' : ACTION_LABELS[status]}
                              </button>
                            ))}
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </main>
  );
}
