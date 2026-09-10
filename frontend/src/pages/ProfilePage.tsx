import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { CalendarBlank, EnvelopeSimple, Phone, UserCircle } from '@phosphor-icons/react';
import toast from 'react-hot-toast';
import { customerApi, type CustomerProfile } from '../api/customerApi';
import AccountNav from '../components/AccountNav';
import { getApiErrorMessage } from '../utils/apiError';

export default function ProfilePage() {
  const [profile, setProfile] = useState<CustomerProfile | null>(null);
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadProfile = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await customerApi.getProfile();
      const nextProfile = response.data.data;
      setProfile(nextProfile);
      setFullName(nextProfile.fullName);
      setPhone(nextProfile.phone ?? '');
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Không thể tải thông tin tài khoản.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadProfile();
  }, [loadProfile]);

  const updateProfile = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const response = await customerApi.updateProfile({
        fullName: fullName.trim() || null,
        phone: phone.trim() || null,
      });
      const nextProfile = response.data.data;
      setProfile(nextProfile);
      setFullName(nextProfile.fullName);
      setPhone(nextProfile.phone ?? '');
      toast.success('Cập nhật thành công');
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Có lỗi xảy ra. Vui lòng thử lại.');
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <main className="page account-page">
      <div className="container account-container">
        <header className="page-heading page-heading--compact">
          <span className="page-context">Tài khoản của tôi</span>
          <h1>Thông tin tài khoản</h1>
          <p>Kiểm tra thông tin đăng nhập và cập nhật cách ShopOnline liên hệ với bạn.</p>
        </header>

        <div className="account-shell">
          <AccountNav />
          <section className="account-workspace" aria-busy={loading}>
            {loading ? (
              <div className="state-card account-state" aria-live="polite">
                <span className="spinner spinner-dark" />
                <div><h2>Đang tải hồ sơ</h2><p>Thông tin tài khoản sẽ xuất hiện sau giây lát.</p></div>
              </div>
            ) : error && !profile ? (
              <div className="state-card account-state">
                <UserCircle size={34} aria-hidden="true" />
                <div><h2>Chưa thể tải hồ sơ</h2><p role="alert">{error}</p></div>
                <button className="btn btn-ghost" onClick={() => void loadProfile()}>Thử lại</button>
              </div>
            ) : profile ? (
              <div className="profile-layout">
                <section className="account-identity-panel">
                  <div className="account-avatar" aria-hidden="true">{profile.fullName.slice(0, 1).toUpperCase()}</div>
                  <div>
                    <span className={profile.status === 'ACTIVE' ? 'status-badge status-active' : 'status-badge status-inactive'}>
                      {profile.status === 'ACTIVE' ? 'Đang hoạt động' : 'Tạm ngừng'}
                    </span>
                    <h2>{profile.fullName}</h2>
                    <p>Thông tin đang được dùng cho tài khoản mua sắm này.</p>
                  </div>
                  <dl className="account-facts">
                    <div>
                      <dt><EnvelopeSimple size={18} aria-hidden="true" /> Email đăng nhập</dt>
                      <dd>{profile.email}</dd>
                    </div>
                    <div>
                      <dt><Phone size={18} aria-hidden="true" /> Số điện thoại</dt>
                      <dd>{profile.phone || 'Chưa cập nhật'}</dd>
                    </div>
                    <div>
                      <dt><CalendarBlank size={18} aria-hidden="true" /> Cập nhật gần nhất</dt>
                      <dd>{new Date(profile.updatedAt).toLocaleString('vi-VN')}</dd>
                    </div>
                  </dl>
                </section>

                <form className="card card-p form-stack profile-form" onSubmit={updateProfile}>
                  <div className="form-section-heading">
                    <span>Thông tin liên hệ</span>
                    <h2>Cập nhật hồ sơ</h2>
                    <p>Những thay đổi này không ảnh hưởng đến email đăng nhập.</p>
                  </div>
                  <label className="field">
                    <span>Họ tên</span>
                    <input className="form-input" required maxLength={200} value={fullName} onChange={(event) => setFullName(event.target.value)} />
                  </label>
                  <label className="field">
                    <span>Số điện thoại</span>
                    <input className="form-input" type="tel" autoComplete="tel" value={phone} onChange={(event) => setPhone(event.target.value)} />
                  </label>
                  {error && <div className="alert alert-error" role="alert">{error}</div>}
                  <button className="btn btn-primary profile-save" type="submit" disabled={saving}>
                    {saving ? <><span className="spinner spinner-inline" /> Đang lưu...</> : 'Lưu thay đổi'}
                  </button>
                </form>
              </div>
            ) : null}
          </section>
        </div>
      </div>
    </main>
  );
}
