import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { adminProductApi } from '../api/adminProductApi';
import { categoryApi } from '../api/categoryApi';
import AdminProductPage from './AdminProductPage';

vi.mock('../api/adminProductApi', () => ({
  adminProductApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('../api/categoryApi', () => ({
  categoryApi: { list: vi.fn() },
}));

vi.mock('react-hot-toast', () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const category = {
  categoryId: 1,
  categoryName: 'Gia dụng',
  categoryCode: 'GIA_DUNG',
  description: '',
  vatRate: 10,
  status: 'ACTIVE',
};

function product(featured: boolean) {
  return {
    id: 7,
    name: 'Ấm điện',
    slug: 'am-dien',
    price: 450000,
    description: '',
    categoryId: 1,
    categoryName: 'Gia dụng',
    inventoryCount: 12,
    status: 'ACTIVE',
    featured,
  };
}

function productPage(featured: boolean) {
  return {
    data: {
      data: {
        content: [product(featured)],
        totalPages: 1,
        totalElements: 1,
        number: 0,
        size: 20,
        last: true,
      },
    },
  };
}

describe('AdminProductPage featured control', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(categoryApi.list).mockResolvedValue({ data: { data: [category] } } as any);
    vi.mocked(adminProductApi.update).mockResolvedValue({} as any);
  });

  it.each([
    { initial: false, expected: true, label: 'bật' },
    { initial: true, expected: false, label: 'tắt' },
  ])('cho phép Admin $label featured trong Product CRUD', async ({ initial, expected }) => {
    vi.mocked(adminProductApi.list).mockResolvedValue(productPage(initial) as any);
    render(<MemoryRouter><AdminProductPage /></MemoryRouter>);

    fireEvent.click(await screen.findByRole('button', { name: 'Sửa' }));
    const checkbox = screen.getByRole('checkbox', { name: 'Sản phẩm nổi bật' });
    expect(checkbox).toHaveProperty('checked', initial);
    fireEvent.click(checkbox);
    fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));

    await waitFor(() => expect(adminProductApi.update).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ featured: expected }),
    ));
  });
});
