import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import toast from 'react-hot-toast';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('react-hot-toast', () => ({
  default: {
    error: vi.fn(),
  },
}));

vi.mock('../api/orderApi', () => ({
  orderApi: {
    list: vi.fn(),
    getStatus: vi.fn(),
    getDetails: vi.fn(),
  },
}));

import {
  orderApi,
  type OrderDetails,
  type OrderStatusDetails,
} from '../api/orderApi';
import OrderStatusPage from './OrderStatusPage';

const orders: OrderStatusDetails[] = [
  {
    orderNumber: 'ORD-2026-002',
    status: 'SHIPPING',
    createdAt: '2026-08-28T03:00:00Z',
    updatedAt: '2026-08-28T05:15:00Z',
  },
  {
    orderNumber: 'ORD-2026-001',
    status: 'PAID',
    createdAt: '2026-08-27T03:00:00Z',
    updatedAt: '2026-08-27T04:00:00Z',
  },
];

const orderDetails: OrderDetails = {
  ...orders[0],
  status: 'DELIVERED',
  updatedAt: '2026-08-28T08:30:00Z',
  totalAmount: 2_960_000,
  items: [
    {
      productId: 501,
      productName: 'Mechanical Keyboard',
      unitPrice: 1_250_000,
      quantity: 2,
      subtotal: 2_500_000,
    },
    {
      productId: 502,
      productName: 'Wireless Mouse',
      unitPrice: 450_000,
      quantity: 1,
      subtotal: 450_000,
    },
  ],
};

function renderPage(initialEntry = '/orders/status') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <OrderStatusPage />
    </MemoryRouter>,
  );
}

describe('OrderStatusPage', () => {
  beforeEach(() => {
    vi.mocked(orderApi.list).mockReset();
    vi.mocked(orderApi.getStatus).mockReset();
    vi.mocked(orderApi.getDetails).mockReset();
    vi.mocked(toast.error).mockReset();
  });

  it('loads and renders the customer order list with Vietnamese statuses', async () => {
    vi.mocked(orderApi.list).mockResolvedValue({
      data: { data: orders },
    } as any);

    renderPage();

    expect(screen.getByText('Đang tải đơn hàng...')).toBeInTheDocument();
    await waitFor(() => expect(orderApi.list).toHaveBeenCalledTimes(1));
    expect(await screen.findByText('ORD-2026-002')).toBeInTheDocument();
    expect(screen.getByText('ORD-2026-001')).toBeInTheDocument();
    expect(screen.getByText('Đang giao hàng')).toBeInTheDocument();
    expect(screen.getByText('Đã thanh toán')).toBeInTheDocument();
    expect(screen.getByText(new Date(orders[0].createdAt).toLocaleString('vi-VN'))).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('renders the empty state when the customer has no orders', async () => {
    vi.mocked(orderApi.list).mockResolvedValue({
      data: { data: [] },
    } as any);

    renderPage();

    expect(await screen.findByText('Bạn chưa có đơn hàng nào.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /ORD-/ })).not.toBeInTheDocument();
  });

  it('loads and renders all item snapshots when the customer selects an order', async () => {
    vi.mocked(orderApi.list).mockResolvedValue({
      data: { data: orders },
    } as any);
    vi.mocked(orderApi.getDetails).mockResolvedValue({
      data: { data: orderDetails },
    } as any);
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /ORD-2026-002/ }));

    await waitFor(() => expect(orderApi.getDetails).toHaveBeenCalledWith('ORD-2026-002'));
    expect(orderApi.getStatus).not.toHaveBeenCalled();
    const result = await screen.findByRole('region', { name: 'Chi tiết đơn hàng' });
    expect(within(result).getByText('Đã giao hàng')).toBeInTheDocument();
    expect(within(result).getByText(new Date(orderDetails.updatedAt).toLocaleString('vi-VN'))).toBeInTheDocument();
    expect(within(result).getByText(/2\.960\.000/)).toBeInTheDocument();
    expect(within(result).getByText('2 sản phẩm')).toBeInTheDocument();

    const keyboardItem = within(result).getByText('Mechanical Keyboard').closest('article');
    expect(keyboardItem).not.toBeNull();
    expect(within(keyboardItem as HTMLElement).getByText(/1\.250\.000/)).toBeInTheDocument();
    expect(within(keyboardItem as HTMLElement).getByText('2')).toBeInTheDocument();
    expect(within(keyboardItem as HTMLElement).getByText(/2\.500\.000/)).toBeInTheDocument();

    const mouseItem = within(result).getByText('Wireless Mouse').closest('article');
    expect(mouseItem).not.toBeNull();
    expect(within(mouseItem as HTMLElement).getAllByText(/450\.000/)).toHaveLength(2);
    expect(within(mouseItem as HTMLElement).getByText('1')).toBeInTheDocument();
  });

  it('shows a detail loading state and prevents selecting another order', async () => {
    vi.mocked(orderApi.list).mockResolvedValue({
      data: { data: orders },
    } as any);
    vi.mocked(orderApi.getDetails).mockImplementation(() => new Promise(() => {}));
    renderPage();

    const selectedButton = await screen.findByRole('button', { name: /ORD-2026-002/ });
    fireEvent.click(selectedButton);

    expect(await screen.findByText('Đang tải chi tiết...')).toBeInTheDocument();
    expect(selectedButton).toBeDisabled();
    expect(screen.getByRole('button', { name: /ORD-2026-001/ })).toBeDisabled();
    expect(orderApi.getDetails).toHaveBeenCalledTimes(1);
  });

  it('renders a safe empty state when an owned order unexpectedly has no items', async () => {
    vi.mocked(orderApi.list).mockResolvedValue({
      data: { data: orders },
    } as any);
    vi.mocked(orderApi.getDetails).mockResolvedValue({
      data: { data: { ...orderDetails, items: [] } },
    } as any);
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /ORD-2026-002/ }));

    expect(await screen.findByText('Đơn hàng này chưa có sản phẩm.')).toBeInTheDocument();
  });

  it('keeps the safe generic message when a selected order returns 404', async () => {
    vi.mocked(orderApi.list).mockResolvedValue({
      data: { data: orders },
    } as any);
    vi.mocked(orderApi.getDetails).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 404,
        data: { message: 'Order not found with id: ORD-2026-002' },
      },
    });
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /ORD-2026-002/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Không tìm thấy đơn hàng.');
    expect(screen.queryByText(/Order not found/)).not.toBeInTheDocument();
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('shows a safe backend error using the existing detail-error convention', async () => {
    vi.mocked(orderApi.list).mockResolvedValue({
      data: { data: orders },
    } as any);
    vi.mocked(orderApi.getDetails).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 503,
        data: { message: 'Không thể tải dữ liệu đơn hàng lúc này.' },
      },
    });
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /ORD-2026-002/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Không thể tải dữ liệu đơn hàng lúc này.',
    );
    expect(toast.error).toHaveBeenCalledWith('Không thể tải dữ liệu đơn hàng lúc này.');
  });

  it('opens an owned order detail from the notification deep link', async () => {
    vi.mocked(orderApi.list).mockResolvedValue({
      data: { data: orders },
    } as any);
    vi.mocked(orderApi.getDetails).mockResolvedValue({
      data: { data: orderDetails },
    } as any);

    renderPage('/orders/status?orderNumber=ORD-2026-002');

    await waitFor(() => expect(orderApi.getDetails).toHaveBeenCalledWith('ORD-2026-002'));
    expect(await screen.findByText('Mechanical Keyboard')).toBeInTheDocument();
  });
});
