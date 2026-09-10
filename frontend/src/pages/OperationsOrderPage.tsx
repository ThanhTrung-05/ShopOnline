import { useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowsClockwise, ClipboardText, Package, Trash } from '@phosphor-icons/react';
import toast from 'react-hot-toast';
import { operationsOrderApi, type OperationsOrder } from '../api/operationsOrderApi';
import type { OrderStatus } from '../api/orderApi';
import Modal from '../components/Modal';
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
  const [statusFilter, setStatusFilter] = useState<OrderStatus | ''>('');
  const [cancelTarget, setCancelTarget] = useState<string | null>(null);

  const visibleOrders = useMemo(
    () => statusFilter ? orders.filter((order) => order.status === statusFilter) : orders,
    [orders, statusFilter],
  );
  const waitingCount = orders.filter((order) => order.status === 'PENDING').length;
  const movingCount = orders.filter((order) => order.status === 'CONFIRMED' || order.status === 'SHIPPING').length;

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
    if (updatingOrderNumber !== null) return;
    setUpdatingOrderNumber(orderNumber);
    try {
      await operationsOrderApi.updateStatus(orderNumber, status);
      toast.success('Cập nhật trạng thái đơn hàng thành công.');
      setCancelTarget(null);
      await loadOrders();
    } catch (requestError) {
      toast.error(getApiErrorMessage(requestError, 'Không thể cập nhật trạng thái đơn hàng.'));
    } finally {
      setUpdatingOrderNumber(null);
    }
  };

  const requestUpdate = (orderNumber: string, status: OrderStatus) => {
    if (status === 'CANCELLED') setCancelTarget(orderNumber);
    else void updateStatus(orderNumber, status);
  };

  return (
    <main className="page operations-page">
      <div className="container admin-container">
        <header className="admin-heading operations-heading">
          <div><span className="page-context">Trung tâm vận hành</span><h1>Quản lý đơn hàng</h1><p>Xác nhận đơn mới và cập nhật đúng bước trong tiến trình giao hàng.</p></div>
          <button type="button" className="btn btn-ghost" disabled={loading || updatingOrderNumber !== null} onClick={() => void loadOrders()}><ArrowsClockwise size={18} aria-hidden="true" /> Làm mới</button>
        </header>

        <section className="operations-overview" aria-label="Tổng quan đơn hàng">
          <div className="operations-stat operations-stat--primary"><span>Tất cả đơn hàng</span><strong>{loading ? '...' : orders.length}</strong><small>đơn trong hệ thống</small></div>
          <div className="operations-stat"><span>Chờ xử lý</span><strong>{loading ? '...' : waitingCount}</strong></div>
          <div className="operations-stat"><span>Đang xử lý hoặc giao</span><strong>{loading ? '...' : movingCount}</strong></div>
        </section>

        <div className="operations-toolbar operations-toolbar--end">
          <label className="field admin-filter-field" htmlFor="operations-status-filter"><span>Trạng thái</span><select id="operations-status-filter" className="form-input admin-filter" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as OrderStatus | '')}><option value="">Tất cả trạng thái</option>{Object.entries(STATUS_PRESENTATION).map(([value, presentation]) => <option key={value} value={value}>{presentation.label}</option>)}</select></label>
        </div>

        {error && <div className="alert alert-error admin-alert" role="alert"><span>{error}</span><button type="button" className="btn btn-ghost btn-sm" onClick={() => void loadOrders()}>Thử lại</button></div>}

        <section className="table-shell" aria-label="Danh sách đơn hàng vận hành">
          <div className="table-scroll">
            <table className="admin-table operations-order-table">
              <thead><tr><th>Mã đơn hàng</th><th>Trạng thái</th><th>Cập nhật gần nhất</th><th>Bước tiếp theo</th></tr></thead>
              <tbody>
                {loading ? (
                  <tr><td colSpan={4}><div className="table-state"><span className="spinner spinner-dark" /> Đang tải...</div></td></tr>
                ) : error ? null : orders.length === 0 ? (
                  <tr><td colSpan={4}><div className="table-state"><ClipboardText size={24} aria-hidden="true" /> Chưa có đơn hàng.</div></td></tr>
                ) : visibleOrders.length === 0 ? (
                  <tr><td colSpan={4}><div className="table-state"><Package size={24} aria-hidden="true" /> Không có đơn hàng phù hợp.</div></td></tr>
                ) : visibleOrders.map((order) => {
                  const presentation = STATUS_PRESENTATION[order.status];
                  const nextStatuses = NEXT_STATUSES[order.status] ?? [];
                  const isUpdating = updatingOrderNumber === order.orderNumber;
                  return (
                    <tr key={order.orderNumber}>
                      <td data-label="Mã đơn hàng"><strong className="order-number-cell">{order.orderNumber}</strong></td>
                      <td data-label="Trạng thái"><span className={`status-badge ${presentation.className}`}>{presentation.label}</span></td>
                      <td data-label="Cập nhật"><time dateTime={order.updatedAt}>{formatDateTime(order.updatedAt)}</time></td>
                      <td data-label="Bước tiếp theo">{nextStatuses.length === 0 ? <span className="muted">Không có thao tác</span> : <div className="table-actions">{nextStatuses.map((status) => <button type="button" className={status === 'CANCELLED' ? 'btn btn-danger btn-sm' : 'btn btn-primary btn-sm'} key={status} disabled={updatingOrderNumber !== null} onClick={() => requestUpdate(order.orderNumber, status)}>{isUpdating ? 'Đang cập nhật...' : ACTION_LABELS[status]}</button>)}</div>}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      </div>

      {cancelTarget && (
        <Modal title="Hủy đơn hàng này?" context={cancelTarget} size="small" onClose={() => setCancelTarget(null)} closeDisabled={updatingOrderNumber !== null}>
          <div className="confirm-dialog-body"><div className="confirm-dialog-icon" aria-hidden="true"><Trash size={24} /></div><p>Trạng thái đơn hàng sẽ chuyển sang đã hủy. Chỉ tiếp tục khi đây là thao tác vận hành đã được xác nhận.</p><div className="modal-actions"><button type="button" className="btn btn-ghost" disabled={updatingOrderNumber !== null} onClick={() => setCancelTarget(null)}>Quay lại</button><button type="button" className="btn btn-danger" disabled={updatingOrderNumber !== null} onClick={() => void updateStatus(cancelTarget, 'CANCELLED')}>{updatingOrderNumber ? <><span className="spinner spinner-inline" /> Đang cập nhật...</> : 'Hủy đơn'}</button></div></div>
        </Modal>
      )}
    </main>
  );
}
