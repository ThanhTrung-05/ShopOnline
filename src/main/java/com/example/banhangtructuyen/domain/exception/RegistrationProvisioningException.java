package com.example.banhangtructuyen.domain.exception;

/**
 * Raised when registration fails outside a known duplicate-data or Keycloak failure path.
 */
public class RegistrationProvisioningException extends RuntimeException {

    public RegistrationProvisioningException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
