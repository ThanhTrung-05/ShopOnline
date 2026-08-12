package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.customer.AddressRequest;
import com.example.banhangtructuyen.application.dto.customer.AddressResponse;
import com.example.banhangtructuyen.application.service.AddressService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link AddressController}. {@link AddressService} is mocked via
 * {@code @MockBean}. Authentication is injected with the {@code jwt()} post-processor,
 * consistent with {@code CustomerControllerTest} — these tests target controller/service
 * wiring and response shape, not the {@code SecurityFilterChain} role-mapping path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AddressController MockMvc Tests")
class AddressControllerTest {

    private static final String SUBJECT = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String BASE_URL = "/api/v1/customers/me/addresses";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AddressService addressService;

    private static AddressResponse sampleResponse(final Long id, final boolean isDefault) {
        return new AddressResponse(id, "Nguyễn Văn A", "0987654321", "123 Đường Lê Lợi",
                "Phường Bến Nghé", "Quận 1", "TP. Hồ Chí Minh", isDefault);
    }

    private static AddressRequest sampleRequest() {
        return new AddressRequest("Nguyễn Văn A", "0987654321", "123 Đường Lê Lợi",
                "Phường Bến Nghé", "Quận 1", "TP. Hồ Chí Minh");
    }

    private String toJson(final Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @Nested
    @DisplayName("GET /api/v1/customers/me/addresses")
    class ListAddresses {

        @Test
        @DisplayName("200 — returns addresses for the authenticated customer")
        void listAddresses_shouldReturn200() throws Exception {
            when(addressService.listAddresses(SUBJECT))
                    .thenReturn(List.of(sampleResponse(1L, true), sampleResponse(2L, false)));

            mockMvc.perform(get(BASE_URL).with(jwt().jwt(builder -> builder.subject(SUBJECT))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].isDefault").value(true));
        }

        @Test
        @DisplayName("401 — no bearer token")
        void listAddresses_shouldReturn401_whenNoToken() throws Exception {
            mockMvc.perform(get(BASE_URL)).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/customers/me/addresses/{addressId}")
    class GetAddress {

        @Test
        @DisplayName("200 — returns own address")
        void getAddress_shouldReturn200() throws Exception {
            when(addressService.getAddress(SUBJECT, 1L)).thenReturn(sampleResponse(1L, true));

            mockMvc.perform(get(BASE_URL + "/1").with(jwt().jwt(builder -> builder.subject(SUBJECT))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.addressId").value(1))
                    .andExpect(jsonPath("$.data.customerId").doesNotExist());
        }

        @Test
        @DisplayName("404 — address belongs to another customer")
        void getAddress_shouldReturn404_whenNotOwned() throws Exception {
            when(addressService.getAddress(SUBJECT, 99L))
                    .thenThrow(new ResourceNotFoundException("Address", 99L));

            mockMvc.perform(get(BASE_URL + "/99").with(jwt().jwt(builder -> builder.subject(SUBJECT))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/customers/me/addresses")
    class CreateAddress {

        @Test
        @DisplayName("201 — creates first address as default")
        void createAddress_shouldReturn201() throws Exception {
            when(addressService.createAddress(eq(SUBJECT), any(AddressRequest.class)))
                    .thenReturn(sampleResponse(1L, true));

            mockMvc.perform(post(BASE_URL)
                            .with(jwt().jwt(builder -> builder.subject(SUBJECT)))
                            .contentType("application/json")
                            .content(toJson(sampleRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.isDefault").value(true));
        }

        @Test
        @DisplayName("400 — missing required field")
        void createAddress_shouldReturn400_whenRecipientNameBlank() throws Exception {
            final AddressRequest invalid = new AddressRequest("", "0987654321", "123 Đường Lê Lợi",
                    null, null, "TP. Hồ Chí Minh");

            mockMvc.perform(post(BASE_URL)
                            .with(jwt().jwt(builder -> builder.subject(SUBJECT)))
                            .contentType("application/json")
                            .content(toJson(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("400 — invalid phone format")
        void createAddress_shouldReturn400_whenPhoneInvalid() throws Exception {
            final AddressRequest invalid = new AddressRequest("Nguyễn Văn A", "123", "123 Đường Lê Lợi",
                    null, null, "TP. Hồ Chí Minh");

            mockMvc.perform(post(BASE_URL)
                            .with(jwt().jwt(builder -> builder.subject(SUBJECT)))
                            .contentType("application/json")
                            .content(toJson(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 — address line exceeds 255 characters")
        void createAddress_shouldReturn400_whenLine1TooLong() throws Exception {
            final AddressRequest invalid = new AddressRequest("Nguyễn Văn A", "0987654321", "A".repeat(256),
                    null, null, "TP. Hồ Chí Minh");

            mockMvc.perform(post(BASE_URL)
                            .with(jwt().jwt(builder -> builder.subject(SUBJECT)))
                            .contentType("application/json")
                            .content(toJson(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("401 — no bearer token")
        void createAddress_shouldReturn401_whenNoToken() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType("application/json")
                            .content(toJson(sampleRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/customers/me/addresses/{addressId}")
    class UpdateAddress {

        @Test
        @DisplayName("200 — updates own address")
        void updateAddress_shouldReturn200() throws Exception {
            when(addressService.updateAddress(eq(SUBJECT), eq(1L), any(AddressRequest.class)))
                    .thenReturn(sampleResponse(1L, true));

            mockMvc.perform(put(BASE_URL + "/1")
                            .with(jwt().jwt(builder -> builder.subject(SUBJECT)))
                            .contentType("application/json")
                            .content(toJson(sampleRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.addressId").value(1));
        }

        @Test
        @DisplayName("404 — address belongs to another customer")
        void updateAddress_shouldReturn404_whenNotOwned() throws Exception {
            when(addressService.updateAddress(eq(SUBJECT), eq(99L), any(AddressRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Address", 99L));

            mockMvc.perform(put(BASE_URL + "/99")
                            .with(jwt().jwt(builder -> builder.subject(SUBJECT)))
                            .contentType("application/json")
                            .content(toJson(sampleRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("400 — invalid request body")
        void updateAddress_shouldReturn400_whenInvalid() throws Exception {
            final AddressRequest invalid = new AddressRequest("Nguyễn Văn A", "0987654321", "",
                    null, null, "TP. Hồ Chí Minh");

            mockMvc.perform(put(BASE_URL + "/1")
                            .with(jwt().jwt(builder -> builder.subject(SUBJECT)))
                            .contentType("application/json")
                            .content(toJson(invalid)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/customers/me/addresses/{addressId}")
    class DeleteAddress {

        @Test
        @DisplayName("204 — deletes own address")
        void deleteAddress_shouldReturn204() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/1").with(jwt().jwt(builder -> builder.subject(SUBJECT))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("404 — address belongs to another customer")
        void deleteAddress_shouldReturn404_whenNotOwned() throws Exception {
            org.mockito.Mockito.doThrow(new ResourceNotFoundException("Address", 99L))
                    .when(addressService).deleteAddress(SUBJECT, 99L);

            mockMvc.perform(delete(BASE_URL + "/99").with(jwt().jwt(builder -> builder.subject(SUBJECT))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("401 — no bearer token")
        void deleteAddress_shouldReturn401_whenNoToken() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/1")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/customers/me/addresses/{addressId}/default")
    class SetDefaultAddress {

        @Test
        @DisplayName("200 — sets address as default")
        void setDefaultAddress_shouldReturn200() throws Exception {
            when(addressService.setDefaultAddress(SUBJECT, 2L)).thenReturn(sampleResponse(2L, true));

            mockMvc.perform(patch(BASE_URL + "/2/default").with(jwt().jwt(builder -> builder.subject(SUBJECT))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isDefault").value(true));
        }

        @Test
        @DisplayName("404 — address belongs to another customer")
        void setDefaultAddress_shouldReturn404_whenNotOwned() throws Exception {
            when(addressService.setDefaultAddress(SUBJECT, 99L))
                    .thenThrow(new ResourceNotFoundException("Address", 99L));

            mockMvc.perform(patch(BASE_URL + "/99/default").with(jwt().jwt(builder -> builder.subject(SUBJECT))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("401 — no bearer token")
        void setDefaultAddress_shouldReturn401_whenNoToken() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/1/default")).andExpect(status().isUnauthorized());
        }
    }
}
