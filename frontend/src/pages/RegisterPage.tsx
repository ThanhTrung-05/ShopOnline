import { useState, type FormEvent } from 'react';
import toast from 'react-hot-toast';
import { authApi, type RegisteredCustomer } from '../api/authApi';
import { useAuth } from '../auth/useAuth';
import { getApiErrorMessage } from '../utils/apiError';

const initialForm = { email: '', password: '', fullName: '', phone: '' };

export default function RegisterPage() {
  const { isAuthenticated, login } = useAuth();
  const [form, setForm] = useState(initialForm);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<RegisteredCustomer | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setCreated(null);
    try {
      const response = await authApi.register({
        email: form.email.trim(),
        password: form.password,
        fullName: form.fullName.trim(),
        phone: form.phone.trim() || null,
      });
      setCreated(response.data.data);
      setForm(initialForm);
      toast.success('Đăng ký thành công');
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Đăng ký không thành công.');
      setError(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="page">
      <div className="container narrow-container">
        <div className="page-heading">
          <span className="eyebrow">Tạo tài khoản</span>
          <h1>Đăng ký tài khoản</h1>
          <p>Nhập thông tin để bắt đầu mua sắm tại ShopOnline.</p>
        </div>

        <form className="card card-p form-stack" onSubmit={submit}>
          <label className="field">
            <span>Email đăng nhập</span>
            <input
              className="form-input"
              type="email"
              autoComplete="email"
              required
              maxLength={200}
              value={form.email}
              onChange={(event) => setForm({ ...form, email: event.target.value })}
            />
          </label>
          <label className="field">
            <span>Mật khẩu</span>
            <input
              className="form-input"
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              maxLength={64}
              value={form.password}
              onChange={(event) => setForm({ ...form, password: event.target.value })}
            />
            <small>Từ 8 đến 64 ký tự.</small>
          </label>
          <label className="field">
            <span>Họ tên</span>
            <input
              className="form-input"
              required
              maxLength={200}
              value={form.fullName}
              onChange={(event) => setForm({ ...form, fullName: event.target.value })}
            />
          </label>
          <label className="field">
            <span>Số điện thoại (không bắt buộc)</span>
            <input
              className="form-input"
              type="tel"
              placeholder="0987654321"
              value={form.phone}
              onChange={(event) => setForm({ ...form, phone: event.target.value })}
            />
          </label>

          {error && <div className="alert alert-error" role="alert">{error}</div>}
          <button className="btn btn-primary btn-full" type="submit" disabled={submitting}>
            {submitting ? <><span className="spinner" /> Đang đăng ký...</> : 'Tạo tài khoản'}
          </button>
        </form>

        {created && (
          <section className="card card-p success-panel" aria-live="polite">
            <span className="badge badge-success">Thành công</span>
            <h2>{created.fullName}</h2>
            <dl className="detail-list">
              <div><dt>Email</dt><dd>{created.email}</dd></div>
              <div><dt>Trạng thái</dt><dd>{created.status === 'ACTIVE' ? 'Đang hoạt động' : 'Tạm ngừng'}</dd></div>
            </dl>
            {!isAuthenticated && (
              <button type="button" className="btn btn-primary" onClick={login}>
                Đăng nhập tài khoản vừa tạo
              </button>
            )}
          </section>
        )}
      </div>
    </main>
  );
}
