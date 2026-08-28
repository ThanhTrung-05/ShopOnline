package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.OrderStatusResponse;
import com.example.banhangtructuyen.application.service.impl.OrderStatusServiceImpl;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
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

    private OrderStatusService service;

    @BeforeEach
    void setUp() {
        service = new OrderStatusServiceImpl(orderRepository);
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

    private static Order sampleOrder() {
        return Order.builder()
                .orderNumber(ORDER_NUMBER)
                .customerId(CUSTOMER_ID)
                .status(OrderStatus.SHIPPING)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();
    }
}
