type Listener = () => void;

const listeners = new Set<Listener>();

/**
 * Bridges forced-logout notifications from non-React modules (e.g. the axios
 * response interceptor) into AuthProvider's React state. keycloak.clearToken()
 * does not invoke onAuthLogout, so callers that clear the token outside of
 * AuthProvider must notify explicitly to keep isAuthenticated in sync.
 */
export function subscribeForcedLogout(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function notifyForcedLogout(): void {
  listeners.forEach((listener) => listener());
}
