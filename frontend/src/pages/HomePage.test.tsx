import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { productApi } from '../api/productApi';
import HomePage from './HomePage';

vi.mock('../api/productApi', () => ({
  productApi: { featured: vi.fn() },
}));

vi.mock('../components/ProductCard', async () => {
  const { Link } = await import('react-router-dom');
  return {
    default: ({ product, variant }: { product: { id: number; name: string }; variant: string }) => (
      <article data-testid="product-card" data-variant={variant}>
        <Link to={`/products/${product.id}`}>{product.name}</Link>
      </article>
    ),
  };
});

function product(id: number) {
  return {
    id,
    name: `Sản phẩm ${id}`,
    slug: `san-pham-${id}`,
    price: id * 10000,
    categoryId: 1,
    categoryName: 'Gia dụng',
    inventoryCount: 20,
    status: 'ACTIVE',
    featured: true,
  };
}

function response(products: ReturnType<typeof product>[]) {
  return { data: { data: products } } as Awaited<ReturnType<typeof productApi.featured>>;
}

function renderPage() {
  return render(<MemoryRouter><HomePage /></MemoryRouter>);
}

describe('HomePage featured products', () => {
  beforeEach(() => {
    vi.mocked(productApi.featured).mockReset();
  });

  it('shows loading, limits rendering to eight products and links cards to detail', async () => {
    let resolveRequest!: (value: Awaited<ReturnType<typeof productApi.featured>>) => void;
    vi.mocked(productApi.featured).mockReturnValueOnce(new Promise((resolve) => {
      resolveRequest = resolve;
    }) as ReturnType<typeof productApi.featured>);

    renderPage();

    expect(screen.getByLabelText('Đang tải sản phẩm nổi bật')).toHaveAttribute('aria-busy', 'true');

    await act(async () => resolveRequest(response(Array.from({ length: 10 }, (_, index) => product(index + 1)))));

    const cards = await screen.findAllByTestId('product-card');
    expect(cards).toHaveLength(8);
    expect(cards[0]).toHaveAttribute('data-variant', 'featured');
    expect(screen.getByRole('link', { name: 'Sản phẩm 1' })).toHaveAttribute('href', '/products/1');
    expect(screen.getByRole('link', { name: 'Xem tất cả' })).toHaveAttribute('href', '/products');
  });

  it('shows the empty state when no featured products exist', async () => {
    vi.mocked(productApi.featured).mockResolvedValue(response([]));
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Chưa có sản phẩm nổi bật' })).toBeInTheDocument();
  });

  it('retries after an error and then renders the recovered result', async () => {
    vi.mocked(productApi.featured)
      .mockRejectedValueOnce({})
      .mockResolvedValueOnce(response([product(3)]));
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Chưa thể tải sản phẩm nổi bật' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));

    expect(await screen.findByRole('link', { name: 'Sản phẩm 3' })).toBeInTheDocument();
    await waitFor(() => expect(productApi.featured).toHaveBeenCalledTimes(2));
  });
});
