package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.service.KeycloakAdminService;
import com.example.banhangtructuyen.config.KeycloakAdminProperties;
import com.example.banhangtructuyen.domain.exception.EmailAlreadyExistsException;
import com.example.banhangtructuyen.domain.exception.KeycloakProvisioningException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
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
        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(toTimeoutMillis(
                properties.getConnectTimeout(), "app.keycloak.admin.connect-timeout"));
        requestFactory.setReadTimeout(toTimeoutMillis(
                properties.getReadTimeout(), "app.keycloak.admin.read-timeout"));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String createUser(final String email, final String fullName) {
        final KeycloakName keycloakName = toKeycloakName(fullName);
        final Map<String, Object> body = Map.of(
                "username", email,
                "email", email,
                "enabled", true,
                "emailVerified", true,
                "firstName", keycloakName.firstName(),
                "lastName", keycloakName.lastName()
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
            if (location == null || location.isBlank() || location.endsWith("/")) {
                log.error("Keycloak create returned success without a usable user id; outcome requires "
                        + "manual reconciliation. email={}", email);
                throw new KeycloakProvisioningException(
                        "Keycloak createUser succeeded but returned no usable Location header");
            }
            return location.substring(location.lastIndexOf('/') + 1);
        } catch (final RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatusCode.valueOf(409)) {
                throw new EmailAlreadyExistsException(email);
            }
            throw new KeycloakProvisioningException(
                    "Failed to create Keycloak user for " + email + ": " + ex.getStatusCode(), ex);
        } catch (final RestClientException ex) {
            log.error("Keycloak create request failed with an unknown remote outcome; manual reconciliation "
                    + "may be required. email={}", email, ex);
            throw new KeycloakProvisioningException(
                    "Keycloak create request failed for " + email + " with an unknown outcome", ex);
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
        } catch (final RestClientException ex) {
            throw new KeycloakProvisioningException(
                    "Failed to set password for Keycloak user " + keycloakUserId, ex);
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
        } catch (final RestClientException ex) {
            throw new KeycloakProvisioningException(
                    "Failed to assign role " + properties.getCustomerRole()
                            + " to Keycloak user " + keycloakUserId, ex);
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
        } catch (final RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                return;
            }
            throw new KeycloakProvisioningException(
                    "Failed to delete Keycloak user " + keycloakUserId + ": " + ex.getStatusCode(), ex);
        } catch (final RestClientException ex) {
            throw new KeycloakProvisioningException(
                    "Failed to delete Keycloak user " + keycloakUserId, ex);
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
            final Object clientUuid = clients.get(0).get("id");
            if (!(clientUuid instanceof String value) || value.isBlank()) {
                throw new KeycloakProvisioningException(
                        "Keycloak client response missing id for: " + clientId);
            }
            return value;
        } catch (final RestClientResponseException ex) {
            throw new KeycloakProvisioningException(
                    "Failed to resolve Keycloak client " + clientId + ": " + ex.getStatusCode(), ex);
        } catch (final RestClientException ex) {
            throw new KeycloakProvisioningException(
                    "Failed to resolve Keycloak client " + clientId, ex);
        }
    }

    private Map<String, Object> resolveClientRole(final String token, final String clientUuid, final String roleName) {
        try {
            final Map<String, Object> role = restClient.get()
                    .uri("/admin/realms/{realm}/clients/{clientUuid}/roles/{roleName}",
                            properties.getRealm(), clientUuid, roleName)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(Map.class);
            if (role == null || role.isEmpty()) {
                throw new KeycloakProvisioningException(
                        "Keycloak client role response was empty for: " + roleName);
            }
            return role;
        } catch (final RestClientResponseException ex) {
            throw new KeycloakProvisioningException(
                    "Failed to resolve Keycloak client role " + roleName + ": " + ex.getStatusCode(), ex);
        } catch (final RestClientException ex) {
            throw new KeycloakProvisioningException(
                    "Failed to resolve Keycloak client role " + roleName, ex);
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

            if (response == null
                    || !(response.get("access_token") instanceof String accessToken)
                    || accessToken.isBlank()) {
                throw new KeycloakProvisioningException("Keycloak token response missing access_token");
            }
            return accessToken;
        } catch (final RestClientResponseException ex) {
            throw new KeycloakProvisioningException(
                    "Failed to obtain Keycloak service account token: " + ex.getStatusCode(), ex);
        } catch (final RestClientException ex) {
            throw new KeycloakProvisioningException(
                    "Failed to obtain Keycloak service account token", ex);
        }
    }

    private static int toTimeoutMillis(final java.time.Duration timeout, final String propertyName) {
        final long millis = timeout.toMillis();
        if (millis <= 0 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(propertyName
                    + " must be between 1ms and " + Integer.MAX_VALUE + "ms");
        }
        return (int) millis;
    }

    static KeycloakName toKeycloakName(final String fullName) {
        final String normalized = fullName.trim().replaceAll("\\s+", " ");
        final int lastSpace = normalized.lastIndexOf(' ');
        if (lastSpace < 0) {
            return new KeycloakName(normalized, normalized);
        }
        return new KeycloakName(
                normalized.substring(lastSpace + 1),
                normalized.substring(0, lastSpace)
        );
    }

    record KeycloakName(String firstName, String lastName) {
    }
}
