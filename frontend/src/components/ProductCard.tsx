import { Link } from 'react-router-dom';
import { Package, ShoppingBag } from '@phosphor-icons/react';
import { useCartStore } from '../store/cartStore';
import { useAuth } from '../auth/useAuth';
import toast from 'react-hot-toast';
import type { Product } from '../types';
import { INSUFFICIENT_STOCK_WARNING, isInsufficientStockError } from '../utils/cartErrorMessages';

export type ProductCardVariant = 'featured' | 'compact' | 'standard' | 'landscape';

interface Props {
  product: Product;
  variant?: ProductCardVariant;
}

export default function ProductCard({ product, variant = 'standard' }: Props) {
  const addItem = useCartStore((state) => state.addItem);
  const cartItem = useCartStore((state) => state.items.find((item) => item.productId === product.id));
  const { isAuthenticated, roles } = useAuth();
  const isCustomer = roles.includes('CUSTOMER');
  const showCartActions = !isAuthenticated || isCustomer;

  const handleAddToCart = async (e: React.MouseEvent) => {
    e.preventDefault();
    if (!isAuthenticated) { toast.error('Vui lòng đăng nhập để thêm vào giỏ hàng'); return; }
    if (!isCustomer) { return; }
    if (product.inventoryCount === 0) { toast.error('Sản phẩm tạm hết hàng'); return; }
    try {
      await addItem(product.id, 1);
      toast.success(`Đã thêm "${product.name}" vào giỏ hàng.`);
    } catch (err: any) {
      if (isInsufficientStockError(err)) {
        toast.error(INSUFFICIENT_STOCK_WARNING);
        return;
      }

      const msg = err?.response?.data?.message || '';
      if (msg.toLowerCase().includes('hết hàng') || msg.toLowerCase().includes('không đủ') || msg.toLowerCase().includes('enough')) {
        toast.error('Không thể thêm sản phẩm vào giỏ hàng');
      }
    }
  };

  const isOutOfStock = product.inventoryCount === 0;

  return (
    <article className={`product-card product-card--${variant} fade-in`}>
      <Link
        to={`/products/${product.id}`}
        className="product-card-media-link"
        aria-label={`Xem ${product.name}`}
      >
        <figure className="product-card-media">
          {product.imageUrl ? (
            <img src={product.imageUrl} alt={product.name} loading="lazy" />
          ) : (
            <span className="product-card-placeholder" aria-hidden="true">
              <Package size={42} weight="duotone" />
            </span>
          )}
        </figure>
      </Link>

      <div className="product-card-content">
        <div className="product-card-body">
          <p className="product-card-category">{product.categoryName}</p>
          <h3 className="product-card-title">
            <Link to={`/products/${product.id}`}>{product.name}</Link>
          </h3>

          <div className="product-card-meta">
            <strong className="product-card-price">
              {product.price.toLocaleString('vi-VN')}₫
            </strong>
            <span className={`product-stock ${isOutOfStock ? 'out' : product.inventoryCount < 10 ? 'low' : 'available'}`}>
              {isOutOfStock ? 'Hết hàng' : `Còn ${product.inventoryCount}`}
            </span>
          </div>
        </div>

        {showCartActions && (
          <div className="product-card-actions">
            <button
              className={`btn ${isOutOfStock ? 'btn-ghost' : 'btn-primary'} btn-sm btn-full`}
              onClick={handleAddToCart}
              disabled={isOutOfStock}
            >
              {!isOutOfStock && <ShoppingBag size={17} weight="bold" aria-hidden="true" />}
              {isOutOfStock ? 'Hết hàng' : '+ Thêm vào giỏ'}
            </button>
            {cartItem && (
              <span className="product-in-cart" aria-label={`Số lượng trong giỏ của ${product.name}`}>
                Trong giỏ: {cartItem.quantity}
              </span>
            )}
          </div>
        )}
      </div>
    </article>
  );
}
