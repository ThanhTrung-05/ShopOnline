import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { useCartStore } from '../store/cartStore';
import { getApiErrorMessage } from '../utils/apiError';

const money = (value: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);

export default function CartPage() {
  const { items, subtotal, isLoading, error, loadCart, updateItemQuantity, removeItem } = useCartStore();
  const [busyItemId, setBusyItemId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    void loadCart();
  }, [loadCart]);

  const update = async (cartItemId: number, quantity: number) => {
    if (quantity < 1) return;
    setBusyItemId(cartItemId);
    setActionError(null);
    try {
      await updateItemQuantity(cartItemId, quantity);
      toast.success('Đã cập nhật số lượng');
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Không thể cập nhật số lượng.');
      setActionError(message);
      toast.error(message);
    } finally {
      setBusyItemId(null);
    }
  };

  const remove = async (cartItemId: number) => {
    setBusyItemId(cartItemId);
    setActionError(null);
    try {
      await removeItem(cartItemId);
      toast.success('Đã xóa sản phẩm khỏi giỏ hàng');
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Không thể xóa sản phẩm.');
      setActionError(message);
      toast.error(message);
    } finally {
      setBusyItemId(null);
    }
  };

  return (
    <main className="page">
      <div className="container content-container">
        <div className="page-heading">
          <span className="eyebrow">Giỏ hàng của bạn</span>
          <h1>Giỏ hàng</h1>
          <p>Kiểm tra sản phẩm và số lượng trước khi tiếp tục.</p>
        </div>

        {(error || actionError) && <div className="alert alert-error" role="alert">{actionError ?? error}</div>}
        {isLoading ? (
          <div className="state-card"><span className="spinner spinner-dark" /> Đang tải...</div>
        ) : items.length === 0 ? (
          <div className="state-card">
            <h2>Giỏ hàng đang trống</h2>
            <p>Hãy chọn sản phẩm bạn muốn mua.</p>
            <Link className="btn btn-primary" to="/products">Xem sản phẩm</Link>
          </div>
        ) : (
          <div className="cart-layout">
            <section className="stack-list">
              {items.map((item) => (
                <article className="card cart-item" key={item.cartItemId}>
                  <div className="cart-item-main">
                    <span className="product-placeholder">SP</span>
                    <div>
                      <h3>{item.productName}</h3>
                      <p>{money(item.unitPrice)} / sản phẩm</p>
                    </div>
                  </div>
                  <div className="quantity-control" aria-label={'Số lượng ' + item.productName}>
                    <button type="button" disabled={busyItemId !== null || item.quantity <= 1} onClick={() => void update(item.cartItemId, item.quantity - 1)}>−</button>
                    <strong>{item.quantity}</strong>
                    <button type="button" disabled={busyItemId !== null} onClick={() => void update(item.cartItemId, item.quantity + 1)}>+</button>
                  </div>
                  <strong className="cart-line-total">{money(item.itemSubtotal)}</strong>
                  <button className="btn btn-danger btn-sm" type="button" disabled={busyItemId !== null} onClick={() => void remove(item.cartItemId)}>
                    {busyItemId === item.cartItemId ? 'Đang xử lý...' : 'Xóa'}
                  </button>
                </article>
              ))}
            </section>

            <aside className="card card-p cart-summary">
              <span className="eyebrow">Đơn hàng</span>
              <h2>Tạm tính</h2>
              <strong className="summary-total">{money(subtotal)}</strong>
              <Link className="btn btn-primary btn-full" to="/shipping">Thông tin giao hàng</Link>
            </aside>
          </div>
        )}
      </div>
    </main>
  );
}
