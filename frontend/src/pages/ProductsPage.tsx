import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  FunnelSimple,
  MagnifyingGlass,
  SlidersHorizontal,
  WarningCircle,
  X,
} from '@phosphor-icons/react';
import { useSearchParams } from 'react-router-dom';
import { categoryApi, type Category } from '../api/categoryApi';
import { productApi } from '../api/productApi';
import ProductCard from '../components/ProductCard';
import type { Product, PageResponse } from '../types';
import { getApiErrorMessage } from '../utils/apiError';

const PAGE_SIZE = 20;

function readPositiveNumber(params: URLSearchParams, key: string): number | undefined {
  const raw = params.get(key);
  if (!raw) return undefined;
  const value = Number(raw);
  return Number.isFinite(value) && value >= 0 ? value : undefined;
}

function readCategoryId(params: URLSearchParams): number | undefined {
  const value = Number(params.get('categoryId'));
  return Number.isInteger(value) && value > 0 ? value : undefined;
}

function readPage(params: URLSearchParams): number {
  const value = Number(params.get('page'));
  return Number.isInteger(value) && value > 0 ? value - 1 : 0;
}

function ProductCardSkeleton() {
  return (
    <article className="product-card product-card--standard product-card-skeleton" aria-hidden="true">
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
  const [searchParams, setSearchParams] = useSearchParams();
  const [data, setData] = useState<PageResponse<Product> | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [categoriesLoading, setCategoriesLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [categoryError, setCategoryError] = useState<string | null>(null);
  const [filterError, setFilterError] = useState<string | null>(null);
  const [filtersOpen, setFiltersOpen] = useState(false);

  const page = readPage(searchParams);
  const categoryId = readCategoryId(searchParams);
  const search = searchParams.get('search')?.trim() ?? '';
  const minPrice = readPositiveNumber(searchParams, 'minPrice');
  const maxPrice = readPositiveNumber(searchParams, 'maxPrice');
  const [searchInput, setSearchInput] = useState(search);
  const [minPriceInput, setMinPriceInput] = useState(minPrice?.toString() ?? '');
  const [maxPriceInput, setMaxPriceInput] = useState(maxPrice?.toString() ?? '');

  const activeCategories = useMemo(
    () => categories.filter((category) => category.status === 'ACTIVE'),
    [categories],
  );
  const activeCategoryLabel = activeCategories.find((category) => category.categoryId === categoryId)?.categoryName
    ?? 'Tất cả sản phẩm';
  const hasFilters = Boolean(categoryId || search || minPrice !== undefined || maxPrice !== undefined);

  useEffect(() => {
    setSearchInput(search);
    setMinPriceInput(minPrice?.toString() ?? '');
    setMaxPriceInput(maxPrice?.toString() ?? '');
  }, [search, minPrice, maxPrice]);

  const loadCategories = useCallback(async () => {
    setCategoriesLoading(true);
    setCategoryError(null);
    try {
      const response = await categoryApi.list();
      setCategories(response.data.data ?? []);
    } catch (requestError) {
      setCategories([]);
      setCategoryError(getApiErrorMessage(requestError, 'Chưa thể tải danh mục.'));
    } finally {
      setCategoriesLoading(false);
    }
  }, []);

  const loadProducts = useCallback(async (
    requestedPage: number,
    requestedCategoryId: number | undefined,
    query: string,
    minimum?: number,
    maximum?: number,
  ) => {
    setLoading(true);
    setError(null);
    try {
      const response = await productApi.list(
        requestedPage,
        PAGE_SIZE,
        requestedCategoryId,
        query || undefined,
        minimum,
        maximum,
      );
      setData(response.data.data);
    } catch (requestError) {
      setData(null);
      setError(getApiErrorMessage(requestError, 'Không thể tải danh sách sản phẩm.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadCategories();
  }, [loadCategories]);

  useEffect(() => {
    void loadProducts(page, categoryId, search, minPrice, maxPrice);
  }, [page, categoryId, search, minPrice, maxPrice, loadProducts]);

  const updateCategory = (nextCategoryId?: number) => {
    const next = new URLSearchParams(searchParams);
    if (nextCategoryId) next.set('categoryId', nextCategoryId.toString());
    else next.delete('categoryId');
    next.delete('page');
    setSearchParams(next);
  };

  const submitFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const nextMinimum = minPriceInput === '' ? undefined : Number(minPriceInput);
    const nextMaximum = maxPriceInput === '' ? undefined : Number(maxPriceInput);
    if (nextMinimum !== undefined && nextMaximum !== undefined && nextMinimum > nextMaximum) {
      setFilterError('Giá tối thiểu không thể lớn hơn giá tối đa.');
      return;
    }

    const next = new URLSearchParams(searchParams);
    const nextSearch = searchInput.trim();
    if (nextSearch) next.set('search', nextSearch);
    else next.delete('search');
    if (nextMinimum !== undefined) next.set('minPrice', String(nextMinimum));
    else next.delete('minPrice');
    if (nextMaximum !== undefined) next.set('maxPrice', String(nextMaximum));
    else next.delete('maxPrice');
    next.delete('page');
    setFilterError(null);
    setFiltersOpen(false);
    setSearchParams(next);
  };

  const resetFilters = () => {
    setFilterError(null);
    setFiltersOpen(false);
    setSearchParams(new URLSearchParams());
  };

  const changePage = (nextPage: number) => {
    const next = new URLSearchParams(searchParams);
    if (nextPage > 0) next.set('page', String(nextPage + 1));
    else next.delete('page');
    setSearchParams(next);
    document.getElementById('catalog-results-title')?.scrollIntoView?.({ block: 'start' });
  };

  return (
    <main className="page catalog-page">
      <div className="container catalog-container">
        <section className="catalog-hero" aria-labelledby="catalog-title">
          <div className="catalog-hero-copy">
            <span className="catalog-kicker">Chọn nhanh, mua rõ ràng</span>
            <h1 id="catalog-title">Đồ dùng mỗi ngày, dễ tìm hơn.</h1>
            <p>Tìm sản phẩm thiết yếu, kiểm tra tồn kho và đưa vào giỏ ngay trong một nhịp.</p>
            <a className="btn btn-primary" href="#catalog-results-title">Khám phá sản phẩm</a>
          </div>
          <figure className="catalog-hero-media">
            <img
              src="/shoponline-market-hero.jpg"
              alt="Túi rau củ, bát sứ và ấm điện trên mặt bàn sáng"
              width="960"
              height="1200"
              loading="eager"
              decoding="async"
            />
          </figure>
        </section>

        <section className="catalog-category-section" aria-labelledby="category-title">
          <div className="catalog-category-heading">
            <div><span>Mua theo nhóm</span><h2 id="category-title">Danh mục</h2></div>
            {categoryError && <button className="text-button" type="button" onClick={() => void loadCategories()}>Tải lại danh mục</button>}
          </div>
          <div className="category-rail" aria-label="Danh mục sản phẩm">
            <button
              type="button"
              className={!categoryId ? 'category-tile active' : 'category-tile'}
              aria-label="Tất cả sản phẩm"
              aria-pressed={!categoryId}
              onClick={() => updateCategory()}
            >
              <span>Tất cả</span><small>Xem toàn bộ sản phẩm</small>
            </button>
            {categoriesLoading ? (
              Array.from({ length: 4 }).map((_, index) => <span className="category-tile category-tile--loading pulse" key={index} aria-hidden="true" />)
            ) : activeCategories.map((category) => (
              <button
                key={category.categoryId}
                type="button"
                className={categoryId === category.categoryId ? 'category-tile active' : 'category-tile'}
                aria-label={category.categoryName}
                aria-pressed={categoryId === category.categoryId}
                onClick={() => updateCategory(category.categoryId)}
              >
                <span>{category.categoryName}</span>
                <small>{category.description || `Thuế VAT ${category.vatRate}%`}</small>
              </button>
            ))}
          </div>
          {categoryError && <p className="category-load-error" role="status">{categoryError} Bạn vẫn có thể xem tất cả sản phẩm.</p>}
        </section>

        <section className="catalog-workspace" aria-label="Duyệt sản phẩm">
          <div className="catalog-mobile-toolbar">
            <button
              type="button"
              className="btn btn-ghost"
              aria-expanded={filtersOpen}
              aria-controls="catalog-filters"
              onClick={() => setFiltersOpen((open) => !open)}
            >
              <SlidersHorizontal size={19} aria-hidden="true" />
              {filtersOpen ? 'Đóng bộ lọc' : 'Lọc sản phẩm'}
            </button>
            {hasFilters && <button className="text-button" type="button" onClick={resetFilters}>Xóa bộ lọc</button>}
          </div>

          <aside id="catalog-filters" className={filtersOpen ? 'catalog-filters is-open' : 'catalog-filters'} aria-labelledby="catalog-filter-title">
            <header className="catalog-filter-heading">
              <span className="catalog-filter-icon" aria-hidden="true"><FunnelSimple size={21} weight="bold" /></span>
              <div><h2 id="catalog-filter-title">Thu hẹp kết quả</h2><p>Tìm theo tên và khoảng giá.</p></div>
              <button className="icon-button catalog-filter-close" type="button" aria-label="Đóng bộ lọc" onClick={() => setFiltersOpen(false)}><X size={19} aria-hidden="true" /></button>
            </header>

            <form className="catalog-filter-form" onSubmit={submitFilters} aria-label="Tìm và lọc sản phẩm">
              <label className="field catalog-search-field">
                <span>Tìm sản phẩm</span>
                <span className="input-with-icon">
                  <MagnifyingGlass size={19} aria-hidden="true" />
                  <input
                    className="form-input"
                    type="search"
                    placeholder="Tên sản phẩm"
                    value={searchInput}
                    onChange={(event) => setSearchInput(event.target.value)}
                  />
                </span>
              </label>

              <fieldset className="catalog-filter-group catalog-price-filter">
                <legend>Khoảng giá niêm yết</legend>
                <div className="catalog-price-inputs">
                  <label className="field"><span>Giá từ</span><input className="form-input" type="number" inputMode="numeric" min={0} placeholder="0 ₫" value={minPriceInput} onChange={(event) => setMinPriceInput(event.target.value)} /></label>
                  <label className="field"><span>Giá đến</span><input className="form-input" type="number" inputMode="numeric" min={0} placeholder="Không giới hạn" value={maxPriceInput} onChange={(event) => setMaxPriceInput(event.target.value)} /></label>
                </div>
              </fieldset>

              {filterError && <div className="alert alert-error" role="alert">{filterError}</div>}
              <button type="submit" className="btn btn-primary catalog-search-submit"><MagnifyingGlass size={18} weight="bold" aria-hidden="true" /> Áp dụng</button>
              {hasFilters && <button type="button" className="btn btn-ghost btn-full" onClick={resetFilters}>Đặt lại</button>}
            </form>
          </aside>

          <div className="catalog-results">
            <header className="catalog-results-header">
              <div>
                <span className="results-context">Đang xem</span>
                <h2 id="catalog-results-title">{activeCategoryLabel}</h2>
                {search && <p>Kết quả cho “{search}”</p>}
              </div>
              {!loading && data && <p className="catalog-count" aria-live="polite">{data.totalElements} sản phẩm</p>}
            </header>

            {error ? (
              <div className="catalog-state catalog-error-state">
                <span className="catalog-state-icon" aria-hidden="true"><WarningCircle size={32} weight="duotone" /></span>
                <div><h3>Chưa thể tải sản phẩm</h3><p role="alert">{error}</p></div>
                <button className="btn btn-ghost" type="button" onClick={() => void loadProducts(page, categoryId, search, minPrice, maxPrice)}>Thử lại</button>
              </div>
            ) : loading ? (
              <div className="product-grid product-grid-loading" aria-label="Đang tải sản phẩm" aria-busy="true">
                {Array.from({ length: 8 }).map((_, index) => <ProductCardSkeleton key={index} />)}
              </div>
            ) : data && data.content.length > 0 ? (
              <>
                <div className="product-grid" aria-labelledby="catalog-results-title">
                  {data.content.map((product) => <ProductCard key={product.id} product={product} />)}
                </div>
                {data.totalPages > 1 && (
                  <nav className="catalog-pagination" aria-label="Phân trang sản phẩm">
                    <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => changePage(page - 1)}><ArrowLeft size={17} weight="bold" aria-hidden="true" /> Trước</button>
                    <span>Trang {page + 1} / {data.totalPages}</span>
                    <button className="btn btn-ghost btn-sm" disabled={data.last} onClick={() => changePage(page + 1)}>Sau <ArrowRight size={17} weight="bold" aria-hidden="true" /></button>
                  </nav>
                )}
              </>
            ) : (
              <div className="catalog-state catalog-empty-state">
                <span className="catalog-state-icon" aria-hidden="true"><MagnifyingGlass size={34} weight="duotone" /></span>
                <div><h3>Không tìm thấy sản phẩm</h3><p>Thử một từ khóa ngắn hơn hoặc bỏ bớt điều kiện lọc.</p></div>
                {hasFilters && <button className="btn btn-ghost" type="button" onClick={resetFilters}>Xóa bộ lọc</button>}
              </div>
            )}
          </div>
        </section>
      </div>
    </main>
  );
}
