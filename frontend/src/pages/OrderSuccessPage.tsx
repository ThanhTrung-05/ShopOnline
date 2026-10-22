/**
 * ATS-41: Order Success Page
 *
 * Rules enforced:
 * 1. Access only via orderNumber query param.
 * 2. Order details fetched from Backend — not from URL state or transient Frontend state.
 * 3. Only shows success content when paymentStatus === 'SUCCESS' from Backend.
 * 4. If paymentStatus is not SUCCESS, redirects to order status page with an error.
 * 5. Invoice download: calls the print-friendly version of this page.
 * 6. Invoice print: window.print() — CSS @media print hides navigation elements.
 * 7. Ownership is validated server-side (the GET /orders/{orderNumber} endpoint
 *    requires the authenticated customer to own the order).
 */
import { useCallback, useEffect, useState } from 'react';
import {
  CheckCircle,
  MapPin,
  Package,
  Printer,
  ReceiptX,
  ShoppingBag,
  ArrowRight,
} from '@phosphor-icons/react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import { orderApi, type OrderDetails } from '../api/orderApi';
import CheckoutSteps from '../components/CheckoutSteps';
import { getApiErrorMessage } from '../utils/apiError';

const money = (value: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);

const formatDateTime = (value: string) => new Date(value).toLocaleString('vi-VN');

