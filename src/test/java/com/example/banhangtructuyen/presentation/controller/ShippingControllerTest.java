package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.shipping.ShippingCheckoutInfo;
import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;
import com.example.banhangtructuyen.application.service.ShippingPreparationService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.ShippingMethod;
import com.example.banhangtructuyen.domain.model.ShippingRegion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ShippingController MockMvc Tests")
class ShippingControllerTest {

    private static final String SUBJECT = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String URL = "/api/v1/customers/me/shipping/prepare";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShippingPreparationService shippingPreparationService;

    @Test
    @DisplayName("200 - CUSTOMER prepares shipping for an owned address")
    void prepareShipping_shouldReturnOk_whenCustomerOwnsAddress() throws Exception {
        final ShippingSelectionRequest request =
                new ShippingSelectionRequest(42L, ShippingMethod.STANDARD);
        when(shippingPreparationService.prepareShipping(SUBJECT, request))
                .thenReturn(sampleCheckoutInfo());

        mockMvc.perform(post(URL)
                        .with(customerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customerId").value(7))
                .andExpect(jsonPath("$.data.addressId").value(42))
                .andExpect(jsonPath("$.data.recipientName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data.shippingMethod").value("STANDARD"))
                .andExpect(jsonPath("$.data.region").value("LOCAL"))
                .andExpect(jsonPath("$.data.shippingFee").value(10000.00));

        verify(shippingPreparationService).prepareShipping(SUBJECT, request);
    }

    @Test
    @DisplayName("404 - foreign address is indistinguishable from a missing address")
    void prepareShipping_shouldReturnNotFound_whenAddressBelongsToAnotherCustomer() throws Exception {
        final ShippingSelectionRequest request =
                new ShippingSelectionRequest(99L, ShippingMethod.STANDARD);
        when(shippingPreparationService.prepareShipping(SUBJECT, request))
                .thenThrow(new ResourceNotFoundException("Address", 99L));

        mockMvc.perform(post(URL)
                        .with(customerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Address not found with id: 99"));
    }

    @Test
    @DisplayName("404 - missing address uses the existing not-found response")
    void prepareShipping_shouldReturnNotFound_whenAddressDoesNotExist() throws Exception {
        final ShippingSelectionRequest request =
                new ShippingSelectionRequest(100L, ShippingMethod.STANDARD);
        when(shippingPreparationService.prepareShipping(SUBJECT, request))
                .thenThrow(new ResourceNotFoundException("Address", 100L));

        mockMvc.perform(post(URL)
                        .with(customerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Address not found with id: 100"));
    }

    @Test
    @DisplayName("400 - unsupported shipping method and region combination")
    void prepareShipping_shouldReturnBadRequest_whenMethodIsUnsupportedForRegion() throws Exception {
        final ShippingSelectionRequest request =
                new ShippingSelectionRequest(42L, ShippingMethod.EXPRESS);
        when(shippingPreparationService.prepareShipping(SUBJECT, request))
                .thenThrow(new IllegalArgumentException(
                        "EXPRESS shipping is not supported for region OTHER"));

        mockMvc.perform(post(URL)
                        .with(customerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("EXPRESS shipping is not supported for region OTHER"));
    }

    @Test
    @DisplayName("400 - address id is required")
    void prepareShipping_shouldReturnBadRequest_whenAddressIdIsMissing() throws Exception {
        final ShippingSelectionRequest request =
                new ShippingSelectionRequest(null, ShippingMethod.STANDARD);

        mockMvc.perform(post(URL)
                        .with(customerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Address id is required"));

        verifyNoInteractions(shippingPreparationService);
    }

    @Test
    @DisplayName("400 - shipping method is required")
    void prepareShipping_shouldReturnBadRequest_whenShippingMethodIsMissing() throws Exception {
        final ShippingSelectionRequest request = new ShippingSelectionRequest(42L, null);

        mockMvc.perform(post(URL)
                        .with(customerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Shipping method is required"));

        verifyNoInteractions(shippingPreparationService);
    }

    @Test
    @DisplayName("401 - anonymous request is rejected")
    void prepareShipping_shouldReturnUnauthorized_whenAnonymous() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ShippingSelectionRequest(
                                42L, ShippingMethod.STANDARD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(shippingPreparationService);
    }

    @Test
    @DisplayName("403 - ADMIN without CUSTOMER role is rejected")
    void prepareShipping_shouldReturnForbidden_whenAdminOnly() throws Exception {
        mockMvc.perform(post(URL)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ShippingSelectionRequest(
                                42L, ShippingMethod.STANDARD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(shippingPreparationService);
    }

    private static ShippingCheckoutInfo sampleCheckoutInfo() {
        return new ShippingCheckoutInfo(
                7L,
                42L,
                "Nguyen Van A",
                "0987654321",
                "123 Le Loi",
                "Ben Nghe",
                "District 1",
                "Ha Noi",
                ShippingMethod.STANDARD,
                ShippingRegion.LOCAL,
                new BigDecimal("10000.00"));
    }

    private String toJson(final Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static RequestPostProcessor customerJwt() {
        return jwt()
                .jwt(builder -> builder.subject(SUBJECT))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(builder -> builder.subject(SUBJECT))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
