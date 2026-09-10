import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  CheckCircle,
  Minus,
  Plus,
  ShoppingBag,
  Truck,
  WarningCircle,
} from '@phosphor-icons/react';
import toast from 'react-hot-toast';
import { productApi, type ProductDetail } from '../api/productApi';
import { useAuth } from '../auth/useAuth';
import ProductImage from '../components/ProductImage';
import { useCartStore } from '../store/cartStore';
import { getApiErrorMessage } from '../utils/apiError';
import { INSUFFICIENT_STOCK_WARNING, isInsufficientStockError } from '../utils/cartErrorMessages';

export default function ProductDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const productId = Number(id);
  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [qty, setQty] = useState(1);
  const [adding, setAdding] = useState(false);
  const { addItem, updateItemQuantity, removeItem } = useCartStore();
  const cartItem = useCartStore((state) => state.items.find((item) => item.productId === productId));
  const { isAuthenticated, roles } = useAuth();
  const isCustomer = roles.includes('CUSTOMER');
  const showCartActions = !isAuthenticated || isCustomer;

  const loadProduct = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    setProduct(null);
    if (!Number.isInteger(productId) || productId < 1) {
      setLoadError('Đường dẫn sản phẩm không hợp lệ.');
      setLoading(false);
      return;
    }

    try {
      const response = await productApi.detail(productId);
      setProduct(response.data.data);
    } catch (requestError) {
      setLoadError(getApiErrorMessage(requestError, 'Không thể tải thông tin sản phẩm.'));
    } finally {
      setLoading(false);
    }
  }, [productId]);

  useEffect(() => {
    void loadProduct();
  }, [loadProduct]);

  useEffect(() => {
    setQty(cartItem?.quantity ?? 1);
  }, [cartItem?.quantity]);

  const displayedQty = cartItem?.quantity ?? qty;

  const showCartError = (error: unknown) => {
    if (isInsufficientStockError(error)) {
      toast.error(INSUFFICIENT_STOCK_WARNING);
      return true;
    }
    return false;
  };

  const handleAdd = async () => {
    if (!isAuthenticated) {
      toast.error('Vui lòng đăng nhập');
      return;
    }
    if (!isCustomer || !product) return;

    setAdding(true);
    try {
      if (cartItem) await updateItemQuantity(cartItem.cartItemId, displayedQty);
      else await addItem(product.id, displayedQty);
      toast.success(cartItem ? 'Đã cập nhật giỏ hàng.' : `Đã thêm ${displayedQty} sản phẩm vào giỏ hàng.`);
    } catch (error) {
      showCartError(error);
    } finally {
      setAdding(false);
    }
  };

  const handleDecrease = async () => {
    if (cartItem) {
      if (cartItem.quantity <= 1) await removeItem(cartItem.cartItemId);
      else await updateItemQuantity(cartItem.cartItemId, cartItem.quantity - 1);
      return;
    }
    setQty((current) => Math.max(1, current - 1));
  };

  const handleIncrease = async () => {
    if (!product) return;
    if (cartItem) {
      try {
        await updateItemQuantity(cartItem.cartItemId, Math.min(product.inventoryCount, cartItem.quantity + 1));
      } catch (error) {
        showCartError(error);
      }
      return;
    }
    setQty((current) => Math.min(product.inventoryCount, current + 1));
  };

  if (loading) {
    return (
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
  }

  if (!product) {
    return (
      <main className="page product-detail-page">
        <div className="container narrow-container">
          <section className="state-card product-detail-error">
            <span className="catalog-state-icon" aria-hidden="true"><WarningCircle size={34} weight="duotone" /></span>
            <div><h1>Chưa thể mở sản phẩm</h1><p role="alert">{loadError}</p></div>
            <div className="button-row">
              <button className="btn btn-primary" type="button" onClick={() => void loadProduct()}>Thử lại</button>
              <Link className="btn btn-ghost" to="/products">Về danh sách sản phẩm</Link>
            </div>
          </section>
        </div>
      </main>
    );
  }

  const isOutOfStock = product.inventoryCount === 0;

  return (
    <main className="page product-detail-page">
      <div className="container product-detail-container">
        <nav className="breadcrumb" aria-label="Đường dẫn">
          <Link to="/products">Sản phẩm</Link><span aria-hidden="true">/</span><span>{product.categoryName}</span>
        </nav>
        <button className="back-button" type="button" onClick={() => navigate(-1)}>
          <ArrowLeft size={18} weight="bold" aria-hidden="true" /> Quay lại
        </button>

        <div className="product-detail-layout">
          <section className="product-detail-gallery" aria-label={`Hình ảnh ${product.name}`}>
            <figure className="product-detail-media">
              <ProductImage src={product.imageUrl} alt={product.name} loading="eager" />
            </figure>
            <p>Ảnh sản phẩm được cung cấp trong danh mục ShopOnline.</p>
          </section>

          <article className="product-detail-panel">
            <header className="product-detail-heading">
              <p className="product-detail-category">{product.categoryName}</p>
              <h1>{product.name}</h1>
              <p className={`product-availability ${isOutOfStock ? 'out' : product.inventoryCount < 10 ? 'low' : ''}`}>
                {isOutOfStock ? 'Tạm hết hàng' : `Còn ${product.inventoryCount} sản phẩm`}
              </p>
            </header>

            <div className="product-price-lead">
              <span>Giá đã bao gồm VAT</span>
              <strong>{product.priceIncludingVat.toLocaleString('vi-VN')}₫</strong>
            </div>

            <dl className="product-pricing">
              <div><dt>Giá trước VAT</dt><dd>{product.price.toLocaleString('vi-VN')}₫</dd></div>
              <div><dt>VAT {product.vatRate}%</dt><dd>{product.vatAmount.toLocaleString('vi-VN')}₫</dd></div>
            </dl>

            {product.description && (
              <section className="product-description-section" aria-labelledby="product-description-title">
                <h2 id="product-description-title">Thông tin sản phẩm</h2>
                <div className="product-description" dangerouslySetInnerHTML={{ __html: product.description }} />
              </section>
            )}

            <ul className="product-service-notes">
              <li><Truck size={20} aria-hidden="true" /><span><strong>Phí giao hàng theo địa chỉ</strong><small>Chọn địa chỉ và phương thức ở bước giao hàng.</small></span></li>
              <li><CheckCircle size={20} aria-hidden="true" /><span><strong>Kiểm tra tồn kho khi thêm</strong><small>Số lượng khả dụng được xác nhận bởi giỏ hàng.</small></span></li>
            </ul>

            {showCartActions && (
              <section className="product-buy-box" aria-label="Thêm sản phẩm vào giỏ">
                {!isOutOfStock && (
                  <div className="product-purchase-row">
                    <span className="quantity-label">Số lượng</span>
                    <div className="quantity-control product-detail-quantity">
                      <button type="button" aria-label="−" disabled={adding} onClick={() => void handleDecrease()}><Minus size={16} weight="bold" aria-hidden="true" /></button>
                      <strong aria-live="polite" aria-label={`Số lượng trong giỏ của ${product.name}`}>{displayedQty}</strong>
                      <button type="button" aria-label="+" disabled={adding || displayedQty >= product.inventoryCount} onClick={() => void handleIncrease()}><Plus size={16} weight="bold" aria-hidden="true" /></button>
                    </div>
                  </div>
                )}
                <button
                  className={`btn btn-lg btn-full ${isOutOfStock ? 'btn-ghost' : 'btn-primary'}`}
                  type="button"
                  onClick={() => void handleAdd()}
                  disabled={isOutOfStock || adding}
                >
                  {adding ? <><span className="spinner spinner-inline" /> Đang cập nhật...</> : isOutOfStock ? 'Tạm hết hàng' : <><ShoppingBag size={20} weight="bold" aria-hidden="true" /> {cartItem ? 'Cập nhật giỏ hàng' : 'Thêm vào giỏ'} · {(product.priceIncludingVat * displayedQty).toLocaleString('vi-VN')}₫</>}
                </button>
              </section>
            )}
          </article>
        </div>
      </div>
    </main>
  );
}
