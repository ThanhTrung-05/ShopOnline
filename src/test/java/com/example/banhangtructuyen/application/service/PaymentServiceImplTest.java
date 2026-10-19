package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.PaymentCallbackRequest;
import com.example.banhangtructuyen.application.service.impl.PaymentServiceImpl;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Inventory;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderItem;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.model.Payment;
import com.example.banhangtructuyen.domain.repository.InventoryRepository;
import com.example.banhangtructuyen.domain.repository.OrderItemRepository;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import com.example.banhangtructuyen.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ATS-29: Unit tests for PaymentServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductService productService;
    @Mock private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private PaymentServiceImpl service;

    private static final String ORDER_NUMBER = "ORD-20261019-ABC12345";
    private static final Long ORDER_ID = 1001L;
    private static final Long CUSTOMER_ID = 42L;
    private static final BigDecimal TOTAL = new BigDecimal("350000");
    private static final String IDEMPOTENCY_KEY = "PAY-1001-test-uuid";

    private Order pendingOrder;
    private Order paidOrder;
    private OrderItem orderItem;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        pendingOrder = Order.builder()
                .orderId(ORDER_ID)
                .customerId(CUSTOMER_ID)
                .orderNumber(ORDER_NUMBER)
                .status(OrderStatus.PENDING)
                .totalAmount(TOTAL)
                .totalBeforeVat(new BigDecimal("318182"))
                .totalVatAmount(new BigDecimal("31818"))
                .shippingAddress("Test Address")
                .build();

        paidOrder = Order.builder()
                .orderId(ORDER_ID)
                .customerId(CUSTOMER_ID)
                .orderNumber(ORDER_NUMBER)
                .status(OrderStatus.PAID)
                .totalAmount(TOTAL)
                .totalBeforeVat(new BigDecimal("318182"))
                .totalVatAmount(new BigDecimal("31818"))
                .shippingAddress("Test Address")
                .build();

        orderItem = OrderItem.builder()
                .orderItemId(1L)
                .order(pendingOrder)
                .productId(101L)
                .productName("Test Product")
                .quantity(2)
                .unitPrice(new BigDecimal("159091"))
                .subtotal(new BigDecimal("318182"))
                .vatRate(new BigDecimal("10"))
                .vatAmount(new BigDecimal("31818"))
                .build();

        inventory = Inventory.builder()
                .inventoryId(1L)
                .quantity(50)
                .reservedQuantity(5)
                .version(1L)
                .build();
    }

    @Nested
    @DisplayName("SUCCESS callback flow")
    class SuccessCallbackTests {

        @Test
        @DisplayName("ATS-29: SUCCESS callback deducts inventory and marks Order PAID")
        void successCallback_deductsInventoryAndMarksPaid() {
            final PaymentCallbackRequest request = new PaymentCallbackRequest(
                    ORDER_NUMBER, IDEMPOTENCY_KEY, TOTAL, "SUCCESS", "TXN-001");
            when(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.empty());
            when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(ORDER_ID)).thenReturn(List.of(orderItem));
            when(inventoryRepository.findByProductIdWithLock(101L)).thenReturn(Optional.of(inventory));

            service.processCallback(request);

            final ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(inventory.getQuantity()).isEqualTo(48);
            assertThat(inventory.getReservedQuantity()).isEqualTo(3);
            verify(productService).clearProductCaches(101L);
            verify(outboxEventPublisher).publishEvent(any(), any(), any(), any());
        }

        @Test
        @DisplayName("ATS-29: SUCCESS callback with multiple items deducts all correctly")
        void successCallback_multipleItems_allDeducted() {
            final OrderItem item2 = OrderItem.builder()
                    .orderItemId(2L).order(pendingOrder).productId(202L).productName("P2")
                    .quantity(3).unitPrice(new BigDecimal("10000")).subtotal(new BigDecimal("30000"))
                    .vatRate(new BigDecimal("5")).vatAmount(new BigDecimal("1500")).build();
            final Inventory inv2 = Inventory.builder().inventoryId(2L).quantity(20).reservedQuantity(3).version(1L).build();

            final PaymentCallbackRequest request = new PaymentCallbackRequest(
                    ORDER_NUMBER, IDEMPOTENCY_KEY, TOTAL, "SUCCESS", "TXN-002");
            when(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.empty());
            when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(ORDER_ID)).thenReturn(List.of(orderItem, item2));
            when(inventoryRepository.findByProductIdWithLock(101L)).thenReturn(Optional.of(inventory));
            when(inventoryRepository.findByProductIdWithLock(202L)).thenReturn(Optional.of(inv2));

            service.processCallback(request);

            assertThat(inventory.getQuantity()).isEqualTo(48);
            assertThat(inv2.getQuantity()).isEqualTo(17);
        }
    }

    @Nested
    @DisplayName("ATS-29: Idempotency guards")
    class IdempotencyGuardTests {

        @Test
        @DisplayName("ATS-29 PRIMARY GUARD: Order already PAID — no deduction, no re-processing")
        void primaryGuard_orderAlreadyPaid_returnsEarlyNoDeduction() {
            final PaymentCallbackRequest request = new PaymentCallbackRequest(
                    ORDER_NUMBER, "DIFFERENT-KEY-FROM-BANK", TOTAL, "SUCCESS", "TXN-999");
            when(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.of(paidOrder));

            service.processCallback(request);

            verify(inventoryRepository, never()).findByProductIdWithLock(anyLong());
            verify(paymentRepository, never()).findByOrderNumberForUpdate(any());
            verify(productService, never()).clearProductCaches(anyLong());
            verify(outboxEventPublisher, never()).publishEvent(any(), any(), any(), any());
        }

        @Test
        @DisplayName("ATS-29 SECONDARY GUARD: Same idempotencyKey SUCCESS — return early")
        void secondaryGuard_sameIdempotencyKeySuccess_returnsEarly() {
            final Payment existingPayment = Payment.builder()
                    .paymentId(1L).order(paidOrder).idempotencyKey(IDEMPOTENCY_KEY)
                    .status(Payment.PaymentStatus.SUCCESS).amount(TOTAL).build();
            final PaymentCallbackRequest request = new PaymentCallbackRequest(
                    ORDER_NUMBER, IDEMPOTENCY_KEY, TOTAL, "SUCCESS", "TXN-001");
            when(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.of(existingPayment));

            service.processCallback(request);

            verify(inventoryRepository, never()).findByProductIdWithLock(anyLong());
            verify(outboxEventPublisher, never()).publishEvent(any(), any(), any(), any());
        }

        @Test
        @DisplayName("ATS-29: Duplicate SUCCESS callback (different key) prevented by Order PAID guard")
        void duplicateCallback_differentKey_orderPaid_prevents_doubleDeduction() {
            final PaymentCallbackRequest secondRequest = new PaymentCallbackRequest(
                    ORDER_NUMBER, "SECOND-KEY-RETRY", TOTAL, "SUCCESS", "TXN-RETRY");
            when(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.of(paidOrder));

            service.processCallback(secondRequest);

            verify(inventoryRepository, never()).findByProductIdWithLock(anyLong());
            verify(outboxEventPublisher, never()).publishEvent(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("FAILED callback flow")
    class FailedCallbackTests {

        @Test
        @DisplayName("FAILED callback sets PAYMENT_FAILED, no inventory deduction")
        void failedCallback_noDeduction_setsPaymentFailed() {
            final PaymentCallbackRequest request = new PaymentCallbackRequest(
                    ORDER_NUMBER, IDEMPOTENCY_KEY, TOTAL, "FAILED", null);
            when(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.empty());

            service.processCallback(request);

            final ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
            verify(inventoryRepository, never()).findByProductIdWithLock(anyLong());
        }
    }

    @Nested
    @DisplayName("Validation and error handling")
    class ValidationTests {

        @Test
        @DisplayName("Amount mismatch throws IllegalArgumentException")
        void amountMismatch_throwsIllegalArgumentException() {
            final PaymentCallbackRequest request = new PaymentCallbackRequest(
                    ORDER_NUMBER, IDEMPOTENCY_KEY, new BigDecimal("999999"), "SUCCESS", "TXN-001");
            when(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.processCallback(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not match");
            verify(inventoryRepository, never()).findByProductIdWithLock(anyLong());
        }

        @Test
        @DisplayName("Order not found throws ResourceNotFoundException")
        void orderNotFound_throwsResourceNotFoundException() {
            final PaymentCallbackRequest request = new PaymentCallbackRequest(
                    "ORD-NONEXISTENT", IDEMPOTENCY_KEY, TOTAL, "SUCCESS", "TXN-001");
            when(orderRepository.findByOrderNumberForUpdate("ORD-NONEXISTENT")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.processCallback(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("ATS-29: Inventory not found throws IllegalStateException")
        void inventoryNotFound_throwsIllegalStateException() {
            final PaymentCallbackRequest request = new PaymentCallbackRequest(
                    ORDER_NUMBER, IDEMPOTENCY_KEY, TOTAL, "SUCCESS", "TXN-001");
            when(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.empty());
            when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(ORDER_ID)).thenReturn(List.of(orderItem));
            when(inventoryRepository.findByProductIdWithLock(101L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.processCallback(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Inventory not found");
        }

        @Test
        @DisplayName("ATS-29: Inventory cannot go negative — deduct throws when insufficient")
        void inventoryCannotGoNegative_throwsIllegalStateException() {
            final Inventory lowInventory = Inventory.builder()
                    .inventoryId(1L).quantity(1).reservedQuantity(1).version(1L).build();
            final PaymentCallbackRequest request = new PaymentCallbackRequest(
                    ORDER_NUMBER, IDEMPOTENCY_KEY, TOTAL, "SUCCESS", "TXN-001");
            when(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(Optional.empty());
            when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(ORDER_ID)).thenReturn(List.of(orderItem));
            when(inventoryRepository.findByProductIdWithLock(101L)).thenReturn(Optional.of(lowInventory));

            assertThatThrownBy(() -> service.processCallback(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot deduct");
        }
    }
}
