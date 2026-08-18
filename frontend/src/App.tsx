import { Routes, Route, Navigate } from 'react-router-dom';
import ProductsPage from './pages/ProductsPage';
import ProductDetailPage from './pages/ProductDetailPage';
import AdminProductPage from './pages/AdminProductPage';
import AdminCategoryPage from './pages/AdminCategoryPage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/products" replace />} />
      <Route path="/products" element={<ProductsPage />} />
      <Route path="/products/:id" element={<ProductDetailPage />} />
      <Route path="/admin/products" element={<AdminProductPage />} />
      <Route path="/admin/categories" element={<AdminCategoryPage />} />
    </Routes>
  );
}
