import { useEffect, useState } from 'react';
import { Package } from '@phosphor-icons/react';

interface ProductImageProps {
  src?: string;
  alt: string;
  className?: string;
  loading?: 'eager' | 'lazy';
}

export default function ProductImage({ src, alt, className = '', loading = 'lazy' }: ProductImageProps) {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [src]);

  if (!src || failed) {
    return (
      <span className={`product-image-fallback ${className}`.trim()} aria-hidden="true">
        <Package size={42} weight="duotone" />
        <small>Chưa có ảnh sản phẩm</small>
      </span>
    );
  }

  return (
    <img
      className={className || undefined}
      src={src}
      alt={alt}
      loading={loading}
      decoding="async"
      onError={() => setFailed(true)}
    />
  );
}
