import { useCallback, useEffect, useState, type FormEvent } from 'react';
import toast from 'react-hot-toast';
import { categoryApi, type Category, type CategoryRequest } from '../api/categoryApi';
import { getApiErrorMessage } from '../utils/apiError';

const GENERIC_ERROR = 'Có lỗi xảy ra. Vui lòng thử lại.';
const EMPTY_CATEGORY: CategoryRequest = {
  categoryName: '',
  categoryCode: '',
  description: '',
  vatRate: 10,
  status: 'ACTIVE',
};

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
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);

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

  const handleOpenCreate = () => {
    setEditingId(null);
    setFormData({ ...EMPTY_CATEGORY });
    setShowModal(true);
  };

  const handleOpenEdit = (category: Category) => {
    setEditingId(category.categoryId);
    setFormData({
      categoryName: category.categoryName,
      categoryCode: category.categoryCode,
      description: category.description ?? '',
      vatRate: category.vatRate,
      status: category.status,
    });
    setShowModal(true);
  };

  const handleDelete = async (category: Category) => {
    if (!window.confirm(`Bạn có chắc muốn xóa danh mục “${category.categoryName}”?`)) {
      return;
    }

    setDeletingId(category.categoryId);
    try {
      await categoryApi.delete(category.categoryId);
      toast.success('Xóa thành công');
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
      toast.error(getApiErrorMessage(requestError, GENERIC_ERROR));
    } finally {
      setSaving(false);
    }
  };

  return (
    <main className="page">
      <div className="container admin-container admin-container-narrow">
        <div className="admin-heading">
          <div>
            <span className="eyebrow">Quản trị</span>
            <h1>Quản lý danh mục</h1>
            <p>Cập nhật danh mục và mức VAT áp dụng cho sản phẩm.</p>
          </div>
          <button type="button" className="btn btn-primary" onClick={handleOpenCreate}>Thêm danh mục</button>
        </div>

        {error && (
          <div className="alert alert-error admin-alert" role="alert">
            <span>{error}</span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void loadData()}>Thử lại</button>
          </div>
        )}

        <section className="table-shell" aria-label="Danh sách danh mục">
          <div className="table-scroll">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Danh mục</th>
                  <th>Mã danh mục</th>
                  <th>VAT</th>
                  <th>Trạng thái</th>
                  <th><span className="sr-only">Thao tác</span></th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr><td colSpan={5}><div className="table-state"><span className="spinner spinner-dark" /> Đang tải...</div></td></tr>
                ) : categories.length === 0 ? (
                  <tr><td colSpan={5}><div className="table-state">Chưa có danh mục.</div></td></tr>
                ) : categories.map((category) => (
                  <tr key={category.categoryId}>
                    <td><strong>{category.categoryName}</strong></td>
                    <td>{category.categoryCode}</td>
                    <td>{category.vatRate}%</td>
                    <td><span className={`status-badge status-${category.status.toLowerCase()}`}>{statusLabel(category.status)}</span></td>
                    <td>
                      <div className="table-actions">
                        <button type="button" className="btn btn-ghost btn-sm" onClick={() => handleOpenEdit(category)}>Sửa</button>
                        <button
                          type="button"
                          className="btn btn-danger btn-sm"
                          disabled={deletingId === category.categoryId}
                          onClick={() => void handleDelete(category)}
                        >
                          {deletingId === category.categoryId ? 'Đang xóa...' : 'Xóa'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>

      {showModal && (
        <div className="modal-backdrop" role="presentation">
          <section className="modal-card modal-card-small" role="dialog" aria-modal="true" aria-labelledby="category-form-title">
            <div className="modal-header">
              <div>
                <span className="eyebrow">Danh mục</span>
                <h2 id="category-form-title">{editingId !== null ? 'Sửa danh mục' : 'Thêm danh mục'}</h2>
              </div>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setShowModal(false)} aria-label="Đóng">×</button>
            </div>

            <form className="form-grid" onSubmit={handleSubmit}>
              <label className="field field-span-2">
                <span>Tên danh mục</span>
                <input required className="form-input" value={formData.categoryName} onChange={(event) => setFormData({ ...formData, categoryName: event.target.value })} />
              </label>
              <label className="field">
                <span>Mã danh mục</span>
                <input required className="form-input" value={formData.categoryCode} onChange={(event) => setFormData({ ...formData, categoryCode: event.target.value })} />
              </label>
              <label className="field">
                <span>Mức VAT</span>
                <select required className="form-input" value={formData.vatRate} onChange={(event) => setFormData({ ...formData, vatRate: Number(event.target.value) })}>
                  <option value={5}>5%</option>
                  <option value={10}>10%</option>
                </select>
              </label>
              <label className="field field-span-2">
                <span>Mô tả</span>
                <textarea className="form-input admin-textarea" value={formData.description} onChange={(event) => setFormData({ ...formData, description: event.target.value })} />
              </label>
              <label className="field field-span-2">
                <span>Trạng thái</span>
                <select className="form-input" value={formData.status} onChange={(event) => setFormData({ ...formData, status: event.target.value })}>
                  <option value="ACTIVE">Đang sử dụng</option>
                  <option value="INACTIVE">Ngừng sử dụng</option>
                </select>
              </label>
              <div className="modal-actions field-span-2">
                <button type="button" className="btn btn-ghost" onClick={() => setShowModal(false)}>Hủy</button>
                <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Đang lưu...' : 'Lưu thay đổi'}</button>
              </div>
            </form>
          </section>
        </div>
      )}
    </main>
  );
}
