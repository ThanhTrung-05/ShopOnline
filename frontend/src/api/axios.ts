import axios, { type InternalAxiosRequestConfig } from 'axios';
import keycloak from '../auth/keycloak';
import { notifyForcedLogout } from '../auth/authEvents';

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
});

apiClient.interceptors.request.use(async (config: RetryableRequestConfig) => {
  if (keycloak.authenticated) {
    try {
      await keycloak.updateToken(30);
    } catch {
      // refresh failed — request proceeds without a fresh token and will
      // surface as 401, handled by the response interceptor below
    }
    if (keycloak.token) {
      config.headers.set('Authorization', `Bearer ${keycloak.token}`);
    }
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config as RetryableRequestConfig | undefined;

    if (error.response?.status === 401 && config && !config._retry) {
      config._retry = true;
      try {
        await keycloak.updateToken(-1);
        if (keycloak.token) {
          config.headers.set('Authorization', `Bearer ${keycloak.token}`);
        }
        return apiClient(config);
      } catch {
        keycloak.clearToken();
        notifyForcedLogout();
        return Promise.reject(error);
      }
    }

    return Promise.reject(error);
  },
);

export default apiClient;
