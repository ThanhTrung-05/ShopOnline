import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Lightning, MapPinLine, Package, Truck } from '@phosphor-icons/react';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { addressApi, type Address } from '../api/addressApi';
import { orderApi } from '../api/orderApi';
import { shippingApi, type ShippingMethod, type ShippingPreparation, type ShippingRegion } from '../api/shippingApi';
import CheckoutSteps from '../components/CheckoutSteps';
import { useCartStore } from '../store/cartStore';
import { getApiErrorMessage } from '../utils/apiError';

const money = (value: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);

const METHOD_LABELS: Record<ShippingMethod, { name: string; description: string }> = {
  STANDARD: { name: 'Giao hàng tiêu chuẩn', description: 'Lựa chọn tiết kiệm cho đơn hàng thông thường.' },
  EXPRESS: { name: 'Giao hàng nhanh', description: 'Ưu tiên thời gian giao nếu khu vực được hỗ trợ.' },
};

const REGION_LABELS: Record<ShippingRegion, string> = {
  LOCAL: 'Nội thành',
  NEARBY: 'Khu vực lân cận',
  OTHER: 'Ngoại tỉnh',
};

export default function ShippingPage() {
  const navigate = useNavigate();
  const clearLocalCart = useCartStore((state) => state.clearLocal);
  const cartItems = useCartStore((state) => state.items);
  const subtotal = useCartStore((state) => state.subtotal);
  const itemCount = useMemo(() => cartItems.reduce((count, item) => count + item.quantity, 0), [cartItems]);
  const placingOrderRef = useRef(false);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [shippingMethod, setShippingMethod] = useState<ShippingMethod | null>(null);
  const [preparation, setPreparation] = useState<ShippingPreparation | null>(null);
  const [loading, setLoading] = useState(true);
  const [preparing, setPreparing] = useState(false);
  const [placingOrder, setPlacingOrder] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [prepareError, setPrepareError] = useState<string | null>(null);
  const [orderError, setOrderError] = useState<string | null>(null);

  const selectedAddress = addresses.find((address) => address.addressId === selectedAddressId) ?? null;

  const loadAddresses = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const response = await addressApi.list();
      const loadedAddresses = response.data.data ?? [];
      setAddresses(loadedAddresses);
      setSelectedAddressId((currentAddressId) => {
        if (currentAddressId !== null && loadedAddresses.some((address) => address.addressId === currentAddressId)) return currentAddressId;
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

  const resetPreparation = () => {
    setPreparation(null);
    setPrepareError(null);
    setOrderError(null);
  };

  const selectAddress = (addressId: number) => {
    setSelectedAddressId(addressId);
    resetPreparation();
  };

  const selectShippingMethod = (method: ShippingMethod) => {
    setShippingMethod(method);
    resetPreparation();
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
    setOrderError(null);
    try {
      const response = await shippingApi.prepare({ addressId: selectedAddressId, shippingMethod });
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

  const placeOrder = async () => {
    if (placingOrderRef.current || preparation === null) return;
    placingOrderRef.current = true;
    setPlacingOrder(true);
    setOrderError(null);
    try {
      const response = await orderApi.create({
        addressId: preparation.addressId,
        shippingMethod: preparation.shippingMethod,
      });
      clearLocalCart();
      toast.success(`Đặt hàng thành công: ${response.data.data.orderNumber}`);
      navigate('/orders/status');
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Không thể đặt hàng.');
      setOrderError(message);
      toast.error(message);
    } finally {
      placingOrderRef.current = false;
      setPlacingOrder(false);
    }
  };

  return (
    <main className="page checkout-page">
      <div className="container checkout-container">
        <CheckoutSteps active="shipping" />
        <header className="page-heading page-heading--compact checkout-heading">
          <span className="page-context">Bước 2</span>
          <h1>Thông tin giao hàng</h1>
          <p>Chọn nơi nhận và cách giao, sau đó kiểm tra phí trước khi đặt hàng.</p>
        </header>

        {loadError && (
          <div className="alert alert-error admin-alert" role="alert">
            <span>{loadError}</span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void loadAddresses()}>Thử lại</button>
          </div>
        )}

        {loading ? (
          <section className="state-card checkout-state" aria-live="polite"><span className="spinner spinner-dark" /><div><h2>Đang tải thông tin giao hàng</h2><p>ShopOnline đang lấy các địa chỉ đã lưu.</p></div></section>
        ) : loadError ? null : addresses.length === 0 ? (
          <section className="state-card checkout-state">
            <span className="empty-state-icon" aria-hidden="true"><MapPinLine size={30} /></span>
            <div><h2>Chưa có địa chỉ giao hàng</h2><p>Thêm một địa chỉ để tiếp tục chuẩn bị đơn hàng.</p></div>
            <Link className="btn btn-primary" to="/addresses">Thêm địa chỉ</Link>
          </section>
        ) : (
          <form className="shipping-layout" onSubmit={prepareShipping} aria-busy={preparing || placingOrder}>
            <div className="shipping-main">
              <section className="checkout-section" aria-labelledby="shipping-address-heading">
                <div className="section-heading checkout-section-heading">
                  <div><span>01</span><h2 id="shipping-address-heading">Nơi nhận hàng</h2></div>
                  <Link className="text-button" to="/addresses">Quản lý địa chỉ</Link>
                </div>
                <div className="shipping-choice-list" role="radiogroup" aria-labelledby="shipping-address-heading">
                  {addresses.map((address) => (
                    <label className={`choice-card shipping-address-option ${selectedAddressId === address.addressId ? 'selected' : ''}`} key={address.addressId}>
                      <input type="radio" name="shippingAddress" value={address.addressId} checked={selectedAddressId === address.addressId} disabled={preparing || placingOrder} required onChange={() => selectAddress(address.addressId)} />
                      <span className="choice-card-copy">
                        <span className="choice-card-title"><strong>{address.recipientName}</strong>{address.isDefault && <small className="choice-default">Mặc định</small>}</span>
                        <small>{address.phone}</small>
                        <small>{[address.line1, address.ward, address.district, address.province].filter(Boolean).join(', ')}</small>
                      </span>
                    </label>
                  ))}
                </div>
              </section>

              <section className="checkout-section" aria-labelledby="shipping-method-heading">
                <div className="section-heading checkout-section-heading"><div><span>02</span><h2 id="shipping-method-heading">Phương thức giao hàng</h2></div></div>
                <div className="shipping-method-grid" role="radiogroup" aria-labelledby="shipping-method-heading">
                  {(['STANDARD', 'EXPRESS'] as ShippingMethod[]).map((method) => {
                    const Icon = method === 'STANDARD' ? Truck : Lightning;
                    return (
                      <label className={`choice-card shipping-method-option ${shippingMethod === method ? 'selected' : ''}`} key={method}>
                        <input type="radio" name="shippingMethod" value={method} checked={shippingMethod === method} disabled={preparing || placingOrder} required onChange={() => selectShippingMethod(method)} />
                        <Icon size={23} aria-hidden="true" />
                        <span><strong>{METHOD_LABELS[method].name}</strong><small>{METHOD_LABELS[method].description}</small></span>
                      </label>
                    );
                  })}
                </div>
                {prepareError && <div className="alert alert-error" role="alert">{prepareError}</div>}
                <button className={preparation ? 'btn btn-ghost shipping-prepare' : 'btn btn-primary shipping-prepare'} type="submit" disabled={preparing || placingOrder || selectedAddressId === null || shippingMethod === null}>
                  {preparing ? <><span className="spinner spinner-inline" /> Đang tính phí...</> : preparation ? 'Tính lại phí giao hàng' : 'Tính phí giao hàng'}
                </button>
              </section>
            </div>

            <aside className="card card-p checkout-summary shipping-summary" aria-labelledby="shipping-summary-title">
              <span className="page-context">Tóm tắt đơn hàng</span>
              <h2 id="shipping-summary-title">Kiểm tra trước khi đặt</h2>
              <dl className="checkout-summary-lines">
                <div><dt>Sản phẩm</dt><dd>{itemCount || cartItems.length}</dd></div>
                <div><dt>Tạm tính</dt><dd>{money(subtotal)}</dd></div>
                <div><dt>Giao hàng</dt><dd>{preparation ? money(preparation.shippingFee) : 'Chưa tính'}</dd></div>
              </dl>

              {selectedAddress && (
                <div className="shipping-summary-block">
                  <span>Giao đến</span>
                  <strong>{selectedAddress.recipientName}</strong>
                  <p>{[selectedAddress.line1, selectedAddress.ward, selectedAddress.district, selectedAddress.province].filter(Boolean).join(', ')}</p>
                </div>
              )}

              {preparation && (
                <section className="shipping-preparation" aria-labelledby="shipping-result-heading" role="status" aria-live="polite">
                  <div><Package size={20} aria-hidden="true" /><h3 id="shipping-result-heading">Phí giao hàng đã xác nhận</h3></div>
                  <dl>
                    <div><dt>Phương thức</dt><dd>{METHOD_LABELS[preparation.shippingMethod].name}</dd></div>
                    <div><dt>Khu vực</dt><dd>{REGION_LABELS[preparation.region]}</dd></div>
                    <div><dt>Phí giao hàng</dt><dd>{money(preparation.shippingFee)}</dd></div>
                  </dl>
                  <div className="shipping-grand-total"><span>Tổng dự kiến</span><strong>{money(subtotal + preparation.shippingFee)}</strong></div>
                </section>
              )}

              {cartItems.length === 0 && <p className="checkout-summary-note">Giỏ hàng chưa hiển thị sản phẩm. Máy chủ sẽ kiểm tra lại khi bạn đặt hàng.</p>}
              {orderError && <div className="alert alert-error" role="alert">{orderError}</div>}
              <button className="btn btn-primary btn-full" type="button" disabled={preparation === null || preparing || placingOrder} onClick={() => void placeOrder()}>
                {placingOrder ? <><span className="spinner spinner-inline" /> Đang đặt hàng...</> : 'Đặt hàng'}
              </button>
            </aside>
          </form>
        )}
      </div>
    </main>
  );
}
