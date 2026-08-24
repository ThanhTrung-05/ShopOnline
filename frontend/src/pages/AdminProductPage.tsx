import { useEffect, useState, useCallback } from 'react';
import { adminProductApi, ProductRequest } from '../api/adminProductApi';
import { categoryApi, Category } from '../api/categoryApi';
import type { Product, PageResponse } from '../types';

export default function AdminProductPage() {
  const [data, setData] = useState<PageResponse<Product> | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  
  // Filters
  const [page, setPage] = useState(0);
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');

  // Modal State
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [formData, setFormData] = useState<ProductRequest>({
    name: '', slug: '', categoryId: 0, description: '', 
    price: 0, imageUrl: '', status: 'ACTIVE', initialQuantity: 0
  });

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [prodRes, catRes] = await Promise.all([
        adminProductApi.list(page, 20, categoryId, search),
        categoryApi.list()
      ]);
      setData(prodRes.data.data);
      setCategories(catRes.data.data);
    } catch (error) {
      console.error('Failed to load admin data', error);
      alert('Không thể tải dữ liệu. Vui lòng kiểm tra quyền Admin.');
    } finally {
      setLoading(false);
    }
  }, [page, categoryId, search]);

  useEffect(() => { loadData(); }, [loadData]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setSearch(searchInput);
  };

  const handleOpenCreate = () => {
    setEditingId(null);
    setFormData({
      name: '', slug: '', categoryId: categories[0]?.categoryId || 0, description: '', 
      price: 0, imageUrl: '', status: 'ACTIVE', initialQuantity: 0
    });
    setShowModal(true);
  };

  const handleOpenEdit = (p: Product) => {
    setEditingId(p.id);
    setFormData({
      name: p.name, slug: p.slug, categoryId: p.categoryId, description: p.description || '',
      price: p.price, imageUrl: p.imageUrl || '', status: p.status, initialQuantity: p.inventoryCount
    });
    setShowModal(true);
  };

  const handleDelete = async (id: number, name: string) => {
    if (!confirm(`Bạn có chắc chắn muốn xóa sản phẩm "${name}"?`)) return;
    try {
      await adminProductApi.delete(id);
      loadData();
    } catch (error: any) {
      alert(error.response?.data?.message || 'Xóa thất bại');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editingId) {
        await adminProductApi.update(editingId, formData);
      } else {
        await adminProductApi.create(formData);
      }
      setShowModal(false);
      loadData();
    } catch (error: any) {
      alert(error.response?.data?.message || 'Lưu thất bại');
    }
  };

  // Find selected category to display VAT
  const selectedCat = categories.find(c => c.categoryId === formData.categoryId);

  return (
    <div className="page" style={{ padding: '24px' }}>
      <div className="container" style={{ maxWidth: 1200, margin: '0 auto' }}>
        
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
          <h2>Quản lý Sản phẩm</h2>
          <button className="btn btn-primary" onClick={handleOpenCreate}>+ Thêm Sản phẩm</button>
        </div>

        {/* Filters */}
        <div style={{ display: 'flex', gap: 12, marginBottom: 24, background: 'var(--surface)', padding: 16, borderRadius: 8 }}>
          <form onSubmit={handleSearch} style={{ display: 'flex', gap: 8, flex: 1 }}>
            <input className="form-input" placeholder="Tìm kiếm tên sản phẩm..."
              value={searchInput} onChange={(e) => setSearchInput(e.target.value)} style={{ flex: 1 }} />
            <button type="submit" className="btn btn-primary">Tìm</button>
          </form>
          <select className="form-input" value={categoryId || ''} onChange={(e) => {
            setCategoryId(e.target.value ? Number(e.target.value) : undefined);
            setPage(0);
          }} style={{ width: 200 }}>
            <option value="">Tất cả danh mục</option>
            {categories.map(c => <option key={c.categoryId} value={c.categoryId}>{c.categoryName}</option>)}
          </select>
        </div>

        {/* Table */}
        <div style={{ overflowX: 'auto', background: 'var(--surface)', borderRadius: 8, boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border)', background: 'var(--background)' }}>
                <th style={{ padding: '12px 16px' }}>ID</th>
                <th style={{ padding: '12px 16px' }}>Tên Sản phẩm</th>
                <th style={{ padding: '12px 16px' }}>Slug</th>
                <th style={{ padding: '12px 16px' }}>Danh mục</th>
                <th style={{ padding: '12px 16px' }}>Giá (VND)</th>
                <th style={{ padding: '12px 16px' }}>Tồn kho</th>
                <th style={{ padding: '12px 16px' }}>Trạng thái</th>
                <th style={{ padding: '12px 16px' }}>Hành động</th>
              </tr>
            </thead>
            <tbody>
              {loading ? <tr><td colSpan={8} style={{ padding: 24, textAlign: 'center' }}>Đang tải...</td></tr> :
                data?.content.map(p => (
                  <tr key={p.id} style={{ borderBottom: '1px solid var(--border)' }}>
                    <td style={{ padding: '12px 16px' }}>{p.id}</td>
                    <td style={{ padding: '12px 16px', fontWeight: 500 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        {p.imageUrl ? <img src={p.imageUrl} alt="" style={{ width: 40, height: 40, objectFit: 'cover', borderRadius: 4 }} /> 
                                    : <div style={{ width: 40, height: 40, background: '#e5e7eb', borderRadius: 4 }} />}
                        {p.name}
                      </div>
                    </td>
                    <td style={{ padding: '12px 16px' }}>{p.slug}</td>
                    <td style={{ padding: '12px 16px' }}>{p.categoryName}</td>
                    <td style={{ padding: '12px 16px' }}>{p.price.toLocaleString()}</td>
                    <td style={{ padding: '12px 16px' }}>{p.inventoryCount}</td>
                    <td style={{ padding: '12px 16px' }}>
                      <span style={{ 
                        padding: '4px 8px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 600,
                        background: p.status === 'ACTIVE' ? '#dcfce7' : '#fef08a',
                        color: p.status === 'ACTIVE' ? '#166534' : '#854d0e'
                      }}>{p.status}</span>
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      <button className="btn btn-ghost btn-sm" onClick={() => handleOpenEdit(p)}>Sửa</button>
                      <button className="btn btn-ghost btn-sm" onClick={() => handleDelete(p.id, p.name)} style={{ color: 'red' }}>Xóa</button>
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
          {data && data.totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', gap: 8, padding: 16 }}>
              <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>←</button>
              <span>{page + 1} / {data.totalPages}</span>
              <button className="btn btn-ghost btn-sm" disabled={data.last} onClick={() => setPage(p => p + 1)}>→</button>
            </div>
          )}
        </div>

        {/* Modal */}
        {showModal && (
          <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
            <div style={{ background: 'var(--surface)', padding: 32, borderRadius: 12, width: '100%', maxWidth: 600, maxHeight: '90vh', overflowY: 'auto' }}>
              <h3 style={{ marginTop: 0 }}>{editingId ? 'Sửa Sản phẩm' : 'Thêm Sản phẩm'}</h3>
              <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                
                <div>
                  <label>Tên sản phẩm *</label>
                  <input required className="form-input" style={{ width: '100%' }} value={formData.name} onChange={e => setFormData({...formData, name: e.target.value})} />
                </div>
                
                <div>
                  <label>Slug (URL) *</label>
                  <input required className="form-input" style={{ width: '100%' }} value={formData.slug} onChange={e => setFormData({...formData, slug: e.target.value})} />
                </div>
                
                <div style={{ display: 'flex', gap: 16 }}>
                  <div style={{ flex: 1 }}>
                    <label>Danh mục (Xác định VAT) *</label>
                    <select required className="form-input" style={{ width: '100%' }} value={formData.categoryId} onChange={e => setFormData({...formData, categoryId: Number(e.target.value)})}>
                      <option value="">Chọn danh mục</option>
                      {categories.map(c => <option key={c.categoryId} value={c.categoryId}>{c.categoryName}</option>)}
                    </select>
                  </div>
                  <div style={{ flex: 1 }}>
                    <label>Mức VAT áp dụng (Read-only)</label>
                    <input disabled className="form-input" style={{ width: '100%', background: '#f3f4f6' }} 
                      value={selectedCat ? `${selectedCat.vatRate}%` : 'Chưa chọn DM'} />
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 16 }}>
                  <div style={{ flex: 1 }}>
                    <label>Giá (VNĐ) *</label>
                    <input required type="number" min="1" className="form-input" style={{ width: '100%' }} value={formData.price} onChange={e => setFormData({...formData, price: Number(e.target.value)})} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <label>{editingId ? 'Sửa tồn kho (tuyệt đối)' : 'Tồn kho ban đầu'} *</label>
                    <input required type="number" min="0" className="form-input" style={{ width: '100%' }} value={formData.initialQuantity} onChange={e => setFormData({...formData, initialQuantity: Number(e.target.value)})} />
                  </div>
                </div>

                <div>
                  <label>URL Hình ảnh</label>
                  <input className="form-input" style={{ width: '100%' }} value={formData.imageUrl} onChange={e => setFormData({...formData, imageUrl: e.target.value})} />
                </div>

                <div>
                  <label>Mô tả</label>
                  <textarea className="form-input" style={{ width: '100%', minHeight: 80 }} value={formData.description} onChange={e => setFormData({...formData, description: e.target.value})} />
                </div>

                <div>
                  <label>Trạng thái</label>
                  <select className="form-input" style={{ width: '100%' }} value={formData.status} onChange={e => setFormData({...formData, status: e.target.value})}>
                    <option value="ACTIVE">Hoạt động (ACTIVE)</option>
                    <option value="INACTIVE">Tạm dừng (INACTIVE)</option>
                  </select>
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12, marginTop: 16 }}>
                  <button type="button" className="btn btn-ghost" onClick={() => setShowModal(false)}>Hủy</button>
                  <button type="submit" className="btn btn-primary">Lưu</button>
                </div>
              </form>
            </div>
          </div>
        )}

      </div>
    </div>
  );
}
