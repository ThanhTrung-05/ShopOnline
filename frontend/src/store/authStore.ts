import { create } from 'zustand';

interface AuthState {
  isAuthenticated: boolean;
  userName: string | null;
  login: (userName: string) => void;
  logout: () => void;
}

const STORAGE_KEY = 'shoponline_user';

export const useAuthStore = create<AuthState>((set) => ({
  isAuthenticated: !!localStorage.getItem(STORAGE_KEY),
  userName: localStorage.getItem(STORAGE_KEY),
  login: (userName) => {
    localStorage.setItem(STORAGE_KEY, userName);
    set({ isAuthenticated: true, userName });
  },
  logout: () => {
    localStorage.removeItem(STORAGE_KEY);
    set({ isAuthenticated: false, userName: null });
  },
}));
