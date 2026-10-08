package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.order.OperationsOrderResponse;
import com.example.banhangtructuyen.application.service.OrderOperationsService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.OrderStatus;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ATS-35 order operations endpoints")
class OrderOperationsControllerTest {

    private static final String ORDER_NUMBER = "ORD-20260907-OPERATIONS";
    private static final String LIST_PATH = "/api/v1/operations/orders";
    private static final String UPDATE_PATH = LIST_PATH + "/" + ORDER_NUMBER + "/status";
    private static final Instant UPDATED_AT = Instant.parse("2026-09-07T03:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderOperationsService orderOperationsService;

    @Test
    @DisplayName("ADMIN can list operational orders")
    void admin_shouldListOrders() throws Exception {
        stubOrderList();

        mockMvc.perform(get(LIST_PATH).with(roleJwt("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].updatedAt").value("2026-09-07T03:00:00Z"))
                .andExpect(jsonPath("$.data[0].customerId").doesNotExist());

        verify(orderOperationsService).getOrders();
    }

    @Test
    @DisplayName("WAREHOUSE_STAFF can list operational orders")
    void warehouse_shouldListOrders() throws Exception {
        stubOrderList();

        mockMvc.perform(get(LIST_PATH).with(roleJwt("WAREHOUSE_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderNumber").value(ORDER_NUMBER));

        verify(orderOperationsService).getOrders();
    }

    @Test
    @DisplayName("ADMIN can update an order status")
    void admin_shouldUpdateStatus() throws Exception {
        stubSuccessfulUpdate(OrderStatus.CONFIRMED);

        assertSuccessfulUpdate(roleJwt("ADMIN"), OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("WAREHOUSE_STAFF can update an order status")
    void warehouse_shouldUpdateStatus() throws Exception {
        stubSuccessfulUpdate(OrderStatus.SHIPPING);

        assertSuccessfulUpdate(roleJwt("WAREHOUSE_STAFF"), OrderStatus.SHIPPING);
    }

    @Test
    @DisplayName("CUSTOMER cannot update order status")
    void customer_shouldReceive403() throws Exception {
        mockMvc.perform(patch(UPDATE_PATH)
                        .with(roleJwt("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderOperationsService);
    }

    @Test
    @DisplayName("CUSTOMER cannot read the operations order list")
    void customer_shouldReceive403_forOperationsList() throws Exception {
        mockMvc.perform(get(LIST_PATH).with(roleJwt("CUSTOMER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderOperationsService);
    }

    @Test
    @DisplayName("anonymous user cannot update order status")
    void anonymous_shouldReceive401() throws Exception {
        mockMvc.perform(patch(UPDATE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderOperationsService);
    }

    @Test
    @DisplayName("anonymous user cannot read the operations order list")
    void anonymous_shouldReceive401_forOperationsList() throws Exception {
        mockMvc.perform(get(LIST_PATH))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderOperationsService);
    }

    @Test
    @DisplayName("missing order returns 404")
    void updateStatus_shouldReturn404_whenOrderDoesNotExist() throws Exception {
        when(orderOperationsService.updateStatus(ORDER_NUMBER, OrderStatus.CONFIRMED))
                .thenThrow(new ResourceNotFoundException("Order", ORDER_NUMBER));

        mockMvc.perform(patch(UPDATE_PATH)
                        .with(roleJwt("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Order not found with id: " + ORDER_NUMBER));
    }

    @Test
    @DisplayName("invalid transition returns 400")
    void updateStatus_shouldReturn400_whenTransitionIsInvalid() throws Exception {
        when(orderOperationsService.updateStatus(ORDER_NUMBER, OrderStatus.DELIVERED))
                .thenThrow(new IllegalArgumentException(
                        "Order status transition from PENDING to DELIVERED is not allowed"));

        mockMvc.perform(patch(UPDATE_PATH)
                        .with(roleJwt("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Order status transition from PENDING to DELIVERED is not allowed"));
    }

    @Test
    @DisplayName("missing status is rejected before the service")
    void updateStatus_shouldReturn400_whenStatusIsMissing() throws Exception {
        mockMvc.perform(patch(UPDATE_PATH)
                        .with(roleJwt("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Order status is required"));

        verifyNoInteractions(orderOperationsService);
    }

    private void stubOrderList() {
        when(orderOperationsService.getOrders()).thenReturn(List.of(
                new OperationsOrderResponse(ORDER_NUMBER, OrderStatus.PENDING, UPDATED_AT)));
    }

    private void stubSuccessfulUpdate(final OrderStatus status) {
        when(orderOperationsService.updateStatus(ORDER_NUMBER, status))
                .thenReturn(new OperationsOrderResponse(ORDER_NUMBER, status, UPDATED_AT));
    }

    private void assertSuccessfulUpdate(
            final RequestPostProcessor authentication,
            final OrderStatus status) throws Exception {
        mockMvc.perform(patch(UPDATE_PATH)
                        .with(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$.data.status").value(status.name()))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-09-07T03:00:00Z"))
                .andExpect(jsonPath("$.data.customerId").doesNotExist());

        verify(orderOperationsService).updateStatus(ORDER_NUMBER, status);
    }

    private static RequestPostProcessor roleJwt(final String role) {
        return jwt()
                .jwt(builder -> builder.subject(role.toLowerCase() + "-subject"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
