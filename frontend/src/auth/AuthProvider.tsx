import { createContext, useEffect, useRef, useState, type ReactNode } from 'react';
import keycloak from './keycloak';
import { subscribeForcedLogout } from './authEvents';

export interface AuthContextValue {
  isAuthenticated: boolean;
  isInitializing: boolean;
  username: string | null;
  login: () => void;
  logout: () => void;
  getToken: (minValiditySeconds?: number) => Promise<string | null>;
  clearAuthState: () => void;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const POST_LOGOUT_REDIRECT_URI = `${window.location.origin}/`;

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isInitializing, setIsInitializing] = useState(true);
  const [username, setUsername] = useState<string | null>(null);
  const initialized = useRef(false);

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
        setIsAuthenticated(authenticated);
        setUsername(authenticated ? (keycloak.tokenParsed?.preferred_username ?? null) : null);
      })
      .catch(() => {
        setIsAuthenticated(false);
        setUsername(null);
      })
      .finally(() => {
        setIsInitializing(false);
      });

    keycloak.onAuthLogout = () => {
      setIsAuthenticated(false);
      setUsername(null);
    };

    const unsubscribe = subscribeForcedLogout(() => {
      setIsAuthenticated(false);
      setUsername(null);
    });

    return unsubscribe;
  }, []);

  const login = () => {
    keycloak.login();
  };

  const logout = () => {
    keycloak.logout({ redirectUri: POST_LOGOUT_REDIRECT_URI });
  };

  const clearAuthState = () => {
    setIsAuthenticated(false);
    setUsername(null);
  };

  const getToken = async (minValiditySeconds = 30): Promise<string | null> => {
    if (!keycloak.authenticated) {
      return null;
    }
    try {
      await keycloak.updateToken(minValiditySeconds);
      return keycloak.token ?? null;
    } catch {
      clearAuthState();
      return null;
    }
  };

  return (
    <AuthContext.Provider
      value={{ isAuthenticated, isInitializing, username, login, logout, getToken, clearAuthState }}
    >
      {children}
    </AuthContext.Provider>
  );
}
