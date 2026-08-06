export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string | null;
  timestamp: string;
  traceId: string;
}

export interface PageResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
  last: boolean;
}

export interface Product {
  id: number;
  name: string;
  slug: string;
  price: number;
  imageUrl?: string;
  description?: string;
  categoryId: number;
  categoryName: string;
  inventoryCount: number;
  status: string;
}