function InvoiceContent({ order }: { order: OrderDetails }) {
  return (
    <article className="invoice" aria-label="Hóa đơn mua hàng">
      {/* Invoice header */}
      <header className="invoice-header">
        <div className="invoice-brand">
          <span className="brand-mark" aria-hidden="true">SO</span>
          <div>
            <strong>ShopOnline</strong>
            <small>Hóa đơn mua hàng</small>
          </div>
        </div>
        <dl className="invoice-meta">
          <div><dt>Mã đơn hàng</dt><dd><strong>{order.orderNumber}</strong></dd></div>
          <div><dt>Ngày đặt</dt><dd>{formatDateTime(order.createdAt)}</dd></div>
          <div><dt>Trạng thái</dt><dd><strong className="invoice-paid">Đã thanh toán</strong></dd></div>
        </dl>
      </header>

      {/* Shipping address */}
      <section className="invoice-section" aria-labelledby="invoice-address-heading">
        <h3 id="invoice-address-heading">Thông tin giao hàng</h3>
        <address className="invoice-address">
          <MapPin size={15} aria-hidden="true" />
          <span>{order.shippingAddress}</span>
        </address>
      </section>

      {/* Order items */}
      <section className="invoice-section" aria-labelledby="invoice-items-heading">
        <h3 id="invoice-items-heading">Sản phẩm</h3>
        <table className="invoice-table" aria-label="Danh sách sản phẩm">
          <thead>
            <tr>
              <th scope="col">Sản phẩm</th>
              <th scope="col" className="text-right">Đơn giá</th>
              <th scope="col" className="text-right">Số lượng</th>
              <th scope="col" className="text-right">VAT</th>
              <th scope="col" className="text-right">Thành tiền</th>
            </tr>
          </thead>
          <tbody>
            {order.items.map((item) => (
              <tr key={item.productId}>
                <td>{item.productName}</td>
                <td className="text-right">{money(item.unitPrice)}</td>
                <td className="text-right">{item.quantity}</td>
                <td className="text-right">{item.vatRate.toFixed(0)}%</td>
                <td className="text-right"><strong>{money(item.subtotalIncludingVat)}</strong></td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {/* Totals */}
      <section className="invoice-totals" aria-labelledby="invoice-totals-heading">
        <h3 id="invoice-totals-heading" className="sr-only">Tổng thanh toán</h3>
        <dl>
          <div><dt>Tiền hàng (chưa VAT)</dt><dd>{money(order.totalBeforeVat)}</dd></div>
          <div><dt>Thuế VAT</dt><dd>{money(order.totalVatAmount)}</dd></div>
          <div><dt>Phí vận chuyển</dt><dd>{money(order.shippingFee)}</dd></div>
          <div className="invoice-grand-total"><dt><strong>Tổng thanh toán</strong></dt><dd><strong>{money(order.totalAmount)}</strong></dd></div>
        </dl>
      </section>

      <footer className="invoice-footer no-print">
        <small>Hóa đơn này được tạo tự động bởi hệ thống ShopOnline.</small>
      </footer>
    </article>
  );
}

export default function OrderSuccessPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const orderNumber = searchParams.get('orderNumber')?.trim() ?? '';

  const [order, setOrder] = useState<OrderDetails | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadOrder = useCallback(async () => {
    if (!orderNumber) {
      setError('Không tìm thấy thông tin đơn hàng.');
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const response = await orderApi.getDetails(orderNumber);
      const data = response.data.data;

      // ATS-41: Verify Backend confirms payment SUCCESS — do NOT rely on URL state.
      if (data.paymentStatus !== 'SUCCESS') {
        toast.error('Thanh toán chưa được xác nhận. Đang chuyển tới theo dõi đơn hàng...');
        navigate(`/orders/status?orderNumber=${encodeURIComponent(orderNumber)}`, { replace: true });
        return;
      }

      setOrder(data);
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Không thể tải thông tin đơn hàng.');
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [orderNumber, navigate]);

  useEffect(() => {
    void loadOrder();
  }, [loadOrder]);

  const handlePrint = () => {
    window.print();
  };

  // -----------------------------------------------------------------------
  // Loading state
  // -----------------------------------------------------------------------
  if (loading) {
    return (
      <main className="page checkout-page">
        <div className="container checkout-container">
          <CheckoutSteps active="complete" />
          <section className="state-card checkout-state" aria-live="polite">
            <span className="spinner spinner-dark" />
            <div><h1>Đang tải thông tin xác nhận...</h1><p>Hệ thống đang xác minh đơn hàng của bạn.</p></div>
          </section>
        </div>
      </main>
    );
  }

  // -----------------------------------------------------------------------
  // Error / not authorised state
  // -----------------------------------------------------------------------
  if (error || !order) {
    return (
      <main className="page checkout-page">
        <div className="container checkout-container">
          <CheckoutSteps active="complete" />
          <section className="state-card checkout-state">
            <span className="empty-state-icon" aria-hidden="true"><ReceiptX size={30} /></span>
            <div>
              <h1>Không thể hiển thị xác nhận đơn hàng</h1>
              <p role="alert">{error ?? 'Đơn hàng không tồn tại hoặc bạn không có quyền truy cập.'}</p>
            </div>
            <Link className="btn btn-primary" to="/orders/status">Xem đơn hàng của tôi</Link>
          </section>
        </div>
      </main>
    );
  }

  // -----------------------------------------------------------------------
  // Success state
  // -----------------------------------------------------------------------
  return (
    <main className="page checkout-page success-page">
      <div className="container checkout-container">
        <CheckoutSteps active="complete" />

        {/* Success banner — hidden during print */}
        <header className="success-banner no-print" aria-live="polite">
          <CheckCircle size={48} weight="fill" className="success-icon" aria-hidden="true" />
          <div>
            <span className="page-context">Hoàn tất</span>
            <h1>Đặt hàng và thanh toán thành công!</h1>
            <p>
              Đơn hàng <strong>{order.orderNumber}</strong> của bạn đã được ghi nhận.
              Chúng tôi sẽ liên hệ xác nhận giao hàng sớm nhất.
            </p>
          </div>
        </header>

        {/* Invoice — printed area */}
        <InvoiceContent order={order} />

        {/* Action bar — hidden during print */}
        <div className="success-actions no-print">
          <button
            id="btn-print-invoice"
            type="button"
            className="btn btn-primary"
            onClick={handlePrint}
            aria-label="In hóa đơn đơn hàng này"
          >
            <Printer size={18} aria-hidden="true" />
            In hóa đơn
          </button>

          <Link
            id="btn-view-orders"
            className="btn btn-ghost"
            to="/orders/status"
            aria-label="Xem tất cả đơn hàng của bạn"
          >
            <ShoppingBag size={18} aria-hidden="true" />
            Đơn hàng của tôi
          </Link>

          <Link
            id="btn-continue-shopping"
            className="btn btn-ghost"
            to="/products"
            aria-label="Tiếp tục mua sắm"
          >
            Tiếp tục mua sắm
            <ArrowRight size={17} aria-hidden="true" />
          </Link>
        </div>
      </div>
    </main>
  );
}
