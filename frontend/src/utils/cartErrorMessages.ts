export const INSUFFICIENT_STOCK_BACKEND_MESSAGE = 'Requested quantity exceeds available stock';
export const INSUFFICIENT_STOCK_WARNING = 'Số lượng yêu cầu vượt quá tồn kho hiện có.';

export function isInsufficientStockError(error: unknown) {
  return (error as { response?: { data?: { message?: string } } })?.response?.data?.message
    === INSUFFICIENT_STOCK_BACKEND_MESSAGE;
}
