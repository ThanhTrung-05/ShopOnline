import axios from 'axios';
import type { ApiResponse } from '../types';

export function getApiErrorMessage(
  error: unknown,
  fallback = 'Có lỗi xảy ra. Vui lòng thử lại.',
): string {
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as Partial<ApiResponse<unknown>> | undefined;
    if (typeof payload?.message === 'string' && payload.message.trim()) {
      return payload.message;
    }

    if (!error.response) {
      return 'Không thể kết nối. Vui lòng kiểm tra mạng và thử lại.';
    }
    if (error.response.status === 401) {
      return 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.';
    }
    if (error.response.status === 403) {
      return 'Tài khoản hiện tại không có quyền thực hiện thao tác này.';
    }
  }

  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }
  return fallback;
}
