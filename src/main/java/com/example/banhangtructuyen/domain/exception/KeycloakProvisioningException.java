package com.example.banhangtructuyen.domain.exception;

/**
 * Raised when a Keycloak Admin API call fails while provisioning a customer identity
 * (create user, set password, assign role, or resolve client/role metadata).
 * Duplicate-user conflicts (HTTP 409) are mapped to {@link EmailAlreadyExistsException}
 * instead, since that is the existing duplicate-account error path.
 */
public class KeycloakProvisioningException extends RuntimeException {

    public KeycloakProvisioningException(final String message) {
        super(message);
    }

    public KeycloakProvisioningException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
