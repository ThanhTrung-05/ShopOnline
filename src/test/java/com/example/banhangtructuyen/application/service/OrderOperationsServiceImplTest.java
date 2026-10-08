package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.OperationsOrderResponse;
import com.example.banhangtructuyen.application.service.impl.OrderOperationsServiceImpl;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderOperationsService")
class OrderOperationsServiceImplTest {

    private static final String ORDER_NUMBER = "ORD-20260907-OPERATIONS";
    private static final Instant UPDATED_AT = Instant.parse("2026-09-07T03:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private NotificationService notificationService;

    private OrderOperationsService service;

    @BeforeEach
    void setUp() {
        service = new OrderOperationsServiceImpl(orderRepository, notificationService);
    }

    @Test
    @DisplayName("lists every order newest first using the operations query")
    void getOrders_shouldMapOperationalOrderList() {
        when(orderRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                sampleOrder("ORD-NEW", OrderStatus.SHIPPING),
                sampleOrder("ORD-OLD", OrderStatus.CONFIRMED)
        ));

        final List<OperationsOrderResponse> result = service.getOrders();

        assertThat(result).extracting(OperationsOrderResponse::orderNumber)
                .containsExactly("ORD-NEW", "ORD-OLD");
        assertThat(result).extracting(OperationsOrderResponse::status)
                .containsExactly(OrderStatus.SHIPPING, OrderStatus.CONFIRMED);
        assertThat(result).allSatisfy(order -> assertThat(order.updatedAt()).isEqualTo(UPDATED_AT));
        verify(orderRepository).findAllByOrderByCreatedAtDesc();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allowedTransitions")
    @DisplayName("persists every allowed transition while holding the order lock")
    void updateStatus_shouldPersistAllowedTransition(
            final OrderStatus currentStatus,
            final OrderStatus targetStatus) {
        final Order order = sampleOrder(ORDER_NUMBER, currentStatus);
        when(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final OperationsOrderResponse result = service.updateStatus(ORDER_NUMBER, targetStatus);

        assertThat(order.getStatus()).isEqualTo(targetStatus);
        assertThat(result.orderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(result.status()).isEqualTo(targetStatus);
        assertThat(result.updatedAt()).isEqualTo(UPDATED_AT);
        verify(orderRepository).findByOrderNumberForUpdate(ORDER_NUMBER);
        verify(orderRepository).saveAndFlush(order);
        verify(notificationService)
                .createOrderStatusChangedNotification(order, currentStatus, targetStatus);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("blockedTransitions")
    @DisplayName("rejects skipped, backward, terminal, repeated, and payment-status transitions")
    void updateStatus_shouldRejectBlockedTransition(
            final OrderStatus currentStatus,
            final OrderStatus targetStatus) {
        final Order order = sampleOrder(ORDER_NUMBER, currentStatus);
        when(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateStatus(ORDER_NUMBER, targetStatus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order status transition from " + currentStatus
                        + " to " + targetStatus + " is not allowed");

        assertThat(order.getStatus()).isEqualTo(currentStatus);
        verify(orderRepository).findByOrderNumberForUpdate(ORDER_NUMBER);
        verify(orderRepository, never()).saveAndFlush(any(Order.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("returns the existing generic 404 when the order number is missing")
    void updateStatus_shouldThrowNotFound_whenOrderDoesNotExist() {
        when(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(ORDER_NUMBER, OrderStatus.CONFIRMED))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order not found with id: " + ORDER_NUMBER);

        verify(orderRepository).findByOrderNumberForUpdate(ORDER_NUMBER);
        verify(orderRepository, never()).saveAndFlush(any(Order.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("rejects a missing target status before loading an order")
    void updateStatus_shouldRejectNullStatus() {
        assertThatThrownBy(() -> service.updateStatus(ORDER_NUMBER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order status is required");

        verifyNoInteractions(orderRepository, notificationService);
    }

    private static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                Arguments.of(OrderStatus.PENDING, OrderStatus.CANCELLED),
                Arguments.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPING),
                Arguments.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
                Arguments.of(OrderStatus.SHIPPING, OrderStatus.DELIVERED)
        );
    }

    private static Stream<Arguments> blockedTransitions() {
        return Stream.of(
                Arguments.of(OrderStatus.PENDING, OrderStatus.SHIPPING),
                Arguments.of(OrderStatus.CONFIRMED, OrderStatus.PENDING),
                Arguments.of(OrderStatus.SHIPPING, OrderStatus.CONFIRMED),
                Arguments.of(OrderStatus.PENDING, OrderStatus.PENDING),
                Arguments.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED),
                Arguments.of(OrderStatus.CANCELLED, OrderStatus.CONFIRMED),
                Arguments.of(OrderStatus.PAID, OrderStatus.SHIPPING),
                Arguments.of(OrderStatus.PAYMENT_FAILED, OrderStatus.CONFIRMED),
                Arguments.of(OrderStatus.REFUNDED, OrderStatus.SHIPPING),
                Arguments.of(OrderStatus.PENDING, OrderStatus.PAID),
                Arguments.of(OrderStatus.CONFIRMED, OrderStatus.PAYMENT_FAILED),
                Arguments.of(OrderStatus.SHIPPING, OrderStatus.REFUNDED)
        );
    }

    private static Order sampleOrder(final String orderNumber, final OrderStatus status) {
        return Order.builder()
                .orderId(10L)
                .customerId(20L)
                .orderNumber(orderNumber)
                .status(status)
                .updatedAt(UPDATED_AT)
                .build();
    }
}
