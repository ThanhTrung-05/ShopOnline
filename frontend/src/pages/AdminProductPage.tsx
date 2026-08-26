import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import toast from 'react-hot-toast';
import { adminProductApi, type ProductRequest } from '../api/adminProductApi';
import { categoryApi, type Category } from '../api/categoryApi';
import type { PageResponse, Product } from '../types';
import { getApiErrorMessage } from '../utils/apiError';

const GENERIC_ERROR = 'Có lỗi xảy ra. Vui lòng thử lại.';

function emptyProduct(categoryId = 0): ProductRequest {
  return {
    productName: '',
    productSlug: '',
    categoryId,
    description: '',
    price: 0,
    imageUrl: '',
    status: 'ACTIVE',
    initialQuantity: 0,
  };
}

function formatPrice(price: number) {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(price);
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
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const categoryById = useMemo(
    () => new Map(categories.map((category) => [category.categoryId, category])),
    [categories],
  );
  const selectedCategory = categoryById.get(formData.categoryId);

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

  const handleOpenCreate = () => {
    setEditingId(null);
    setFormData(emptyProduct(categories[0]?.categoryId));
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
    setShowModal(true);
  };

  const handleDelete = async (product: Product) => {
    if (!window.confirm(`Bạn có chắc muốn xóa sản phẩm “${product.name}”?`)) {
      return;
    }

    setDeletingId(product.id);
    try {
      await adminProductApi.delete(product.id);
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
        await adminProductApi.update(editingId, formData);
        toast.success('Cập nhật thành công');
      } else {
        await adminProductApi.create(formData);
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
      <div className="container admin-container">
        <div className="admin-heading">
          <div>
            <span className="eyebrow">Quản trị</span>
            <h1>Quản lý sản phẩm</h1>
            <p>Theo dõi và cập nhật thông tin sản phẩm đang kinh doanh.</p>
          </div>
          <button type="button" className="btn btn-primary" onClick={handleOpenCreate}>
            Thêm sản phẩm
          </button>
        </div>

        <div className="admin-toolbar card">
          <form className="admin-search" onSubmit={handleSearch}>
            <label className="sr-only" htmlFor="admin-product-search">Tìm sản phẩm</label>
            <input
              id="admin-product-search"
              className="form-input"
              placeholder="Tìm theo tên sản phẩm"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
            />
            <button type="submit" className="btn btn-primary">Tìm kiếm</button>
          </form>
          <label className="sr-only" htmlFor="admin-product-category">Lọc theo danh mục</label>
          <select
            id="admin-product-category"
            className="form-input admin-filter"
            value={categoryId ?? ''}
            onChange={(event) => {
              setCategoryId(event.target.value ? Number(event.target.value) : undefined);
              setPage(0);
            }}
          >
            <option value="">Tất cả danh mục</option>
            {categories.map((category) => (
              <option key={category.categoryId} value={category.categoryId}>{category.categoryName}</option>
            ))}
          </select>
        </div>

        {error && (
          <div className="alert alert-error admin-alert" role="alert">
            <span>{error}</span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void loadData()}>
              Thử lại
            </button>
          </div>
        )}

        <section className="table-shell" aria-label="Danh sách sản phẩm">
          <div className="table-scroll">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Sản phẩm</th>
                  <th>Danh mục</th>
                  <th>VAT</th>
                  <th>Giá</th>
                  <th>Trạng thái</th>
                  <th><span className="sr-only">Thao tác</span></th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan={6}><div className="table-state"><span className="spinner spinner-dark" /> Đang tải...</div></td>
                  </tr>
                ) : !data?.content.length ? (
                  <tr>
                    <td colSpan={6}><div className="table-state">Chưa có sản phẩm phù hợp.</div></td>
                  </tr>
                ) : data.content.map((product) => {
                  const category = categoryById.get(product.categoryId);
                  return (
                    <tr key={product.id}>
                      <td>
                        <div className="product-cell">
                          {product.imageUrl ? (
                            <img className="product-thumb" src={product.imageUrl} alt="" />
                          ) : (
                            <span className="product-thumb product-thumb-placeholder">SO</span>
                          )}
                          <span><strong>{product.name}</strong><small>{product.slug}</small></span>
                        </div>
                      </td>
                      <td>{product.categoryName || category?.categoryName || '—'}</td>
                      <td>{category ? `${category.vatRate}%` : '—'}</td>
                      <td className="price-cell">{formatPrice(product.price)}</td>
                      <td><span className={`status-badge status-${product.status.toLowerCase()}`}>{statusLabel(product.status)}</span></td>
                      <td>
                        <div className="table-actions">
                          <button type="button" className="btn btn-ghost btn-sm" onClick={() => handleOpenEdit(product)}>Sửa</button>
                          <button
                            type="button"
                            className="btn btn-danger btn-sm"
                            disabled={deletingId === product.id}
                            onClick={() => void handleDelete(product)}
                          >
                            {deletingId === product.id ? 'Đang xóa...' : 'Xóa'}
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {data && data.totalPages > 1 && (
            <div className="pagination" aria-label="Phân trang sản phẩm">
              <button type="button" className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>
                Trang trước
              </button>
              <span>Trang {page + 1} / {data.totalPages}</span>
              <button type="button" className="btn btn-ghost btn-sm" disabled={data.last} onClick={() => setPage((value) => value + 1)}>
                Trang sau
              </button>
            </div>
          )}
        </section>
      </div>

      {showModal && (
        <div className="modal-backdrop" role="presentation">
          <section className="modal-card" role="dialog" aria-modal="true" aria-labelledby="product-form-title">
            <div className="modal-header">
              <div>
                <span className="eyebrow">Sản phẩm</span>
                <h2 id="product-form-title">{editingId !== null ? 'Sửa sản phẩm' : 'Thêm sản phẩm'}</h2>
              </div>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setShowModal(false)} aria-label="Đóng">×</button>
            </div>

            <form className="form-grid" onSubmit={handleSubmit}>
              <label className="field field-span-2">
                <span>Tên sản phẩm</span>
                <input required className="form-input" value={formData.productName} onChange={(event) => setFormData({ ...formData, productName: event.target.value })} />
              </label>
              <label className="field field-span-2">
                <span>Đường dẫn sản phẩm</span>
                <input required className="form-input" value={formData.productSlug} onChange={(event) => setFormData({ ...formData, productSlug: event.target.value })} />
              </label>
              <label className="field">
                <span>Danh mục</span>
                <select required className="form-input" value={formData.categoryId || ''} onChange={(event) => setFormData({ ...formData, categoryId: Number(event.target.value) })}>
                  <option value="" disabled>Chọn danh mục</option>
                  {categories.map((category) => (
                    <option key={category.categoryId} value={category.categoryId}>{category.categoryName}</option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Mức VAT</span>
                <input className="form-input" value={selectedCategory ? `${selectedCategory.vatRate}%` : 'Chưa chọn'} readOnly />
              </label>
              <label className="field">
                <span>Giá bán (VNĐ)</span>
                <input required type="number" min="1" className="form-input" value={formData.price} onChange={(event) => setFormData({ ...formData, price: Number(event.target.value) })} />
              </label>
              <label className="field">
                <span>{editingId !== null ? 'Số lượng hiện có' : 'Số lượng ban đầu'}</span>
                <input required type="number" min="0" className="form-input" value={formData.initialQuantity} onChange={(event) => setFormData({ ...formData, initialQuantity: Number(event.target.value) })} />
              </label>
              <label className="field field-span-2">
                <span>Hình ảnh</span>
                <input type="url" className="form-input" placeholder="https://" value={formData.imageUrl} onChange={(event) => setFormData({ ...formData, imageUrl: event.target.value })} />
              </label>
              <label className="field field-span-2">
                <span>Mô tả</span>
                <textarea className="form-input admin-textarea" value={formData.description} onChange={(event) => setFormData({ ...formData, description: event.target.value })} />
              </label>
              <label className="field field-span-2">
                <span>Trạng thái</span>
                <select className="form-input" value={formData.status} onChange={(event) => setFormData({ ...formData, status: event.target.value })}>
                  <option value="ACTIVE">Đang bán</option>
                  <option value="INACTIVE">Ngừng bán</option>
                </select>
              </label>
              <div className="modal-actions field-span-2">
                <button type="button" className="btn btn-ghost" onClick={() => setShowModal(false)}>Hủy</button>
                <button type="submit" className="btn btn-primary" disabled={saving}>
                  {saving ? 'Đang lưu...' : 'Lưu thay đổi'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </main>
  );
}
