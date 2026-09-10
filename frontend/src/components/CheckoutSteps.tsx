import { Link } from 'react-router-dom';

type CheckoutStep = 'cart' | 'shipping' | 'complete';

const steps: Array<{ id: CheckoutStep; label: string; number: string }> = [
  { id: 'cart', label: 'Giỏ hàng', number: '1' },
  { id: 'shipping', label: 'Giao hàng', number: '2' },
  { id: 'complete', label: 'Hoàn tất', number: '3' },
];

export default function CheckoutSteps({ active }: { active: CheckoutStep }) {
  const activeIndex = steps.findIndex((step) => step.id === active);

  return (
    <nav className="checkout-progress" aria-label="Tiến trình đặt hàng">
      <ol>
        {steps.map((step, index) => {
          const state = index === activeIndex ? 'active' : index < activeIndex ? 'complete' : 'upcoming';
          const content = <><span aria-hidden="true">{step.number}</span><strong>{step.label}</strong></>;
          return (
            <li key={step.id} className={state} aria-current={state === 'active' ? 'step' : undefined}>
              {step.id === 'cart' && active !== 'cart' ? <Link to="/cart">{content}</Link> : <div>{content}</div>}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
