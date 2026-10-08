package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.order.CreateOrderResponse;
import com.example.banhangtructuyen.application.dto.order.OrderDetailResponse;
import com.example.banhangtructuyen.application.dto.order.OrderItemResponse;
import com.example.banhangtructuyen.application.dto.order.OrderStatusResponse;
import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;
import com.example.banhangtructuyen.application.service.AuthenticatedCustomerResolver;
import com.example.banhangtructuyen.application.service.OrderCreationService;
import com.example.banhangtructuyen.application.service.OrderStatusService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.model.ShippingMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Customer order tracking endpoints")
class OrderControllerTest {

    private static final String ORDER_NUMBER = "ORD-001";
    private static final String CUSTOMER_SUBJECT = "customer-subject";
    private static final Long CUSTOMER_ID = 10L;
    private static final String LIST_PATH = "/api/v1/customers/me/orders";
    private static final String DETAIL_PATH = "/api/v1/customers/me/orders/" + ORDER_NUMBER;
    private static final String PATH = "/api/v1/customers/me/orders/" + ORDER_NUMBER + "/status";
    private static final Instant CREATED_AT = Instant.parse("2026-08-27T03:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-27T05:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticatedCustomerResolver authenticatedCustomerResolver;

    @MockBean
    private OrderStatusService orderStatusService;

    @MockBean
    private OrderCreationService orderCreationService;

    @BeforeEach
    void setUpCustomer() {
        when(authenticatedCustomerResolver.resolveActiveCustomer(CUSTOMER_SUBJECT))
                .thenReturn(Customer.builder().customerId(CUSTOMER_ID).build());
    }

    @Test
    @DisplayName("CUSTOMER creates an order using only the authenticated subject and shipping selection")
    void customer_shouldCreateOrderFromCurrentCart() throws Exception {
        final ShippingSelectionRequest request =
                new ShippingSelectionRequest(42L, ShippingMethod.STANDARD);
        when(orderCreationService.createOrder(CUSTOMER_SUBJECT, request))
                .thenReturn(new CreateOrderResponse(
                        "ORD-20260907-ABCDEF12",
                        OrderStatus.PENDING,
                        new BigDecimal("49000.00"),
                        new BigDecimal("10000.00"),
                        CREATED_AT));

        mockMvc.perform(post(LIST_PATH)
                        .with(customerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "addressId": 42,
                                  "shippingMethod": "STANDARD",
                                  "customerId": 999,
                                  "orderNumber": "CLIENT-CONTROLLED",
                                  "status": "DELIVERED",
                                  "totalAmount": 1,
                                  "items": [{"productId": 1, "unitPrice": 1}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber")
                        .value("ORD-20260907-ABCDEF12"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalAmount").value(49000.00))
                .andExpect(jsonPath("$.data.shippingFee").value(10000.00))
                .andExpect(jsonPath("$.data.createdAt").value("2026-08-27T03:00:00Z"))
                .andExpect(jsonPath("$.data.customerId").doesNotExist())
                .andExpect(jsonPath("$.data.items").doesNotExist());

        verify(orderCreationService).createOrder(CUSTOMER_SUBJECT, request);
    }

    @Test
    @DisplayName("foreign address remains ownership-safe during order creation")
    void customer_shouldReceive404_whenCreatingWithForeignAddress() throws Exception {
        final ShippingSelectionRequest request =
                new ShippingSelectionRequest(99L, ShippingMethod.STANDARD);
        when(orderCreationService.createOrder(CUSTOMER_SUBJECT, request))
                .thenThrow(new ResourceNotFoundException("Address", 99L));

        mockMvc.perform(post(LIST_PATH)
                        .with(customerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Address not found with id: 99"));

        verify(orderCreationService).createOrder(CUSTOMER_SUBJECT, request);
    }

    @Test
    @DisplayName("unsupported shipping selection returns the existing validation response")
    void customer_shouldReceive400_whenCreatingWithUnsupportedShipping() throws Exception {
        final ShippingSelectionRequest request =
                new ShippingSelectionRequest(42L, ShippingMethod.EXPRESS);
        when(orderCreationService.createOrder(CUSTOMER_SUBJECT, request))
                .thenThrow(new IllegalArgumentException(
                        "EXPRESS shipping is not supported for region OTHER"));

        mockMvc.perform(post(LIST_PATH)
                        .with(customerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("EXPRESS shipping is not supported for region OTHER"));
    }

    @Test
    @DisplayName("missing checkout fields are rejected before order creation")
    void customer_shouldReceive400_whenCreateRequestIsIncomplete() throws Exception {
        mockMvc.perform(post(LIST_PATH)
                        .with(customerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(orderCreationService);
    }

    @Test
    @DisplayName("anonymous order creation receives 401")
    void anonymous_shouldReceive401_forOrderCreation() throws Exception {
        mockMvc.perform(post(LIST_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShippingSelectionRequest(
                                42L, ShippingMethod.STANDARD))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderCreationService);
    }

    @Test
    @DisplayName("ADMIN without CUSTOMER role receives 403 for order creation")
    void adminOnly_shouldReceive403_forOrderCreation() throws Exception {
        mockMvc.perform(post(LIST_PATH)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShippingSelectionRequest(
                                42L, ShippingMethod.STANDARD))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderCreationService);
    }

    @Test
    @DisplayName("CUSTOMER receives only the order list scoped to the resolved customer id")
    void customer_shouldReceiveOwnedOrderList() throws Exception {
        when(orderStatusService.getOrderStatuses(CUSTOMER_ID))
                .thenReturn(List.of(new OrderStatusResponse(
                        ORDER_NUMBER, OrderStatus.SHIPPING, CREATED_AT, UPDATED_AT)));

        mockMvc.perform(get(LIST_PATH).with(customerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$.data[0].status").value("SHIPPING"))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-08-27T03:00:00Z"))
                .andExpect(jsonPath("$.data[0].updatedAt").value("2026-08-27T05:00:00Z"))
                .andExpect(jsonPath("$.data[0].customerId").doesNotExist())
                .andExpect(jsonPath("$.data[0].orderId").doesNotExist());

        verify(authenticatedCustomerResolver).resolveActiveCustomer(CUSTOMER_SUBJECT);
        verify(orderStatusService).getOrderStatuses(CUSTOMER_ID);
    }

    @Test
    @DisplayName("CUSTOMER with no orders receives an empty list")
    void customer_shouldReceiveEmptyOrderList() throws Exception {
        when(orderStatusService.getOrderStatuses(CUSTOMER_ID)).thenReturn(List.of());

        mockMvc.perform(get(LIST_PATH).with(customerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(authenticatedCustomerResolver).resolveActiveCustomer(CUSTOMER_SUBJECT);
        verify(orderStatusService).getOrderStatuses(CUSTOMER_ID);
    }

    @Test
    @DisplayName("anonymous order-list request receives 401")
    void anonymous_shouldReceive401_forOrderList() throws Exception {
        mockMvc.perform(get(LIST_PATH))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderStatusService);
    }

    @Test
    @DisplayName("ADMIN without CUSTOMER role receives 403 for the order list")
    void adminOnly_shouldReceive403_forOrderList() throws Exception {
        mockMvc.perform(get(LIST_PATH).with(adminJwt()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderStatusService);
    }

    @Test
    @DisplayName("CUSTOMER receives product snapshots for an owned order")
    void ownerCustomer_shouldReceiveOrderDetails() throws Exception {
        when(orderStatusService.getOrderDetails(ORDER_NUMBER, CUSTOMER_ID))
                .thenReturn(sampleOrderDetails());

        mockMvc.perform(get(DETAIL_PATH).with(customerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$.data.status").value("SHIPPING"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-08-27T03:00:00Z"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-08-27T05:00:00Z"))
                .andExpect(jsonPath("$.data.totalAmount").value(2960000.00))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].productId").value(501))
                .andExpect(jsonPath("$.data.items[0].productName").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(1250000.00))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].subtotal").value(2500000.00))
                .andExpect(jsonPath("$.data.items[1].productName").value("Wireless Mouse"))
                .andExpect(jsonPath("$.data.customerId").doesNotExist())
                .andExpect(jsonPath("$.data.orderId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].orderItemId").doesNotExist());

        verify(authenticatedCustomerResolver).resolveActiveCustomer(CUSTOMER_SUBJECT);
        verify(orderStatusService).getOrderDetails(ORDER_NUMBER, CUSTOMER_ID);
    }

    @Test
    @DisplayName("CUSTOMER requesting nonexistent order detail receives 404")
    void customer_shouldReceive404_whenOrderDetailsDoNotExist() throws Exception {
        stubOrderDetailsNotFound();

        assertOrderDetailsNotFoundResponse();
    }

    @Test
    @DisplayName("CUSTOMER requesting foreign order detail receives the same 404")
    void customer_shouldReceive404_whenOrderDetailsBelongToAnotherCustomer() throws Exception {
        stubOrderDetailsNotFound();

        assertOrderDetailsNotFoundResponse();
    }

    @Test
    @DisplayName("anonymous order-detail request receives 401")
    void anonymous_shouldReceive401_forOrderDetails() throws Exception {
        mockMvc.perform(get(DETAIL_PATH))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderStatusService);
    }

    @Test
    @DisplayName("ADMIN without CUSTOMER role receives 403 for order details")
    void adminOnly_shouldReceive403_forOrderDetails() throws Exception {
        mockMvc.perform(get(DETAIL_PATH).with(adminJwt()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderStatusService);
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

    private void stubOrderDetailsNotFound() {
        when(orderStatusService.getOrderDetails(ORDER_NUMBER, CUSTOMER_ID))
                .thenThrow(new ResourceNotFoundException("Order", ORDER_NUMBER));
    }

    private void assertOrderDetailsNotFoundResponse() throws Exception {
        mockMvc.perform(get(DETAIL_PATH).with(customerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value(
                        "Order not found with id: " + ORDER_NUMBER));

        verify(authenticatedCustomerResolver).resolveActiveCustomer(CUSTOMER_SUBJECT);
        verify(orderStatusService).getOrderDetails(ORDER_NUMBER, CUSTOMER_ID);
    }

    private static OrderDetailResponse sampleOrderDetails() {
        return new OrderDetailResponse(
                ORDER_NUMBER,
                OrderStatus.SHIPPING,
                CREATED_AT,
                UPDATED_AT,
                new BigDecimal("2960000.00"),
                List.of(
                        new OrderItemResponse(
                                501L,
                                "Mechanical Keyboard",
                                new BigDecimal("1250000.00"),
                                2,
                                new BigDecimal("2500000.00")),
                        new OrderItemResponse(
                                502L,
                                "Wireless Mouse",
                                new BigDecimal("450000.00"),
                                1,
                                new BigDecimal("450000.00"))
                )
        );
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
