import Keycloak from 'keycloak-js';

const url = import.meta.env.VITE_KEYCLOAK_URL;
const realm = import.meta.env.VITE_KEYCLOAK_REALM;
const clientId = import.meta.env.VITE_KEYCLOAK_CLIENT_ID;

if (!url || !realm || !clientId) {
  throw new Error(
    'Missing required Keycloak env vars: VITE_KEYCLOAK_URL, VITE_KEYCLOAK_REALM, VITE_KEYCLOAK_CLIENT_ID must all be set.',
  );
}

const keycloak = new Keycloak({ url, realm, clientId });

export default keycloak;
