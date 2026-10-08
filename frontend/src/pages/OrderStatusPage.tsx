import { useCallback, useEffect, useRef, useState } from 'react';
import { CalendarBlank, Package, ShoppingBag } from '@phosphor-icons/react';
import axios from 'axios';
import { Link, useSearchParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import { orderApi, type OrderDetails, type OrderStatus, type OrderStatusDetails } from '../api/orderApi';
import AccountNav from '../components/AccountNav';
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

const ORDER_STEPS: Array<{ status: OrderStatus; label: string }> = [
  { status: 'PENDING', label: 'Đã nhận đơn' },
  { status: 'CONFIRMED', label: 'Đã xác nhận' },
  { status: 'PAID', label: 'Đã thanh toán' },
  { status: 'SHIPPING', label: 'Đang giao' },
  { status: 'DELIVERED', label: 'Đã giao' },
];

const formatDateTime = (value: string) => new Date(value).toLocaleString('vi-VN');
const formatCurrency = (value: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);

function OrderTimeline({ status }: { status: OrderStatus }) {
  const currentIndex = ORDER_STEPS.findIndex((step) => step.status === status);
  const isException = currentIndex < 0;

  if (isException) {
    return <p className={`order-exception ${STATUS_PRESENTATION[status].className}`}>{STATUS_PRESENTATION[status].label}</p>;
  }

  return (
    <ol className="order-timeline" aria-label="Tiến trình đơn hàng">
      {ORDER_STEPS.map((step, index) => (
        <li key={step.status} className={index < currentIndex ? 'complete' : index === currentIndex ? 'current' : 'upcoming'} aria-current={index === currentIndex ? 'step' : undefined}>
          <span aria-hidden="true" />
          <small>{step.label}</small>
        </li>
      ))}
    </ol>
  );
}

export default function OrderStatusPage() {
  const [searchParams] = useSearchParams();
  const requestedOrderNumber = searchParams.get('orderNumber')?.trim() ?? '';
  const handledRequestedOrder = useRef<string | null>(null);
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
      setLoadError(getApiErrorMessage(requestError, 'Không thể tải danh sách đơn hàng.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  const loadOrderDetails = useCallback(async (orderNumber: string) => {
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
        const message = getApiErrorMessage(requestError, 'Không thể tải chi tiết đơn hàng.');
        setDetailError(message);
        toast.error(message);
      }
    } finally {
      setCheckingOrderNumber(null);
    }
  }, []);

  useEffect(() => {
    if (!requestedOrderNumber) {
      handledRequestedOrder.current = null;
      return;
    }
    if (handledRequestedOrder.current === requestedOrderNumber) return;
    handledRequestedOrder.current = requestedOrderNumber;
    void loadOrderDetails(requestedOrderNumber);
  }, [loadOrderDetails, requestedOrderNumber]);

  const viewOrderDetails = (orderNumber: string) => {
    if (checkingOrderNumber === null) void loadOrderDetails(orderNumber);
  };

  return (
    <main className="page account-page orders-page">
      <div className="container account-container">
        <header className="page-heading page-heading--compact">
          <span className="page-context">Tài khoản của tôi</span>
          <h1>Theo dõi đơn hàng</h1>
          <p>Xem trạng thái xử lý và các sản phẩm trong từng đơn hàng của bạn.</p>
        </header>

        <div className="account-shell">
          <AccountNav />
          <section className="account-workspace order-workspace">
            {loadError && (
              <div className="alert alert-error admin-alert" role="alert"><span>{loadError}</span><button type="button" className="btn btn-ghost btn-sm" onClick={() => void loadOrders()}>Thử lại</button></div>
            )}

            {loading ? (
              <div className="state-card account-state" aria-live="polite"><span className="spinner spinner-dark" /><div><h2>Đang tải đơn hàng...</h2><p>Lịch sử mua sắm sẽ xuất hiện sau giây lát.</p></div></div>
            ) : loadError ? null : orders.length === 0 ? (
              <div className="state-card account-state">
                <span className="empty-state-icon" aria-hidden="true"><ShoppingBag size={29} /></span>
                <div><h2>Bạn chưa có đơn hàng nào.</h2><p>Các đơn hàng đã đặt sẽ được lưu và theo dõi tại đây.</p></div>
                <Link className="btn btn-primary" to="/products">Bắt đầu mua sắm</Link>
              </div>
            ) : (
              <div className="orders-layout">
                <section className="order-list-panel" aria-labelledby="order-list-heading">
                  <div className="section-heading order-list-heading">
                    <div><span>Lịch sử</span><h2 id="order-list-heading">Đơn hàng của bạn</h2></div>
                    <strong>{orders.length}</strong>
                  </div>
                  <div className="order-list">
                    {orders.map((order) => {
                      const isChecking = checkingOrderNumber === order.orderNumber;
                      const isSelected = selectedOrder?.orderNumber === order.orderNumber;
                      const presentation = STATUS_PRESENTATION[order.status];
                      return (
                        <button type="button" className={`order-card ${isSelected ? 'selected' : ''}`} key={order.orderNumber} aria-pressed={isSelected} disabled={checkingOrderNumber !== null} onClick={() => viewOrderDetails(order.orderNumber)}>
                          <span className="order-card-header">
                            <span className="order-card-identity"><small>Mã đơn hàng</small><strong>{order.orderNumber}</strong></span>
                            <span className={`status-badge ${presentation.className}`}>{presentation.label}</span>
                          </span>
                          <span className="order-card-date"><CalendarBlank size={16} aria-hidden="true" /><time dateTime={order.createdAt}>{formatDateTime(order.createdAt)}</time></span>
                          {isChecking && <span className="order-card-loading"><span className="spinner spinner-inline" /> Đang tải chi tiết...</span>}
                        </button>
                      );
                    })}
                  </div>
                </section>

                <div className="order-detail-panel">
                  {detailError && <div className="alert alert-error order-detail-error" role="alert">{detailError}</div>}
                  {checkingOrderNumber && !selectedOrder ? (
                    <div className="order-detail-placeholder" aria-live="polite"><span className="spinner spinner-dark" /><h2>Đang mở đơn hàng</h2><p>Chi tiết sản phẩm đang được tải.</p></div>
                  ) : selectedOrder ? (
                    <section className="order-status-result" role="region" aria-label="Chi tiết đơn hàng" aria-live="polite">
                      <div className="order-detail-heading">
                        <div><span>Chi tiết đơn hàng</span><h2 id="order-status-result-heading">{selectedOrder.orderNumber}</h2></div>
                        <span className={`status-badge ${STATUS_PRESENTATION[selectedOrder.status].className}`}>{STATUS_PRESENTATION[selectedOrder.status].label}</span>
                      </div>
                      <OrderTimeline status={selectedOrder.status} />
                      <dl className="order-detail-facts">
                        <div><dt>Thời gian tạo</dt><dd>{formatDateTime(selectedOrder.createdAt)}</dd></div>
                        <div><dt>Cập nhật gần nhất</dt><dd>{formatDateTime(selectedOrder.updatedAt)}</dd></div>
                        <div className="order-total-fact"><dt>Tổng tiền</dt><dd>{formatCurrency(selectedOrder.totalAmount)}</dd></div>
                      </dl>
                      <div className="order-items-heading"><h3>Sản phẩm trong đơn</h3><span>{selectedOrder.items.length} sản phẩm</span></div>
                      {selectedOrder.items.length === 0 ? (
                        <p className="empty-inline">Đơn hàng này chưa có sản phẩm.</p>
                      ) : (
                        <div className="order-item-list">
                          {selectedOrder.items.map((item) => (
                            <article className="order-item" key={item.productId}>
                              <Link className="order-item-mark" to={`/products/${item.productId}`} aria-label={`Xem ${item.productName}`}><Package size={20} weight="duotone" aria-hidden="true" /></Link>
                              <div className="order-item-name"><strong>{item.productName}</strong><small><span>{formatCurrency(item.unitPrice)}</span><span aria-hidden="true">×</span><strong>{item.quantity}</strong></small></div>
                              <strong>{formatCurrency(item.subtotal)}</strong>
                            </article>
                          ))}
                        </div>
                      )}
                    </section>
                  ) : (
                    <div className="order-detail-placeholder"><Package size={32} weight="duotone" aria-hidden="true" /><h2>Chọn một đơn hàng</h2><p>Trạng thái và sản phẩm sẽ hiển thị tại đây.</p></div>
                  )}
                </div>
              </div>
            )}
          </section>
        </div>
      </div>
    </main>
  );
}
