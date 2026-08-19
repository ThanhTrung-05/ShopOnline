import { useEffect, useState } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './auth/useAuth';
import apiClient from './api/axios';
import ProductsPage from './pages/ProductsPage';
import ProductDetailPage from './pages/ProductDetailPage';
import { useAuthStore } from './store/authStore';
import { useCartStore } from './store/cartStore';

interface SessionData {
  authenticated: boolean;
  subject: string;
  username: string | null;
}

function SessionBar() {
  const { isAuthenticated, isInitializing, username, login, logout } = useAuth();
  const [session, setSession] = useState<SessionData | null>(null);
  const [sessionError, setSessionError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAuthenticated) {
      setSession(null);
      setSessionError(null);
      useAuthStore.getState().logout();
      useCartStore.getState().clearLocal();
      return;
    }

    useAuthStore.getState().login(username ?? 'customer');
    useCartStore.getState().clearLocal();
    void useCartStore.getState().loadCart();

    apiClient
      .get<{ data: SessionData }>('/auth/session')
      .then((response) => {
        setSession(response.data.data);
        setSessionError(null);
      })
      .catch(() => setSessionError('Unable to load session information.'));
  }, [isAuthenticated, username]);

  return (
    <div style={{
      borderBottom: '1px solid var(--border)',
      background: 'var(--bg-card)',
    }}>
      <div
        className="container"
        style={{
          minHeight: 56,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 16,
        }}
      >
        <strong>ShopOnline</strong>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          {sessionError && <span role="alert" style={{ color: 'var(--warning)', fontSize: '0.85rem' }}>{sessionError}</span>}
          {isInitializing ? (
            <span style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>Checking session...</span>
          ) : isAuthenticated ? (
            <>
              <span style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
                {session?.username ?? username ?? 'Customer'}
              </span>
              <button type="button" className="btn btn-ghost btn-sm" onClick={logout}>
                Logout
              </button>
            </>
          ) : (
            <button type="button" className="btn btn-primary btn-sm" onClick={login}>
              Login
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <>
      <SessionBar />
      <Routes>
        <Route path="/" element={<Navigate to="/products" replace />} />
        <Route path="/products" element={<ProductsPage />} />
        <Route path="/products/:id" element={<ProductDetailPage />} />
      </Routes>
    </>
  );
}
