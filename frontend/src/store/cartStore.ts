import { create } from 'zustand';
import { cartApi, emptyCart, type CartItem } from '../api/cartApi';

interface CartState {
  items: CartItem[];
  subtotal: number;
  isLoading: boolean;
  error: string | null;
  loadCart: () => Promise<void>;
  addItem: (productId: number, quantity: number) => Promise<void>;
  updateItemQuantity: (cartItemId: number, quantity: number) => Promise<void>;
  removeItem: (cartItemId: number) => Promise<void>;
  clearLocal: () => void;
}

const LEGACY_STORAGE_KEY = 'shoponline_cart';

function clearLegacyCartStorage() {
  try {
    localStorage.removeItem(LEGACY_STORAGE_KEY);
  } catch {
    // Ignore storage failures; backend cart remains the source of truth.
  }
}

export const useCartStore = create<CartState>((set, get) => ({
  items: [],
  subtotal: 0,
  isLoading: false,
  error: null,

  loadCart: async () => {
    set({ isLoading: true, error: null });
    try {
      const response = await cartApi.get();
      const cart = response.data.data ?? emptyCart;
      set({
        items: cart.items,
        subtotal: cart.subtotal,
        isLoading: false,
        error: null,
      });
    } catch {
      set({
        items: [],
        subtotal: 0,
        isLoading: false,
        error: 'Unable to load cart.',
      });
    }
  },

  addItem: async (productId, quantity) => {
    await cartApi.addItem({ productId, quantity });
    await get().loadCart();
  },

  updateItemQuantity: async (cartItemId, quantity) => {
    await cartApi.updateItemQuantity(cartItemId, { quantity });
    await get().loadCart();
  },

  removeItem: async (cartItemId) => {
    await cartApi.removeItem(cartItemId);
    await get().loadCart();
  },

  clearLocal: () => {
    clearLegacyCartStorage();
    set({ items: [], subtotal: 0, isLoading: false, error: null });
  },
}));
