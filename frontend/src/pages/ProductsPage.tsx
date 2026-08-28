import { useEffect, useState, useCallback } from 'react';
import { productApi } from '../api/productApi';
import ProductCard from '../components/ProductCard';
import type { Product, PageResponse } from '../types';
import { getApiErrorMessage } from '../utils/apiError';

const CATEGORIES = [
  { code: '', label: '🌟 Tất cả' },
  { code: 'THUC_PHAM', label: '🥩 Thực phẩm' },
  { code: 'DIEN_MAY',  label: '📺 Điện máy' },
  { code: 'SANH_SU',   label: '🏺 Sành sứ' },
];

export default function ProductsPage() {
  const [data, setData]         = useState<PageResponse<Product> | null>(null);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState<string | null>(null);
  const [page, setPage]         = useState(0);
  const [category, setCategory] = useState('');
  const [search, setSearch]     = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [minPrice, setMinPrice] = useState<number | undefined>(undefined);
  const [maxPrice, setMaxPrice] = useState<number | undefined>(undefined);
  const [minPriceInput, setMinPriceInput] = useState('');
  const [maxPriceInput, setMaxPriceInput] = useState('');

  const load = useCallback(async (
    p: number, cat: string, q: string, min?: number, max?: number,
  ) => {
    setLoading(true);
    setError(null);
    try {
      const res = await productApi.list(p, 20, cat || undefined, q || undefined, min, max);
      setData(res.data.data);
    } catch (requestError) {
      setData(null);
      setError(getApiErrorMessage(requestError, 'Không thể tải danh sách sản phẩm.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(page, category, search, minPrice, maxPrice); },
    [page, category, search, minPrice, maxPrice]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setSearch(searchInput);
    setMinPrice(minPriceInput ? Number(minPriceInput) : undefined);
    setMaxPrice(maxPriceInput ? Number(maxPriceInput) : undefined);
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

        {/* Search + price range */}
        <form onSubmit={handleSearch} style={{ display: 'flex', gap: 12, flexWrap: 'wrap', maxWidth: 720, margin: '0 auto 32px', justifyContent: 'center' }}>
          <input className="form-input" placeholder="🔍 Tìm kiếm sản phẩm..."
            value={searchInput} onChange={(e) => setSearchInput(e.target.value)} style={{ flex: '1 1 240px', minWidth: 200 }} />
          <input className="form-input" type="number" min={0} placeholder="Giá từ"
            value={minPriceInput} onChange={(e) => setMinPriceInput(e.target.value)} style={{ width: 120 }} />
          <input className="form-input" type="number" min={0} placeholder="Giá đến"
            value={maxPriceInput} onChange={(e) => setMaxPriceInput(e.target.value)} style={{ width: 120 }} />
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
        {error ? (
          <div className="state-card">
            <div className="alert alert-error" role="alert">{error}</div>
            <button className="btn btn-ghost" type="button" onClick={() => void load(page, category, search, minPrice, maxPrice)}>
              Thử lại
            </button>
          </div>
        ) : loading ? (
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
