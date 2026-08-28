import Keycloak from 'keycloak-js';

function isPrivateNetworkHost(hostname: string): boolean {
  return (
    hostname === 'localhost' ||
    hostname === '127.0.0.1' ||
    hostname === '::1' ||
    /^10\./.test(hostname) ||
    /^192\.168\./.test(hostname) ||
    /^172\.(1[6-9]|2\d|3[0-1])\./.test(hostname)
  );
}

function resolveKeycloakUrl(): string {
  const configuredUrl = import.meta.env.VITE_KEYCLOAK_URL?.trim();
  const localUrl = `${window.location.protocol}//${window.location.hostname}:8081`;

  if (!configuredUrl) {
    return localUrl;
  }

  try {
    const parsedUrl = new URL(configuredUrl);
    if (isPrivateNetworkHost(parsedUrl.hostname) && isPrivateNetworkHost(window.location.hostname)) {
      return localUrl;
    }
    return parsedUrl.toString().replace(/\/$/, '');
  } catch {
    return localUrl;
  }
}

const url = resolveKeycloakUrl();
const realm = import.meta.env.VITE_KEYCLOAK_REALM;
const clientId = import.meta.env.VITE_KEYCLOAK_CLIENT_ID;

if (!url || !realm || !clientId) {
  throw new Error(
    'Missing required Keycloak env vars: VITE_KEYCLOAK_URL, VITE_KEYCLOAK_REALM, VITE_KEYCLOAK_CLIENT_ID must all be set.',
  );
}

const keycloak = new Keycloak({ url, realm, clientId });

export default keycloak;
