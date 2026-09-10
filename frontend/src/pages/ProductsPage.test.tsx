import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { categoryApi } from '../api/categoryApi';
import { productApi } from '../api/productApi';
import ProductsPage from './ProductsPage';

vi.mock('../api/categoryApi', () => ({
  categoryApi: {
    list: vi.fn(),
  },
}));

vi.mock('../api/productApi', () => ({
  productApi: {
    list: vi.fn(),
  },
}));

vi.mock('../components/ProductCard', () => ({
  default: ({ product, variant = 'standard' }: { product: { id: number; name: string }; variant?: string }) => (
    <article data-testid="product-card" data-variant={variant}>{product.name}</article>
  ),
}));

const products = [
  {
    id: 1,
    name: 'Nồi cơm điện',
    slug: 'noi-com-dien',
    price: 89000,
    categoryId: 2,
    categoryName: 'Điện máy',
    inventoryCount: 24,
    status: 'ACTIVE',
  },
  {
    id: 2,
    name: 'Bộ bát sứ',
    slug: 'bo-bat-su',
    price: 178000,
    categoryId: 3,
    categoryName: 'Sành sứ',
    inventoryCount: 18,
    status: 'ACTIVE',
  },
  {
    id: 3,
    name: 'Rau xanh',
    slug: 'rau-xanh',
    price: 27000,
    categoryId: 1,
    categoryName: 'Thực phẩm',
    inventoryCount: 6,
    status: 'ACTIVE',
  },
];

const categories = [
  { categoryId: 1, categoryName: 'Thực phẩm', categoryCode: 'THUC_PHAM', description: 'Thực phẩm mỗi ngày', vatRate: 5, status: 'ACTIVE' },
  { categoryId: 2, categoryName: 'Điện máy', categoryCode: 'DIEN_MAY', description: 'Thiết bị gia dụng', vatRate: 10, status: 'ACTIVE' },
  { categoryId: 3, categoryName: 'Sành sứ', categoryCode: 'SANH_SU', description: 'Đồ dùng bàn ăn', vatRate: 10, status: 'ACTIVE' },
];

const response = (content = products, overrides: Record<string, unknown> = {}) => ({
  data: {
    data: {
      content,
      totalPages: 2,
      totalElements: content.length,
      number: 0,
      size: 20,
      last: false,
      ...overrides,
    },
  },
});

function renderPage() {
  return render(
    <MemoryRouter>
      <ProductsPage />
    </MemoryRouter>,
  );
}

describe('ProductsPage catalog behavior', () => {
  beforeEach(() => {
    vi.mocked(productApi.list).mockReset();
    vi.mocked(categoryApi.list).mockReset();
    vi.mocked(categoryApi.list).mockResolvedValue({ data: { data: categories } } as any);
  });

  it('keeps the existing initial request while rendering the redesigned loading and product compositions', async () => {
    let resolveRequest!: (value: Awaited<ReturnType<typeof productApi.list>>) => void;
    vi.mocked(productApi.list).mockReturnValueOnce(new Promise((resolve) => {
      resolveRequest = resolve;
    }) as ReturnType<typeof productApi.list>);

    renderPage();

    expect(screen.getByLabelText('Đang tải sản phẩm')).toHaveAttribute('aria-busy', 'true');
    expect(productApi.list).toHaveBeenCalledWith(0, 20, undefined, undefined, undefined, undefined);

    await act(async () => resolveRequest(
      response() as Awaited<ReturnType<typeof productApi.list>>,
    ));

    expect(await screen.findByText('3 sản phẩm')).toBeInTheDocument();
    const cards = screen.getAllByTestId('product-card');
    expect(cards).toHaveLength(3);
    cards.forEach((card) => expect(card).toHaveAttribute('data-variant', 'standard'));
  });

  it('preserves pagination, submitted search and price filters, and immediate category filtering', async () => {
    vi.mocked(productApi.list).mockResolvedValue(response() as Awaited<ReturnType<typeof productApi.list>>);
    renderPage();

    await screen.findByText('3 sản phẩm');
    fireEvent.click(screen.getByRole('button', { name: 'Sau' }));
    await waitFor(() => expect(productApi.list).toHaveBeenLastCalledWith(
      1, 20, undefined, undefined, undefined, undefined,
    ));

    fireEvent.change(screen.getByLabelText('Tìm sản phẩm'), { target: { value: 'ấm điện' } });
    fireEvent.change(screen.getByLabelText('Giá từ'), { target: { value: '100000' } });
    fireEvent.change(screen.getByLabelText('Giá đến'), { target: { value: '500000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Áp dụng' }));

    await waitFor(() => expect(productApi.list).toHaveBeenLastCalledWith(
      0, 20, undefined, 'ấm điện', 100000, 500000,
    ));

    fireEvent.click(screen.getByRole('button', { name: 'Thực phẩm' }));
    await waitFor(() => expect(productApi.list).toHaveBeenLastCalledWith(
      0, 20, 1, 'ấm điện', 100000, 500000,
    ));
    expect(screen.getByRole('heading', { name: 'Thực phẩm' })).toBeInTheDocument();
  });

  it('keeps retry behavior and transitions from the redesigned error state to the empty state', async () => {
    vi.mocked(productApi.list)
      .mockRejectedValueOnce({})
      .mockResolvedValueOnce(response([], { totalPages: 0, totalElements: 0, last: true }) as Awaited<ReturnType<typeof productApi.list>>);

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Chưa thể tải sản phẩm' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Không thể tải danh sách sản phẩm.');

    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));

    expect(await screen.findByRole('heading', { name: 'Không tìm thấy sản phẩm' })).toBeInTheDocument();
    expect(productApi.list).toHaveBeenCalledTimes(2);
  });
});
