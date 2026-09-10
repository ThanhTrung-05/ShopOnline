import { useState, type FormEvent } from 'react';
import { ArrowLeft, CheckCircle, MapPinLine, Package } from '@phosphor-icons/react';
import toast from 'react-hot-toast';
import { Link } from 'react-router-dom';
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
    <main className="page registration-page">
      <div className="container registration-layout">
        <section className="registration-intro">
          <Link className="back-link" to="/products"><ArrowLeft size={17} aria-hidden="true" /> Tiếp tục xem sản phẩm</Link>
          <span className="page-context">Tài khoản ShopOnline</span>
          <h1>Mua sắm và theo dõi đơn hàng ở cùng một nơi.</h1>
          <p>Tạo tài khoản để lưu thông tin nhận hàng và quay lại đơn hàng của bạn khi cần.</p>
          <ul className="registration-benefits">
            <li><MapPinLine size={21} aria-hidden="true" /><span><strong>Lưu địa chỉ giao hàng</strong><small>Chọn lại địa chỉ đã dùng ở bước giao hàng.</small></span></li>
            <li><Package size={21} aria-hidden="true" /><span><strong>Theo dõi đơn hàng</strong><small>Xem trạng thái và chi tiết sản phẩm đã đặt.</small></span></li>
          </ul>
        </section>

        <div className="registration-panel">
          {!created ? (
            <form className="card card-p form-stack registration-form" onSubmit={submit}>
              <div className="form-section-heading">
                <span>Bắt đầu</span>
                <h2>Đăng ký tài khoản</h2>
                <p>Dùng email của bạn để đăng nhập sau khi tạo tài khoản.</p>
              </div>
              <label className="field">
                <span>Email đăng nhập</span>
                <input
                  className="form-input"
                  type="email"
                  autoComplete="email"
                  inputMode="email"
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
                  aria-describedby="password-hint"
                  value={form.password}
                  onChange={(event) => setForm({ ...form, password: event.target.value })}
                />
                <small id="password-hint">Từ 8 đến 64 ký tự.</small>
              </label>
              <label className="field">
                <span>Họ tên</span>
                <input
                  className="form-input"
                  autoComplete="name"
                  required
                  maxLength={200}
                  value={form.fullName}
                  onChange={(event) => setForm({ ...form, fullName: event.target.value })}
                />
              </label>
              <label className="field">
                <span>Số điện thoại <small>Không bắt buộc</small></span>
                <input
                  className="form-input"
                  type="tel"
                  autoComplete="tel"
                  inputMode="tel"
                  placeholder="0987654321"
                  value={form.phone}
                  onChange={(event) => setForm({ ...form, phone: event.target.value })}
                />
              </label>

              {error && <div className="alert alert-error" role="alert">{error}</div>}
              <button className="btn btn-primary btn-full" type="submit" disabled={submitting}>
                {submitting ? <><span className="spinner spinner-inline" /> Đang đăng ký...</> : 'Tạo tài khoản'}
              </button>
              {!isAuthenticated && <p className="form-footnote">Đã có tài khoản? <button type="button" className="text-button" onClick={login}>Đăng nhập</button></p>}
            </form>
          ) : (
            <section className="card card-p registration-success" aria-live="polite">
              <CheckCircle size={44} weight="fill" aria-hidden="true" />
              <span className="page-context">Tài khoản đã sẵn sàng</span>
              <h2>Chào {created.fullName}</h2>
              <p>Bạn có thể đăng nhập bằng email <strong>{created.email}</strong> và bắt đầu mua sắm.</p>
              <div className="button-row">
                {!isAuthenticated && <button type="button" className="btn btn-primary" onClick={login}>Đăng nhập ngay</button>}
                <Link className="btn btn-ghost" to="/products">Xem sản phẩm</Link>
              </div>
            </section>
          )}
        </div>

      </div>
    </main>
  );
}
