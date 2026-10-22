/**
 * ATS-39: QR Payment Page
 *
 * Displays the QR code for payment and polls backend for confirmation every 3 s.
 * - Does NOT trust frontend-only state for "payment succeeded".
 * - Only navigates to the success page when Backend returns PaymentStatus = SUCCESS.
 * - Handles expired QR, failed payment, and page reload gracefully.
 * - The ref guard `pollingRef` prevents multiple simultaneous polling timers.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { ArrowCounterClockwise, CheckCircle, QrCode, Timer, WarningCircle } from '@phosphor-icons/react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import { paymentApi, type PaymentStatus, type QrPaymentInfo } from '../api/paymentApi';
import CheckoutSteps from '../components/CheckoutSteps';
import { getApiErrorMessage } from '../utils/apiError';

const POLL_INTERVAL_MS = 3_000;

const money = (value: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);

function QrDisplay({ qrCode, amount }: { qrCode: string; amount: number }) {
  // In production this would render an actual QR image via a VietQR library.
  // For demo mode the payload is "orderId|orderNumber|amount"; we display it as text + simple visual.
  return (
    <div className="qr-display" aria-label="Mã QR thanh toán">
      <div className="qr-code-box" role="img" aria-label={`QR payload: ${qrCode}`}>
        {/* Demo placeholder — replace with <QRCodeSVG value={qrCode} /> in production */}
        <QrCode size={120} weight="light" aria-hidden="true" />
        <code className="qr-payload">{qrCode}</code>
      </div>
      <p className="qr-amount">
        Số tiền: <strong>{money(amount)}</strong>
      </p>
    </div>
  );
}

