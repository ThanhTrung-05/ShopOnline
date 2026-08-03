package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.auth.RegisterRequest;
import com.example.banhangtructuyen.application.dto.auth.RegisterResponse;
import com.example.banhangtructuyen.application.service.AuthService;
import com.example.banhangtructuyen.domain.exception.EmailAlreadyExistsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc integration tests for {@link AuthController}.
 * Uses {@code @SpringBootTest + @AutoConfigureMockMvc} consistent with the existing test convention.
 * {@link AuthService} is mocked via {@code @MockBean} — no DB required.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AuthController MockMvc Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    private static RegisterResponse sampleResponse() {
        return new RegisterResponse(1L, "customer@example.com", "Nguyễn Văn A", "0987654321",
                "USER", "ACTIVE", Instant.parse("2026-08-03T00:00:00Z"));
    }

    private String toJson(final Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @Nested
    @DisplayName("POST /api/v1/auth/register — success")
    class RegisterSuccess {

        @Test
        @DisplayName("201 — valid registration returns account data")
        void register_shouldReturn201_whenValid() throws Exception {
            when(authService.register(any(RegisterRequest.class))).thenReturn(sampleResponse());

            final RegisterRequest request = new RegisterRequest(
                    "customer@example.com", "SecurePass123", "Nguyễn Văn A", "0987654321");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType("application/json")
                            .content(toJson(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.customerId").value(1))
                    .andExpect(jsonPath("$.data.email").value("customer@example.com"))
                    .andExpect(jsonPath("$.data.role").value("USER"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.password").doesNotExist())
                    .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.traceId").exists());
        }

        @Test
        @DisplayName("201 — phone is optional and may be omitted")
        void register_shouldReturn201_whenPhoneOmitted() throws Exception {
            when(authService.register(any(RegisterRequest.class))).thenReturn(sampleResponse());

            final RegisterRequest request = new RegisterRequest(
                    "customer@example.com", "SecurePass123", "Nguyễn Văn A", null);

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType("application/json")
                            .content(toJson(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/register — validation errors")
    class RegisterValidation {

        @Test
        @DisplayName("400 — email is blank")
        void register_shouldReturn400_whenEmailBlank() throws Exception {
            final RegisterRequest request = new RegisterRequest("", "SecurePass123", "Nguyễn Văn A", null);

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType("application/json")
                            .content(toJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("400 — email format invalid")
        void register_shouldReturn400_whenEmailInvalid() throws Exception {
            final RegisterRequest request = new RegisterRequest("not-an-email", "SecurePass123", "Nguyễn Văn A", null);

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType("application/json")
                            .content(toJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("400 — password too short")
        void register_shouldReturn400_whenPasswordTooShort() throws Exception {
            final RegisterRequest request = new RegisterRequest("customer@example.com", "short1", "Nguyễn Văn A", null);

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType("application/json")
                            .content(toJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("400 — full name is blank")
        void register_shouldReturn400_whenFullNameBlank() throws Exception {
            final RegisterRequest request = new RegisterRequest("customer@example.com", "SecurePass123", "", null);

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType("application/json")
                            .content(toJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("400 — phone format invalid")
        void register_shouldReturn400_whenPhoneInvalid() throws Exception {
            final RegisterRequest request = new RegisterRequest(
                    "customer@example.com", "SecurePass123", "Nguyễn Văn A", "123");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType("application/json")
                            .content(toJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/register — duplicate email")
    class RegisterDuplicateEmail {

        @Test
        @DisplayName("409 — email already registered")
        void register_shouldReturn409_whenEmailDuplicate() throws Exception {
            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new EmailAlreadyExistsException("customer@example.com"));

            final RegisterRequest request = new RegisterRequest(
                    "customer@example.com", "SecurePass123", "Nguyễn Văn A", null);

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType("application/json")
                            .content(toJson(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}
