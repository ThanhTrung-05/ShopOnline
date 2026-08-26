import { createContext, useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import keycloak from './keycloak';
import { subscribeForcedLogout } from './authEvents';

type ParsedAccessToken = {
  preferred_username?: string;
  resource_access?: Record<string, { roles?: string[] }>;
};

const BACKEND_CLIENT_ID = 'shoponline-backend';
const SHOP_ROLES = new Set(['CUSTOMER', 'WAREHOUSE_STAFF', 'ADMIN']);

function readShopRoles(): string[] {
  const parsed = keycloak.tokenParsed as ParsedAccessToken | undefined;
  const clientRoles = parsed?.resource_access?.[BACKEND_CLIENT_ID]?.roles;

  if (!Array.isArray(clientRoles)) {
    return [];
  }

  return [...new Set(clientRoles)]
    .filter((role) => SHOP_ROLES.has(role))
    .sort();
}

export interface AuthContextValue {
  isAuthenticated: boolean;
  isInitializing: boolean;
  username: string | null;
  roles: string[];
  error: string | null;
  login: () => void;
  logout: () => void;
  getToken: (minValiditySeconds?: number) => Promise<string | null>;
  clearAuthState: () => void;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const POST_LOGOUT_REDIRECT_URI = window.location.origin + '/';
const POST_LOGIN_REDIRECT_URI = window.location.origin + '/';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isInitializing, setIsInitializing] = useState(true);
  const [username, setUsername] = useState<string | null>(null);
  const [roles, setRoles] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const initialized = useRef(false);

  const clearAuthState = useCallback(() => {
    setIsAuthenticated(false);
    setUsername(null);
    setRoles([]);
  }, []);

  const syncAuthenticatedState = useCallback((authenticated = Boolean(keycloak.authenticated)) => {
    setIsAuthenticated(authenticated);
    setUsername(keycloak.tokenParsed?.preferred_username ?? null);
    setRoles(readShopRoles());
    setError(null);
  }, []);

  useEffect(() => {
    if (initialized.current) {
      return;
    }
    initialized.current = true;

    keycloak
      .init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        pkceMethod: 'S256',
      })
      .then((authenticated) => {
        if (authenticated) {
          syncAuthenticatedState(true);
        } else {
          clearAuthState();
        }
      })
      .catch(() => {
        clearAuthState();
        setError('Không thể kết nối dịch vụ đăng nhập. Vui lòng thử lại sau.');
      })
      .finally(() => {
        setIsInitializing(false);
      });

    keycloak.onAuthLogout = clearAuthState;
    keycloak.onAuthRefreshSuccess = syncAuthenticatedState;
    keycloak.onAuthRefreshError = () => {
      clearAuthState();
      setError('Không thể làm mới phiên đăng nhập. Vui lòng đăng nhập lại.');
    };

    const unsubscribe = subscribeForcedLogout(() => {
      clearAuthState();
      setError('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
    });

    return unsubscribe;
  }, [clearAuthState, syncAuthenticatedState]);

  const login = () => {
    setError(null);
    void Promise.resolve(keycloak.login({ redirectUri: POST_LOGIN_REDIRECT_URI })).catch(() => {
      setError('Không thể mở trang đăng nhập. Vui lòng thử lại.');
    });
  };

  const logout = () => {
    void Promise.resolve(keycloak.logout({ redirectUri: POST_LOGOUT_REDIRECT_URI })).catch(() => {
      setError('Không thể đăng xuất. Vui lòng thử lại.');
    });
  };

  const getToken = async (minValiditySeconds = 30): Promise<string | null> => {
    if (!keycloak.authenticated) {
      return null;
    }
    try {
      await keycloak.updateToken(minValiditySeconds);
      syncAuthenticatedState();
      return keycloak.token ?? null;
    } catch {
      clearAuthState();
      setError('Không thể làm mới phiên đăng nhập. Vui lòng đăng nhập lại.');
      return null;
    }
  };

  return (
    <AuthContext.Provider
      value={{
        isAuthenticated,
        isInitializing,
        username,
        roles,
        error,
        login,
        logout,
        getToken,
        clearAuthState,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