export default function PaymentPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const orderIdParam = searchParams.get('orderId');
  const orderId = orderIdParam ? parseInt(orderIdParam, 10) : null;

  const [qrInfo, setQrInfo] = useState<QrPaymentInfo | null>(null);
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const navigatedRef = useRef(false);

  const stopPolling = () => {
    if (pollingRef.current !== null) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
  };

  /** Navigate to success page — called only once when backend confirms SUCCESS. */
  const handleSuccess = useCallback(
    (orderNumber: string) => {
      if (navigatedRef.current) return;
      navigatedRef.current = true;
      stopPolling();
      navigate(`/order-success?orderNumber=${encodeURIComponent(orderNumber)}`);
    },
    [navigate],
  );

  /** Fetch (or refresh) the QR code from the backend. */
  const loadQr = useCallback(
    async (showRefreshing = false) => {
      if (orderId === null || isNaN(orderId)) {
        setError('Mã đơn hàng không hợp lệ. Vui lòng quay lại và thử đặt hàng lại.');
        setLoading(false);
        return;
      }

      if (showRefreshing) setRefreshing(true);
      else setLoading(true);
      setError(null);

      try {
        const response = await paymentApi.generateQr(orderId);
        const info = response.data.data;
        setQrInfo(info);
        setPaymentStatus(info.status);

        // If the QR call itself returns SUCCESS (e.g. race with callback), navigate immediately.
        if (info.status === 'SUCCESS') {
          handleSuccess(info.orderNumber);
        }
      } catch (requestError) {
        const message = getApiErrorMessage(requestError, 'Không thể tải mã QR. Vui lòng thử lại.');
        setError(message);
        toast.error(message);
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [orderId, handleSuccess],
  );

  /** Start polling payment status every 3 seconds. */
  const startPolling = useCallback(() => {
    if (orderId === null || pollingRef.current !== null) return;

    pollingRef.current = setInterval(async () => {
      try {
        const response = await paymentApi.getStatus(orderId);
        const status = response.data.data.status;
        setPaymentStatus(status);

        if (status === 'SUCCESS') {
          // Backend confirmed — navigate to success page.
          if (qrInfo) handleSuccess(qrInfo.orderNumber);
        } else if (status === 'FAILED' || status === 'EXPIRED') {
          stopPolling();
        }
      } catch {
        // Network blip — keep polling; log silently.
      }
    }, POLL_INTERVAL_MS);
  }, [orderId, qrInfo, handleSuccess]);

  useEffect(() => {
    void loadQr();
    return () => stopPolling();
  }, [loadQr]);

  // Start polling once we have QR info and payment is still in progress.
  useEffect(() => {
    if (qrInfo && paymentStatus === 'PROCESSING') {
      startPolling();
    } else {
      stopPolling();
    }
  }, [qrInfo, paymentStatus, startPolling]);

  // -----------------------------------------------------------------------
  // Render helpers
  // -----------------------------------------------------------------------

  if (loading) {
    return (
      <main className="page checkout-page">
        <div className="container checkout-container">
          <CheckoutSteps active="payment" />
          <section className="state-card checkout-state" aria-live="polite">
            <span className="spinner spinner-dark" />
            <div><h1>Đang tạo mã QR...</h1><p>Vui lòng chờ trong giây lát.</p></div>
          </section>
        </div>
      </main>
    );
  }

  if (!orderId || isNaN(orderId) || error) {
    return (
      <main className="page checkout-page">
        <div className="container checkout-container">
          <CheckoutSteps active="payment" />
          <section className="state-card checkout-state">
            <span className="empty-state-icon" aria-hidden="true"><WarningCircle size={30} /></span>
            <div>
              <h1>Không thể tải trang thanh toán</h1>
              <p role="alert">{error ?? 'Mã đơn hàng bị thiếu hoặc không hợp lệ.'}</p>
            </div>
            <Link className="btn btn-primary" to="/cart">Quay lại giỏ hàng</Link>
          </section>
        </div>
      </main>
    );
  }

  if (paymentStatus === 'FAILED') {
    return (
      <main className="page checkout-page">
        <div className="container checkout-container">
          <CheckoutSteps active="payment" />
          <section className="state-card checkout-state">
            <span className="empty-state-icon" aria-hidden="true"><WarningCircle size={30} /></span>
            <div>
              <h1>Thanh toán thất bại</h1>
              <p>Giao dịch không thành công. Đơn hàng <strong>{qrInfo?.orderNumber}</strong> chưa được thanh toán.</p>
              <p>Vui lòng liên hệ hỗ trợ hoặc kiểm tra lại đơn hàng của bạn.</p>
            </div>
            <Link className="btn btn-primary" to="/orders/status">Xem đơn hàng</Link>
          </section>
        </div>
      </main>
    );
  }

  if (paymentStatus === 'EXPIRED') {
    return (
      <main className="page checkout-page">
        <div className="container checkout-container">
          <CheckoutSteps active="payment" />
          <section className="state-card checkout-state">
            <span className="empty-state-icon" aria-hidden="true"><Timer size={30} /></span>
            <div>
              <h1>Mã QR đã hết hạn</h1>
              <p>Phiên thanh toán cho đơn hàng <strong>{qrInfo?.orderNumber}</strong> đã hết hạn.</p>
            </div>
            <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', justifyContent: 'center' }}>
              <button
                type="button"
                className="btn btn-primary"
                disabled={refreshing}
                onClick={() => void loadQr(true)}
              >
                {refreshing ? <><span className="spinner spinner-inline" /> Đang làm mới...</> : <><ArrowCounterClockwise size={17} aria-hidden="true" /> Làm mới mã QR</>}
              </button>
              <Link className="btn btn-ghost" to="/orders/status">Xem đơn hàng</Link>
            </div>
          </section>
        </div>
      </main>
    );
  }

  return (
    <main className="page checkout-page">
      <div className="container checkout-container">
        <CheckoutSteps active="payment" />
        <header className="page-heading page-heading--compact checkout-heading">
          <span className="page-context">Bước 3</span>
          <h1>Thanh toán bằng QR</h1>
          <p>Quét mã QR bên dưới bằng ứng dụng ngân hàng để hoàn tất đơn hàng.</p>
        </header>

        <div className="payment-layout">
          <section className="card card-p payment-qr-section" aria-labelledby="qr-section-heading">
            <h2 id="qr-section-heading">Mã QR thanh toán</h2>
            {qrInfo && <QrDisplay qrCode={qrInfo.qrCode} amount={qrInfo.amount} />}

            <div className="payment-status-row" aria-live="polite" role="status">
              <span className="spinner spinner-inline" aria-hidden="true" />
              <span>Đang chờ xác nhận thanh toán...</span>
            </div>

            <p className="payment-instruction">
              Mã QR có hiệu lực trong <strong>15 phút</strong>. Sau khi quét, hệ thống sẽ tự động xác nhận.
            </p>

            <div className="payment-actions">
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                disabled={refreshing}
                onClick={() => void loadQr(true)}
                aria-label="Tạo lại mã QR"
              >
                {refreshing ? <><span className="spinner spinner-inline" /> Đang làm mới...</> : <><ArrowCounterClockwise size={16} aria-hidden="true" /> Làm mới QR</>}
              </button>
            </div>
          </section>

          <aside className="card card-p payment-summary" aria-labelledby="payment-summary-heading">
            <h2 id="payment-summary-heading">Thông tin đơn hàng</h2>
            {qrInfo && (
              <dl className="payment-summary-lines">
                <div><dt>Mã đơn hàng</dt><dd><strong>{qrInfo.orderNumber}</strong></dd></div>
                <div><dt>Trạng thái</dt><dd><span className="status-badge status-inactive">Chờ thanh toán</span></dd></div>
                <div className="payment-total-line"><dt>Tổng tiền</dt><dd><strong>{money(qrInfo.amount)}</strong></dd></div>
              </dl>
            )}
            <p className="payment-note">
              <CheckCircle size={16} aria-hidden="true" />
              Trang sẽ tự động chuyển sau khi thanh toán thành công.
            </p>
            <Link className="btn btn-ghost btn-sm btn-full" to="/orders/status">
              Xem trạng thái đơn hàng
            </Link>
          </aside>
        </div>
      </div>
    </main>
  );
}
