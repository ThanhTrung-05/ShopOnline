import { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import toast from 'react-hot-toast';
import {
  orderApi,
  type OrderDetails,
  type OrderStatus,
  type OrderStatusDetails,
} from '../api/orderApi';
import { getApiErrorMessage } from '../utils/apiError';

const NOT_FOUND_MESSAGE = 'Không tìm thấy đơn hàng.';

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

const formatDateTime = (value: string) => new Date(value).toLocaleString('vi-VN');
const formatCurrency = (value: number) => new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
}).format(value);

export default function OrderStatusPage() {
  const [orders, setOrders] = useState<OrderStatusDetails[]>([]);
  const [selectedOrder, setSelectedOrder] = useState<OrderDetails | null>(null);
  const [loading, setLoading] = useState(true);
  const [checkingOrderNumber, setCheckingOrderNumber] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);

  const loadOrders = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    setDetailError(null);
    setSelectedOrder(null);
    try {
      const response = await orderApi.list();
      setOrders(response.data.data ?? []);
    } catch (requestError) {
      setOrders([]);
      setLoadError(getApiErrorMessage(
        requestError,
        'Không thể tải danh sách đơn hàng.',
      ));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  const viewOrderDetails = async (orderNumber: string) => {
    if (checkingOrderNumber !== null) {
      return;
    }

    setCheckingOrderNumber(orderNumber);
    setSelectedOrder(null);
    setDetailError(null);
    try {
      const response = await orderApi.getDetails(orderNumber);
      setSelectedOrder(response.data.data);
    } catch (requestError) {
      if (axios.isAxiosError(requestError) && requestError.response?.status === 404) {
        setDetailError(NOT_FOUND_MESSAGE);
      } else {
        const message = getApiErrorMessage(
          requestError,
          'Không thể tải chi tiết đơn hàng.',
        );
        setDetailError(message);
        toast.error(message);
      }
    } finally {
      setCheckingOrderNumber(null);
    }
  };

  return (
    <main className="page">
      <div className="container narrow-container">
        <div className="page-heading">
          <span className="eyebrow">Đơn hàng</span>
          <h1>Theo dõi đơn hàng</h1>
          <p>Chọn một đơn hàng để xem trạng thái và các sản phẩm đã đặt.</p>
        </div>

        {loadError && (
          <div className="alert alert-error admin-alert" role="alert">
            <span>{loadError}</span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void loadOrders()}>
              Thử lại
            </button>
          </div>
        )}

        {loading ? (
          <div className="state-card" aria-live="polite">
            <span className="spinner spinner-dark" />
            <p>Đang tải đơn hàng...</p>
          </div>
        ) : loadError ? null : orders.length === 0 ? (
          <div className="state-card">
            <p>Bạn chưa có đơn hàng nào.</p>
          </div>
        ) : (
          <section aria-labelledby="order-list-heading">
            <div className="section-heading compact order-list-heading">
              <h2 id="order-list-heading">Đơn hàng của bạn</h2>
              <span className="muted">{orders.length} đơn hàng</span>
            </div>
            <div className="stack-list">
              {orders.map((order) => {
                const isChecking = checkingOrderNumber === order.orderNumber;
                const isSelected = selectedOrder?.orderNumber === order.orderNumber;
                const presentation = STATUS_PRESENTATION[order.status];

                return (
                  <button
                    type="button"
                    className={`card card-p order-card ${isSelected ? 'selected' : ''}`}
                    key={order.orderNumber}
                    aria-pressed={isSelected}
                    disabled={checkingOrderNumber !== null}
                    onClick={() => void viewOrderDetails(order.orderNumber)}
                  >
                    <span className="order-card-header">
                      <span className="order-card-identity">
                        <small>Mã đơn hàng</small>
                        <strong>{order.orderNumber}</strong>
                      </span>
                      <span className={`status-badge ${presentation.className}`}>
                        {presentation.label}
                      </span>
                    </span>
                    <span className="muted">
                      Ngày tạo: <time dateTime={order.createdAt}>{formatDateTime(order.createdAt)}</time>
                    </span>
                    {isChecking && <span className="order-card-loading">Đang tải chi tiết...</span>}
                  </button>
                );
              })}
            </div>
          </section>
        )}

        {detailError && <div className="alert alert-error order-detail-error" role="alert">{detailError}</div>}

        {selectedOrder && (
          <section
            className="card card-p order-status-result fade-in"
            aria-labelledby="order-status-result-heading"
            aria-live="polite"
          >
            <div className="section-heading compact">
              <h2 id="order-status-result-heading">Chi tiết đơn hàng</h2>
              <span className={`status-badge ${STATUS_PRESENTATION[selectedOrder.status].className}`}>
                {STATUS_PRESENTATION[selectedOrder.status].label}
              </span>
            </div>
            <dl className="detail-list">
              <div><dt>Mã đơn hàng</dt><dd>{selectedOrder.orderNumber}</dd></div>
              <div><dt>Thời gian tạo</dt><dd>{formatDateTime(selectedOrder.createdAt)}</dd></div>
              <div><dt>Cập nhật gần nhất</dt><dd>{formatDateTime(selectedOrder.updatedAt)}</dd></div>
              <div><dt>Tổng tiền</dt><dd>{formatCurrency(selectedOrder.totalAmount)}</dd></div>
            </dl>
            <div className="order-items-heading">
              <h3>Sản phẩm trong đơn</h3>
              <span className="muted">{selectedOrder.items.length} sản phẩm</span>
            </div>
            {selectedOrder.items.length === 0 ? (
              <p className="empty-inline">Đơn hàng này chưa có sản phẩm.</p>
            ) : (
              <div className="order-item-list">
                {selectedOrder.items.map((item) => (
                  <article className="order-item" key={item.productId}>
                    <strong>{item.productName}</strong>
                    <dl>
                      <div><dt>Đơn giá</dt><dd>{formatCurrency(item.unitPrice)}</dd></div>
                      <div><dt>Số lượng</dt><dd>{item.quantity}</dd></div>
                      <div><dt>Thành tiền</dt><dd>{formatCurrency(item.subtotal)}</dd></div>
                    </dl>
                  </article>
                ))}
              </div>
            )}
          </section>
        )}
      </div>
    </main>
  );
}
