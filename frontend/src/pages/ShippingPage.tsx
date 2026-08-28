import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { addressApi, type Address } from '../api/addressApi';
import {
  shippingApi,
  type ShippingMethod,
  type ShippingPreparation,
} from '../api/shippingApi';
import { getApiErrorMessage } from '../utils/apiError';

const money = (value: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);

export default function ShippingPage() {
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [shippingMethod, setShippingMethod] = useState<ShippingMethod | null>(null);
  const [preparation, setPreparation] = useState<ShippingPreparation | null>(null);
  const [loading, setLoading] = useState(true);
  const [preparing, setPreparing] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [prepareError, setPrepareError] = useState<string | null>(null);

  const loadAddresses = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const response = await addressApi.list();
      const loadedAddresses = response.data.data ?? [];
      setAddresses(loadedAddresses);
      setSelectedAddressId((currentAddressId) => {
        if (currentAddressId !== null
            && loadedAddresses.some((address) => address.addressId === currentAddressId)) {
          return currentAddressId;
        }
        return loadedAddresses.find((address) => address.isDefault)?.addressId ?? null;
      });
    } catch (requestError) {
      setLoadError(getApiErrorMessage(requestError, 'Không thể tải địa chỉ giao hàng.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAddresses();
  }, [loadAddresses]);

  const selectAddress = (addressId: number) => {
    setSelectedAddressId(addressId);
    setPreparation(null);
    setPrepareError(null);
  };

  const selectShippingMethod = (method: ShippingMethod) => {
    setShippingMethod(method);
    setPreparation(null);
    setPrepareError(null);
  };

  const prepareShipping = async (event: FormEvent) => {
    event.preventDefault();
    if (selectedAddressId === null || shippingMethod === null) {
      setPrepareError('Vui lòng chọn địa chỉ và phương thức giao hàng.');
      return;
    }

    setPreparing(true);
    setPreparation(null);
    setPrepareError(null);
    try {
      const response = await shippingApi.prepare({
        addressId: selectedAddressId,
        shippingMethod,
      });
      setPreparation(response.data.data);
      toast.success('Đã tính phí giao hàng');
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Không thể tính phí giao hàng.');
      setPrepareError(message);
      toast.error(message);
    } finally {
      setPreparing(false);
    }
  };

  return (
    <main className="page">
      <div className="container content-container">
        <div className="page-heading">
          <span className="eyebrow">Giao hàng</span>
          <h1>Thông tin giao hàng</h1>
          <p>Kiểm tra địa chỉ nhận hàng trước khi tiếp tục.</p>
        </div>

        {loadError && (
          <div className="alert alert-error admin-alert" role="alert">
            <span>{loadError}</span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void loadAddresses()}>Thử lại</button>
          </div>
        )}

        {loading ? (
          <div className="state-card" aria-live="polite"><span className="spinner spinner-dark" /> Đang tải...</div>
        ) : loadError ? null : addresses.length === 0 ? (
          <div className="state-card">
            <h2>Chưa có địa chỉ giao hàng</h2>
            <p>Thêm địa chỉ để chuẩn bị cho đơn hàng của bạn.</p>
            <Link className="btn btn-primary" to="/addresses">Thêm địa chỉ</Link>
          </div>
        ) : (
          <form className="form-stack" onSubmit={prepareShipping} aria-busy={preparing}>
            <section className="card card-p form-stack" aria-labelledby="shipping-address-heading">
              <div className="section-heading compact">
                <h2 id="shipping-address-heading">Chọn địa chỉ giao hàng</h2>
                <Link className="text-button" to="/addresses">Quản lý địa chỉ</Link>
              </div>
              <div className="form-stack" role="radiogroup" aria-labelledby="shipping-address-heading">
                {addresses.map((address) => (
                  <label
                    className={`choice-card ${selectedAddressId === address.addressId ? 'selected' : ''}`}
                    key={address.addressId}
                  >
                    <input
                      type="radio"
                      name="shippingAddress"
                      value={address.addressId}
                      checked={selectedAddressId === address.addressId}
                      disabled={preparing}
                      required
                      onChange={() => selectAddress(address.addressId)}
                    />
                    <span>
                      <strong>{address.recipientName} {address.isDefault && '· Mặc định'}</strong>
                      <small>{address.phone}</small>
                      <small>{[address.line1, address.ward, address.district, address.province].filter(Boolean).join(', ')}</small>
                    </span>
                  </label>
                ))}
              </div>
            </section>

            <section className="card card-p form-stack" aria-labelledby="shipping-method-heading">
              <div className="section-heading compact">
                <h2 id="shipping-method-heading">Chọn phương thức giao hàng</h2>
              </div>
              <div className="form-stack" role="radiogroup" aria-labelledby="shipping-method-heading">
                <label className={`choice-card ${shippingMethod === 'STANDARD' ? 'selected' : ''}`}>
                  <input
                    type="radio"
                    name="shippingMethod"
                    value="STANDARD"
                    checked={shippingMethod === 'STANDARD'}
                    disabled={preparing}
                    required
                    onChange={() => selectShippingMethod('STANDARD')}
                  />
                  <span><strong>STANDARD</strong><small>Giao hàng tiêu chuẩn</small></span>
                </label>
                <label className={`choice-card ${shippingMethod === 'EXPRESS' ? 'selected' : ''}`}>
                  <input
                    type="radio"
                    name="shippingMethod"
                    value="EXPRESS"
                    checked={shippingMethod === 'EXPRESS'}
                    disabled={preparing}
                    required
                    onChange={() => selectShippingMethod('EXPRESS')}
                  />
                  <span><strong>EXPRESS</strong><small>Giao hàng nhanh</small></span>
                </label>
              </div>

              {prepareError && <div className="alert alert-error" role="alert">{prepareError}</div>}

              <button
                className="btn btn-primary btn-full"
                type="submit"
                disabled={preparing || selectedAddressId === null || shippingMethod === null}
              >
                {preparing ? 'Đang tính phí...' : 'Tính phí giao hàng'}
              </button>
            </section>

            {preparation && (
              <section className="card card-p form-stack" aria-labelledby="shipping-result-heading" role="status" aria-live="polite">
                <div className="section-heading compact">
                  <h2 id="shipping-result-heading">Phí giao hàng</h2>
                </div>
                <div className="shipping-result">
                  <div><span>Phương thức</span><strong>{preparation.shippingMethod}</strong></div>
                  <div><span>Khu vực</span><strong>{preparation.region}</strong></div>
                  <div><span>Phí giao hàng</span><strong>{money(preparation.shippingFee)}</strong></div>
                </div>
              </section>
            )}
          </form>
        )}
      </div>
    </main>
  );
}
