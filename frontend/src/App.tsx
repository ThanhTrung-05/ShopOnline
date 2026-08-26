import { useEffect, useState } from 'react';
import { Routes, Route, Navigate, NavLink, Link } from 'react-router-dom';
import { useAuth } from './auth/useAuth';
import { authApi, type SessionData } from './api/authApi';
import AuthGate from './components/AuthGate';
import RegisterPage from './pages/RegisterPage';
import ProductsPage from './pages/ProductsPage';
import ProductDetailPage from './pages/ProductDetailPage';
import CartPage from './pages/CartPage';
import ProfilePage from './pages/ProfilePage';
import AddressesPage from './pages/AddressesPage';
import ShippingPage from './pages/ShippingPage';
import AdminProductPage from './pages/AdminProductPage';
import AdminCategoryPage from './pages/AdminCategoryPage';
import { useAuthStore } from './store/authStore';
import { useCartStore } from './store/cartStore';

type NavItem = { to: string; label: string };

const GUEST_NAV: NavItem[] = [{ to: '/products', label: 'Sản phẩm' }];
const CUSTOMER_NAV: NavItem[] = [
  { to: '/products', label: 'Sản phẩm' },
  { to: '/cart', label: 'Giỏ hàng' },
  { to: '/profile', label: 'Hồ sơ' },
  { to: '/addresses', label: 'Địa chỉ' },
  { to: '/shipping', label: 'Giao hàng' },
];
const ADMIN_NAV: NavItem[] = [
  { to: '/admin/products', label: 'Quản lý sản phẩm' },
  { to: '/admin/categories', label: 'Quản lý danh mục' },
];

function RoleLandingRedirect() {
  const { isInitializing, roles } = useAuth();

  if (isInitializing) {
    return (
      <main className="page">
        <div className="container narrow-container">
          <div className="state-card" aria-live="polite">
            <span className="spinner spinner-dark" />
            <p>Đang tải...</p>
          </div>
        </div>
      </main>
    );
  }

  return <Navigate to={roles.includes('ADMIN') ? '/admin/products' : '/products'} replace />;
}

function SessionBar() {
  const {
    isAuthenticated,
    isInitializing,
    username,
    roles = [],
    error: authError,
    login,
    logout,
  } = useAuth();
  const [session, setSession] = useState<SessionData | null>(null);
  const [sessionError, setSessionError] = useState<string | null>(null);
  const isAdmin = roles.includes('ADMIN');
  const isCustomer = roles.includes('CUSTOMER');
  const navItems = isAdmin ? ADMIN_NAV : isCustomer ? CUSTOMER_NAV : GUEST_NAV;
  const accountLabel = isAdmin ? 'Quản trị viên' : isCustomer ? 'Khách hàng' : 'Tài khoản';

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
    if (isCustomer) {
      void useCartStore.getState().loadCart();
    }

    authApi
      .session()
      .then((response) => {
        setSession(response.data.data);
        setSessionError(null);
      })
      .catch(() => setSessionError('Không thể tải thông tin tài khoản.'));
  }, [isAuthenticated, isCustomer, username]);

  return (
    <header className="site-header">
      <div className="container header-inner">
        <Link className="brand" to="/" aria-label="ShopOnline">
          <span className="brand-mark">SO</span>
          <span><strong>ShopOnline</strong><small>Mua sắm trực tuyến</small></span>
        </Link>

        <nav className="main-nav" aria-label="Điều hướng chính">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="auth-actions">
          {isInitializing ? (
            <span className="auth-loading"><span className="spinner spinner-dark" /> Đang tải...</span>
          ) : isAuthenticated ? (
            <>
              <div className="user-chip">
                <span className="avatar">{(session?.username ?? username ?? 'C').slice(0, 1).toUpperCase()}</span>
                <span>
                  <strong>{session?.username ?? username ?? 'Tài khoản'}</strong>
                  <small>{accountLabel}</small>
                </span>
              </div>
              <button type="button" className="btn btn-ghost btn-sm" onClick={logout}>Đăng xuất</button>
            </>
          ) : (
            <>
              <button type="button" className="btn btn-primary btn-sm" onClick={login}>Đăng nhập</button>
              <Link className="btn btn-ghost btn-sm" to="/register">Đăng ký</Link>
            </>
          )}
        </div>
      </div>
      {(authError || sessionError) && (
        <div className="header-error" role="alert">{authError ?? sessionError}</div>
      )}
    </header>
  );
}

export default function App() {
  return (
    <>
      <SessionBar />
      <Routes>
        <Route path="/" element={<RoleLandingRedirect />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/products" element={<ProductsPage />} />
        <Route path="/products/:id" element={<ProductDetailPage />} />
        <Route path="/cart" element={<AuthGate><CartPage /></AuthGate>} />
        <Route path="/profile" element={<AuthGate><ProfilePage /></AuthGate>} />
        <Route path="/addresses" element={<AuthGate><AddressesPage /></AuthGate>} />
        <Route path="/shipping" element={<AuthGate><ShippingPage /></AuthGate>} />
        <Route path="/admin/products" element={<AuthGate requiredRole="ADMIN"><AdminProductPage /></AuthGate>} />
        <Route path="/admin/categories" element={<AuthGate requiredRole="ADMIN"><AdminCategoryPage /></AuthGate>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
