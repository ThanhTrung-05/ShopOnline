import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { List, MagnifyingGlass, ShoppingBag, SignOut, X } from '@phosphor-icons/react';
import { Routes, Route, Navigate, NavLink, Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from './auth/useAuth';
import { authApi, type SessionData } from './api/authApi';
import { customerApi } from './api/customerApi';
import AuthGate from './components/AuthGate';
import NotificationBell from './components/NotificationBell';
import RegisterPage from './pages/RegisterPage';
import ProductsPage from './pages/ProductsPage';
import ProductDetailPage from './pages/ProductDetailPage';
import CartPage from './pages/CartPage';
import ProfilePage from './pages/ProfilePage';
import AddressesPage from './pages/AddressesPage';
import ShippingPage from './pages/ShippingPage';
import OrderStatusPage from './pages/OrderStatusPage';
import OperationsOrderPage from './pages/OperationsOrderPage';
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
  { to: '/orders/status', label: 'Theo dõi đơn hàng' },
];
const ADMIN_NAV: NavItem[] = [
  { to: '/admin/products', label: 'Quản lý sản phẩm' },
  { to: '/admin/categories', label: 'Quản lý danh mục' },
  { to: '/operations/orders', label: 'Quản lý đơn hàng' },
];
const WAREHOUSE_NAV: NavItem[] = [
  { to: '/operations/orders', label: 'Quản lý đơn hàng' },
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

  const destination = roles.includes('ADMIN')
    ? '/admin/products'
    : roles.includes('WAREHOUSE_STAFF')
      ? '/operations/orders'
      : '/products';
  return <Navigate to={destination} replace />;
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
  const location = useLocation();
  const navigate = useNavigate();
  const cartItems = useCartStore((state) => state.items);
  const [session, setSession] = useState<SessionData | null>(null);
  const [customerName, setCustomerName] = useState<string | null>(null);
  const [sessionError, setSessionError] = useState<string | null>(null);
  const [navigationOpen, setNavigationOpen] = useState(false);
  const [globalSearch, setGlobalSearch] = useState(
    () => new URLSearchParams(location.search).get('search') ?? '',
  );
  const isAdmin = roles.includes('ADMIN');
  const isWarehouse = roles.includes('WAREHOUSE_STAFF');
  const isCustomer = roles.includes('CUSTOMER');
  const isOperations = isAdmin || isWarehouse;
  const navItems = isAdmin ? ADMIN_NAV : isWarehouse ? WAREHOUSE_NAV : isCustomer ? CUSTOMER_NAV : GUEST_NAV;
  const cartCount = useMemo(
    () => cartItems.reduce((count, item) => count + item.quantity, 0),
    [cartItems],
  );
  const accountLabel = isAdmin
    ? 'Quản trị viên'
    : isWarehouse
      ? 'Nhân viên kho'
      : isCustomer
        ? 'Khách hàng'
        : 'Tài khoản';

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

  useEffect(() => {
    let active = true;
    setCustomerName(null);

    if (!isAuthenticated || !isCustomer) {
      return () => {
        active = false;
      };
    }

    customerApi
      .getProfile()
      .then((response) => {
        const fullName = response.data.data?.fullName?.trim();
        if (active && fullName) {
          setCustomerName(fullName);
        }
      })
      .catch(() => {
        if (active) {
          setCustomerName(null);
        }
      });

    return () => {
      active = false;
    };
  }, [isAuthenticated, isCustomer, username]);

  useEffect(() => {
    setNavigationOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (location.pathname === '/products') {
      setGlobalSearch(new URLSearchParams(location.search).get('search') ?? '');
    }
  }, [location.pathname, location.search]);

  useEffect(() => {
    if (!navigationOpen) return undefined;

    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setNavigationOpen(false);
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [navigationOpen]);

  const submitGlobalSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const params = location.pathname === '/products'
      ? new URLSearchParams(location.search)
      : new URLSearchParams();
    const query = globalSearch.trim();
    if (query) params.set('search', query);
    else params.delete('search');
    params.delete('page');
    const queryString = params.toString();
    navigate(`/products${queryString ? `?${queryString}` : ''}`);
    setNavigationOpen(false);
  };

  const displayName = customerName ?? session?.username ?? username ?? 'Tài khoản';

  return (
    <header className={isOperations ? 'site-header site-header--operations' : 'site-header site-header--storefront'}>
      <div className="container header-inner">
        <Link className="brand" to="/" aria-label="ShopOnline">
          <span className="brand-mark">SO</span>
          <span><strong>ShopOnline</strong><small>{isOperations ? 'Không gian vận hành' : 'Mua sắm mỗi ngày'}</small></span>
        </Link>

        {!isOperations && (
          <form className="header-search" role="search" onSubmit={submitGlobalSearch}>
            <label className="sr-only" htmlFor="global-product-search">Tìm kiếm sản phẩm</label>
            <MagnifyingGlass size={19} aria-hidden="true" />
            <input
              id="global-product-search"
              type="search"
              value={globalSearch}
              onChange={(event) => setGlobalSearch(event.target.value)}
              placeholder="Tìm tên sản phẩm..."
              autoComplete="off"
            />
            <button type="submit" aria-label="Tìm kiếm">
              <MagnifyingGlass size={18} weight="bold" aria-hidden="true" />
            </button>
          </form>
        )}

        <button
          type="button"
          className="navigation-toggle"
          aria-label={navigationOpen ? 'Đóng điều hướng' : 'Mở điều hướng'}
          aria-expanded={navigationOpen}
          aria-controls="site-navigation"
          onClick={() => setNavigationOpen((open) => !open)}
        >
          {navigationOpen ? (
            <X size={21} weight="bold" aria-hidden="true" />
          ) : (
            <List size={22} weight="bold" aria-hidden="true" />
          )}
        </button>

        <nav
          id="site-navigation"
          className={navigationOpen ? 'main-nav is-open' : 'main-nav'}
          aria-label="Điều hướng chính"
        >
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              aria-label={item.to === '/cart' && cartCount > 0 ? `Giỏ hàng, ${cartCount} sản phẩm` : undefined}
              className={({ isActive }) => [
                'nav-link',
                item.to === '/cart' ? 'nav-link--cart' : '',
                item.to.startsWith('/admin') || item.to.startsWith('/operations') ? 'nav-link--operations' : '',
                isActive ? 'active' : '',
              ].filter(Boolean).join(' ')}
              onClick={() => setNavigationOpen(false)}
            >
              {item.to === '/cart' && <ShoppingBag size={18} aria-hidden="true" />}
              <span>{item.label}</span>
              {item.to === '/cart' && cartCount > 0 && (
                <span className="cart-count" aria-hidden="true">{cartCount > 99 ? '99+' : cartCount}</span>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="auth-actions">
          {isInitializing ? (
            <span className="auth-loading"><span className="spinner spinner-dark" /> Đang tải...</span>
          ) : isAuthenticated ? (
            <>
              {isCustomer && <NotificationBell identityKey={username ?? ''} />}
              <div className="user-chip">
                <span className="avatar">{displayName.slice(0, 1).toUpperCase()}</span>
                <span>
                  <strong>{displayName}</strong>
                  <small>{accountLabel}</small>
                </span>
              </div>
              <button type="button" className="btn btn-ghost btn-sm header-logout" onClick={logout}>
                <SignOut size={17} aria-hidden="true" />
                <span>Đăng xuất</span>
              </button>
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

function AppFooter() {
  const { isAuthenticated, roles = [] } = useAuth();
  const isAdmin = roles.includes('ADMIN');
  const isWarehouse = roles.includes('WAREHOUSE_STAFF');
  const isCustomer = roles.includes('CUSTOMER');

  if (isAdmin || isWarehouse) {
    return (
      <footer className="site-footer site-footer--operations">
        <div className="container footer-inner">
          <div><strong>ShopOnline</strong><span>Không gian vận hành</span></div>
          <p>{isAdmin ? 'Quản lý danh mục, sản phẩm và đơn hàng trong cùng một quy trình.' : 'Theo dõi và cập nhật đơn hàng trong một quy trình tập trung.'}</p>
        </div>
      </footer>
    );
  }

  return (
    <footer className="site-footer">
      <div className="container footer-grid">
        <div className="footer-brand">
          <span className="brand-mark" aria-hidden="true">SO</span>
          <div>
            <strong>ShopOnline</strong>
            <p>Mua sắm thiết yếu, tìm kiếm rõ ràng và theo dõi đơn hàng trong một tài khoản.</p>
          </div>
        </div>
        <nav className="footer-links" aria-label="Điều hướng cuối trang">
          <Link to="/products">Sản phẩm</Link>
          {isCustomer && <Link to="/cart">Giỏ hàng</Link>}
          {isCustomer && <Link to="/orders/status">Đơn hàng</Link>}
          {isCustomer && <Link to="/addresses">Địa chỉ</Link>}
          {!isAuthenticated && <Link to="/register">Tạo tài khoản</Link>}
        </nav>
      </div>
    </footer>
  );
}

export default function App() {
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">Chuyển đến nội dung chính</a>
      <SessionBar />
      <div id="main-content" className="app-content" tabIndex={-1}>
        <Routes>
          <Route path="/" element={<RoleLandingRedirect />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/products" element={<ProductsPage />} />
          <Route path="/products/:id" element={<ProductDetailPage />} />
          <Route path="/cart" element={<AuthGate><CartPage /></AuthGate>} />
          <Route path="/profile" element={<AuthGate><ProfilePage /></AuthGate>} />
          <Route path="/addresses" element={<AuthGate><AddressesPage /></AuthGate>} />
          <Route path="/shipping" element={<AuthGate><ShippingPage /></AuthGate>} />
          <Route path="/orders/status" element={<AuthGate><OrderStatusPage /></AuthGate>} />
          <Route path="/admin/products" element={<AuthGate requiredRole="ADMIN"><AdminProductPage /></AuthGate>} />
          <Route path="/admin/categories" element={<AuthGate requiredRole="ADMIN"><AdminCategoryPage /></AuthGate>} />
          <Route
            path="/operations/orders"
            element={(
              <AuthGate requiredRole={['ADMIN', 'WAREHOUSE_STAFF']}>
                <OperationsOrderPage />
              </AuthGate>
            )}
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
      <AppFooter />
    </div>
  );
}
