import { useCallback, useEffect, useState, type FormEvent } from 'react';
import toast from 'react-hot-toast';
import { customerApi, type CustomerProfile } from '../api/customerApi';
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
    <main className="page">
      <div className="container content-container">
        <div className="page-heading">
          <span className="eyebrow">Tài khoản</span>
          <h1>Thông tin tài khoản</h1>
          <p>Quản lý thông tin liên hệ của bạn.</p>
        </div>

        {loading ? (
          <div className="state-card"><span className="spinner spinner-dark" /> Đang tải...</div>
        ) : error && !profile ? (
          <div className="state-card">
            <div className="alert alert-error" role="alert">{error}</div>
            <button className="btn btn-ghost" onClick={() => void loadProfile()}>Thử lại</button>
          </div>
        ) : profile ? (
          <div className="split-layout">
            <section className="card card-p">
              <span className="badge badge-success">{profile.status === 'ACTIVE' ? 'Đang hoạt động' : 'Tạm ngừng'}</span>
              <h2>{profile.fullName}</h2>
              <dl className="detail-list">
                <div><dt>Email</dt><dd>{profile.email}</dd></div>
                <div><dt>Cập nhật gần nhất</dt><dd>{new Date(profile.updatedAt).toLocaleString('vi-VN')}</dd></div>
              </dl>
            </section>

            <form className="card card-p form-stack" onSubmit={updateProfile}>
              <h2>Cập nhật thông tin</h2>
              <label className="field">
                <span>Họ tên</span>
                <input className="form-input" required maxLength={200} value={fullName} onChange={(event) => setFullName(event.target.value)} />
              </label>
              <label className="field">
                <span>Số điện thoại</span>
                <input className="form-input" type="tel" value={phone} onChange={(event) => setPhone(event.target.value)} />
              </label>
              {error && <div className="alert alert-error" role="alert">{error}</div>}
              <button className="btn btn-primary" type="submit" disabled={saving}>
                {saving ? 'Đang lưu...' : 'Lưu thay đổi'}
              </button>
            </form>
          </div>
        ) : null}
      </div>
    </main>
  );
}
