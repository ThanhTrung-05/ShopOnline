import { useCallback, useEffect, useState, type FormEvent } from 'react';
import toast from 'react-hot-toast';
import { addressApi, type Address, type AddressRequest } from '../api/addressApi';
import { getApiErrorMessage } from '../utils/apiError';

type AddressForm = {
  recipientName: string;
  phone: string;
  line1: string;
  ward: string;
  district: string;
  province: string;
};

const EMPTY_FORM: AddressForm = {
  recipientName: '',
  phone: '',
  line1: '',
  ward: '',
  district: '',
  province: '',
};

function toRequest(form: AddressForm): AddressRequest {
  return {
    recipientName: form.recipientName.trim(),
    phone: form.phone.trim(),
    line1: form.line1.trim(),
    ward: form.ward.trim() || null,
    district: form.district.trim() || null,
    province: form.province.trim(),
  };
}

export default function AddressesPage() {
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [form, setForm] = useState<AddressForm>(EMPTY_FORM);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadAddresses = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await addressApi.list();
      setAddresses(response.data.data ?? []);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Không thể tải danh sách địa chỉ.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAddresses();
  }, [loadAddresses]);

  const resetForm = () => {
    setForm(EMPTY_FORM);
    setEditingId(null);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      if (editingId === null) {
        await addressApi.create(toRequest(form));
        toast.success('Đã thêm địa chỉ');
      } else {
        await addressApi.update(editingId, toRequest(form));
        toast.success('Đã cập nhật địa chỉ');
      }
      resetForm();
      await loadAddresses();
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Không thể lưu địa chỉ.');
      setError(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  const edit = (address: Address) => {
    setEditingId(address.addressId);
    setForm({
      recipientName: address.recipientName,
      phone: address.phone,
      line1: address.line1,
      ward: address.ward ?? '',
      district: address.district ?? '',
      province: address.province,
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const setDefault = async (addressId: number) => {
    setBusyAction('default-' + addressId);
    setError(null);
    try {
      await addressApi.setDefault(addressId);
      toast.success('Đã đặt địa chỉ mặc định');
      await loadAddresses();
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Không thể đặt địa chỉ mặc định.');
      setError(message);
      toast.error(message);
    } finally {
      setBusyAction(null);
    }
  };

  const remove = async (address: Address) => {
    if (!window.confirm('Xóa địa chỉ của ' + address.recipientName + '?')) {
      return;
    }
    setBusyAction('delete-' + address.addressId);
    setError(null);
    try {
      await addressApi.remove(address.addressId);
      if (editingId === address.addressId) resetForm();
      toast.success('Đã xóa địa chỉ');
      await loadAddresses();
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Không thể xóa địa chỉ.');
      setError(message);
      toast.error(message);
    } finally {
      setBusyAction(null);
    }
  };

  return (
    <main className="page">
      <div className="container">
        <div className="page-heading">
          <span className="eyebrow">Tài khoản</span>
          <h1>Địa chỉ giao hàng</h1>
          <p>Quản lý địa chỉ nhận hàng và địa chỉ mặc định của bạn.</p>
        </div>

        <div className="split-layout address-layout">
          <form className="card card-p form-stack sticky-card" onSubmit={submit}>
            <div className="section-heading compact">
              <h2>{editingId === null ? 'Thêm địa chỉ' : 'Sửa địa chỉ #' + editingId}</h2>
              {editingId !== null && <button type="button" className="text-button" onClick={resetForm}>Hủy sửa</button>}
            </div>
            <label className="field"><span>Người nhận</span><input className="form-input" required maxLength={200} value={form.recipientName} onChange={(event) => setForm({ ...form, recipientName: event.target.value })} /></label>
            <label className="field"><span>Số điện thoại</span><input className="form-input" required type="tel" value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} /></label>
            <label className="field"><span>Địa chỉ</span><input className="form-input" required maxLength={255} value={form.line1} onChange={(event) => setForm({ ...form, line1: event.target.value })} /></label>
            <div className="field-row">
              <label className="field"><span>Phường/xã</span><input className="form-input" maxLength={100} value={form.ward} onChange={(event) => setForm({ ...form, ward: event.target.value })} /></label>
              <label className="field"><span>Quận/huyện</span><input className="form-input" maxLength={100} value={form.district} onChange={(event) => setForm({ ...form, district: event.target.value })} /></label>
            </div>
            <label className="field"><span>Tỉnh/thành phố</span><input className="form-input" required maxLength={100} value={form.province} onChange={(event) => setForm({ ...form, province: event.target.value })} /></label>
            <button className="btn btn-primary btn-full" type="submit" disabled={submitting}>
              {submitting ? 'Đang lưu...' : editingId === null ? 'Thêm địa chỉ' : 'Lưu thay đổi'}
            </button>
          </form>

          <section>
            {error && <div className="alert alert-error" role="alert">{error}</div>}
            {loading ? (
              <div className="state-card"><span className="spinner spinner-dark" /> Đang tải...</div>
            ) : addresses.length === 0 ? (
              <div className="state-card"><h2>Chưa có địa chỉ</h2><p>Địa chỉ đầu tiên sẽ tự động trở thành mặc định.</p></div>
            ) : (
              <div className="stack-list">
                {addresses.map((address) => (
                  <article className={'card card-p address-card ' + (address.isDefault ? 'is-default' : '')} key={address.addressId}>
                    <div className="address-card-header">
                      <div>
                        <h3>{address.recipientName}</h3>
                        <span>{address.phone}</span>
                      </div>
                      {address.isDefault && <span className="badge badge-success">Mặc định</span>}
                    </div>
                    <p>{[address.line1, address.ward, address.district, address.province].filter(Boolean).join(', ')}</p>
                    <div className="button-row">
                      <button className="btn btn-ghost btn-sm" type="button" onClick={() => edit(address)}>Sửa</button>
                      {!address.isDefault && (
                        <button className="btn btn-ghost btn-sm" type="button" disabled={busyAction !== null} onClick={() => void setDefault(address.addressId)}>
                          {busyAction === 'default-' + address.addressId ? 'Đang đặt...' : 'Đặt mặc định'}
                        </button>
                      )}
                      <button className="btn btn-danger btn-sm" type="button" disabled={busyAction !== null} onClick={() => void remove(address)}>
                        {busyAction === 'delete-' + address.addressId ? 'Đang xóa...' : 'Xóa'}
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </main>
  );
}
