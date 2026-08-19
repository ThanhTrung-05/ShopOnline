package com.example.banhangtructuyen.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the Spring Security CORS configuration used by browser frontends.
 *
 * <p>{@link JwtDecoder} is mocked so no Keycloak/JWKS network call happens.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("CORS configuration")
class SecurityConfigTest {

    private static final String LOCAL_FRONTEND_ORIGIN = "http://localhost:5173";
    private static final String CONFIGURED_ORIGIN = "http://configured.example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("preflight OPTIONS from localhost 5173 is accepted")
    void preflight_fromLocalhost5173_isAccepted() throws Exception {
        mockMvc.perform(options("/api/v1/auth/session")
                        .header(HttpHeaders.ORIGIN, LOCAL_FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCAL_FRONTEND_ORIGIN));
    }

    @Test
    @DisplayName("preflight OPTIONS from configured origin is accepted")
    void preflight_fromConfiguredOrigin_isAccepted() throws Exception {
        mockMvc.perform(options("/api/v1/cart/items")
                        .header(HttpHeaders.ORIGIN, CONFIGURED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CONFIGURED_ORIGIN));
    }

    @Test
    @DisplayName("preflight allows required methods")
    void preflight_allowsRequiredMethods() throws Exception {
        for (final String method : new String[] {"GET", "POST", "PUT", "DELETE", "OPTIONS"}) {
            mockMvc.perform(options("/api/v1/cart/items")
                            .header(HttpHeaders.ORIGIN, LOCAL_FRONTEND_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                            org.hamcrest.Matchers.containsString(method)));
        }
    }

    @Test
    @DisplayName("preflight allows Authorization and Content-Type headers")
    void preflight_allowsRequiredHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/cart/items")
                        .header(HttpHeaders.ORIGIN, LOCAL_FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("Authorization"),
                                org.hamcrest.Matchers.containsString("Content-Type"))));
    }

    @Test
    @DisplayName("actual GET request from allowed origin echoes Access-Control-Allow-Origin")
    void actualRequest_fromAllowedOrigin_echoesAllowOrigin() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header(HttpHeaders.ORIGIN, LOCAL_FRONTEND_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCAL_FRONTEND_ORIGIN));
    }

    @Test
    @DisplayName("preflight from a disallowed origin is rejected by Spring CORS")
    void preflight_fromDisallowedOrigin_isRejected() throws Exception {
        mockMvc.perform(options("/api/v1/auth/session")
                        .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Access-Control-Allow-Credentials is absent for Bearer auth")
    void response_doesNotAllowCredentials() throws Exception {
        mockMvc.perform(options("/api/v1/auth/session")
                        .header(HttpHeaders.ORIGIN, LOCAL_FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }
}
