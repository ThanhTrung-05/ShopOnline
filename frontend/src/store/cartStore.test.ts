import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../api/cartApi', () => ({
  emptyCart: { items: [], subtotal: 0 },
  cartApi: {
    get: vi.fn(),
    addItem: vi.fn(),
    updateItemQuantity: vi.fn(),
    removeItem: vi.fn(),
  },
}));

import { cartApi } from '../api/cartApi';
import { useCartStore } from './cartStore';

const backendCart = {
  items: [
    {
      cartItemId: 1,
      productId: 10,
      productName: 'Lavie 500ml',
      quantity: 2,
      unitPrice: 5000,
      itemSubtotal: 10000,
    },
  ],
  subtotal: 10000,
};

describe('cartStore', () => {
  beforeEach(() => {
    vi.mocked(cartApi.get).mockReset();
    vi.mocked(cartApi.addItem).mockReset();
    vi.mocked(cartApi.updateItemQuantity).mockReset();
    vi.mocked(cartApi.removeItem).mockReset();
    localStorage.clear();
    useCartStore.getState().clearLocal();
  });

  it('loads cart from backend', async () => {
    vi.mocked(cartApi.get).mockResolvedValue({ data: { data: backendCart } } as any);

    await useCartStore.getState().loadCart();

    expect(useCartStore.getState().items).toEqual(backendCart.items);
    expect(useCartStore.getState().subtotal).toBe(10000);
    expect(useCartStore.getState().error).toBeNull();
  });

  it('does not use legacy localStorage as cart source', async () => {
    localStorage.setItem('shoponline_cart', JSON.stringify([{ productId: 99, quantity: 9 }]));
    vi.mocked(cartApi.get).mockResolvedValue({ data: { data: { items: [], subtotal: 0 } } } as any);

    await useCartStore.getState().loadCart();

    expect(useCartStore.getState().items).toEqual([]);
    expect(useCartStore.getState().subtotal).toBe(0);
  });

  it('addItem calls backend then reloads cart', async () => {
    vi.mocked(cartApi.addItem).mockResolvedValue({} as any);
    vi.mocked(cartApi.get).mockResolvedValue({ data: { data: backendCart } } as any);

    await useCartStore.getState().addItem(10, 2);

    expect(cartApi.addItem).toHaveBeenCalledWith({ productId: 10, quantity: 2 });
    expect(cartApi.get).toHaveBeenCalledTimes(1);
    expect(useCartStore.getState().items).toEqual(backendCart.items);
  });

  it('updateItemQuantity calls backend then reloads cart', async () => {
    vi.mocked(cartApi.updateItemQuantity).mockResolvedValue({} as any);
    vi.mocked(cartApi.get).mockResolvedValue({ data: { data: backendCart } } as any);

    await useCartStore.getState().updateItemQuantity(1, 4);

    expect(cartApi.updateItemQuantity).toHaveBeenCalledWith(1, { quantity: 4 });
    expect(cartApi.get).toHaveBeenCalledTimes(1);
  });

  it('removeItem calls backend then reloads cart', async () => {
    vi.mocked(cartApi.removeItem).mockResolvedValue({} as any);
    vi.mocked(cartApi.get).mockResolvedValue({ data: { data: { items: [], subtotal: 0 } } } as any);

    await useCartStore.getState().removeItem(1);

    expect(cartApi.removeItem).toHaveBeenCalledWith(1);
    expect(cartApi.get).toHaveBeenCalledTimes(1);
    expect(useCartStore.getState().items).toEqual([]);
  });

  it('clearLocal clears frontend cart state and legacy storage only', () => {
    localStorage.setItem('shoponline_cart', '[{"productId":10,"quantity":2}]');
    useCartStore.setState({ items: backendCart.items, subtotal: backendCart.subtotal, error: 'x' });

    useCartStore.getState().clearLocal();

    expect(useCartStore.getState().items).toEqual([]);
    expect(useCartStore.getState().subtotal).toBe(0);
    expect(useCartStore.getState().error).toBeNull();
    expect(localStorage.getItem('shoponline_cart')).toBeNull();
    expect(cartApi.removeItem).not.toHaveBeenCalled();
  });
});
