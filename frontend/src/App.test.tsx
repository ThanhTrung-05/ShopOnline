import { render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

let authState = {
  isAuthenticated: false,
  isInitializing: false,
  username: null as string | null,
  roles: [] as string[],
  error: null as string | null,
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

vi.mock('./pages/ProductsPage', () => ({ default: () => <div>Products page</div> }));
vi.mock('./pages/ProductDetailPage', () => ({ default: () => <div>Product detail page</div> }));
vi.mock('./pages/RegisterPage', () => ({ default: () => <div>Register page</div> }));
vi.mock('./pages/CartPage', () => ({ default: () => <div>Cart page</div> }));
vi.mock('./pages/ProfilePage', () => ({ default: () => <div>Profile page</div> }));
vi.mock('./pages/AddressesPage', () => ({ default: () => <div>Addresses page</div> }));
vi.mock('./pages/ShippingPage', () => ({ default: () => <div>Shipping page</div> }));
vi.mock('./pages/AdminProductPage', () => ({ default: () => <div>Admin products page</div> }));
vi.mock('./pages/AdminCategoryPage', () => ({ default: () => <div>Admin categories page</div> }));

import apiClient from './api/axios';
import App from './App';
import { useCartStore } from './store/cartStore';

function renderApp(initialPath = '/products') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <App />
    </MemoryRouter>,
  );
}

function mainNavigation() {
  return screen.getByRole('navigation', { name: 'Điều hướng chính' });
}

beforeEach(() => {
  authState = {
    isAuthenticated: false,
    isInitializing: false,
    username: null,
    roles: [],
    error: null,
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

describe('App navigation and route guards', () => {
  it('redirects a guest from Home to Products and shows guest navigation', async () => {
    renderApp('/');

    expect(await screen.findByText('Products page')).toBeInTheDocument();
    const navigation = mainNavigation();
    expect(within(navigation).getByRole('link', { name: 'Sản phẩm' })).toBeInTheDocument();
    expect(within(navigation).queryByRole('link', { name: 'Giỏ hàng' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Đăng ký' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đăng nhập' })).toBeInTheDocument();
    expect(screen.queryByText('Home')).not.toBeInTheDocument();
  });

  it('redirects CUSTOMER to Products and shows only customer navigation', async () => {
    authState = { ...authState, isAuthenticated: true, username: 'customer-a', roles: ['CUSTOMER'] };

    renderApp('/');

    expect(await screen.findByText('Products page')).toBeInTheDocument();
    const navigation = mainNavigation();
    for (const label of ['Sản phẩm', 'Giỏ hàng', 'Hồ sơ', 'Địa chỉ', 'Giao hàng']) {
      expect(within(navigation).getByRole('link', { name: label })).toBeInTheDocument();
    }
    expect(within(navigation).queryByRole('link', { name: 'Quản lý sản phẩm' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đăng xuất' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Đăng ký' })).not.toBeInTheDocument();
  });

  it('redirects ADMIN to product management and hides customer navigation', async () => {
    authState = { ...authState, isAuthenticated: true, username: 'admin-a', roles: ['ADMIN'] };

    renderApp('/');

    expect(await screen.findByText('Admin products page')).toBeInTheDocument();
    const navigation = mainNavigation();
    expect(within(navigation).getByRole('link', { name: 'Quản lý sản phẩm' })).toBeInTheDocument();
    expect(within(navigation).getByRole('link', { name: 'Quản lý danh mục' })).toBeInTheDocument();
    for (const label of ['Sản phẩm', 'Giỏ hàng', 'Hồ sơ', 'Địa chỉ', 'Giao hàng']) {
      expect(within(navigation).queryByRole('link', { name: label })).not.toBeInTheDocument();
    }
    expect(screen.getByRole('button', { name: 'Đăng xuất' })).toBeInTheDocument();
  });

  it('waits for authentication initialization before choosing the landing route', async () => {
    authState = { ...authState, isInitializing: true };
    const view = renderApp('/');

    expect(screen.queryByText('Admin products page')).not.toBeInTheDocument();

    authState = { ...authState, isAuthenticated: true, isInitializing: false, roles: ['ADMIN'] };
    view.rerender(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Admin products page')).toBeInTheDocument();
  });

  it('allows ADMIN to open category management', async () => {
    authState = { ...authState, isAuthenticated: true, username: 'admin-a', roles: ['ADMIN'] };

    renderApp('/admin/categories');

    expect(await screen.findByText('Admin categories page')).toBeInTheDocument();
  });

  it('blocks CUSTOMER from admin routes', () => {
    authState = { ...authState, isAuthenticated: true, username: 'customer-a', roles: ['CUSTOMER'] };

    renderApp('/admin/products');

    expect(screen.getByText('Không thể truy cập trang này')).toBeInTheDocument();
    expect(screen.queryByText('Admin products page')).not.toBeInTheDocument();
  });

  it('asks a guest to log in before opening an admin route', () => {
    renderApp('/admin/products');

    expect(screen.getByRole('heading', { name: 'Đăng nhập để tiếp tục' })).toBeInTheDocument();
    expect(screen.queryByText('Admin products page')).not.toBeInTheDocument();
  });
});

describe('App cart auth lifecycle', () => {
  it('loads cart after authentication/check-sso', async () => {
    authState = { ...authState, isAuthenticated: true, username: 'customer-a', roles: ['CUSTOMER'] };

    renderApp();

    await waitFor(() => expect(useCartStore.getState().loadCart).toHaveBeenCalledTimes(1));
    expect(useCartStore.getState().clearLocal).toHaveBeenCalledTimes(1);
  });

  it('clears local cart state on logout', async () => {
    renderApp();

    await waitFor(() => expect(useCartStore.getState().clearLocal).toHaveBeenCalledTimes(1));
    expect(useCartStore.getState().loadCart).not.toHaveBeenCalled();
  });

  it('does not load a CUSTOMER cart for an unrelated authenticated role', async () => {
    authState = { ...authState, isAuthenticated: true, username: 'admin-a', roles: ['ADMIN'] };

    renderApp();

    await waitFor(() => expect(useCartStore.getState().clearLocal).toHaveBeenCalledTimes(1));
    expect(useCartStore.getState().loadCart).not.toHaveBeenCalled();
  });

  it('clears before loading when switching customers', async () => {
    authState = { ...authState, isAuthenticated: true, username: 'customer-a', roles: ['CUSTOMER'] };
    const view = renderApp();

    await waitFor(() => expect(useCartStore.getState().loadCart).toHaveBeenCalledTimes(1));

    authState = { ...authState, isAuthenticated: true, username: 'customer-b', roles: ['CUSTOMER'] };
    view.rerender(
      <MemoryRouter initialEntries={['/products']}>
        <App />
      </MemoryRouter>,
    );

    await waitFor(() => expect(useCartStore.getState().loadCart).toHaveBeenCalledTimes(2));
    expect(useCartStore.getState().clearLocal).toHaveBeenCalledTimes(2);
  });
});
