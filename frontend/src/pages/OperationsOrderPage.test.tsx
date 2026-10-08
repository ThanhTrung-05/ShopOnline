import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import toast from 'react-hot-toast';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('react-hot-toast', () => ({
  default: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

vi.mock('../api/operationsOrderApi', () => ({
  operationsOrderApi: {
    list: vi.fn(),
    updateStatus: vi.fn(),
  },
}));

import {
  operationsOrderApi,
  type OperationsOrder,
} from '../api/operationsOrderApi';
import OperationsOrderPage from './OperationsOrderPage';

const orders: OperationsOrder[] = [
  { orderNumber: 'ORD-PENDING', status: 'PENDING', updatedAt: '2026-09-07T01:00:00Z' },
  { orderNumber: 'ORD-CONFIRMED', status: 'CONFIRMED', updatedAt: '2026-09-07T02:00:00Z' },
  { orderNumber: 'ORD-SHIPPING', status: 'SHIPPING', updatedAt: '2026-09-07T03:00:00Z' },
  { orderNumber: 'ORD-DELIVERED', status: 'DELIVERED', updatedAt: '2026-09-07T04:00:00Z' },
  { orderNumber: 'ORD-CANCELLED', status: 'CANCELLED', updatedAt: '2026-09-07T05:00:00Z' },
  { orderNumber: 'ORD-PAID', status: 'PAID', updatedAt: '2026-09-07T06:00:00Z' },
  { orderNumber: 'ORD-PAYMENT-FAILED', status: 'PAYMENT_FAILED', updatedAt: '2026-09-07T07:00:00Z' },
  { orderNumber: 'ORD-REFUNDED', status: 'REFUNDED', updatedAt: '2026-09-07T08:00:00Z' },
];

function orderRow(orderNumber: string) {
  const row = screen.getByText(orderNumber).closest('tr');
  expect(row).not.toBeNull();
  return within(row as HTMLTableRowElement);
}

describe('OperationsOrderPage', () => {
  beforeEach(() => {
    vi.mocked(operationsOrderApi.list).mockReset();
    vi.mocked(operationsOrderApi.updateStatus).mockReset();
    vi.mocked(toast.success).mockReset();
    vi.mocked(toast.error).mockReset();
  });

  it('loads orders and shows only server-supported next actions', async () => {
    vi.mocked(operationsOrderApi.list).mockResolvedValue({ data: { data: orders } } as any);

    render(<OperationsOrderPage />);

    expect(screen.getByText('Đang tải...')).toBeInTheDocument();
    expect(await screen.findByText('ORD-PENDING')).toBeInTheDocument();
    expect(orderRow('ORD-PENDING').getByRole('button', { name: 'Xác nhận' })).toBeInTheDocument();
    expect(orderRow('ORD-PENDING').getByRole('button', { name: 'Hủy đơn' })).toBeInTheDocument();
    expect(orderRow('ORD-PENDING').queryByRole('button', { name: 'Bắt đầu giao hàng' })).not.toBeInTheDocument();

    expect(orderRow('ORD-CONFIRMED').getByRole('button', { name: 'Bắt đầu giao hàng' })).toBeInTheDocument();
    expect(orderRow('ORD-CONFIRMED').getByRole('button', { name: 'Hủy đơn' })).toBeInTheDocument();
    expect(orderRow('ORD-SHIPPING').getByRole('button', { name: 'Xác nhận đã giao' })).toBeInTheDocument();

    for (const orderNumber of [
      'ORD-DELIVERED',
      'ORD-CANCELLED',
      'ORD-PAID',
      'ORD-PAYMENT-FAILED',
      'ORD-REFUNDED',
    ]) {
      expect(orderRow(orderNumber).queryByRole('button')).not.toBeInTheDocument();
      expect(orderRow(orderNumber).getByText('Không có thao tác')).toBeInTheDocument();
    }

    expect(orderRow('ORD-SHIPPING').getByText(
      new Date('2026-09-07T03:00:00Z').toLocaleString('vi-VN'),
    )).toBeInTheDocument();
  });

  it('sends the chosen transition and refetches the list after success', async () => {
    const pendingOrder = orders[0];
    const confirmedOrder = { ...pendingOrder, status: 'CONFIRMED' as const };
    vi.mocked(operationsOrderApi.list)
      .mockResolvedValueOnce({ data: { data: [pendingOrder] } } as any)
      .mockResolvedValueOnce({ data: { data: [confirmedOrder] } } as any);
    vi.mocked(operationsOrderApi.updateStatus).mockResolvedValue({
      data: { data: confirmedOrder },
    } as any);
    render(<OperationsOrderPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận' }));

    await waitFor(() => expect(operationsOrderApi.updateStatus)
      .toHaveBeenCalledWith('ORD-PENDING', 'CONFIRMED'));
    await waitFor(() => expect(operationsOrderApi.list).toHaveBeenCalledTimes(2));
    expect(orderRow('ORD-PENDING').getByText('Đã xác nhận')).toBeInTheDocument();
    expect(toast.success).toHaveBeenCalledWith('Cập nhật trạng thái đơn hàng thành công.');
  });

  it('prevents another status action while an update is in progress', async () => {
    vi.mocked(operationsOrderApi.list).mockResolvedValue({
      data: { data: [orders[0], orders[1]] },
    } as any);
    vi.mocked(operationsOrderApi.updateStatus).mockImplementation(() => new Promise(() => {}));
    render(<OperationsOrderPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận' }));

    expect(await screen.findAllByText('Đang cập nhật...')).not.toHaveLength(0);
    for (const button of screen.getAllByRole('button')) {
      if (button.textContent !== 'Làm mới') {
        expect(button).toBeDisabled();
      }
    }
    expect(operationsOrderApi.updateStatus).toHaveBeenCalledTimes(1);
  });

  it('uses existing error feedback when a status update fails', async () => {
    vi.mocked(operationsOrderApi.list).mockResolvedValue({ data: { data: [orders[0]] } } as any);
    vi.mocked(operationsOrderApi.updateStatus).mockRejectedValue({
      isAxiosError: true,
      response: { status: 400, data: { message: 'Invalid transition' } },
    });
    render(<OperationsOrderPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Invalid transition'));
    expect(operationsOrderApi.list).toHaveBeenCalledTimes(1);
  });

  it('shows list loading errors and supports retry', async () => {
    vi.mocked(operationsOrderApi.list)
      .mockRejectedValueOnce(new Error('List unavailable'))
      .mockResolvedValueOnce({ data: { data: [] } } as any);
    render(<OperationsOrderPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('List unavailable');
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));

    expect(await screen.findByText('Chưa có đơn hàng.')).toBeInTheDocument();
    expect(operationsOrderApi.list).toHaveBeenCalledTimes(2);
  });
});
