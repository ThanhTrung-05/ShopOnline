import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { AuthProvider } from './AuthProvider';
import { useAuth } from './useAuth';
import keycloak from './keycloak';
import { notifyForcedLogout } from './authEvents';

vi.mock('./keycloak', () => ({
  default: {
    init: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    updateToken: vi.fn(),
    authenticated: false,
    token: undefined,
    tokenParsed: undefined,
  },
}));

function Probe() {
  const { isAuthenticated, isInitializing, username, roles, error, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="initializing">{String(isInitializing)}</span>
      <span data-testid="authenticated">{String(isAuthenticated)}</span>
      <span data-testid="username">{username ?? ''}</span>
      <span data-testid="roles">{roles.join(',')}</span>
      <span data-testid="error">{error ?? ''}</span>
      <button data-testid="login" onClick={login}>
        login
      </button>
      <button data-testid="logout" onClick={logout}>
        logout
      </button>
    </div>
  );
}

describe('AuthProvider', () => {
  beforeEach(() => {
    vi.mocked(keycloak.init).mockReset();
    vi.mocked(keycloak.login).mockReset();
    vi.mocked(keycloak.logout).mockReset();
    vi.mocked(keycloak.updateToken).mockReset();
    Object.defineProperty(keycloak, 'authenticated', { value: false, writable: true, configurable: true });
    Object.defineProperty(keycloak, 'tokenParsed', { value: undefined, writable: true, configurable: true });
  });

  it('starts as not authenticated and not stuck initializing when guest', async () => {
    vi.mocked(keycloak.init).mockResolvedValue(false);

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('initializing').textContent).toBe('false'));
    expect(screen.getByTestId('authenticated').textContent).toBe('false');
  });

  it('does not call keycloak.login() during check-sso init (no forced redirect for guests)', async () => {
    vi.mocked(keycloak.init).mockResolvedValue(false);

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('initializing').textContent).toBe('false'));
    expect(keycloak.login).not.toHaveBeenCalled();
  });

  it('exposes authenticated state and username after a successful check-sso', async () => {
    vi.mocked(keycloak.init).mockResolvedValue(true);
    Object.defineProperty(keycloak, 'tokenParsed', {
      value: {
        preferred_username: 'test-customer',
        resource_access: { 'shoponline-backend': { roles: ['CUSTOMER'] } },
      },
      configurable: true,
    });

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('authenticated').textContent).toBe('true'));
    expect(screen.getByTestId('username').textContent).toBe('test-customer');
    expect(screen.getByTestId('roles').textContent).toBe('CUSTOMER');
  });

  it('accepts ADMIN from the backend client role claim', async () => {
    vi.mocked(keycloak.init).mockResolvedValue(true);
    Object.defineProperty(keycloak, 'tokenParsed', {
      value: {
        preferred_username: 'admin-user',
        resource_access: { 'shoponline-backend': { roles: ['ADMIN'] } },
      },
      configurable: true,
    });

    render(<AuthProvider><Probe /></AuthProvider>);

    await waitFor(() => expect(screen.getByTestId('authenticated').textContent).toBe('true'));
    expect(screen.getByTestId('roles').textContent).toBe('ADMIN');
  });

  it('ignores realm roles and roles belonging to other clients', async () => {
    vi.mocked(keycloak.init).mockResolvedValue(true);
    Object.defineProperty(keycloak, 'tokenParsed', {
      value: {
        preferred_username: 'customer-user',
        realm_access: { roles: ['ADMIN'] },
        resource_access: {
          'another-client': { roles: ['ADMIN'] },
          'shoponline-backend': { roles: ['CUSTOMER', 'UNKNOWN_ROLE'] },
        },
      },
      configurable: true,
    });

    render(<AuthProvider><Probe /></AuthProvider>);

    await waitFor(() => expect(screen.getByTestId('authenticated').textContent).toBe('true'));
    expect(screen.getByTestId('roles').textContent).toBe('CUSTOMER');
  });

  it('returns no business role when the backend client claim is malformed', async () => {
    vi.mocked(keycloak.init).mockResolvedValue(true);
    Object.defineProperty(keycloak, 'tokenParsed', {
      value: {
        preferred_username: 'unknown-user',
        resource_access: { 'shoponline-backend': { roles: 'ADMIN' } },
      },
      configurable: true,
    });

    render(<AuthProvider><Probe /></AuthProvider>);

    await waitFor(() => expect(screen.getByTestId('authenticated').textContent).toBe('true'));
    expect(screen.getByTestId('roles').textContent).toBe('');
  });

  it('returns to the role landing route after login', async () => {
    vi.mocked(keycloak.init).mockResolvedValue(false);

    render(<AuthProvider><Probe /></AuthProvider>);
    await waitFor(() => expect(screen.getByTestId('initializing').textContent).toBe('false'));

    screen.getByTestId('login').click();

    expect(keycloak.login).toHaveBeenCalledWith({ redirectUri: `${window.location.origin}/` });
  });

  it('logs out with a trailing-slash redirect URI matching the registered http://localhost:3000/* pattern', async () => {
    vi.mocked(keycloak.init).mockResolvedValue(false);

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('initializing').textContent).toBe('false'));

    screen.getByTestId('logout').click();

    expect(keycloak.logout).toHaveBeenCalledWith({ redirectUri: `${window.location.origin}/` });
  });

  it('clears authenticated state when a forced logout is notified (failed refresh)', async () => {
    vi.mocked(keycloak.init).mockResolvedValue(true);
    Object.defineProperty(keycloak, 'tokenParsed', {
      value: { preferred_username: 'test-customer' },
      configurable: true,
    });

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('authenticated').textContent).toBe('true'));

    notifyForcedLogout();

    await waitFor(() => expect(screen.getByTestId('authenticated').textContent).toBe('false'));
    expect(screen.getByTestId('username').textContent).toBe('');
  });
});
