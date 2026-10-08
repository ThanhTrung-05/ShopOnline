import { useEffect, useMemo, useState } from 'react';
import { ArrowRight, Minus, Package, Plus, ShoppingBag, Trash } from '@phosphor-icons/react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import CheckoutSteps from '../components/CheckoutSteps';
import { useCartStore } from '../store/cartStore';
import { getApiErrorMessage } from '../utils/apiError';

const money = (value: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);

export default function CartPage() {
  const { items, subtotal, isLoading, error, loadCart, updateItemQuantity, removeItem } = useCartStore();
  const [busyItemId, setBusyItemId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const itemCount = useMemo(() => items.reduce((count, item) => count + item.quantity, 0), [items]);

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
    <main className="page checkout-page">
      <div className="container checkout-container">
        <CheckoutSteps active="cart" />
        <header className="page-heading page-heading--compact checkout-heading">
          <span className="page-context">Bước 1</span>
          <h1>Giỏ hàng</h1>
          <p>Kiểm tra sản phẩm, số lượng và tạm tính trước khi chọn cách giao hàng.</p>
        </header>

        {actionError && <div className="alert alert-error checkout-alert" role="alert">{actionError}</div>}
        {isLoading ? (
          <section className="cart-loading" aria-label="Đang tải giỏ hàng" aria-busy="true">
            {Array.from({ length: 3 }).map((_, index) => (
              <div className="cart-item cart-item-skeleton" key={index} aria-hidden="true">
                <span className="cart-product-mark pulse" />
                <div><span className="skeleton-line pulse" /><span className="skeleton-line skeleton-line-short pulse" /></div>
                <span className="skeleton-button pulse" />
              </div>
            ))}
          </section>
        ) : error && items.length === 0 ? (
          <section className="state-card checkout-state">
            <span className="empty-state-icon" aria-hidden="true"><ShoppingBag size={29} /></span>
            <div><h2>Chưa thể tải giỏ hàng</h2><p role="alert">{error}</p></div>
            <button className="btn btn-primary" type="button" onClick={() => void loadCart()}>Thử lại</button>
          </section>
        ) : items.length === 0 ? (
          <section className="state-card checkout-state">
            <span className="empty-state-icon" aria-hidden="true"><ShoppingBag size={29} /></span>
            <div><h2>Giỏ hàng đang trống</h2><p>Khám phá danh mục và thêm sản phẩm bạn muốn mua.</p></div>
            <Link className="btn btn-primary" to="/products">Xem sản phẩm</Link>
          </section>
        ) : (
          <div className="cart-layout">
            <section className="cart-lines" aria-labelledby="cart-lines-title">
              <div className="cart-lines-heading">
                <h2 id="cart-lines-title">Sản phẩm đã chọn</h2>
                <span>{itemCount} sản phẩm</span>
              </div>
              {error && <div className="alert alert-error" role="alert">{error}</div>}
              <div className="cart-line-list">
                {items.map((item) => (
                  <article className="cart-item" key={item.cartItemId} aria-busy={busyItemId === item.cartItemId}>
                    <Link className="cart-product-mark" to={`/products/${item.productId}`} aria-label={`Xem ${item.productName}`}>
                      <Package size={24} weight="duotone" aria-hidden="true" />
                    </Link>
                    <div className="cart-item-main">
                      <div>
                        <h3><Link to={`/products/${item.productId}`}>{item.productName}</Link></h3>
                        <p>{money(item.unitPrice)} mỗi sản phẩm</p>
                      </div>
                    </div>
                    <div className="cart-item-controls">
                      <span>Số lượng</span>
                      <div className="quantity-control">
                        <button type="button" aria-label={`Giảm số lượng ${item.productName}`} disabled={busyItemId !== null || item.quantity <= 1} onClick={() => void update(item.cartItemId, item.quantity - 1)}><Minus size={15} weight="bold" aria-hidden="true" /></button>
                        <strong aria-live="polite">{item.quantity}</strong>
                        <button type="button" aria-label={`Tăng số lượng ${item.productName}`} disabled={busyItemId !== null} onClick={() => void update(item.cartItemId, item.quantity + 1)}><Plus size={15} weight="bold" aria-hidden="true" /></button>
                      </div>
                    </div>
                    <div className="cart-item-total"><span>Thành tiền</span><strong className="cart-line-total">{money(item.itemSubtotal)}</strong></div>
                    <button className="icon-button cart-remove" type="button" aria-label={`Xóa ${item.productName} khỏi giỏ`} disabled={busyItemId !== null} onClick={() => void remove(item.cartItemId)}>
                      {busyItemId === item.cartItemId ? <span className="spinner spinner-inline" /> : <Trash size={18} aria-hidden="true" />}
                    </button>
                  </article>
                ))}
              </div>
              <Link className="back-link cart-continue" to="/products">Tiếp tục mua sắm</Link>
            </section>

            <aside className="card card-p cart-summary" aria-labelledby="cart-summary-title">
              <span className="page-context">Tóm tắt</span>
              <h2 id="cart-summary-title">Tạm tính giỏ hàng</h2>
              <dl className="cart-summary-lines">
                <div><dt>Sản phẩm</dt><dd>{itemCount}</dd></div>
                <div><dt>Phí giao hàng</dt><dd>Tính ở bước sau</dd></div>
              </dl>
              <div className="cart-summary-total"><span>Tạm tính</span><strong className="summary-total">{money(subtotal)}</strong></div>
              <p>Phí giao hàng phụ thuộc vào địa chỉ và phương thức bạn chọn.</p>
              <Link className="btn btn-primary btn-full" to="/shipping">Tiếp tục giao hàng <ArrowRight size={18} weight="bold" aria-hidden="true" /></Link>
            </aside>
          </div>
        )}
      </div>
    </main>
  );
}
