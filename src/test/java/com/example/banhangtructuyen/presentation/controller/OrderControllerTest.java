package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.order.OrderStatusResponse;
import com.example.banhangtructuyen.application.service.AuthenticatedCustomerResolver;
import com.example.banhangtructuyen.application.service.OrderStatusService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("GET /api/v1/customers/me/orders/{orderNumber}/status")
class OrderControllerTest {

    private static final String ORDER_NUMBER = "ORD-001";
    private static final String CUSTOMER_SUBJECT = "customer-subject";
    private static final Long CUSTOMER_ID = 10L;
    private static final String PATH = "/api/v1/customers/me/orders/" + ORDER_NUMBER + "/status";
    private static final Instant CREATED_AT = Instant.parse("2026-08-27T03:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-27T05:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticatedCustomerResolver authenticatedCustomerResolver;

    @MockBean
    private OrderStatusService orderStatusService;

    @BeforeEach
    void setUpCustomer() {
        when(authenticatedCustomerResolver.resolveActiveCustomer(CUSTOMER_SUBJECT))
                .thenReturn(Customer.builder().customerId(CUSTOMER_ID).build());
    }

    @Test
    @DisplayName("CUSTOMER owning the order receives 200 and the status response")
    void ownerCustomer_shouldReceiveStatus() throws Exception {
        when(orderStatusService.getOrderStatus(ORDER_NUMBER, CUSTOMER_ID))
                .thenReturn(new OrderStatusResponse(
                        ORDER_NUMBER, OrderStatus.SHIPPING, CREATED_AT, UPDATED_AT));

        mockMvc.perform(get(PATH).with(customerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$.data.status").value("SHIPPING"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-08-27T03:00:00Z"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-08-27T05:00:00Z"))
                .andExpect(jsonPath("$.data.customerId").doesNotExist())
                .andExpect(jsonPath("$.data.orderId").doesNotExist())
                .andExpect(jsonPath("$.data.totalAmount").doesNotExist())
                .andExpect(jsonPath("$.data.shippingAddress").doesNotExist());

        verify(authenticatedCustomerResolver).resolveActiveCustomer(CUSTOMER_SUBJECT);
        verify(orderStatusService).getOrderStatus(ORDER_NUMBER, CUSTOMER_ID);
    }

    @Test
    @DisplayName("CUSTOMER requesting a nonexistent order receives 404")
    void customer_shouldReceive404_whenOrderDoesNotExist() throws Exception {
        stubOrderNotFound();

        assertNotFoundResponse();
    }

    @Test
    @DisplayName("CUSTOMER requesting another customer's order receives the same 404")
    void customer_shouldReceive404_whenOrderBelongsToAnotherCustomer() throws Exception {
        stubOrderNotFound();

        assertNotFoundResponse();
    }

    @Test
    @DisplayName("anonymous request receives 401")
    void anonymous_shouldReceive401() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderStatusService);
    }

    @Test
    @DisplayName("ADMIN without CUSTOMER role receives 403")
    void adminOnly_shouldReceive403() throws Exception {
        mockMvc.perform(get(PATH).with(adminJwt()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderStatusService);
    }

    private void stubOrderNotFound() {
        when(orderStatusService.getOrderStatus(ORDER_NUMBER, CUSTOMER_ID))
                .thenThrow(new ResourceNotFoundException("Order", ORDER_NUMBER));
    }

    private void assertNotFoundResponse() throws Exception {
        mockMvc.perform(get(PATH).with(customerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value(
                        "Order not found with id: " + ORDER_NUMBER));

        verify(authenticatedCustomerResolver).resolveActiveCustomer(CUSTOMER_SUBJECT);
        verify(orderStatusService).getOrderStatus(ORDER_NUMBER, CUSTOMER_ID);
    }

    private static RequestPostProcessor customerJwt() {
        return jwt()
                .jwt(builder -> builder.subject(CUSTOMER_SUBJECT))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(builder -> builder.subject("admin-subject"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
