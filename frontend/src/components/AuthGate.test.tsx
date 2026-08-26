import { render, screen } from '@testing-library/react';
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

vi.mock('../auth/useAuth', () => ({
  useAuth: () => authState,
}));

import AuthGate from './AuthGate';

describe('AuthGate', () => {
  beforeEach(() => {
    authState = {
      ...authState,
      isAuthenticated: false,
      isInitializing: false,
      roles: [],
      error: null,
      login: vi.fn(),
    };
  });

  it('asks a guest to authenticate', () => {
    render(<AuthGate><div>Customer content</div></AuthGate>);

    expect(screen.getByText('Đăng nhập để tiếp tục')).toBeInTheDocument();
    expect(screen.queryByText('Customer content')).not.toBeInTheDocument();
  });

  it('does not treat ADMIN as CUSTOMER', () => {
    authState = { ...authState, isAuthenticated: true, roles: ['ADMIN'] };

    render(<AuthGate><div>Customer content</div></AuthGate>);

    expect(screen.getByText('Không thể truy cập trang này')).toBeInTheDocument();
    expect(screen.queryByText('Customer content')).not.toBeInTheDocument();
  });

  it('renders customer-owned content for CUSTOMER', () => {
    authState = { ...authState, isAuthenticated: true, roles: ['CUSTOMER'] };

    render(<AuthGate><div>Customer content</div></AuthGate>);

    expect(screen.getByText('Customer content')).toBeInTheDocument();
  });

  it('renders admin content only for ADMIN', () => {
    authState = { ...authState, isAuthenticated: true, roles: ['ADMIN'] };

    render(<AuthGate requiredRole="ADMIN"><div>Admin content</div></AuthGate>);

    expect(screen.getByText('Admin content')).toBeInTheDocument();
  });

  it('does not treat CUSTOMER as ADMIN', () => {
    authState = { ...authState, isAuthenticated: true, roles: ['CUSTOMER'] };

    render(<AuthGate requiredRole="ADMIN"><div>Admin content</div></AuthGate>);

    expect(screen.getByText('Không thể truy cập trang này')).toBeInTheDocument();
    expect(screen.queryByText('Admin content')).not.toBeInTheDocument();
  });
});
