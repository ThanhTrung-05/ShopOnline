import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductCard from './ProductCard';
import ProductDetailPage from '../pages/ProductDetailPage';
import { useAuthStore } from '../store/authStore';

vi.mock('react-hot-toast', () => ({
  default: {
    error: vi.fn(),
    success: vi.fn(),
  },
}));

vi.mock('../api/cartApi', () => ({
  emptyCart: { items: [], subtotal: 0 },
  cartApi: {
    get: vi.fn(),
    addItem: vi.fn(),
    updateItemQuantity: vi.fn(),
    removeItem: vi.fn(),
  },
}));

vi.mock('../api/productApi', () => ({
  productApi: {
    detail: vi.fn(),
  },
}));

import { cartApi } from '../api/cartApi';
import { productApi } from '../api/productApi';
import { useCartStore } from '../store/cartStore';

const product = {
  id: 1,
  name: 'Rice',
  slug: 'rice',
  price: 10000,
  priceIncludingVat: 11000,
  vatRate: 10,
  vatAmount: 1000,
  categoryId: 1,
  categoryName: 'Food',
  inventoryCount: 20,
  status: 'ACTIVE',
};

function cart(quantity: number) {
  return {
    items: [
      {
        cartItemId: 11,
        productId: 1,
        productName: 'Rice',
        quantity,
        unitPrice: 10000,
        itemSubtotal: 10000 * quantity,
      },
    ],
    subtotal: 10000 * quantity,
  };
}

function renderCard() {
  return render(
    <MemoryRouter>
      <ProductCard product={product} />
    </MemoryRouter>,
  );
}

