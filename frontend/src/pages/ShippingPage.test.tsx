import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import toast from 'react-hot-toast';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('react-hot-toast', () => ({
  default: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

vi.mock('../api/addressApi', () => ({
  addressApi: {
    list: vi.fn(),
  },
}));

vi.mock('../api/shippingApi', () => ({
  shippingApi: {
    prepare: vi.fn(),
  },
}));

vi.mock('../api/orderApi', () => ({
  orderApi: {
    create: vi.fn(),
    list: vi.fn(),
    getStatus: vi.fn(),
    getDetails: vi.fn(),
  },
}));

import { addressApi, type Address } from '../api/addressApi';
import { orderApi, type CreatedOrderSummary } from '../api/orderApi';
import { shippingApi, type ShippingPreparation } from '../api/shippingApi';
import { useCartStore } from '../store/cartStore';
import OrderStatusPage from './OrderStatusPage';
import ShippingPage from './ShippingPage';

const addresses: Address[] = [
  {
    addressId: 42,
    recipientName: 'Nguyễn Văn A',
    phone: '0987654321',
    line1: '123 Lê Lợi',
    ward: 'Bến Nghé',
    district: 'Quận 1',
    province: 'Hà Nội',
    isDefault: true,
  },
  {
    addressId: 77,
    recipientName: 'Trần Thị B',
    phone: '0912345678',
    line1: '45 Trần Phú',
    ward: null,
    district: 'Từ Sơn',
    province: 'Bắc Ninh',
    isDefault: false,
  },
];

const preparation: ShippingPreparation = {
  customerId: 7,
  addressId: 77,
  recipientName: 'Trần Thị B',
  phone: '0912345678',
  line1: '45 Trần Phú',
  ward: null,
  district: 'Từ Sơn',
  province: 'Bắc Ninh',
  shippingMethod: 'EXPRESS',
  region: 'NEARBY',
  shippingFee: 40000,
};

const createdOrder: CreatedOrderSummary = {
  orderNumber: 'ORD-20260907-ABCDEF12',
  status: 'PENDING',
  totalAmount: 79000,
  shippingFee: 40000,
  createdAt: '2026-09-07T02:00:00Z',
};

const clearLocalCart = vi.fn();

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/shipping']}>
      <Routes>
        <Route path="/shipping" element={<ShippingPage />} />
        <Route path="/orders/status" element={<OrderStatusPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function addressChoices() {
  const group = await screen.findByRole('radiogroup', { name: 'Nơi nhận hàng' });
  return {
    defaultAddress: within(group).getByRole('radio', { name: /Nguyễn Văn A/ }),
    otherAddress: within(group).getByRole('radio', { name: /Trần Thị B/ }),
  };
}

function methodChoices() {
  const group = screen.getByRole('radiogroup', { name: 'Phương thức giao hàng' });
  return {
    standard: within(group).getByRole('radio', { name: /Giao hàng tiêu chuẩn/ }),
    express: within(group).getByRole('radio', { name: /Giao hàng nhanh/ }),
  };
}

describe('ShippingPage', () => {
  beforeEach(() => {
    vi.mocked(addressApi.list).mockReset();
    vi.mocked(shippingApi.prepare).mockReset();
    vi.mocked(orderApi.create).mockReset();
    vi.mocked(orderApi.list).mockReset();
    vi.mocked(orderApi.getStatus).mockReset();
    vi.mocked(orderApi.getDetails).mockReset();
    vi.mocked(toast.success).mockReset();
    vi.mocked(toast.error).mockReset();
    clearLocalCart.mockReset();
    useCartStore.setState({ items: [], subtotal: 0, clearLocal: clearLocalCart });
    vi.mocked(addressApi.list).mockResolvedValue({
      data: { data: addresses },
    } as any);
    vi.mocked(orderApi.list).mockResolvedValue({ data: { data: [] } } as any);
  });

  it('renders addresses and initially selects the default address', async () => {
    renderPage();

    const { defaultAddress, otherAddress } = await addressChoices();

    expect(screen.getAllByText(/Nguyễn Văn A/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Trần Thị B/).length).toBeGreaterThan(0);
    expect(defaultAddress).toBeChecked();
    expect(otherAddress).not.toBeChecked();
    expect(screen.getByText(/Mặc định/)).toBeInTheDocument();
  });

  it('lets the customer select another address', async () => {
    renderPage();
    const { defaultAddress, otherAddress } = await addressChoices();

    fireEvent.click(otherAddress);

    expect(otherAddress).toBeChecked();
    expect(defaultAddress).not.toBeChecked();
  });

  it('lets the customer select STANDARD or EXPRESS', async () => {
    renderPage();
    await addressChoices();
    const { standard, express } = methodChoices();

    expect(standard).not.toBeChecked();
    expect(express).not.toBeChecked();

    fireEvent.click(standard);
    expect(standard).toBeChecked();
    expect(express).not.toBeChecked();

    fireEvent.click(express);
    expect(express).toBeChecked();
    expect(standard).not.toBeChecked();
  });

  it('prevents preparation until a shipping method is selected', async () => {
    renderPage();
    await addressChoices();
    const submit = screen.getByRole('button', { name: 'Tính phí giao hàng' });

    expect(submit).toBeDisabled();
    fireEvent.click(methodChoices().standard);
    expect(submit).toBeEnabled();
  });

  it('keeps order placement disabled until shipping preparation succeeds', async () => {
    vi.mocked(shippingApi.prepare).mockResolvedValue({
      data: { data: preparation },
    } as any);
    renderPage();
    const { otherAddress } = await addressChoices();
    const placeOrder = screen.getByRole('button', { name: 'Đặt hàng' });

    expect(placeOrder).toBeDisabled();
    fireEvent.click(otherAddress);
    fireEvent.click(methodChoices().express);
    fireEvent.click(screen.getByRole('button', { name: 'Tính phí giao hàng' }));

    await waitFor(() => expect(placeOrder).toBeEnabled());
  });

  it('submits the selected values and displays the server region and fee', async () => {
    vi.mocked(shippingApi.prepare).mockResolvedValue({
      data: { data: preparation },
    } as any);
    renderPage();
    const { otherAddress } = await addressChoices();

    fireEvent.click(otherAddress);
    fireEvent.click(methodChoices().express);
    fireEvent.click(screen.getByRole('button', { name: 'Tính phí giao hàng' }));

    await waitFor(() => expect(shippingApi.prepare).toHaveBeenCalledWith({
      addressId: 77,
      shippingMethod: 'EXPRESS',
    }));

    const result = await screen.findByRole('status');
    const formattedFee = new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
    }).format(40000);
    expect(within(result).getByText('Giao hàng nhanh')).toBeInTheDocument();
    expect(within(result).getByText('Khu vực lân cận')).toBeInTheDocument();
    expect(within(result).getByText((_, element) =>
      element?.tagName === 'STRONG' && element.textContent === formattedFee,
    )).toBeInTheDocument();
    expect(toast.success).toHaveBeenCalledWith('Đã tính phí giao hàng');
  });

  it.each([
    ['EXPRESS shipping is not supported for region OTHER', 400],
    ['Address not found with id: 42', 404],
  ])('shows a backend API error to the customer: %s', async (backendMessage, status) => {
    vi.mocked(shippingApi.prepare).mockRejectedValue({
      isAxiosError: true,
      response: {
        status,
        data: { message: backendMessage },
      },
    });
    renderPage();
    await addressChoices();

    fireEvent.click(methodChoices().express);
    fireEvent.click(screen.getByRole('button', { name: 'Tính phí giao hàng' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(backendMessage);
    expect(toast.error).toHaveBeenCalledWith(backendMessage);
  });

  it('creates from the prepared selection, clears the cart, and reloads ATS-34', async () => {
    vi.mocked(shippingApi.prepare).mockResolvedValue({
      data: { data: preparation },
    } as any);
    vi.mocked(orderApi.create).mockResolvedValue({
      data: { data: createdOrder },
    } as any);
    vi.mocked(orderApi.list).mockResolvedValue({
      data: {
        data: [{
          orderNumber: createdOrder.orderNumber,
          status: createdOrder.status,
          createdAt: createdOrder.createdAt,
          updatedAt: createdOrder.createdAt,
        }],
      },
    } as any);
    renderPage();
    const { otherAddress } = await addressChoices();

    fireEvent.click(otherAddress);
    fireEvent.click(methodChoices().express);
    fireEvent.click(screen.getByRole('button', { name: 'Tính phí giao hàng' }));
    const placeOrder = await screen.findByRole('button', { name: 'Đặt hàng' });
    await waitFor(() => expect(placeOrder).toBeEnabled());
    fireEvent.click(placeOrder);

    await waitFor(() => expect(orderApi.create).toHaveBeenCalledWith({
      addressId: 77,
      shippingMethod: 'EXPRESS',
    }));
    expect(clearLocalCart).toHaveBeenCalledTimes(1);
    expect(toast.success).toHaveBeenCalledWith(
      `Đặt hàng thành công: ${createdOrder.orderNumber}`,
    );
    expect(await screen.findByText(createdOrder.orderNumber)).toBeInTheDocument();
    expect(orderApi.list).toHaveBeenCalledTimes(1);
  });

  it('prevents a double submit while order creation is pending', async () => {
    vi.mocked(shippingApi.prepare).mockResolvedValue({
      data: { data: preparation },
    } as any);
    let resolveCreate!: (value: unknown) => void;
    vi.mocked(orderApi.create).mockImplementation(() => new Promise((resolve) => {
      resolveCreate = resolve;
    }) as any);
    renderPage();
    const { otherAddress } = await addressChoices();

    fireEvent.click(otherAddress);
    fireEvent.click(methodChoices().express);
    fireEvent.click(screen.getByRole('button', { name: 'Tính phí giao hàng' }));
    const placeOrder = screen.getByRole('button', { name: 'Đặt hàng' });
    await waitFor(() => expect(placeOrder).toBeEnabled());

    fireEvent.click(placeOrder);
    fireEvent.click(placeOrder);

    expect(orderApi.create).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole('button', { name: 'Đang đặt hàng...' })).toBeDisabled();

    resolveCreate({ data: { data: createdOrder } });
    await waitFor(() => expect(clearLocalCart).toHaveBeenCalledTimes(1));
  });

  it('shows a backend checkout error and keeps the prepared order retryable', async () => {
    vi.mocked(shippingApi.prepare).mockResolvedValue({
      data: { data: preparation },
    } as any);
    vi.mocked(orderApi.create).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 400,
        data: { message: 'Cart must contain at least one item' },
      },
    });
    renderPage();
    const { otherAddress } = await addressChoices();

    fireEvent.click(otherAddress);
    fireEvent.click(methodChoices().express);
    fireEvent.click(screen.getByRole('button', { name: 'Tính phí giao hàng' }));
    const placeOrder = screen.getByRole('button', { name: 'Đặt hàng' });
    await waitFor(() => expect(placeOrder).toBeEnabled());
    fireEvent.click(placeOrder);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Cart must contain at least one item',
    );
    expect(toast.error).toHaveBeenCalledWith('Cart must contain at least one item');
    expect(placeOrder).toBeEnabled();
    expect(clearLocalCart).not.toHaveBeenCalled();
  });
});
