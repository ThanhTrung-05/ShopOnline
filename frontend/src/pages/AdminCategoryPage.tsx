import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { FolderOpen, Plus, Trash } from '@phosphor-icons/react';
import toast from 'react-hot-toast';
import { categoryApi, type Category, type CategoryRequest } from '../api/categoryApi';
import Modal from '../components/Modal';
import { getApiErrorMessage } from '../utils/apiError';

const GENERIC_ERROR = 'Có lỗi xảy ra. Vui lòng thử lại.';
const EMPTY_CATEGORY: CategoryRequest = { categoryName: '', categoryCode: '', description: '', vatRate: 10, status: 'ACTIVE' };

function statusLabel(status: string) {
  return status === 'ACTIVE' ? 'Đang sử dụng' : 'Ngừng sử dụng';
}

export default function AdminCategoryPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [formData, setFormData] = useState<CategoryRequest>(EMPTY_CATEGORY);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null);

  const activeCount = useMemo(() => categories.filter((category) => category.status === 'ACTIVE').length, [categories]);
  const vatRates = useMemo(() => new Set(categories.map((category) => category.vatRate)).size, [categories]);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await categoryApi.list();
      setCategories(response.data.data ?? []);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, GENERIC_ERROR));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const closeForm = () => {
    if (!saving) setShowModal(false);
  };

  const handleOpenCreate = () => {
    setEditingId(null);
    setFormData({ ...EMPTY_CATEGORY });
    setFormError(null);
    setShowModal(true);
  };

  const handleOpenEdit = (category: Category) => {
    setEditingId(category.categoryId);
    setFormData({ categoryName: category.categoryName, categoryCode: category.categoryCode, description: category.description ?? '', vatRate: category.vatRate, status: category.status });
    setFormError(null);
    setShowModal(true);
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeletingId(deleteTarget.categoryId);
    try {
      await categoryApi.delete(deleteTarget.categoryId);
      toast.success('Xóa thành công');
      setDeleteTarget(null);
      await loadData();
    } catch (requestError) {
      toast.error(getApiErrorMessage(requestError, GENERIC_ERROR));
    } finally {
      setDeletingId(null);
    }
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setFormError(null);
    try {
      if (editingId !== null) {
        await categoryApi.update(editingId, formData);
        toast.success('Cập nhật thành công');
      } else {
        await categoryApi.create(formData);
        toast.success('Tạo mới thành công');
      }
      setShowModal(false);
      await loadData();
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, GENERIC_ERROR);
      setFormError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <main className="page operations-page">
      <div className="container admin-container admin-container-narrow">
        <header className="admin-heading operations-heading">
          <div><span className="page-context">Cấu trúc danh mục</span><h1>Quản lý danh mục</h1><p>Cập nhật nhóm sản phẩm, trạng thái hiển thị và mức VAT áp dụng.</p></div>
          <button type="button" className="btn btn-primary" onClick={handleOpenCreate}><Plus size={18} weight="bold" aria-hidden="true" /> Thêm danh mục</button>
        </header>

        <section className="operations-overview operations-overview--compact" aria-label="Tổng quan danh mục">
          <div className="operations-stat operations-stat--primary"><span>Tổng danh mục</span><strong>{loading ? '...' : categories.length}</strong><small>nhóm sản phẩm</small></div>
          <div className="operations-stat"><span>Đang sử dụng</span><strong>{loading ? '...' : activeCount}</strong></div>
          <div className="operations-stat"><span>Mức VAT đang dùng</span><strong>{loading ? '...' : vatRates}</strong></div>
        </section>

        {error && <div className="alert alert-error admin-alert" role="alert"><span>{error}</span><button type="button" className="btn btn-ghost btn-sm" onClick={() => void loadData()}>Thử lại</button></div>}

        <section className="table-shell" aria-label="Danh sách danh mục">
          <div className="table-scroll">
            <table className="admin-table">
              <thead><tr><th>Danh mục</th><th>Mã danh mục</th><th>Mức VAT</th><th>Trạng thái</th><th><span className="sr-only">Thao tác</span></th></tr></thead>
              <tbody>
                {loading ? (
                  <tr><td colSpan={5}><div className="table-state"><span className="spinner spinner-dark" /> Đang tải danh mục...</div></td></tr>
                ) : error ? null : categories.length === 0 ? (
                  <tr><td colSpan={5}><div className="table-state"><FolderOpen size={24} aria-hidden="true" /> Chưa có danh mục.</div></td></tr>
                ) : categories.map((category) => (
                  <tr key={category.categoryId}>
                    <td data-label="Danh mục"><div className="category-cell"><span aria-hidden="true"><FolderOpen size={19} /></span><div><strong>{category.categoryName}</strong>{category.description && <small>{category.description}</small>}</div></div></td>
                    <td data-label="Mã danh mục"><code>{category.categoryCode}</code></td>
                    <td data-label="Mức VAT"><strong>{category.vatRate}%</strong></td>
                    <td data-label="Trạng thái"><span className={`status-badge status-${category.status.toLowerCase()}`}>{statusLabel(category.status)}</span></td>
                    <td data-label="Thao tác"><div className="table-actions"><button type="button" className="btn btn-ghost btn-sm" onClick={() => handleOpenEdit(category)}>Sửa</button><button type="button" className="btn btn-danger btn-sm" disabled={deletingId !== null} onClick={() => setDeleteTarget(category)}>Xóa</button></div></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>

      {showModal && (
        <Modal title={editingId !== null ? 'Sửa danh mục' : 'Thêm danh mục'} context="Thông tin danh mục" size="small" onClose={closeForm} closeDisabled={saving}>
          <form className="form-grid" onSubmit={handleSubmit}>
            <label className="field field-span-2"><span>Tên danh mục</span><input required autoFocus className="form-input" value={formData.categoryName} onChange={(event) => setFormData({ ...formData, categoryName: event.target.value })} /></label>
            <label className="field"><span>Mã danh mục</span><input required className="form-input" value={formData.categoryCode} onChange={(event) => setFormData({ ...formData, categoryCode: event.target.value })} /></label>
            <label className="field"><span>Mức VAT</span><select required className="form-input" value={formData.vatRate} onChange={(event) => setFormData({ ...formData, vatRate: Number(event.target.value) })}><option value={5}>5%</option><option value={10}>10%</option></select></label>
            <label className="field field-span-2"><span>Mô tả</span><textarea className="form-input admin-textarea" value={formData.description} onChange={(event) => setFormData({ ...formData, description: event.target.value })} /></label>
            <label className="field field-span-2"><span>Trạng thái</span><select className="form-input" value={formData.status} onChange={(event) => setFormData({ ...formData, status: event.target.value })}><option value="ACTIVE">Đang sử dụng</option><option value="INACTIVE">Ngừng sử dụng</option></select></label>
            {formError && <div className="alert alert-error field-span-2" role="alert">{formError}</div>}
            <div className="modal-actions field-span-2"><button type="button" className="btn btn-ghost" onClick={closeForm} disabled={saving}>Hủy</button><button type="submit" className="btn btn-primary" disabled={saving}>{saving ? <><span className="spinner spinner-inline" /> Đang lưu...</> : 'Lưu thay đổi'}</button></div>
          </form>
        </Modal>
      )}

      {deleteTarget && (
        <Modal title="Xóa danh mục này?" context="Kiểm tra sản phẩm liên quan" size="small" onClose={() => setDeleteTarget(null)} closeDisabled={deletingId !== null}>
          <div className="confirm-dialog-body"><div className="confirm-dialog-icon" aria-hidden="true"><Trash size={24} /></div><p>Danh mục “{deleteTarget.categoryName}” sẽ được xóa nếu không còn sản phẩm tham chiếu.</p><div className="modal-actions"><button type="button" className="btn btn-ghost" disabled={deletingId !== null} onClick={() => setDeleteTarget(null)}>Giữ lại</button><button type="button" className="btn btn-danger" disabled={deletingId !== null} onClick={() => void handleDelete()}>{deletingId !== null ? <><span className="spinner spinner-inline" /> Đang xóa...</> : 'Xóa danh mục'}</button></div></div>
        </Modal>
      )}
    </main>
  );
}
