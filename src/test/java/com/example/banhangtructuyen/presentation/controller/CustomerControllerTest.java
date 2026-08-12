package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.customer.CustomerResponse;
import com.example.banhangtructuyen.application.dto.customer.UpdateProfileRequest;
import com.example.banhangtructuyen.application.service.CustomerProfileService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link CustomerController}. {@link CustomerProfileService} is mocked via
 * {@code @MockBean}. Authentication is injected with the {@code jwt()} post-processor,
 * consistent with {@code SessionControllerTest} — these tests target controller/service wiring
 * and response shape, not the {@code SecurityFilterChain} role-mapping path itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("CustomerController MockMvc Tests")
class CustomerControllerTest {

    private static final String SUBJECT = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CustomerProfileService customerProfileService;

    private static CustomerResponse sampleResponse() {
        return new CustomerResponse(1L, "customer@example.com", "Nguyễn Văn A", "0987654321",
                "USER", "ACTIVE",
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"));
    }

    private String toJson(final Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    private static RequestPostProcessor customerJwt() {
        return jwt()
                .jwt(builder -> builder.subject(SUBJECT))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    @Nested
    @DisplayName("GET /api/v1/customers/me")
    class GetProfile {

        @Test
        @DisplayName("200 — returns profile resolved from JWT subject, never from client input")
        void getProfile_shouldReturn200() throws Exception {
            when(customerProfileService.getProfile(SUBJECT)).thenReturn(sampleResponse());

            mockMvc.perform(get("/api/v1/customers/me").with(customerJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.customerId").value(1))
                    .andExpect(jsonPath("$.data.email").value("customer@example.com"))
                    .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn A"))
                    .andExpect(jsonPath("$.data.phone").value("0987654321"))
                    .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.data.keycloakUserId").doesNotExist());
        }

        @Test
        @DisplayName("404 — no customer record for this identity")
        void getProfile_shouldReturn404_whenCustomerNotFound() throws Exception {
            when(customerProfileService.getProfile(SUBJECT))
                    .thenThrow(new ResourceNotFoundException("Customer", SUBJECT));

            mockMvc.perform(get("/api/v1/customers/me").with(customerJwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("401 — no bearer token")
        void getProfile_shouldReturn401_whenNoToken() throws Exception {
            mockMvc.perform(get("/api/v1/customers/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/customers/me — partial update")
    class UpdateProfile {

        @Test
        @DisplayName("200 — updates fullName only")
        void updateProfile_shouldReturn200_forFullNameOnly() throws Exception {
            final CustomerResponse updated = new CustomerResponse(1L, "customer@example.com", "Nguyễn Văn B",
                    "0987654321", "USER", "ACTIVE", Instant.now(), Instant.now());
            when(customerProfileService.updateProfile(eq(SUBJECT), any(UpdateProfileRequest.class)))
                    .thenReturn(updated);

            mockMvc.perform(patch("/api/v1/customers/me")
                            .with(customerJwt())
                            .contentType("application/json")
                            .content(toJson(new UpdateProfileRequest("Nguyễn Văn B", null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn B"));
        }

        @Test
        @DisplayName("200 — updates phone only")
        void updateProfile_shouldReturn200_forPhoneOnly() throws Exception {
            final CustomerResponse updated = new CustomerResponse(1L, "customer@example.com", "Nguyễn Văn A",
                    "0912345678", "USER", "ACTIVE", Instant.now(), Instant.now());
            when(customerProfileService.updateProfile(eq(SUBJECT), any(UpdateProfileRequest.class)))
                    .thenReturn(updated);

            mockMvc.perform(patch("/api/v1/customers/me")
                            .with(customerJwt())
                            .contentType("application/json")
                            .content(toJson(new UpdateProfileRequest(null, "0912345678"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.phone").value("0912345678"));
        }

        @Test
        @DisplayName("200 — updates both fullName and phone")
        void updateProfile_shouldReturn200_forBoth() throws Exception {
            final CustomerResponse updated = new CustomerResponse(1L, "customer@example.com", "Nguyễn Văn B",
                    "0912345678", "USER", "ACTIVE", Instant.now(), Instant.now());
            when(customerProfileService.updateProfile(eq(SUBJECT), any(UpdateProfileRequest.class)))
                    .thenReturn(updated);

            mockMvc.perform(patch("/api/v1/customers/me")
                            .with(customerJwt())
                            .contentType("application/json")
                            .content(toJson(new UpdateProfileRequest("Nguyễn Văn B", "0912345678"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn B"))
                    .andExpect(jsonPath("$.data.phone").value("0912345678"));
        }

        @Test
        @DisplayName("200 — both fields null leaves profile unchanged (request body accepted)")
        void updateProfile_shouldReturn200_whenBothNull() throws Exception {
            when(customerProfileService.updateProfile(eq(SUBJECT), any(UpdateProfileRequest.class)))
                    .thenReturn(sampleResponse());

            mockMvc.perform(patch("/api/v1/customers/me")
                            .with(customerJwt())
                            .contentType("application/json")
                            .content(toJson(new UpdateProfileRequest(null, null))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("400 — fullName exceeds max length")
        void updateProfile_shouldReturn400_whenFullNameTooLong() throws Exception {
            final String tooLong = "A".repeat(201);

            mockMvc.perform(patch("/api/v1/customers/me")
                            .with(customerJwt())
                            .contentType("application/json")
                            .content(toJson(new UpdateProfileRequest(tooLong, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("400 — phone format invalid")
        void updateProfile_shouldReturn400_whenPhoneInvalid() throws Exception {
            mockMvc.perform(patch("/api/v1/customers/me")
                            .with(customerJwt())
                            .contentType("application/json")
                            .content(toJson(new UpdateProfileRequest(null, "123"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("401 — no bearer token")
        void updateProfile_shouldReturn401_whenNoToken() throws Exception {
            mockMvc.perform(patch("/api/v1/customers/me")
                            .contentType("application/json")
                            .content(toJson(new UpdateProfileRequest("Name", null))))
                    .andExpect(status().isUnauthorized());
        }
    }
}
