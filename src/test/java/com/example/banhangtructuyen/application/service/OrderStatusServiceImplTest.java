package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.OrderDetailResponse;
import com.example.banhangtructuyen.application.dto.order.OrderStatusResponse;
import com.example.banhangtructuyen.application.service.impl.OrderStatusServiceImpl;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderItem;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.repository.OrderItemRepository;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderStatusService")
class OrderStatusServiceImplTest {

    private static final String ORDER_NUMBER = "ORD-001";
    private static final Long CUSTOMER_ID = 10L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-27T03:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-27T05:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    private OrderStatusService service;

    @BeforeEach
    void setUp() {
        service = new OrderStatusServiceImpl(orderRepository, orderItemRepository);
    }

    @Test
    @DisplayName("returns all persisted item snapshots for an owned order")
    void getOrderDetails_shouldReturnOwnedOrderItemSnapshots() {
        final Order order = sampleOrder();
        final List<OrderItem> items = List.of(
                OrderItem.builder()
                        .orderItemId(101L)
                        .order(order)
                        .productId(501L)
                        .productName("Mechanical Keyboard")
                        .unitPrice(new BigDecimal("1250000.00"))
                        .quantity(2)
                        .subtotal(new BigDecimal("2500000.00"))
                        .build(),
                OrderItem.builder()
                        .orderItemId(102L)
                        .order(order)
                        .productId(502L)
                        .productName("Wireless Mouse")
                        .unitPrice(new BigDecimal("450000.00"))
                        .quantity(1)
                        .subtotal(new BigDecimal("450000.00"))
                        .build()
        );
        when(orderRepository.findByOrderNumberAndCustomerId(ORDER_NUMBER, CUSTOMER_ID))
                .thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(order.getOrderId()))
                .thenReturn(items);

        final OrderDetailResponse response = service.getOrderDetails(ORDER_NUMBER, CUSTOMER_ID);

        assertThat(response.orderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(response.status()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(response.totalAmount()).isEqualByComparingTo("2960000.00");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).productId()).isEqualTo(501L);
        assertThat(response.items().get(0).productName()).isEqualTo("Mechanical Keyboard");
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("1250000.00");
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("2500000.00");
        assertThat(response.items().get(1).productName()).isEqualTo("Wireless Mouse");
        verify(orderRepository).findByOrderNumberAndCustomerId(ORDER_NUMBER, CUSTOMER_ID);
        verify(orderItemRepository).findAllByOrder_OrderIdOrderByOrderItemIdAsc(order.getOrderId());
    }

    @Test
    @DisplayName("missing order detail produces the generic Order not-found result without reading items")
    void getOrderDetails_shouldThrowNotFound_whenOrderDoesNotExist() {
        when(orderRepository.findByOrderNumberAndCustomerId(ORDER_NUMBER, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertOrderDetailsNotFound();
    }

    @Test
    @DisplayName("foreign-owned order detail produces the same not-found result without reading items")
    void getOrderDetails_shouldThrowNotFound_whenOrderBelongsToAnotherCustomer() {
        when(orderRepository.findByOrderNumberAndCustomerId(ORDER_NUMBER, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertOrderDetailsNotFound();
    }

    @Test
    @DisplayName("returns status for the ownership-scoped order lookup")
    void getOrderStatus_shouldReturnOwnedOrderStatus() {
        when(orderRepository.findByOrderNumberAndCustomerId(ORDER_NUMBER, CUSTOMER_ID))
                .thenReturn(Optional.of(sampleOrder()));

        final OrderStatusResponse response = service.getOrderStatus(ORDER_NUMBER, CUSTOMER_ID);

        assertThat(response.orderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(response.status()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
        verify(orderRepository).findByOrderNumberAndCustomerId(ORDER_NUMBER, CUSTOMER_ID);
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    @DisplayName("returns the ownership-scoped order status list")
    void getOrderStatuses_shouldReturnOwnedOrderStatuses() {
        final Order secondOrder = Order.builder()
                .orderNumber("ORD-002")
                .customerId(CUSTOMER_ID)
                .status(OrderStatus.DELIVERED)
                .createdAt(Instant.parse("2026-08-26T03:00:00Z"))
                .updatedAt(Instant.parse("2026-08-26T07:00:00Z"))
                .build();
        when(orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID))
                .thenReturn(List.of(sampleOrder(), secondOrder));

        final List<OrderStatusResponse> response = service.getOrderStatuses(CUSTOMER_ID);

        assertThat(response)
                .extracting(OrderStatusResponse::orderNumber)
                .containsExactly(ORDER_NUMBER, "ORD-002");
        assertThat(response.get(0).status()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(response.get(0).createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.get(0).updatedAt()).isEqualTo(UPDATED_AT);
        verify(orderRepository).findAllByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID);
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    @DisplayName("returns an empty list when the customer has no orders")
    void getOrderStatuses_shouldReturnEmptyList() {
        when(orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID))
                .thenReturn(List.of());

        assertThat(service.getOrderStatuses(CUSTOMER_ID)).isEmpty();

        verify(orderRepository).findAllByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID);
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    @DisplayName("missing order produces the generic Order not-found result")
    void getOrderStatus_shouldThrowNotFound_whenOrderDoesNotExist() {
        when(orderRepository.findByOrderNumberAndCustomerId(ORDER_NUMBER, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertNotFound();
    }

    @Test
    @DisplayName("foreign-owned order produces the same generic Order not-found result")
    void getOrderStatus_shouldThrowNotFound_whenOrderBelongsToAnotherCustomer() {
        when(orderRepository.findByOrderNumberAndCustomerId(ORDER_NUMBER, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertNotFound();
    }

    private void assertNotFound() {
        assertThatThrownBy(() -> service.getOrderStatus(ORDER_NUMBER, CUSTOMER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order not found with id: " + ORDER_NUMBER);
        verify(orderRepository).findByOrderNumberAndCustomerId(ORDER_NUMBER, CUSTOMER_ID);
        verifyNoMoreInteractions(orderRepository);
    }

    private void assertOrderDetailsNotFound() {
        assertThatThrownBy(() -> service.getOrderDetails(ORDER_NUMBER, CUSTOMER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order not found with id: " + ORDER_NUMBER);
        verify(orderRepository).findByOrderNumberAndCustomerId(ORDER_NUMBER, CUSTOMER_ID);
        verifyNoInteractions(orderItemRepository);
        verifyNoMoreInteractions(orderRepository);
    }

    private static Order sampleOrder() {
        return Order.builder()
                .orderId(100L)
                .orderNumber(ORDER_NUMBER)
                .customerId(CUSTOMER_ID)
                .status(OrderStatus.SHIPPING)
                .totalAmount(new BigDecimal("2960000.00"))
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();
    }
}
