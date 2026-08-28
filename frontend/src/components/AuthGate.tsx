import type { ReactNode } from 'react';
import { useAuth } from '../auth/useAuth';

type ProtectedRole = 'CUSTOMER' | 'ADMIN';

type AuthGateProps = {
  children: ReactNode;
  requiredRole?: ProtectedRole;
};

export default function AuthGate({ children, requiredRole = 'CUSTOMER' }: AuthGateProps) {
  const { isAuthenticated, isInitializing, roles, login, error } = useAuth();

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

  if (!isAuthenticated) {
    return (
      <main className="page">
        <div className="container narrow-container">
          <section className="card card-p auth-required">
            <span className="eyebrow">Tài khoản</span>
            <h1>Đăng nhập để tiếp tục</h1>
            <p className="muted">Vui lòng đăng nhập để sử dụng chức năng này.</p>
            {error && <div className="alert alert-error" role="alert">{error}</div>}
            <button type="button" className="btn btn-primary" onClick={login}>
              Đăng nhập
            </button>
          </section>
        </div>
      </main>
    );
  }

  if (!roles.includes(requiredRole)) {
    return (
      <main className="page">
        <div className="container narrow-container">
          <section className="card card-p auth-required">
            <span className="eyebrow">Quyền truy cập</span>
            <h1>Không thể truy cập trang này</h1>
            <p className="muted">Tài khoản của bạn không có quyền sử dụng chức năng này.</p>
          </section>
        </div>
      </main>
    );
  }

  return <>{children}</>;
}
