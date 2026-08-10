package com.example.banhangtructuyen.application.service;

/**
 * Wraps Keycloak Admin REST API calls needed to provision a customer identity on
 * registration, using the {@code shoponline-user-admin} service account.
 */
public interface KeycloakAdminService {

    /**
     * Creates a Keycloak user (username = email).
     *
     * @return the Keycloak user id
     * @throws com.example.banhangtructuyen.domain.exception.EmailAlreadyExistsException
     *         if Keycloak reports a conflict (user/email already exists)
     * @throws com.example.banhangtructuyen.domain.exception.KeycloakProvisioningException
     *         on any other failure
     */
    String createUser(String email, String fullName);

    /**
     * Sets a permanent (non-temporary) password for the given Keycloak user.
     */
    void setPassword(String keycloakUserId, String rawPassword);

    /**
     * Assigns the {@code CUSTOMER} client role (on {@code shoponline-backend}) to the given user.
     * Resolves the client and role ids by name via the Admin API — never hardcoded.
     */
    void assignCustomerRole(String keycloakUserId);

    /**
     * Best-effort deletion used to compensate a partially-provisioned Keycloak user when a
     * later step in registration fails. Never throws — failures are logged only.
     */
    void deleteUser(String keycloakUserId);
}
