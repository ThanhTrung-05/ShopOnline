import { render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

let authState = {
  isAuthenticated: false,
  isInitializing: false,
  username: null as string | null,
  login: vi.fn(),
  logout: vi.fn(),
  getToken: vi.fn(),
  clearAuthState: vi.fn(),
};

vi.mock('./auth/useAuth', () => ({
  useAuth: () => authState,
}));

vi.mock('./api/axios', () => ({
  default: {
    get: vi.fn(),
  },
}));

vi.mock('./pages/ProductsPage', () => ({
  default: () => <div>Products</div>,
}));

vi.mock('./pages/ProductDetailPage', () => ({
  default: () => <div>Product detail</div>,
}));

import apiClient from './api/axios';
import App from './App';
import { useCartStore } from './store/cartStore';

function renderApp() {
  return render(
    <MemoryRouter initialEntries={['/products']}>
      <App />
    </MemoryRouter>,
  );
}

describe('App cart auth lifecycle', () => {
  beforeEach(() => {
    authState = {
      isAuthenticated: false,
      isInitializing: false,
      username: null,
      login: vi.fn(),
      logout: vi.fn(),
      getToken: vi.fn(),
      clearAuthState: vi.fn(),
    };
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { data: { authenticated: true, subject: 'subject-1', username: 'customer-a' } },
    });
    useCartStore.setState({
      items: [],
      subtotal: 0,
      isLoading: false,
      error: null,
      loadCart: vi.fn().mockResolvedValue(undefined),
      clearLocal: vi.fn(),
    });
  });

  it('loads cart after authentication/check-sso', async () => {
    authState = { ...authState, isAuthenticated: true, username: 'customer-a' };

    renderApp();

    await waitFor(() => expect(useCartStore.getState().loadCart).toHaveBeenCalledTimes(1));
    expect(useCartStore.getState().clearLocal).toHaveBeenCalledTimes(1);
  });

  it('clears local cart state on logout', async () => {
    authState = { ...authState, isAuthenticated: false };

    renderApp();

    await waitFor(() => expect(useCartStore.getState().clearLocal).toHaveBeenCalledTimes(1));
    expect(useCartStore.getState().loadCart).not.toHaveBeenCalled();
  });

  it('clears before loading when switching customers', async () => {
    authState = { ...authState, isAuthenticated: true, username: 'customer-a' };
    const { rerender } = renderApp();

    await waitFor(() => expect(useCartStore.getState().loadCart).toHaveBeenCalledTimes(1));

    authState = { ...authState, isAuthenticated: true, username: 'customer-b' };
    rerender(
      <MemoryRouter initialEntries={['/products']}>
        <App />
      </MemoryRouter>,
    );

    await waitFor(() => expect(useCartStore.getState().loadCart).toHaveBeenCalledTimes(2));
    expect(useCartStore.getState().clearLocal).toHaveBeenCalledTimes(2);
  });
});
