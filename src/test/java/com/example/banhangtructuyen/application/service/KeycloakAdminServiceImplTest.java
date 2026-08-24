package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.service.impl.KeycloakAdminServiceImpl;
import com.example.banhangtructuyen.config.KeycloakAdminProperties;
import com.example.banhangtructuyen.domain.exception.EmailAlreadyExistsException;
import com.example.banhangtructuyen.domain.exception.KeycloakProvisioningException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class KeycloakAdminServiceImplTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;
    private AtomicReference<String> createUserPayload;
    private AtomicInteger createUserRequests;
    private volatile int createUserStatus;
    private volatile int deleteUserStatus;
    private volatile boolean includeLocation;
    private volatile long createDelayMillis;
    private KeycloakAdminServiceImpl service;

    @BeforeEach
    void setUp() throws IOException {
        createUserPayload = new AtomicReference<>();
        createUserRequests = new AtomicInteger();
        createUserStatus = 201;
        deleteUserStatus = 204;
        includeLocation = true;
        createDelayMillis = 0;

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/realms/shoponline/protocol/openid-connect/token", this::handleToken);
        server.createContext("/admin/realms/shoponline/users", this::handleUsers);
        server.start();
        service = newService(Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("multi-word fullName sends firstName as last word and lastName as preceding words")
        void createUser_shouldSplitMultiWordFullNameForKeycloakPayload() throws Exception {
            final String keycloakUserId = service.createUser(
                    "customer@example.com", "  Nguyen   Van   A  ");

            assertThat(keycloakUserId).isEqualTo("kc-user-1");
            final JsonNode payload = OBJECT_MAPPER.readTree(createUserPayload.get());
            assertThat(payload.get("firstName").asText()).isEqualTo("A");
            assertThat(payload.get("lastName").asText()).isEqualTo("Nguyen Van");
        }

        @Test
        @DisplayName("single-word fullName sends the same value as firstName and lastName")
        void createUser_shouldUseSingleWordFullNameForBothKeycloakNames() throws Exception {
            service.createUser("customer@example.com", "  Madonna  ");

            final JsonNode payload = OBJECT_MAPPER.readTree(createUserPayload.get());
            assertThat(payload.get("firstName").asText()).isEqualTo("Madonna");
            assertThat(payload.get("lastName").asText()).isEqualTo("Madonna");
        }

        @Test
        @DisplayName("409 is a duplicate email conflict")
        void createUser_shouldMap409ToDuplicateEmail() {
            createUserStatus = 409;

            assertThatThrownBy(() -> service.createUser("duplicate@example.com", "Duplicate User"))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining("duplicate@example.com");
        }

        @Test
        @DisplayName("Keycloak 5xx is a provisioning failure")
        void createUser_shouldMapServerErrorToProvisioningFailure() {
            createUserStatus = 500;

            assertThatThrownBy(() -> service.createUser("customer@example.com", "Customer User"))
                    .isInstanceOf(KeycloakProvisioningException.class)
                    .hasMessageContaining("500");
        }

        @Test
        @DisplayName("success without Location is logged as outcome-ambiguous")
        void createUser_shouldRejectMissingLocation(final CapturedOutput output) {
            includeLocation = false;

            assertThatThrownBy(() -> service.createUser("ambiguous@example.com", "Ambiguous User"))
                    .isInstanceOf(KeycloakProvisioningException.class)
                    .hasMessageContaining("Location");
            assertThat(output.getOut())
                    .contains("ambiguous@example.com", "manual reconciliation");
        }

        @Test
        @DisplayName("read timeout is wrapped and logged without retrying create")
        void createUser_shouldWrapReadTimeout(final CapturedOutput output) {
            createDelayMillis = 750;
            service = newService(Duration.ofSeconds(1), Duration.ofMillis(200));

            assertThatThrownBy(() -> service.createUser("timeout@example.com", "Timeout User"))
                    .isInstanceOf(KeycloakProvisioningException.class)
                    .hasCauseInstanceOf(RestClientException.class);
            assertThat(output.getOut())
                    .contains("timeout@example.com", "unknown remote outcome");
            assertThat(createUserRequests).hasValue(1);
        }

        @Test
        @DisplayName("connection failure while obtaining a token is a provisioning failure")
        void createUser_shouldWrapConnectionFailure() {
            final KeycloakAdminProperties properties = propertiesFor("http://127.0.0.1:1");
            properties.setConnectTimeout(Duration.ofMillis(100));
            properties.setReadTimeout(Duration.ofMillis(100));
            final KeycloakAdminServiceImpl unavailableService = new KeycloakAdminServiceImpl(properties);

            assertThatThrownBy(() -> unavailableService.createUser(
                    "customer@example.com", "Customer User"))
                    .isInstanceOf(KeycloakProvisioningException.class)
                    .hasMessageContaining("service account token")
                    .hasCauseInstanceOf(RestClientException.class);
        }
    }

    @Nested
    @DisplayName("deleteUser compensation")
    class DeleteUserCompensation {

        @Test
        @DisplayName("204 confirms compensation")
        void deleteUser_shouldComplete_whenKeycloakDeletesUser() {
            assertThatCode(() -> service.deleteUser("kc-user-1")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("404 is idempotent success because the user is already absent")
        void deleteUser_shouldTreat404AsAlreadyCompensated() {
            deleteUserStatus = 404;

            assertThatCode(() -> service.deleteUser("kc-user-1")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("5xx is reported to the registration orchestrator")
        void deleteUser_shouldExposeCleanupFailure() {
            deleteUserStatus = 500;

            assertThatThrownBy(() -> service.deleteUser("kc-user-1"))
                    .isInstanceOf(KeycloakProvisioningException.class)
                    .hasMessageContaining("kc-user-1", "500");
        }
    }

    @Test
    @DisplayName("non-positive timeout configuration is rejected")
    void constructor_shouldRejectUnboundedTimeout() {
        final KeycloakAdminProperties properties = propertiesFor("http://localhost:8081");
        properties.setConnectTimeout(Duration.ZERO);

        assertThatThrownBy(() -> new KeycloakAdminServiceImpl(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connect-timeout");
    }

    private KeycloakAdminServiceImpl newService(
            final Duration connectTimeout,
            final Duration readTimeout) {
        final KeycloakAdminProperties properties = propertiesFor(
                "http://localhost:" + server.getAddress().getPort());
        properties.setConnectTimeout(connectTimeout);
        properties.setReadTimeout(readTimeout);
        return new KeycloakAdminServiceImpl(properties);
    }

    private static KeycloakAdminProperties propertiesFor(final String baseUrl) {
        final KeycloakAdminProperties properties = new KeycloakAdminProperties();
        properties.setBaseUrl(baseUrl);
        properties.setRealm("shoponline");
        properties.setClientId("shoponline-user-admin");
        properties.setClientSecret("test-secret");
        properties.setBackendClientId("shoponline-backend");
        properties.setCustomerRole("CUSTOMER");
        return properties;
    }

    private void handleToken(final HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, "{\"access_token\":\"test-token\"}");
    }

    private void handleUsers(final HttpExchange exchange) throws IOException {
        if ("DELETE".equals(exchange.getRequestMethod())) {
            sendWithoutBody(exchange, deleteUserStatus);
            return;
        }

        createUserRequests.incrementAndGet();
        createUserPayload.set(new String(
                exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (createDelayMillis > 0) {
            try {
                Thread.sleep(createDelayMillis);
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                exchange.close();
                return;
            }
        }
        if (includeLocation && createUserStatus >= 200 && createUserStatus < 300) {
            exchange.getResponseHeaders().add("Location",
                    "http://localhost:" + server.getAddress().getPort()
                            + "/admin/realms/shoponline/users/kc-user-1");
        }
        if (createUserStatus >= 400) {
            sendJson(exchange, createUserStatus, "{\"error\":\"test failure\"}");
            return;
        }
        sendWithoutBody(exchange, createUserStatus);
    }

    private static void sendWithoutBody(final HttpExchange exchange, final int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static void sendJson(final HttpExchange exchange, final int status, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
