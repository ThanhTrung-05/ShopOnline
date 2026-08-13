package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.service.impl.KeycloakAdminServiceImpl;
import com.example.banhangtructuyen.config.KeycloakAdminProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakAdminServiceImplTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;
    private AtomicReference<String> createUserPayload;
    private KeycloakAdminServiceImpl service;

    @BeforeEach
    void setUp() throws IOException {
        createUserPayload = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/realms/shoponline/protocol/openid-connect/token", this::handleToken);
        server.createContext("/admin/realms/shoponline/users", this::handleCreateUser);
        server.start();

        final KeycloakAdminProperties properties = new KeycloakAdminProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setRealm("shoponline");
        properties.setClientId("shoponline-user-admin");
        properties.setClientSecret("admin123");
        properties.setBackendClientId("shoponline-backend");
        properties.setCustomerRole("CUSTOMER");
        service = new KeycloakAdminServiceImpl(properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Nested
    @DisplayName("createUser name mapping")
    class CreateUserNameMapping {

        @Test
        @DisplayName("multi-word fullName sends firstName as last word and lastName as preceding words")
        void createUser_shouldSplitMultiWordFullNameForKeycloakPayload() throws Exception {
            service.createUser("customer@example.com", "  Nguyen   Van   A  ");

            final JsonNode payload = OBJECT_MAPPER.readTree(createUserPayload.get());
            assertThat(payload.get("firstName").asText()).isEqualTo("A");
            assertThat(payload.get("lastName").asText()).isEqualTo("Nguyen Van");
        }

        @Test
        @DisplayName("single-word fullName sends same value as firstName and lastName")
        void createUser_shouldUseSingleWordFullNameForBothKeycloakNames() throws Exception {
            service.createUser("customer@example.com", "  Madonna  ");

            final JsonNode payload = OBJECT_MAPPER.readTree(createUserPayload.get());
            assertThat(payload.get("firstName").asText()).isEqualTo("Madonna");
            assertThat(payload.get("lastName").asText()).isEqualTo("Madonna");
        }
    }

    private void handleToken(final HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, "{\"access_token\":\"test-token\"}");
    }

    private void handleCreateUser(final HttpExchange exchange) throws IOException {
        createUserPayload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        exchange.getResponseHeaders().add("Location",
                "http://localhost:" + server.getAddress().getPort()
                        + "/admin/realms/shoponline/users/kc-user-1");
        exchange.sendResponseHeaders(201, -1);
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
