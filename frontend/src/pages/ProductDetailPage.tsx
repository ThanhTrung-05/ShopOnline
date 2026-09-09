import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Minus, Package, Plus, ShoppingBag } from '@phosphor-icons/react';
import { productApi, ProductDetail } from '../api/productApi';
import { useCartStore } from '../store/cartStore';
import { useAuth } from '../auth/useAuth';
import toast from 'react-hot-toast';
import { INSUFFICIENT_STOCK_WARNING, isInsufficientStockError } from '../utils/cartErrorMessages';

export default function ProductDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [qty, setQty] = useState(1);
  const [adding, setAdding] = useState(false);
  const { addItem, updateItemQuantity, removeItem } = useCartStore();
  const cartItem = useCartStore((state) => state.items.find((item) => item.productId === Number(id)));
  const { isAuthenticated, roles } = useAuth();
  const isCustomer = roles.includes('CUSTOMER');
  const showCartActions = !isAuthenticated || isCustomer;
  const navigate = useNavigate();

  useEffect(() => {
    productApi.detail(Number(id))
      .then(res => setProduct(res.data.data))
      .catch(() => navigate('/products'))
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => {
    setQty(cartItem?.quantity ?? 1);
  }, [cartItem?.quantity]);

  const displayedQty = cartItem?.quantity ?? qty;

  const showCartError = (err: unknown) => {
    if (isInsufficientStockError(err)) {
      toast.error(INSUFFICIENT_STOCK_WARNING);
      return true;
    }
    return false;
  };

  const handleAdd = async () => {
    if (!isAuthenticated) { toast.error('Vui lòng đăng nhập'); return; }
    if (!isCustomer) { return; }
    setAdding(true);
    try {
      if (cartItem) {
        await updateItemQuantity(cartItem.cartItemId, displayedQty);
      } else {
        await addItem(product!.id, displayedQty);
      }
      toast.success(`Đã thêm ${displayedQty} sản phẩm vào giỏ hàng.`);
    } catch (err) {
      showCartError(err);
    } finally { setAdding(false); }
  };

  const handleDecrease = async () => {
    if (cartItem) {
      if (cartItem.quantity <= 1) {
        await removeItem(cartItem.cartItemId);
      } else {
        await updateItemQuantity(cartItem.cartItemId, cartItem.quantity - 1);
      }
      return;
    }

    setQty(q => Math.max(1, q - 1));
  };

  const handleIncrease = async () => {
    if (!product) return;

    if (cartItem) {
      try {
        await updateItemQuantity(cartItem.cartItemId, Math.min(product.inventoryCount, cartItem.quantity + 1));
      } catch (err) {
        showCartError(err);
      }
      return;
    }

    setQty(q => Math.min(product.inventoryCount, q + 1));
  };

  if (loading) return (
    <main className="page product-detail-page">
      <div className="container product-detail-container">
        <div className="product-detail-skeleton" aria-label="Đang tải sản phẩm" aria-busy="true">
          <div className="skeleton-media pulse" />
          <div className="skeleton-copy">
            <span className="skeleton-line skeleton-line-short pulse" />
            <span className="skeleton-line skeleton-line-title pulse" />
            <span className="skeleton-line pulse" />
            <span className="skeleton-line pulse" />
          </div>
        </div>
      </div>
    </main>
  );
  if (!product) return null;

  const isOutOfStock = product.inventoryCount === 0;

  return (
    <main className="page product-detail-page">
      <div className="container product-detail-container">
        <button className="back-button" type="button" onClick={() => navigate(-1)}>
          <ArrowLeft size={18} weight="bold" aria-hidden="true" />
          Quay lại
        </button>

        <div className="product-detail-layout">
          <section className="product-detail-media" aria-label={`Hình ảnh ${product.name}`}>
            {product.imageUrl ? (
              <img src={product.imageUrl} alt={product.name} />
            ) : (
              <span className="product-detail-placeholder" aria-hidden="true">
                <Package size={72} weight="duotone" />
              </span>
            )}
          </section>

          <article className="product-detail-panel fade-in">
            <header className="product-detail-heading">
              <p className="product-detail-category">{product.categoryName}</p>
              <h1>{product.name}</h1>
            </header>

            <dl className="product-pricing">
              <div>
                <dt>Đơn giá (chưa VAT)</dt>
                <dd>{product.price.toLocaleString('vi-VN')}₫</dd>
              </div>
              <div>
                <dt>Thuế VAT ({product.vatRate}%)</dt>
                <dd>{product.vatAmount.toLocaleString('vi-VN')}₫</dd>
              </div>
              <div className="product-price-total">
                <dt>Giá đã bao gồm VAT</dt>
                <dd>{product.priceIncludingVat.toLocaleString('vi-VN')}₫</dd>
              </div>
            </dl>

            <p className={`product-availability ${isOutOfStock ? 'out' : product.inventoryCount < 10 ? 'low' : ''}`}>
              {isOutOfStock ? 'Hết hàng' : `Còn ${product.inventoryCount} sản phẩm`}
            </p>

            {product.description && (
              <div className="product-description" dangerouslySetInnerHTML={{ __html: product.description }} />
            )}

            {showCartActions && !isOutOfStock && (
              <div className="product-purchase-row">
                <span className="quantity-label">Số lượng</span>
                <div className="quantity-control product-detail-quantity">
                  <button type="button" aria-label="−" onClick={handleDecrease}>
                    <Minus size={16} weight="bold" aria-hidden="true" />
                  </button>
                  <strong aria-label={`Số lượng trong giỏ của ${product.name}`}>{displayedQty}</strong>
                  <button type="button" aria-label="+" onClick={handleIncrease}>
                    <Plus size={16} weight="bold" aria-hidden="true" />
                  </button>
                </div>
              </div>
            )}

            {showCartActions && (
              <button
                className={`btn btn-lg btn-full ${isOutOfStock ? 'btn-ghost' : 'btn-primary'}`}
                onClick={handleAdd}
                disabled={isOutOfStock || adding}
              >
                {adding ? (
                  <><span className="spinner spinner-inline" /> Đang thêm...</>
                ) : isOutOfStock ? (
                  'Hết hàng'
                ) : (
                  <><ShoppingBag size={20} weight="bold" aria-hidden="true" /> Thêm vào giỏ - {(product.priceIncludingVat * displayedQty).toLocaleString('vi-VN')}₫</>
                )}
              </button>
            )}
          </article>
        </div>
      </div>
    </main>
  );
}
