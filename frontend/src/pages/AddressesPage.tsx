import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { HouseLine, MapPinLine, Plus, Trash, X } from '@phosphor-icons/react';
import toast from 'react-hot-toast';
import { addressApi, type Address, type AddressRequest } from '../api/addressApi';
import AccountNav from '../components/AccountNav';
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
  const [deleteTarget, setDeleteTarget] = useState<Address | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const deleteCancelRef = useRef<HTMLButtonElement>(null);

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

  useEffect(() => {
    if (!deleteTarget) return undefined;

    const previousFocus = document.activeElement as HTMLElement | null;
    deleteCancelRef.current?.focus();
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setDeleteTarget(null);
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('keydown', closeOnEscape);
      previousFocus?.focus();
    };
  }, [deleteTarget]);

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
    formRef.current?.scrollIntoView({ block: 'start', behavior: 'smooth' });
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
      setDeleteTarget(null);
    }
  };

  return (
    <main className="page account-page">
      <div className="container account-container">
        <header className="page-heading page-heading--compact">
          <span className="page-context">Tài khoản của tôi</span>
          <h1>Địa chỉ giao hàng</h1>
          <p>Lưu địa chỉ nhận hàng để bước giao hàng nhanh và chính xác hơn.</p>
        </header>

        <div className="account-shell">
          <AccountNav />
          <div className="account-workspace address-workspace">
            <section className="address-book" aria-busy={loading}>
              <div className="section-heading address-book-heading">
                <div>
                  <span>Địa chỉ đã lưu</span>
                  <h2>{addresses.length > 0 ? `${addresses.length} địa chỉ nhận hàng` : 'Sổ địa chỉ'}</h2>
                </div>
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => {
                  resetForm();
                  formRef.current?.scrollIntoView({ block: 'start', behavior: 'smooth' });
                }}>
                  <Plus size={17} weight="bold" aria-hidden="true" /> Thêm mới
                </button>
              </div>

              {error && <div className="alert alert-error" role="alert">{error}</div>}
              {loading ? (
                <div className="state-card account-state" aria-live="polite">
                  <span className="spinner spinner-dark" />
                  <div><h2>Đang tải sổ địa chỉ</h2><p>Các địa chỉ đã lưu sẽ xuất hiện sau giây lát.</p></div>
                </div>
              ) : addresses.length === 0 ? (
                <div className="state-card account-state">
                  <MapPinLine size={36} aria-hidden="true" />
                  <div><h2>Chưa có địa chỉ</h2><p>Thêm địa chỉ đầu tiên để dùng làm nơi nhận hàng mặc định.</p></div>
                </div>
              ) : (
                <div className="address-list">
                  {addresses.map((address) => (
                    <article className={'address-card ' + (address.isDefault ? 'is-default' : '')} key={address.addressId}>
                      <div className="address-card-icon" aria-hidden="true"><HouseLine size={21} /></div>
                      <div className="address-card-body">
                        <div className="address-card-header">
                          <div>
                            <h3>{address.recipientName}</h3>
                            <span>{address.phone}</span>
                          </div>
                          {address.isDefault && <span className="badge badge-success">Mặc định</span>}
                        </div>
                        <p>{[address.line1, address.ward, address.district, address.province].filter(Boolean).join(', ')}</p>
                        <div className="address-actions">
                          <button className="text-button" type="button" onClick={() => edit(address)}>Chỉnh sửa</button>
                          {!address.isDefault && (
                            <button className="text-button" type="button" disabled={busyAction !== null} onClick={() => void setDefault(address.addressId)}>
                              {busyAction === 'default-' + address.addressId ? 'Đang đặt...' : 'Đặt làm mặc định'}
                            </button>
                          )}
                          <button className="text-button text-button--danger" type="button" disabled={busyAction !== null} onClick={() => setDeleteTarget(address)}>
                            <Trash size={16} aria-hidden="true" /> Xóa
                          </button>
                        </div>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </section>

            <form ref={formRef} className="card card-p form-stack address-form" onSubmit={submit}>
              <div className="form-section-heading">
                <span>{editingId === null ? 'Địa chỉ mới' : 'Đang chỉnh sửa'}</span>
                <div className="section-heading compact">
                  <h2>{editingId === null ? 'Thêm nơi nhận hàng' : 'Cập nhật địa chỉ'}</h2>
                  {editingId !== null && <button type="button" className="text-button" onClick={resetForm}>Hủy sửa</button>}
                </div>
              </div>
              <label className="field"><span>Người nhận</span><input className="form-input" autoComplete="name" required maxLength={200} value={form.recipientName} onChange={(event) => setForm({ ...form, recipientName: event.target.value })} /></label>
              <label className="field"><span>Số điện thoại</span><input className="form-input" autoComplete="tel" required type="tel" value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} /></label>
              <label className="field"><span>Số nhà, tên đường</span><input className="form-input" autoComplete="address-line1" required maxLength={255} value={form.line1} onChange={(event) => setForm({ ...form, line1: event.target.value })} /></label>
              <div className="field-row">
                <label className="field"><span>Phường/xã</span><input className="form-input" autoComplete="address-level3" maxLength={100} value={form.ward} onChange={(event) => setForm({ ...form, ward: event.target.value })} /></label>
                <label className="field"><span>Quận/huyện</span><input className="form-input" autoComplete="address-level2" maxLength={100} value={form.district} onChange={(event) => setForm({ ...form, district: event.target.value })} /></label>
              </div>
              <label className="field"><span>Tỉnh/thành phố</span><input className="form-input" autoComplete="address-level1" required maxLength={100} value={form.province} onChange={(event) => setForm({ ...form, province: event.target.value })} /></label>
              <button className="btn btn-primary btn-full" type="submit" disabled={submitting}>
                {submitting ? <><span className="spinner spinner-inline" /> Đang lưu...</> : editingId === null ? 'Lưu địa chỉ' : 'Lưu thay đổi'}
              </button>
            </form>
          </div>
        </div>
      </div>

      {deleteTarget && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget && busyAction === null) setDeleteTarget(null);
        }}>
          <section className="modal-card modal-card-small confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="delete-address-title">
            <button className="icon-button modal-close" type="button" aria-label="Đóng" disabled={busyAction !== null} onClick={() => setDeleteTarget(null)}>
              <X size={20} aria-hidden="true" />
            </button>
            <div className="confirm-dialog-icon" aria-hidden="true"><Trash size={24} /></div>
            <h2 id="delete-address-title">Xóa địa chỉ này?</h2>
            <p>Địa chỉ của {deleteTarget.recipientName} sẽ bị xóa khỏi sổ địa chỉ.</p>
            <div className="modal-actions">
              <button ref={deleteCancelRef} className="btn btn-ghost" type="button" disabled={busyAction !== null} onClick={() => setDeleteTarget(null)}>Giữ lại</button>
              <button className="btn btn-danger" type="button" disabled={busyAction !== null} onClick={() => void remove(deleteTarget)}>
                {busyAction === 'delete-' + deleteTarget.addressId ? <><span className="spinner spinner-inline" /> Đang xóa...</> : 'Xóa địa chỉ'}
              </button>
            </div>
          </section>
        </div>
      )}
    </main>
  );
}
