import { MapPinLine, Package, UserCircle } from '@phosphor-icons/react';
import { NavLink } from 'react-router-dom';

const accountLinks = [
  { to: '/profile', label: 'Thông tin', icon: UserCircle },
  { to: '/addresses', label: 'Địa chỉ giao hàng', icon: MapPinLine },
  { to: '/orders/status', label: 'Đơn hàng của tôi', icon: Package },
];

export default function AccountNav() {
  return (
    <nav className="account-nav" aria-label="Điều hướng tài khoản">
      <p>Tài khoản của tôi</p>
      <div className="account-nav-links">
        {accountLinks.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) => isActive ? 'account-nav-link active' : 'account-nav-link'}
          >
            <Icon size={19} aria-hidden="true" />
            <span>{label}</span>
          </NavLink>
        ))}
      </div>
    </nav>
  );
}
