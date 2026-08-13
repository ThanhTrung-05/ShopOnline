package com.example.banhangtructuyen.presentation.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link SessionController} and the resource-server 401 behavior.
 *
 * <p>{@link JwtDecoder} is mocked so no Keycloak/JWKS network call happens. The {@code jwt()}
 * post-processor injects an authenticated principal directly into the security context,
 * bypassing the decoder for the success case.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SessionController + Resource Server 401 Tests")
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Nested
    @DisplayName("GET /api/v1/auth/session — authenticated")
    class Authenticated {

        @Test
        @DisplayName("200 — returns authenticated, subject and preferred_username from JWT")
        void session_shouldReturn200_withClaims() throws Exception {
            mockMvc.perform(get("/api/v1/auth/session")
                            .with(jwt().jwt(builder -> builder
                                    .subject("f47ac10b-58cc-4372-a567-0e02b2c3d479")
                                    .claim("preferred_username", "test-customer"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.authenticated").value(true))
                    .andExpect(jsonPath("$.data.subject").value("f47ac10b-58cc-4372-a567-0e02b2c3d479"))
                    .andExpect(jsonPath("$.data.username").value("test-customer"));
        }

        @Test
        @DisplayName("200 — username is null when preferred_username claim is absent")
        void session_shouldReturn200_whenUsernameAbsent() throws Exception {
            mockMvc.perform(get("/api/v1/auth/session")
                            .with(jwt().jwt(builder -> builder.subject("sub-only"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.subject").value("sub-only"))
                    .andExpect(jsonPath("$.data.username").doesNotExist());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/session — 401 unauthorized")
    class Unauthorized {

        @Test
        @DisplayName("401 — no bearer token, with WWW-Authenticate: Bearer header")
        void session_shouldReturn401_whenNoToken() throws Exception {
            mockMvc.perform(get("/api/v1/auth/session"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("401 — invalid/malformed token")
        void session_shouldReturn401_whenTokenInvalid() throws Exception {
            when(jwtDecoder.decode(anyString()))
                    .thenThrow(new BadJwtException("Malformed token"));

            mockMvc.perform(get("/api/v1/auth/session")
                            .header("Authorization", "Bearer malformed.token.value"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("401 — expired token")
        void session_shouldReturn401_whenTokenExpired() throws Exception {
            when(jwtDecoder.decode(anyString()))
                    .thenThrow(new JwtValidationException(
                            "Token expired",
                            List.of(new OAuth2Error("invalid_token", "Jwt expired", null))));

            mockMvc.perform(get("/api/v1/auth/session")
                            .header("Authorization", "Bearer expired.token.value"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}
