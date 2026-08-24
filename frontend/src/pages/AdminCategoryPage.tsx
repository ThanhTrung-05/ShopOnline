import { useEffect, useState, useCallback } from 'react';
import { categoryApi, Category, CategoryRequest } from '../api/categoryApi';

export default function AdminCategoryPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);

  // Modal State
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [formData, setFormData] = useState<CategoryRequest>({
    categoryName: '', categoryCode: '', description: '', vatRate: 10, status: 'ACTIVE'
  });

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await categoryApi.list();
      setCategories(res.data.data);
    } catch (error) {
      console.error('Failed to load categories', error);
      alert('Không thể tải dữ liệu danh mục.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  const handleOpenCreate = () => {
    setEditingId(null);
    setFormData({
      categoryName: '', categoryCode: '', description: '', vatRate: 10, status: 'ACTIVE'
    });
    setShowModal(true);
  };

  const handleOpenEdit = (c: Category) => {
    setEditingId(c.categoryId);
    setFormData({
      categoryName: c.categoryName, categoryCode: c.categoryCode, description: c.description || '',
      vatRate: c.vatRate, status: c.status
    });
    setShowModal(true);
  };

  const handleDelete = async (id: number, name: string) => {
    if (!confirm(`Bạn có chắc chắn muốn xóa danh mục "${name}"?`)) return;
    try {
      await categoryApi.delete(id);
      loadData();
    } catch (error: any) {
      alert(error.response?.data?.message || 'Xóa thất bại');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editingId) {
        await categoryApi.update(editingId, formData);
      } else {
        await categoryApi.create(formData);
      }
      setShowModal(false);
      loadData();
    } catch (error: any) {
      alert(error.response?.data?.message || 'Lưu thất bại. Kiểm tra mã danh mục có bị trùng không.');
    }
  };

  return (
    <div className="page" style={{ padding: '24px' }}>
      <div className="container" style={{ maxWidth: 1000, margin: '0 auto' }}>
        
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
          <h2>Quản lý Danh mục (ATS-5)</h2>
          <button className="btn btn-primary" onClick={handleOpenCreate}>+ Thêm Danh mục</button>
        </div>

        {/* Table */}
        <div style={{ overflowX: 'auto', background: 'var(--surface)', borderRadius: 8, boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border)', background: 'var(--background)' }}>
                <th style={{ padding: '12px 16px' }}>ID</th>
                <th style={{ padding: '12px 16px' }}>Tên Danh mục</th>
                <th style={{ padding: '12px 16px' }}>Mã (Code)</th>
                <th style={{ padding: '12px 16px' }}>Mức VAT (%)</th>
                <th style={{ padding: '12px 16px' }}>Trạng thái</th>
                <th style={{ padding: '12px 16px' }}>Hành động</th>
              </tr>
            </thead>
            <tbody>
              {loading ? <tr><td colSpan={6} style={{ padding: 24, textAlign: 'center' }}>Đang tải...</td></tr> :
                categories.map(c => (
                  <tr key={c.categoryId} style={{ borderBottom: '1px solid var(--border)' }}>
                    <td style={{ padding: '12px 16px' }}>{c.categoryId}</td>
                    <td style={{ padding: '12px 16px', fontWeight: 500 }}>{c.categoryName}</td>
                    <td style={{ padding: '12px 16px' }}>{c.categoryCode}</td>
                    <td style={{ padding: '12px 16px' }}>{c.vatRate}%</td>
                    <td style={{ padding: '12px 16px' }}>
                      <span style={{ 
                        padding: '4px 8px', borderRadius: 12, fontSize: '0.8rem', fontWeight: 600,
                        background: c.status === 'ACTIVE' ? '#dcfce7' : '#fef08a',
                        color: c.status === 'ACTIVE' ? '#166534' : '#854d0e'
                      }}>{c.status}</span>
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      <button className="btn btn-ghost btn-sm" onClick={() => handleOpenEdit(c)}>Sửa</button>
                      <button className="btn btn-ghost btn-sm" onClick={() => handleDelete(c.categoryId, c.categoryName)} style={{ color: 'red' }}>Xóa</button>
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>

        {/* Modal */}
        {showModal && (
          <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
            <div style={{ background: 'var(--surface)', padding: 32, borderRadius: 12, width: '100%', maxWidth: 500, maxHeight: '90vh', overflowY: 'auto' }}>
              <h3 style={{ marginTop: 0 }}>{editingId ? 'Sửa Danh mục' : 'Thêm Danh mục'}</h3>
              <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                
                <div>
                  <label>Tên danh mục *</label>
                  <input required className="form-input" style={{ width: '100%' }} value={formData.categoryName} onChange={e => setFormData({...formData, categoryName: e.target.value})} />
                </div>
                
                <div>
                  <label>Mã danh mục (CODE) *</label>
                  <input required className="form-input" style={{ width: '100%' }} value={formData.categoryCode} onChange={e => setFormData({...formData, categoryCode: e.target.value})} />
                </div>
                
                <div>
                  <label>Mức VAT (%) *</label>
                  <select required className="form-input" style={{ width: '100%' }} value={formData.vatRate} onChange={e => setFormData({...formData, vatRate: Number(e.target.value)})}>
                    <option value={5}>5%</option>
                    <option value={10}>10%</option>
                  </select>
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
