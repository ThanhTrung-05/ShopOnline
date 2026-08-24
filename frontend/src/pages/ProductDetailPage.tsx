import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { productApi, ProductDetail } from '../api/productApi';
import { useCartStore } from '../store/cartStore';
import { useAuthStore } from '../store/authStore';
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
  const { isAuthenticated } = useAuthStore();
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
    setAdding(true);
    try {
      if (cartItem) {
        await updateItemQuantity(cartItem.cartItemId, displayedQty);
      } else {
        await addItem(product!.id, displayedQty);
      }
      toast.success(`Đã thêm ${displayedQty} sản phẩm vào giỏ! 🛍️`);
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
    <div className="page"><div className="container">
      <div className="card pulse" style={{ height: 400 }} />
    </div></div>
  );
  if (!product) return null;

  const isOutOfStock = product.inventoryCount === 0;

  return (
    <div className="page">
      <div className="container">
        <button className="btn btn-ghost btn-sm" onClick={() => navigate(-1)} style={{ marginBottom: 24 }}>← Quay lại</button>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 40, alignItems: 'start' }}>
          {/* Image */}
          <div className="card" style={{ aspectRatio: '1', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '6rem', background: 'linear-gradient(135deg,rgba(99,102,241,0.08),rgba(139,92,246,0.04))' }}>
            {product.imageUrl
              ? <img src={product.imageUrl} alt={product.name} style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: 16 }} />
              : <span>🛒</span>
            }
          </div>

          {/* Info */}
          <div className="card card-p fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div>
              <p style={{ color: 'var(--accent)', fontSize: '0.875rem', fontWeight: 600, marginBottom: 8 }}>{product.categoryName}</p>
              <h1 style={{ fontSize: '1.5rem', fontWeight: 800 }}>{product.name}</h1>
            </div>
            
            {/* Pricing Details */}
            <div style={{ padding: '16px', background: 'rgba(99, 102, 241, 0.05)', borderRadius: '12px', border: '1px solid rgba(99, 102, 241, 0.1)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', color: 'var(--text-secondary)' }}>
                <span>Đơn giá (chưa VAT):</span>
                <span>{product.price.toLocaleString('vi-VN')}₫</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', color: 'var(--text-secondary)' }}>
                <span>Thuế VAT ({product.vatRate}%):</span>
                <span>{product.vatAmount.toLocaleString('vi-VN')}₫</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '12px', paddingTop: '12px', borderTop: '1px dashed rgba(99, 102, 241, 0.2)' }}>
                <span style={{ fontWeight: 600 }}>Giá đã bao gồm VAT:</span>
                <span style={{ fontSize: '1.75rem', fontWeight: 800, background: 'linear-gradient(135deg,#6366f1,#a78bfa)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                  {product.priceIncludingVat.toLocaleString('vi-VN')}₫
                </span>
              </div>
            </div>

            <p style={{ color: product.inventoryCount < 10 ? 'var(--warning)' : 'var(--text-secondary)', fontSize: '0.9rem' }}>
              {isOutOfStock ? '❌ Hết hàng' : `✅ Còn ${product.inventoryCount} sản phẩm`}
            </p>

            {/* Description */}
            {product.description && (
              <div 
                style={{ fontSize: '0.95rem', color: 'var(--text-secondary)', lineHeight: 1.6, marginTop: 16 }}
                dangerouslySetInnerHTML={{ __html: product.description }} 
              />
            )}

            {/* Quantity */}
            {!isOutOfStock && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <span style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>Số lượng:</span>
                <div style={{ display: 'flex', alignItems: 'center', gap: 0, background: 'var(--bg-card)', borderRadius: 8, border: '1px solid var(--border)' }}>
                  <button className="btn btn-ghost btn-sm" onClick={handleDecrease} style={{ border: 'none' }}>−</button>
                  <span aria-label={`cart quantity for ${product.name}`} style={{ padding: '0 16px', fontWeight: 700 }}>{displayedQty}</span>
                  <button className="btn btn-ghost btn-sm" onClick={handleIncrease} style={{ border: 'none' }}>+</button>
                </div>
              </div>
            )}

            <button className={`btn btn-lg btn-full ${isOutOfStock ? 'btn-ghost' : 'btn-primary'}`}
              onClick={handleAdd} disabled={isOutOfStock || adding}>
              {adding ? <><span className="spinner" style={{ width: 18, height: 18 }} /> Đang thêm...</> : isOutOfStock ? 'Hết hàng' : `🛍️ Thêm vào giỏ — ${(product.priceIncludingVat * displayedQty).toLocaleString('vi-VN')}₫`}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
