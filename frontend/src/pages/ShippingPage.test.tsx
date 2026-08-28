import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
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

import { addressApi, type Address } from '../api/addressApi';
import { shippingApi, type ShippingPreparation } from '../api/shippingApi';
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

function renderPage() {
  return render(
    <MemoryRouter>
      <ShippingPage />
    </MemoryRouter>,
  );
}

async function addressChoices() {
  const group = await screen.findByRole('radiogroup', { name: 'Chọn địa chỉ giao hàng' });
  return {
    defaultAddress: within(group).getByRole('radio', { name: /Nguyễn Văn A/ }),
    otherAddress: within(group).getByRole('radio', { name: /Trần Thị B/ }),
  };
}

function methodChoices() {
  const group = screen.getByRole('radiogroup', { name: 'Chọn phương thức giao hàng' });
  return {
    standard: within(group).getByRole('radio', { name: /STANDARD/ }),
    express: within(group).getByRole('radio', { name: /EXPRESS/ }),
  };
}

describe('ShippingPage', () => {
  beforeEach(() => {
    vi.mocked(addressApi.list).mockReset();
    vi.mocked(shippingApi.prepare).mockReset();
    vi.mocked(toast.success).mockReset();
    vi.mocked(toast.error).mockReset();
    vi.mocked(addressApi.list).mockResolvedValue({
      data: { data: addresses },
    } as any);
  });

  it('renders addresses and initially selects the default address', async () => {
    renderPage();

    const { defaultAddress, otherAddress } = await addressChoices();

    expect(screen.getByText(/Nguyễn Văn A/)).toBeInTheDocument();
    expect(screen.getByText(/Trần Thị B/)).toBeInTheDocument();
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
    expect(within(result).getByText('EXPRESS')).toBeInTheDocument();
    expect(within(result).getByText('NEARBY')).toBeInTheDocument();
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
});
