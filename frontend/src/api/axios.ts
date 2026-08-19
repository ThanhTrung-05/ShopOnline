import axios, { type InternalAxiosRequestConfig } from 'axios';
import keycloak from '../auth/keycloak';
import { notifyForcedLogout } from '../auth/authEvents';

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
});

apiClient.interceptors.request.use(async (config: RetryableRequestConfig) => {
  if (keycloak.authenticated) {
    try {
      await keycloak.updateToken(30);
    } catch {
      // Let the 401 response path handle failed refresh consistently.
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
