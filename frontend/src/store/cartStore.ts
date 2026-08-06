import { create } from 'zustand';

export interface CartItem {
  productId: number;
  quantity: number;
}

interface CartState {
  items: CartItem[];
  addItem: (productId: number, quantity: number) => Promise<void>;
  removeItem: (productId: number) => void;
  clear: () => void;
}

const STORAGE_KEY = 'shoponline_cart';

function loadCart(): CartItem[] {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
  } catch {
    return [];
  }
}

function saveCart(items: CartItem[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
}

export const useCartStore = create<CartState>((set, get) => ({
  items: loadCart(),
  addItem: async (productId, quantity) => {
    const items = [...get().items];
    const existing = items.find((i) => i.productId === productId);
    if (existing) {
      existing.quantity += quantity;
    } else {
      items.push({ productId, quantity });
    }
    saveCart(items);
    set({ items });
  },
  removeItem: (productId) => {
    const items = get().items.filter((i) => i.productId !== productId);
    saveCart(items);
    set({ items });
  },
  clear: () => {
    saveCart([]);
    set({ items: [] });
  },
}));