async function renderDetail() {
  vi.mocked(productApi.detail).mockResolvedValue({
    data: { data: product },
  } as any);

  render(
    <MemoryRouter initialEntries={['/products/1']}>
      <Routes>
        <Route path="/products/:id" element={<ProductDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );

  await screen.findByRole('heading', { name: 'Rice' });
}

describe('product cart quantity display', () => {
  beforeEach(() => {
    vi.mocked(cartApi.get).mockReset();
    vi.mocked(cartApi.addItem).mockReset();
    vi.mocked(cartApi.updateItemQuantity).mockReset();
    vi.mocked(cartApi.removeItem).mockReset();
    vi.mocked(productApi.detail).mockReset();
    localStorage.clear();
    useAuthStore.getState().login('customer-a');
    useCartStore.getState().clearLocal();
  });

  it('shows persisted backend quantity after loadCart', async () => {
    vi.mocked(cartApi.get).mockResolvedValue({ data: { data: cart(5) } } as any);

    await useCartStore.getState().loadCart();
    renderCard();

    expect(screen.getByLabelText('cart quantity for Rice')).toHaveTextContent('Trong giỏ: 5');
  });

  it('refresh/load restores quantity into product detail', async () => {
    vi.mocked(cartApi.get).mockResolvedValue({ data: { data: cart(5) } } as any);

    await useCartStore.getState().loadCart();
    await renderDetail();

    expect(screen.getByLabelText('cart quantity for Rice')).toHaveTextContent('5');
  });

  it('add, update, and remove keep product detail synchronized with cartStore', async () => {
    vi.mocked(cartApi.get)
      .mockResolvedValueOnce({ data: { data: cart(2) } } as any)
      .mockResolvedValueOnce({ data: { data: cart(3) } } as any)
      .mockResolvedValueOnce({ data: { data: cart(2) } } as any)
      .mockResolvedValueOnce({ data: { data: { items: [], subtotal: 0 } } } as any);
    vi.mocked(cartApi.addItem).mockResolvedValue({} as any);
    vi.mocked(cartApi.updateItemQuantity).mockResolvedValue({} as any);
    vi.mocked(cartApi.removeItem).mockResolvedValue({} as any);

    await useCartStore.getState().addItem(1, 2);
    await renderDetail();
    expect(screen.getByLabelText('cart quantity for Rice')).toHaveTextContent('2');

    fireEvent.click(screen.getByRole('button', { name: '+' }));
    await waitFor(() => expect(screen.getByLabelText('cart quantity for Rice')).toHaveTextContent('3'));

    fireEvent.click(screen.getByRole('button', { name: '−' }));
    await waitFor(() => expect(screen.getByLabelText('cart quantity for Rice')).toHaveTextContent('2'));

    await useCartStore.getState().removeItem(11);
    await waitFor(() => expect(screen.getByLabelText('cart quantity for Rice')).toHaveTextContent('1'));
  });

  it('logout clears displayed product quantity without deleting backend cart', async () => {
    useCartStore.setState({ items: cart(5).items, subtotal: cart(5).subtotal });
    await renderDetail();

    useCartStore.getState().clearLocal();

    await waitFor(() => expect(screen.getByLabelText('cart quantity for Rice')).toHaveTextContent('1'));
    expect(cartApi.removeItem).not.toHaveBeenCalled();
  });

  it('login reload restores displayed quantity', async () => {
    await renderDetail();
    expect(screen.getByLabelText('cart quantity for Rice')).toHaveTextContent('1');

    vi.mocked(cartApi.get).mockResolvedValue({ data: { data: cart(5) } } as any);
    await useCartStore.getState().loadCart();

    await waitFor(() => expect(screen.getByLabelText('cart quantity for Rice')).toHaveTextContent('5'));
  });

  it('customer switch clears previous quantity before loading the next cart', async () => {
    const customerBCart = {
      ...cart(2),
      items: [{ ...cart(2).items[0], cartItemId: 22 }],
    };
    useCartStore.setState({ items: cart(5).items, subtotal: cart(5).subtotal });
    renderCard();
    expect(screen.getByLabelText('cart quantity for Rice')).toHaveTextContent('5');

    useCartStore.getState().clearLocal();
    await waitFor(() => expect(screen.queryByLabelText('cart quantity for Rice')).not.toBeInTheDocument());

    vi.mocked(cartApi.get).mockResolvedValue({ data: { data: customerBCart } } as any);
    await useCartStore.getState().loadCart();

    expect(screen.getByLabelText('cart quantity for Rice')).toHaveTextContent('2');
    expect(screen.getByLabelText('cart quantity for Rice')).not.toHaveTextContent('5');
  });

  it('keeps product card stock unchanged after add while cart quantity changes', async () => {
    vi.mocked(cartApi.addItem).mockResolvedValue({} as any);
    vi.mocked(cartApi.get).mockResolvedValue({ data: { data: cart(1) } } as any);

    renderCard();
    expect(screen.getByText('Còn 20')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '+ Thêm vào giỏ' }));

    await waitFor(() => expect(screen.getByLabelText('cart quantity for Rice')).toHaveTextContent('1'));
    expect(screen.getByText('Còn 20')).toBeInTheDocument();
  });

  it('keeps product detail stock unchanged after quantity update while cart quantity changes', async () => {
    useCartStore.setState({ items: cart(2).items, subtotal: cart(2).subtotal });
    vi.mocked(cartApi.updateItemQuantity).mockResolvedValue({} as any);
    vi.mocked(cartApi.get).mockResolvedValue({ data: { data: cart(3) } } as any);

    await renderDetail();
    expect(screen.getByText(/Còn 20 sản phẩm/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '+' }));

    await waitFor(() => expect(screen.getByLabelText('cart quantity for Rice')).toHaveTextContent('3'));
    expect(screen.getByText(/Còn 20 sản phẩm/)).toBeInTheDocument();
  });

  it('keeps product detail stock unchanged after remove while cart item is cleared', async () => {
    useCartStore.setState({ items: cart(1).items, subtotal: cart(1).subtotal });
    vi.mocked(cartApi.removeItem).mockResolvedValue({} as any);
    vi.mocked(cartApi.get).mockResolvedValue({ data: { data: { items: [], subtotal: 0 } } } as any);

    await renderDetail();
    expect(screen.getByText(/Còn 20 sản phẩm/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '−' }));

    await waitFor(() => expect(useCartStore.getState().items).toEqual([]));
    expect(screen.getByLabelText('cart quantity for Rice')).toHaveTextContent('1');
    expect(screen.getByText(/Còn 20 sản phẩm/)).toBeInTheDocument();
  });

  it('keeps stock display consistent after refresh/loadCart', async () => {
    vi.mocked(cartApi.get).mockResolvedValue({ data: { data: cart(5) } } as any);

    await useCartStore.getState().loadCart();
    await renderDetail();

    expect(screen.getByLabelText('cart quantity for Rice')).toHaveTextContent('5');
    expect(screen.getByText(/Còn 20 sản phẩm/)).toBeInTheDocument();
  });
});
