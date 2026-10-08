import { useCallback, useEffect, useState } from 'react';
import { ArrowRight, Package, WarningCircle } from '@phosphor-icons/react';
import { Link } from 'react-router-dom';
import { productApi } from '../api/productApi';
import ProductCard from '../components/ProductCard';
import type { Product } from '../types';
import { getApiErrorMessage } from '../utils/apiError';
import './HomePage.css';

const FEATURED_LIMIT = 8;

function FeaturedCardSkeleton({ featured = false }: { featured?: boolean }) {
  return (
    <article
      className={`product-card product-card--${featured ? 'featured' : 'standard'} product-card-skeleton`}
      aria-hidden="true"
    >
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

export default function HomePage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadFeaturedProducts = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await productApi.featured();
      setProducts((response.data.data ?? []).slice(0, FEATURED_LIMIT));
    } catch (requestError) {
      setProducts([]);
      setError(getApiErrorMessage(requestError, 'Không thể tải sản phẩm nổi bật.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadFeaturedProducts();
  }, [loadFeaturedProducts]);

  return (
    <main className="page home-page">
      <div className="container home-container">
        <section className="home-hero" aria-labelledby="home-title">
          <div className="home-hero-copy">
            <span className="home-kicker">Mua sắm mỗi ngày</span>
            <h1 id="home-title">Hàng thiết yếu, chọn nhanh.</h1>
            <p>Sản phẩm rõ giá, rõ tồn kho và sẵn sàng đưa vào giỏ.</p>
          </div>
          <figure className="home-hero-media">
            <img
              src="/shoponline-home-hero.jpg"
              alt="Rau củ, thực phẩm khô và ấm điện trên bàn bếp sáng"
              width="1536"
              height="1024"
              loading="eager"
              decoding="async"
            />
          </figure>
        </section>

        <section className="home-featured" aria-labelledby="featured-products-title">
          <header className="home-section-heading">
            <div>
              <h2 id="featured-products-title">Sản phẩm nổi bật</h2>
              <p>Những lựa chọn mới được cửa hàng ưu tiên giới thiệu.</p>
            </div>
            <Link className="btn btn-ghost" to="/products">
              Xem tất cả <ArrowRight size={18} weight="bold" aria-hidden="true" />
            </Link>
          </header>

          {error ? (
            <div className="home-featured-state" role="alert">
              <WarningCircle size={34} weight="duotone" aria-hidden="true" />
              <div><h3>Chưa thể tải sản phẩm nổi bật</h3><p>{error}</p></div>
              <button className="btn btn-ghost" type="button" onClick={() => void loadFeaturedProducts()}>Thử lại</button>
            </div>
          ) : loading ? (
            <div className="product-grid home-featured-grid" aria-label="Đang tải sản phẩm nổi bật" aria-busy="true">
              {Array.from({ length: FEATURED_LIMIT }).map((_, index) => (
                <FeaturedCardSkeleton key={index} featured={index === 0} />
              ))}
            </div>
          ) : products.length > 0 ? (
            <div className="product-grid home-featured-grid">
              {products.map((product, index) => (
                <ProductCard key={product.id} product={product} variant={index === 0 ? 'featured' : 'standard'} />
              ))}
            </div>
          ) : (
            <div className="home-featured-state home-featured-empty">
              <Package size={36} weight="duotone" aria-hidden="true" />
              <div><h3>Chưa có sản phẩm nổi bật</h3><p>Bạn vẫn có thể xem toàn bộ sản phẩm đang bán.</p></div>
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
