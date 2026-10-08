import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
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

vi.mock('../auth/useAuth', () => ({
  useAuth: () => authState,
}));

import AuthGate from './AuthGate';

function renderGate(element: ReactNode) {
  return render(<MemoryRouter>{element}</MemoryRouter>);
}

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
    renderGate(<AuthGate><div>Customer content</div></AuthGate>);

    expect(screen.getByText('Đăng nhập để tiếp tục')).toBeInTheDocument();
    expect(screen.queryByText('Customer content')).not.toBeInTheDocument();
  });

  it('does not treat ADMIN as CUSTOMER', () => {
    authState = { ...authState, isAuthenticated: true, roles: ['ADMIN'] };

    renderGate(<AuthGate><div>Customer content</div></AuthGate>);

    expect(screen.getByText('Không thể truy cập trang này')).toBeInTheDocument();
    expect(screen.queryByText('Customer content')).not.toBeInTheDocument();
  });

  it('renders customer-owned content for CUSTOMER', () => {
    authState = { ...authState, isAuthenticated: true, roles: ['CUSTOMER'] };

    renderGate(<AuthGate><div>Customer content</div></AuthGate>);

    expect(screen.getByText('Customer content')).toBeInTheDocument();
  });

  it('renders admin content only for ADMIN', () => {
    authState = { ...authState, isAuthenticated: true, roles: ['ADMIN'] };

    renderGate(<AuthGate requiredRole="ADMIN"><div>Admin content</div></AuthGate>);

    expect(screen.getByText('Admin content')).toBeInTheDocument();
  });

  it('does not treat CUSTOMER as ADMIN', () => {
    authState = { ...authState, isAuthenticated: true, roles: ['CUSTOMER'] };

    renderGate(<AuthGate requiredRole="ADMIN"><div>Admin content</div></AuthGate>);

    expect(screen.getByText('Không thể truy cập trang này')).toBeInTheDocument();
    expect(screen.queryByText('Admin content')).not.toBeInTheDocument();
  });

  it('accepts either ADMIN or WAREHOUSE_STAFF for shared operations content', () => {
    authState = { ...authState, isAuthenticated: true, roles: ['WAREHOUSE_STAFF'] };

    const view = renderGate(
      <AuthGate requiredRole={['ADMIN', 'WAREHOUSE_STAFF']}><div>Operations content</div></AuthGate>,
    );

    expect(screen.getByText('Operations content')).toBeInTheDocument();

    authState = { ...authState, roles: ['ADMIN'] };
    view.rerender(
      <MemoryRouter>
        <AuthGate requiredRole={['ADMIN', 'WAREHOUSE_STAFF']}><div>Operations content</div></AuthGate>
      </MemoryRouter>,
    );

    expect(screen.getByText('Operations content')).toBeInTheDocument();
  });

  it('blocks CUSTOMER from shared operations content', () => {
    authState = { ...authState, isAuthenticated: true, roles: ['CUSTOMER'] };

    renderGate(
      <AuthGate requiredRole={['ADMIN', 'WAREHOUSE_STAFF']}><div>Operations content</div></AuthGate>,
    );

    expect(screen.getByText('Không thể truy cập trang này')).toBeInTheDocument();
    expect(screen.queryByText('Operations content')).not.toBeInTheDocument();
  });
});
