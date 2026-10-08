import type { ReactNode } from 'react';
import { LockKey, Storefront } from '@phosphor-icons/react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

type ProtectedRole = 'CUSTOMER' | 'WAREHOUSE_STAFF' | 'ADMIN';

type AuthGateProps = {
  children: ReactNode;
  requiredRole?: ProtectedRole | ProtectedRole[];
};

export default function AuthGate({ children, requiredRole = 'CUSTOMER' }: AuthGateProps) {
  const { isAuthenticated, isInitializing, roles, login, error } = useAuth();
  const acceptedRoles = Array.isArray(requiredRole) ? requiredRole : [requiredRole];

  if (isInitializing) {
    return (
      <main className="page gate-page">
        <div className="container narrow-container">
          <div className="state-card gate-state" aria-live="polite">
            <span className="spinner spinner-dark" />
            <div><h1>Đang kiểm tra phiên đăng nhập</h1><p>ShopOnline đang chuẩn bị nội dung dành cho tài khoản của bạn.</p></div>
          </div>
        </div>
      </main>
    );
  }

  if (!isAuthenticated) {
    return (
      <main className="page gate-page">
        <div className="container narrow-container">
          <section className="auth-required">
            <div className="gate-icon" aria-hidden="true"><LockKey size={31} /></div>
            <span className="page-context">Nội dung dành cho khách hàng</span>
            <h1>Đăng nhập để tiếp tục</h1>
            <p>Bạn cần đăng nhập để xem thông tin riêng của tài khoản và tiếp tục quy trình mua sắm.</p>
            {error && <div className="alert alert-error" role="alert">{error}</div>}
            <div className="button-row gate-actions">
              <button type="button" className="btn btn-primary" onClick={login}>Đăng nhập</button>
              <Link className="btn btn-ghost" to="/products"><Storefront size={18} aria-hidden="true" /> Xem sản phẩm</Link>
            </div>
          </section>
        </div>
      </main>
    );
  }

  if (!acceptedRoles.some((role) => roles.includes(role))) {
    return (
      <main className="page gate-page">
        <div className="container narrow-container">
          <section className="auth-required">
            <div className="gate-icon gate-icon--warning" aria-hidden="true"><LockKey size={31} /></div>
            <span className="page-context">Quyền truy cập</span>
            <h1>Không thể truy cập trang này</h1>
            <p>Tài khoản hiện tại không có vai trò cần thiết để sử dụng chức năng này.</p>
            <Link className="btn btn-ghost" to="/"><Storefront size={18} aria-hidden="true" /> Về trang phù hợp</Link>
          </section>
        </div>
      </main>
    );
  }

  return <>{children}</>;
}
