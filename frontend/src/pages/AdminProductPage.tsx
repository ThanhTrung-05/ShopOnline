import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { MagnifyingGlass, Package, Plus, Trash } from '@phosphor-icons/react';
import toast from 'react-hot-toast';
import { adminProductApi, type ProductRequest } from '../api/adminProductApi';
import { categoryApi, type Category } from '../api/categoryApi';
import Modal from '../components/Modal';
import ProductImage from '../components/ProductImage';
import type { PageResponse, Product } from '../types';
import { getApiErrorMessage } from '../utils/apiError';

const GENERIC_ERROR = 'Có lỗi xảy ra. Vui lòng thử lại.';

function emptyProduct(categoryId = 0): ProductRequest {
  return { productName: '', productSlug: '', categoryId, description: '', price: 0, imageUrl: '', status: 'ACTIVE', initialQuantity: 0 };
}

function formatPrice(price: number) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(price);
}

function statusLabel(status: string) {
  return status === 'ACTIVE' ? 'Đang bán' : 'Ngừng bán';
}

export default function AdminProductPage() {
  const [data, setData] = useState<PageResponse<Product> | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [formData, setFormData] = useState<ProductRequest>(() => emptyProduct());
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Product | null>(null);

  const categoryById = useMemo(() => new Map(categories.map((category) => [category.categoryId, category])), [categories]);
  const selectedCategory = categoryById.get(formData.categoryId);
  const visibleProducts = data?.content ?? [];
  const activeOnPage = visibleProducts.filter((product) => product.status === 'ACTIVE').length;
  const lowStockOnPage = visibleProducts.filter((product) => product.inventoryCount < 10).length;

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [productResponse, categoryResponse] = await Promise.all([
        adminProductApi.list(page, 20, categoryId, search),
        categoryApi.list(),
      ]);
      setData(productResponse.data.data);
      setCategories(categoryResponse.data.data ?? []);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, GENERIC_ERROR));
    } finally {
      setLoading(false);
    }
  }, [categoryId, page, search]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleSearch = (event: FormEvent) => {
    event.preventDefault();
    setPage(0);
    setSearch(searchInput.trim());
  };

  const closeForm = () => {
    if (!saving) setShowModal(false);
  };

  const handleOpenCreate = () => {
    setEditingId(null);
    setFormData(emptyProduct(categories[0]?.categoryId));
    setFormError(null);
    setShowModal(true);
  };

  const handleOpenEdit = (product: Product) => {
    setEditingId(product.id);
    setFormData({
      productName: product.name,
      productSlug: product.slug,
      categoryId: product.categoryId,
      description: product.description ?? '',
      price: product.price,
      imageUrl: product.imageUrl ?? '',
      status: product.status,
      initialQuantity: product.inventoryCount,
    });
    setFormError(null);
    setShowModal(true);
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeletingId(deleteTarget.id);
    try {
      await adminProductApi.delete(deleteTarget.id);
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
        await adminProductApi.update(editingId, formData);
        toast.success('Cập nhật thành công');
      } else {
        await adminProductApi.create(formData);
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
      <div className="container admin-container">
        <header className="admin-heading operations-heading">
          <div><span className="page-context">Danh mục hàng hóa</span><h1>Quản lý sản phẩm</h1><p>Tìm, kiểm tra tồn kho và cập nhật thông tin sản phẩm đang kinh doanh.</p></div>
          <button type="button" className="btn btn-primary" onClick={handleOpenCreate}><Plus size={18} weight="bold" aria-hidden="true" /> Thêm sản phẩm</button>
        </header>

        <section className="operations-overview" aria-label="Tổng quan sản phẩm hiện tại">
          <div className="operations-stat operations-stat--primary"><span>Kết quả đang lọc</span><strong>{loading ? '...' : data?.totalElements ?? 0}</strong><small>sản phẩm</small></div>
          <div className="operations-stat"><span>Đang bán trong trang</span><strong>{loading ? '...' : activeOnPage}</strong></div>
          <div className="operations-stat"><span>Sắp hoặc đã hết hàng</span><strong>{loading ? '...' : lowStockOnPage}</strong></div>
        </section>

        <div className="admin-toolbar operations-toolbar">
          <form className="admin-search" role="search" onSubmit={handleSearch}>
            <label className="sr-only" htmlFor="admin-product-search">Tìm sản phẩm</label>
            <span className="input-with-icon"><MagnifyingGlass size={18} aria-hidden="true" /><input id="admin-product-search" className="form-input" type="search" placeholder="Tìm theo tên sản phẩm" value={searchInput} onChange={(event) => setSearchInput(event.target.value)} /></span>
            <button type="submit" className="btn btn-primary">Tìm kiếm</button>
          </form>
          <label className="field admin-filter-field" htmlFor="admin-product-category"><span>Danh mục</span><select id="admin-product-category" className="form-input admin-filter" value={categoryId ?? ''} onChange={(event) => { setCategoryId(event.target.value ? Number(event.target.value) : undefined); setPage(0); }}>
            <option value="">Tất cả danh mục</option>
            {categories.map((category) => <option key={category.categoryId} value={category.categoryId}>{category.categoryName}</option>)}
          </select></label>
        </div>

        {error && <div className="alert alert-error admin-alert" role="alert"><span>{error}</span><button type="button" className="btn btn-ghost btn-sm" onClick={() => void loadData()}>Thử lại</button></div>}

        <section className="table-shell" aria-label="Danh sách sản phẩm">
          <div className="table-scroll">
            <table className="admin-table admin-product-table">
              <thead><tr><th>Sản phẩm</th><th>Danh mục</th><th>VAT</th><th>Giá trước VAT</th><th>Tồn kho</th><th>Trạng thái</th><th><span className="sr-only">Thao tác</span></th></tr></thead>
              <tbody>
                {loading ? (
                  <tr><td colSpan={7}><div className="table-state"><span className="spinner spinner-dark" /> Đang tải sản phẩm...</div></td></tr>
                ) : error ? null : visibleProducts.length === 0 ? (
                  <tr><td colSpan={7}><div className="table-state"><Package size={24} aria-hidden="true" /> Chưa có sản phẩm phù hợp.</div></td></tr>
                ) : visibleProducts.map((product) => {
                  const category = categoryById.get(product.categoryId);
                  return (
                    <tr key={product.id}>
                      <td data-label="Sản phẩm"><div className="product-cell"><ProductImage className="product-thumb" src={product.imageUrl} alt="" /><span><strong>{product.name}</strong><small>{product.slug}</small></span></div></td>
                      <td data-label="Danh mục">{product.categoryName || category?.categoryName || 'Chưa có'}</td>
                      <td data-label="VAT">{category ? `${category.vatRate}%` : 'Chưa có'}</td>
                      <td data-label="Giá trước VAT" className="price-cell">{formatPrice(product.price)}</td>
                      <td data-label="Tồn kho"><strong className={product.inventoryCount < 10 ? 'stock-value stock-value--low' : 'stock-value'}>{product.inventoryCount}</strong></td>
                      <td data-label="Trạng thái"><span className={`status-badge status-${product.status.toLowerCase()}`}>{statusLabel(product.status)}</span></td>
                      <td data-label="Thao tác"><div className="table-actions"><button type="button" className="btn btn-ghost btn-sm" onClick={() => handleOpenEdit(product)}>Sửa</button><button type="button" className="btn btn-danger btn-sm" disabled={deletingId !== null} onClick={() => setDeleteTarget(product)}>Xóa</button></div></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {data && data.totalPages > 1 && <nav className="pagination" aria-label="Phân trang sản phẩm"><button type="button" className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Trang trước</button><span>Trang {page + 1} / {data.totalPages}</span><button type="button" className="btn btn-ghost btn-sm" disabled={data.last} onClick={() => setPage((value) => value + 1)}>Trang sau</button></nav>}
        </section>
      </div>

      {showModal && (
        <Modal title={editingId !== null ? 'Sửa sản phẩm' : 'Thêm sản phẩm'} context="Thông tin sản phẩm" onClose={closeForm} closeDisabled={saving}>
          <form className="admin-form-layout" onSubmit={handleSubmit}>
            <div className="form-grid">
              <label className="field field-span-2"><span>Tên sản phẩm</span><input required autoFocus className="form-input" value={formData.productName} onChange={(event) => setFormData({ ...formData, productName: event.target.value })} /></label>
              <label className="field field-span-2"><span>Đường dẫn sản phẩm</span><input required className="form-input" value={formData.productSlug} onChange={(event) => setFormData({ ...formData, productSlug: event.target.value })} /></label>
              <label className="field"><span>Danh mục</span><select required className="form-input" value={formData.categoryId || ''} onChange={(event) => setFormData({ ...formData, categoryId: Number(event.target.value) })}><option value="" disabled>Chọn danh mục</option>{categories.map((category) => <option key={category.categoryId} value={category.categoryId}>{category.categoryName}</option>)}</select></label>
              <label className="field"><span>Mức VAT</span><input className="form-input" value={selectedCategory ? `${selectedCategory.vatRate}%` : 'Chưa chọn'} readOnly /></label>
              <label className="field"><span>Giá bán trước VAT (VNĐ)</span><input required type="number" inputMode="numeric" min="1" className="form-input" value={formData.price} onChange={(event) => setFormData({ ...formData, price: Number(event.target.value) })} /></label>
              <label className="field"><span>{editingId !== null ? 'Số lượng hiện có' : 'Số lượng ban đầu'}</span><input required type="number" inputMode="numeric" min="0" className="form-input" value={formData.initialQuantity} onChange={(event) => setFormData({ ...formData, initialQuantity: Number(event.target.value) })} /></label>
              <label className="field field-span-2"><span>Địa chỉ hình ảnh</span><input type="url" className="form-input" placeholder="https://" value={formData.imageUrl} onChange={(event) => setFormData({ ...formData, imageUrl: event.target.value })} /></label>
              <label className="field field-span-2"><span>Mô tả</span><textarea className="form-input admin-textarea" value={formData.description} onChange={(event) => setFormData({ ...formData, description: event.target.value })} /></label>
              <label className="field field-span-2"><span>Trạng thái</span><select className="form-input" value={formData.status} onChange={(event) => setFormData({ ...formData, status: event.target.value })}><option value="ACTIVE">Đang bán</option><option value="INACTIVE">Ngừng bán</option></select></label>
            </div>
            <aside className="admin-image-preview"><span>Xem trước hình ảnh</span><figure><ProductImage src={formData.imageUrl} alt={formData.productName || 'Sản phẩm mới'} /></figure><small>Ảnh lỗi hoặc bỏ trống sẽ dùng hình thay thế của ShopOnline.</small></aside>
            {formError && <div className="alert alert-error admin-form-error" role="alert">{formError}</div>}
            <div className="modal-actions admin-form-actions"><button type="button" className="btn btn-ghost" onClick={closeForm} disabled={saving}>Hủy</button><button type="submit" className="btn btn-primary" disabled={saving}>{saving ? <><span className="spinner spinner-inline" /> Đang lưu...</> : 'Lưu thay đổi'}</button></div>
          </form>
        </Modal>
      )}

      {deleteTarget && (
        <Modal title="Xóa sản phẩm này?" context="Thao tác không thể hoàn tác" size="small" onClose={() => setDeleteTarget(null)} closeDisabled={deletingId !== null}>
          <div className="confirm-dialog-body"><div className="confirm-dialog-icon" aria-hidden="true"><Trash size={24} /></div><p>Sản phẩm “{deleteTarget.name}” sẽ không còn xuất hiện trong danh mục bán hàng.</p><div className="modal-actions"><button type="button" className="btn btn-ghost" disabled={deletingId !== null} onClick={() => setDeleteTarget(null)}>Giữ lại</button><button type="button" className="btn btn-danger" disabled={deletingId !== null} onClick={() => void handleDelete()}>{deletingId !== null ? <><span className="spinner spinner-inline" /> Đang xóa...</> : 'Xóa sản phẩm'}</button></div></div>
        </Modal>
      )}
    </main>
  );
}
