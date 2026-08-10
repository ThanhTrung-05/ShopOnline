package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.service.KeycloakAdminService;
import com.example.banhangtructuyen.config.KeycloakAdminProperties;
import com.example.banhangtructuyen.domain.exception.EmailAlreadyExistsException;
import com.example.banhangtructuyen.domain.exception.KeycloakProvisioningException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Calls the Keycloak Admin REST API using the {@code shoponline-user-admin} service
 * account (client_credentials grant) to provision a customer identity on registration.
 *
 * <p>The {@code shoponline-backend} client id and {@code CUSTOMER} role id are resolved
 * by name on every call rather than hardcoded, since client/role UUIDs can change across
 * a realm re-import.
 */
@Slf4j
@Service
public class KeycloakAdminServiceImpl implements KeycloakAdminService {

    private final RestClient restClient;
    private final KeycloakAdminProperties properties;

    public KeycloakAdminServiceImpl(final KeycloakAdminProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public String createUser(final String email, final String fullName) {
        final Map<String, Object> body = Map.of(
                "username", email,
                "email", email,
                "enabled", true,
                "emailVerified", true,
                "firstName", fullName
        );

        try {
            final var response = restClient.post()
                    .uri("/admin/realms/{realm}/users", properties.getRealm())
                    .header("Authorization", "Bearer " + fetchAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            final String location = response.getHeaders().getFirst("Location");
            if (location == null) {
                throw new KeycloakProvisioningException(
                        "Keycloak createUser succeeded but returned no Location header");
            }
            return location.substring(location.lastIndexOf('/') + 1);
        } catch (final RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatusCode.valueOf(409)) {
                throw new EmailAlreadyExistsException(email);
            }
            throw new KeycloakProvisioningException(
                    "Failed to create Keycloak user for " + email + ": " + ex.getStatusCode(), ex);
        }
    }

    @Override
    public void setPassword(final String keycloakUserId, final String rawPassword) {
        final Map<String, Object> credential = Map.of(
                "type", "password",
                "value", rawPassword,
                "temporary", false
        );

        try {
            restClient.put()
                    .uri("/admin/realms/{realm}/users/{id}/reset-password", properties.getRealm(), keycloakUserId)
                    .header("Authorization", "Bearer " + fetchAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(credential)
                    .retrieve()
                    .toBodilessEntity();
        } catch (final RestClientResponseException ex) {
            throw new KeycloakProvisioningException(
                    "Failed to set password for Keycloak user " + keycloakUserId + ": " + ex.getStatusCode(), ex);
        }
    }

    @Override
    public void assignCustomerRole(final String keycloakUserId) {
        final String token = fetchAccessToken();
        final String backendClientUuid = resolveClientUuid(token, properties.getBackendClientId());
        final Map<String, Object> role = resolveClientRole(token, backendClientUuid, properties.getCustomerRole());

        try {
            restClient.post()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/clients/{clientUuid}",
                            properties.getRealm(), keycloakUserId, backendClientUuid)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(role))
                    .retrieve()
                    .toBodilessEntity();
        } catch (final RestClientResponseException ex) {
            throw new KeycloakProvisioningException(
                    "Failed to assign role " + properties.getCustomerRole()
                            + " to Keycloak user " + keycloakUserId + ": " + ex.getStatusCode(), ex);
        }
    }

    @Override
    public void deleteUser(final String keycloakUserId) {
        try {
            restClient.delete()
                    .uri("/admin/realms/{realm}/users/{id}", properties.getRealm(), keycloakUserId)
                    .header("Authorization", "Bearer " + fetchAccessToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (final Exception ex) {
            log.error("Best-effort compensation failed: could not delete orphaned Keycloak user {}. "
                    + "Manual cleanup required.", keycloakUserId, ex);
        }
    }

    private String resolveClientUuid(final String token, final String clientId) {
        try {
            final List<Map<String, Object>> clients = restClient.get()
                    .uri("/admin/realms/{realm}/clients?clientId={clientId}", properties.getRealm(), clientId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(List.class);

            if (clients == null || clients.isEmpty()) {
                throw new KeycloakProvisioningException("Keycloak client not found: " + clientId);
            }
            return (String) clients.get(0).get("id");
        } catch (final RestClientResponseException ex) {
            throw new KeycloakProvisioningException(
                    "Failed to resolve Keycloak client " + clientId + ": " + ex.getStatusCode(), ex);
        }
    }

    private Map<String, Object> resolveClientRole(final String token, final String clientUuid, final String roleName) {
        try {
            return restClient.get()
                    .uri("/admin/realms/{realm}/clients/{clientUuid}/roles/{roleName}",
                            properties.getRealm(), clientUuid, roleName)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(Map.class);
        } catch (final RestClientResponseException ex) {
            throw new KeycloakProvisioningException(
                    "Failed to resolve Keycloak client role " + roleName + ": " + ex.getStatusCode(), ex);
        }
    }

    private String fetchAccessToken() {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());

        try {
            final Map<String, Object> response = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", properties.getRealm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("access_token") == null) {
                throw new KeycloakProvisioningException("Keycloak token response missing access_token");
            }
            return (String) response.get("access_token");
        } catch (final RestClientResponseException ex) {
            throw new KeycloakProvisioningException(
                    "Failed to obtain Keycloak service account token: " + ex.getStatusCode(), ex);
        }
    }
}
