import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { addressApi, type Address } from '../api/addressApi';
import { getApiErrorMessage } from '../utils/apiError';

export default function ShippingPage() {
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadAddresses = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await addressApi.list();
      setAddresses(response.data.data ?? []);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Không thể tải địa chỉ giao hàng.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAddresses();
  }, [loadAddresses]);

  return (
    <main className="page">
      <div className="container content-container">
        <div className="page-heading">
          <span className="eyebrow">Giao hàng</span>
          <h1>Thông tin giao hàng</h1>
          <p>Kiểm tra địa chỉ nhận hàng trước khi tiếp tục.</p>
        </div>

        {error && (
          <div className="alert alert-error admin-alert" role="alert">
            <span>{error}</span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void loadAddresses()}>Thử lại</button>
          </div>
        )}

        {loading ? (
          <div className="state-card"><span className="spinner spinner-dark" /> Đang tải...</div>
        ) : addresses.length === 0 ? (
          <div className="state-card">
            <h2>Chưa có địa chỉ giao hàng</h2>
            <p>Thêm địa chỉ để chuẩn bị cho đơn hàng của bạn.</p>
            <Link className="btn btn-primary" to="/addresses">Thêm địa chỉ</Link>
          </div>
        ) : (
          <section className="card card-p form-stack" aria-label="Địa chỉ giao hàng">
            <div className="section-heading compact">
              <h2>Địa chỉ đã lưu</h2>
              <Link className="text-button" to="/addresses">Quản lý địa chỉ</Link>
            </div>
            {addresses.map((address) => (
              <article className={`choice-card shipping-address-card ${address.isDefault ? 'selected' : ''}`} key={address.addressId}>
                <span>
                  <strong>{address.recipientName} {address.isDefault && '· Mặc định'}</strong>
                  <small>{address.phone}</small>
                  <small>{[address.line1, address.ward, address.district, address.province].filter(Boolean).join(', ')}</small>
                </span>
              </article>
            ))}
          </section>
        )}
      </div>
    </main>
  );
}
