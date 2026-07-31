import { useEffect, useState, useCallback } from 'react';
import { productApi } from '../api/productApi';
import ProductCard from '../components/ProductCard';
import type { Product, PageResponse } from '../types';

const CATEGORIES = [
  { code: '', label: '🌟 Tất cả' },
  { code: 'THUC_PHAM', label: '🥩 Thực phẩm' },
  { code: 'DIEN_MAY',  label: '📺 Điện máy' },
  { code: 'SANH_SU',   label: '🏺 Sành sứ' },
];

export default function ProductsPage() {
  const [data, setData]         = useState<PageResponse<Product> | null>(null);
  const [loading, setLoading]   = useState(true);
  const [page, setPage]         = useState(0);
  const [category, setCategory] = useState('');
  const [search, setSearch]     = useState('');
  const [searchInput, setSearchInput] = useState('');

  const load = useCallback(async (p: number, cat: string, q: string) => {
    setLoading(true);
    try {
      const res = await productApi.list(p, 20, cat || undefined, q || undefined);
      setData(res.data.data);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(page, category, search); }, [page, category, search]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setSearch(searchInput);
  };

  return (
    <div className="page">
      <div className="container">
        {/* Hero */}
        <div style={{ textAlign: 'center', marginBottom: 24, paddingTop: 8 }}>
          <h1 style={{ fontSize: '2rem', fontWeight: 800, marginBottom: 8, lineHeight: 1.2 }}>
            Siêu thị <span style={{ background: 'linear-gradient(135deg,#6366f1,#a78bfa)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>TrựcTuyến</span>
          </h1>
          <p style={{ color: 'var(--text-secondary)', maxWidth: 480, margin: '0 auto', fontSize: '0.9rem' }}>Hàng nghìn sản phẩm chính hãng, giao hàng nhanh, giá tốt nhất</p>
        </div>

        {/* Search */}
        <form onSubmit={handleSearch} style={{ display: 'flex', gap: 12, maxWidth: 560, margin: '0 auto 32px' }}>
          <input className="form-input" placeholder="🔍 Tìm kiếm sản phẩm..."
            value={searchInput} onChange={(e) => setSearchInput(e.target.value)} style={{ flex: 1 }} />
          <button type="submit" className="btn btn-primary">Tìm</button>
        </form>

        {/* Category Filter */}
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 28, justifyContent: 'center' }}>
          {CATEGORIES.map((cat) => (
            <button key={cat.code}
              className={`btn btn-sm ${category === cat.code ? 'btn-primary' : 'btn-ghost'}`}
              onClick={() => { setCategory(cat.code); setPage(0); }}>
              {cat.label}
            </button>
          ))}
        </div>

        {/* Products Grid */}
        {loading ? (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(240px,1fr))', gap: 20 }}>
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="card pulse" style={{ height: 340 }} />
            ))}
          </div>
        ) : data && data.content.length > 0 ? (
          <>
            <div className="product-grid">
              {data.content.map((p) => <ProductCard key={p.id} product={p} />)}
            </div>

            {/* Pagination */}
            {data.totalPages > 1 && (
              <div style={{ display: 'flex', justifyContent: 'center', gap: 8, marginTop: 40 }}>
                <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>← Trước</button>
                <span style={{ display: 'flex', alignItems: 'center', color: 'var(--text-secondary)', fontSize: '0.875rem', padding: '0 12px' }}>
                  Trang {page + 1} / {data.totalPages}
                </span>
                <button className="btn btn-ghost btn-sm" disabled={data.last} onClick={() => setPage(p => p + 1)}>Sau →</button>
              </div>
            )}
          </>
        ) : (
          <div className="empty-state">
            <span style={{ fontSize: '4rem' }}>🔍</span>
            <h3>Không tìm thấy sản phẩm</h3>
            <p>Thử thay đổi từ khóa hoặc danh mục</p>
          </div>
        )}
      </div>
    </div>
  );
}
