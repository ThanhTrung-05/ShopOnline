package com.example.banhangtructuyen.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Keycloak realm configuration")
class KeycloakRealmConfigurationTest {

    private static final String FRONTEND_CLIENT_ID = "shoponline-frontend";
    private static final String VITE_ORIGIN = "http://localhost:5173";

    @Test
    @DisplayName("frontend client allows the default Vite development origin")
    void frontendClient_shouldAllowDefaultViteOrigin() throws IOException {
        final JsonNode realm = new ObjectMapper().readTree(
                Path.of("keycloak", "import", "shoponline-realm.json").toFile());
        final JsonNode frontendClient = StreamSupport.stream(realm.path("clients").spliterator(), false)
                .filter(client -> FRONTEND_CLIENT_ID.equals(client.path("clientId").asText()))
                .findFirst()
                .orElseThrow();

        assertThat(textValues(frontendClient.path("redirectUris")))
                .contains(VITE_ORIGIN + "/*");
        assertThat(textValues(frontendClient.path("webOrigins")))
                .contains(VITE_ORIGIN);
    }

    private static List<String> textValues(final JsonNode arrayNode) {
        return StreamSupport.stream(arrayNode.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }
}
