import { useEffect, useState, useCallback } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  FunnelSimple,
  MagnifyingGlass,
  WarningCircle,
} from '@phosphor-icons/react';
import { productApi } from '../api/productApi';
import ProductCard, { type ProductCardVariant } from '../components/ProductCard';
import type { Product, PageResponse } from '../types';
import { getApiErrorMessage } from '../utils/apiError';

const CATEGORIES = [
  { code: '', label: 'Tất cả' },
  { code: 'THUC_PHAM', label: 'Thực phẩm' },
  { code: 'DIEN_MAY', label: 'Điện máy' },
  { code: 'SANH_SU', label: 'Sành sứ' },
];

const productCardVariant = (index: number): ProductCardVariant => {
  if (index === 0) return 'featured';
  if (index === 1 || index === 2) return 'compact';
  if (index > 3 && index % 7 === 0) return 'landscape';
  return 'standard';
};

function ProductCardSkeleton({ variant }: { variant: ProductCardVariant }) {
  return (
    <article className={`product-card product-card--${variant} product-card-skeleton`} aria-hidden="true">
      <span className="product-card-media skeleton-block pulse" />
      <div className="product-card-content">
        <div className="product-card-body skeleton-copy">
          <span className="skeleton-line skeleton-line-short pulse" />
          <span className="skeleton-line skeleton-line-title pulse" />
          <span className="skeleton-line skeleton-line-price pulse" />
        </div>
        <span className="skeleton-button pulse" />
      </div>
    </article>
  );
}

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

  const activeCategoryLabel = CATEGORIES.find((item) => item.code === category)?.label ?? CATEGORIES[0].label;

  return (
    <main className="page catalog-page">
      <div className="container catalog-container">
        <section className="catalog-hero" aria-labelledby="catalog-title">
          <div className="catalog-hero-copy">
            <p className="catalog-kicker">Mua sắm mỗi ngày</p>
            <h1 id="catalog-title"><span>Siêu thị</span> <strong>TrựcTuyến</strong></h1>
            <p>Hàng nghìn sản phẩm chính hãng, giao hàng nhanh, giá tốt nhất</p>
          </div>

          <figure className="catalog-hero-media">
            <img
              src="/shoponline-market-hero.jpg"
              alt="Túi rau củ, bát sứ và ấm điện trên mặt bàn sáng"
              width="960"
              height="1200"
              decoding="async"
            />
          </figure>
        </section>

        <section className="catalog-workspace" aria-label="Danh mục sản phẩm">
          <aside className="catalog-filters" aria-labelledby="catalog-filter-title">
            <header className="catalog-filter-heading">
              <span className="catalog-filter-icon" aria-hidden="true">
                <FunnelSimple size={21} weight="bold" />
              </span>
              <div>
                <h2 id="catalog-filter-title">Bộ lọc</h2>
                <p>Tìm theo tên, mức giá hoặc danh mục.</p>
              </div>
            </header>

            <form className="catalog-filter-form" onSubmit={handleSearch} aria-label="Tìm và lọc sản phẩm">
              <label className="field catalog-search-field">
                <span>Tìm sản phẩm</span>
                <span className="input-with-icon">
                  <MagnifyingGlass size={19} weight="bold" aria-hidden="true" />
                  <input
                    className="form-input"
                    placeholder="Tên sản phẩm"
                    value={searchInput}
                    onChange={(event) => setSearchInput(event.target.value)}
                  />
                </span>
              </label>

              <fieldset className="catalog-filter-group catalog-price-filter">
                <legend>Khoảng giá</legend>
                <div className="catalog-price-inputs">
                  <label className="field">
                    <span>Giá từ</span>
                    <input
                      className="form-input"
                      type="number"
                      min={0}
                      placeholder="0 ₫"
                      value={minPriceInput}
                      onChange={(event) => setMinPriceInput(event.target.value)}
                    />
                  </label>
                  <label className="field">
                    <span>Giá đến</span>
                    <input
                      className="form-input"
                      type="number"
                      min={0}
                      placeholder="Không giới hạn"
                      value={maxPriceInput}
                      onChange={(event) => setMaxPriceInput(event.target.value)}
                    />
                  </label>
                </div>
              </fieldset>

              <fieldset className="catalog-filter-group catalog-category-filter">
                <legend>Danh mục</legend>
                <div className="category-filter">
                  {CATEGORIES.map((cat) => (
                    <button
                      key={cat.code}
                      type="button"
                      className={category === cat.code ? 'category-filter-button active' : 'category-filter-button'}
                      aria-pressed={category === cat.code}
                      onClick={() => { setCategory(cat.code); setPage(0); }}
                    >
                      <span>{cat.label}</span>
                      <span className="category-filter-indicator" aria-hidden="true" />
                    </button>
                  ))}
                </div>
              </fieldset>

              <button type="submit" className="btn btn-primary catalog-search-submit">
                <MagnifyingGlass size={18} weight="bold" aria-hidden="true" />
                Tìm kiếm
              </button>
            </form>
          </aside>

          <div className="catalog-results">
            <header className="catalog-results-header">
              <div>
                <h2 id="catalog-results-title">{activeCategoryLabel}</h2>
                {search && <p>Kết quả cho “{search}”</p>}
              </div>
              {!loading && data && (
                <p className="catalog-count" aria-live="polite">{data.totalElements} sản phẩm</p>
              )}
            </header>

            {error ? (
              <div className="catalog-state catalog-error-state">
                <span className="catalog-state-icon" aria-hidden="true">
                  <WarningCircle size={32} weight="duotone" />
                </span>
                <div>
                  <h3>Chưa thể tải sản phẩm</h3>
                  <p role="alert">{error}</p>
                </div>
                <button className="btn btn-ghost" type="button" onClick={() => void load(page, category, search, minPrice, maxPrice)}>
                  Thử lại
                </button>
              </div>
            ) : loading ? (
              <div className="product-grid product-grid-loading" aria-label="Đang tải sản phẩm" aria-busy="true">
                {Array.from({ length: 8 }).map((_, index) => (
                  <ProductCardSkeleton key={index} variant={productCardVariant(index)} />
                ))}
              </div>
            ) : data && data.content.length > 0 ? (
              <>
                <div className="product-grid" aria-labelledby="catalog-results-title">
                  {data.content.map((product, index) => (
                    <ProductCard
                      key={product.id}
                      product={product}
                      variant={productCardVariant(index)}
                    />
                  ))}
                </div>

                {data.totalPages > 1 && (
                  <nav className="catalog-pagination" aria-label="Phân trang sản phẩm">
                    <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
                      <ArrowLeft size={17} weight="bold" aria-hidden="true" /> Trước
                    </button>
                    <span>
                      Trang {page + 1} / {data.totalPages}
                    </span>
                    <button className="btn btn-ghost btn-sm" disabled={data.last} onClick={() => setPage(p => p + 1)}>
                      Sau <ArrowRight size={17} weight="bold" aria-hidden="true" />
                    </button>
                  </nav>
                )}
              </>
            ) : (
              <div className="catalog-state catalog-empty-state">
                <span className="catalog-state-icon" aria-hidden="true">
                  <MagnifyingGlass size={34} weight="duotone" />
                </span>
                <div>
                  <h3>Không tìm thấy sản phẩm</h3>
                  <p>Thử thay đổi từ khóa, khoảng giá hoặc danh mục.</p>
                </div>
              </div>
            )}
          </div>
        </section>
      </div>
    </main>
  );
}
