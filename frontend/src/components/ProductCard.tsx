import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useCartStore } from '../store/cartStore';
import { useAuthStore } from '../store/authStore';
import toast from 'react-hot-toast';
import type { Product } from '../types';

interface Props { product: Product; }

export default function ProductCard({ product }: Props) {
  const { addItem } = useCartStore();
  const { isAuthenticated } = useAuthStore();
  const [localInventory, setLocalInventory] = useState(product.inventoryCount);

  useEffect(() => {
    setLocalInventory(product.inventoryCount);
  }, [product.inventoryCount]);

  const handleAddToCart = async (e: React.MouseEvent) => {
    e.preventDefault();
    if (!isAuthenticated) { toast.error('Vui lòng đăng nhập để thêm vào giỏ hàng'); return; }
    if (localInventory === 0) { toast.error('Sản phẩm tạm hết hàng'); return; }
    try {
      await addItem(product.id, 1);
      toast.success(`Đã thêm "${product.name}" vào giỏ hàng! 🛍️`);
      setLocalInventory(prev => Math.max(0, prev - 1));
    } catch (err: any) { 
      const msg = err?.response?.data?.message || '';
      if (msg.toLowerCase().includes('hết hàng') || msg.toLowerCase().includes('không đủ') || msg.toLowerCase().includes('enough')) {
        setLocalInventory(0);
      }
    }
  };

  const isOutOfStock = localInventory === 0;

  return (
    <Link to={`/products/${product.id}`} className="fade-in">
      <div className="card" style={{ overflow: 'hidden', cursor: 'pointer', height: '100%', display: 'flex', flexDirection: 'column' }}>
        {/* Image */}
        <div style={{
          aspectRatio: '4/3',
          background: 'linear-gradient(135deg, rgba(99,102,241,0.1), rgba(139,92,246,0.05))',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: '3.5rem', position: 'relative', overflow: 'hidden',
        }}>
          {product.imageUrl
            ? <img src={product.imageUrl} alt={product.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            : <span>🛒</span>
          }
          {isOutOfStock && (
            <div style={{
              position: 'absolute', inset: 0,
              background: 'rgba(0,0,0,0.65)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              backdropFilter: 'blur(2px)',
            }}>
              <span style={{
                fontSize: '0.85rem', fontWeight: 800, color: '#fff',
                background: 'linear-gradient(135deg, #ef4444, #dc2626)',
                padding: '6px 16px', borderRadius: 8,
                boxShadow: '0 4px 12px rgba(239,68,68,0.5)',
                letterSpacing: '0.05em',
              }}>🚫 HẾT HÀNG</span>
            </div>
          )}
        </div>

        {/* Content */}
        <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 8, flex: 1 }}>
          <p style={{ fontSize: '0.75rem', color: 'var(--accent)', fontWeight: 600 }}>{product.categoryName}</p>
          <h3 style={{ fontSize: '0.9rem', fontWeight: 600, lineHeight: 1.4, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
            {product.name}
          </h3>

          <div style={{ marginTop: 'auto', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{
              fontWeight: 800, fontSize: '1.1rem',
              color: 'var(--success)',
            }}>
              {product.price.toLocaleString('vi-VN')}₫
            </span>
            <span style={{ 
              fontSize: '0.7rem', 
              fontWeight: 700,
              padding: '2px 8px',
              borderRadius: '12px',
              background: isOutOfStock ? 'rgba(239, 68, 68, 0.15)' : localInventory < 10 ? 'rgba(245, 158, 11, 0.15)' : 'rgba(16, 185, 129, 0.15)',
              color: isOutOfStock ? 'var(--danger)' : localInventory < 10 ? 'var(--warning)' : 'var(--success)',
              border: `1px solid ${isOutOfStock ? 'rgba(239, 68, 68, 0.3)' : localInventory < 10 ? 'rgba(245, 158, 11, 0.3)' : 'rgba(16, 185, 129, 0.3)'}`
            }}>
              {isOutOfStock ? 'Hết hàng' : `Còn ${localInventory}`}
            </span>
          </div>

          <button
            className={`btn ${isOutOfStock ? 'btn-ghost' : 'btn-primary'} btn-sm btn-full`}
            onClick={handleAddToCart}
            disabled={isOutOfStock}
            style={{ marginTop: 8 }}
          >
            {isOutOfStock ? 'Hết hàng' : '+ Thêm vào giỏ'}
          </button>
        </div>
      </div>
    </Link>
  );
}
