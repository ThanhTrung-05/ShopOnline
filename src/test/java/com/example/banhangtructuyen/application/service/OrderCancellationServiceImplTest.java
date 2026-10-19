package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.service.impl.OrderCancellationServiceImpl;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ATS-30: Unit tests for OrderCancellationServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductService productService;
    @Mock private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private OrderCancellationServiceImpl service;

    private static final Long ORDER_ID = 1001L;
    private static final Long PAYMENT_ID = 2001L;
    private static final BigDecimal TOTAL = new BigDecimal("350000");

    private Order pendingOrder;
    private Payment processingPayment;
    private OrderItem orderItem;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        pendingOrder = Order.builder()
                .orderId(ORDER_ID).customerId(42L).orderNumber("ORD-20261019-ABCD1234")
                .status(OrderStatus.PENDING).totalAmount(TOTAL)
                .totalBeforeVat(new BigDecimal("318182")).totalVatAmount(new BigDecimal("31818"))
                .shippingAddress("Test Address").build();

        processingPayment = Payment.builder()
                .paymentId(PAYMENT_ID).order(pendingOrder).idempotencyKey("PAY-1001-uuid")
                .status(Payment.PaymentStatus.PROCESSING).amount(TOTAL)
                .qrExpiresAt(Instant.now().minusSeconds(60)).build();

        orderItem = OrderItem.builder()
                .orderItemId(1L).order(pendingOrder).productId(101L).productName("Test Product")
                .quantity(2).unitPrice(new BigDecimal("159091")).subtotal(new BigDecimal("318182"))
                .vatRate(new BigDecimal("10")).vatAmount(new BigDecimal("31818")).build();

        inventory = Inventory.builder()
                .inventoryId(1L).quantity(50).reservedQuantity(5).version(1L).build();
    }

    @Nested
    @DisplayName("Normal cancellation flow")
    class NormalCancellationTests {

        @Test
        @DisplayName("ATS-30: Expired PENDING order is cancelled and reserve released")
        void cancelExpiredOrder_pendingOrder_cancelledAndReserveReleased() {
            when(orderRepository.findByOrderIdAndStatusForUpdate(ORDER_ID, OrderStatus.PENDING))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(processingPayment));
            when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(ORDER_ID))
                    .thenReturn(List.of(orderItem));
            when(inventoryRepository.findByProductIdWithLock(101L))
                    .thenReturn(Optional.of(inventory));

            service.cancelExpiredOrder(ORDER_ID, PAYMENT_ID);

            final ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);

            final ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(Payment.PaymentStatus.EXPIRED);

            assertThat(inventory.getReservedQuantity()).isEqualTo(3); // 5 - 2
            assertThat(inventory.getQuantity()).isEqualTo(50);         // unchanged

            verify(productService).clearProductCaches(101L);
            verify(outboxEventPublisher).publishEvent(any(), any(), any(), any());
        }

        @Test
        @DisplayName("ATS-30: Multiple items - all reserves released correctly")
        void cancelExpiredOrder_multipleItems_allReservesReleased() {
            final OrderItem item2 = OrderItem.builder()
                    .orderItemId(2L).order(pendingOrder).productId(202L).productName("P2")
                    .quantity(3).unitPrice(new BigDecimal("10000")).subtotal(new BigDecimal("30000"))
                    .vatRate(new BigDecimal("5")).vatAmount(new BigDecimal("1500")).build();
            final Inventory inv2 = Inventory.builder().inventoryId(2L).quantity(20).reservedQuantity(7).version(1L).build();

            when(orderRepository.findByOrderIdAndStatusForUpdate(ORDER_ID, OrderStatus.PENDING))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(processingPayment));
            when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(ORDER_ID))
                    .thenReturn(List.of(orderItem, item2));
            when(inventoryRepository.findByProductIdWithLock(101L)).thenReturn(Optional.of(inventory));
            when(inventoryRepository.findByProductIdWithLock(202L)).thenReturn(Optional.of(inv2));

            service.cancelExpiredOrder(ORDER_ID, PAYMENT_ID);

            assertThat(inventory.getReservedQuantity()).isEqualTo(3);   // 5 - 2
            assertThat(inv2.getReservedQuantity()).isEqualTo(4);        // 7 - 3
            assertThat(inventory.getQuantity()).isEqualTo(50);          // unchanged
            assertThat(inv2.getQuantity()).isEqualTo(20);               // unchanged
        }
    }

    @Nested
    @DisplayName("ATS-30: Idempotency and race condition guards")
    class IdempotencyTests {

        @Test
        @DisplayName("ATS-30: Order already PAID - scheduler skips, no release")
        void orderAlreadyPaid_skipCancellation() {
            when(orderRepository.findByOrderIdAndStatusForUpdate(ORDER_ID, OrderStatus.PENDING))
                    .thenReturn(Optional.empty());

            service.cancelExpiredOrder(ORDER_ID, PAYMENT_ID);

            verify(paymentRepository, never()).findByOrder_OrderIdForUpdate(anyLong());
            verify(orderRepository, never()).save(any());
            verify(inventoryRepository, never()).findByProductIdWithLock(anyLong());
            verify(outboxEventPublisher, never()).publishEvent(any(), any(), any(), any());
        }

        @Test
        @DisplayName("ATS-30: Order already CANCELLED - scheduler skips (idempotent)")
        void orderAlreadyCancelled_skipCancellation() {
            when(orderRepository.findByOrderIdAndStatusForUpdate(ORDER_ID, OrderStatus.PENDING))
                    .thenReturn(Optional.empty());

            service.cancelExpiredOrder(ORDER_ID, PAYMENT_ID);

            verify(inventoryRepository, never()).findByProductIdWithLock(anyLong());
            verify(outboxEventPublisher, never()).publishEvent(any(), any(), any(), any());
        }

        @Test
        @DisplayName("ATS-30: Payment already SUCCESS - does not override, returns early")
        void paymentAlreadySuccess_returnsEarly() {
            final Payment successPayment = Payment.builder()
                    .paymentId(PAYMENT_ID).order(pendingOrder).idempotencyKey("PAY-1001-uuid")
                    .status(Payment.PaymentStatus.SUCCESS).amount(TOTAL).build();
            when(orderRepository.findByOrderIdAndStatusForUpdate(ORDER_ID, OrderStatus.PENDING))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(successPayment));

            service.cancelExpiredOrder(ORDER_ID, PAYMENT_ID);

            verify(orderRepository, never()).save(any());
            verify(inventoryRepository, never()).findByProductIdWithLock(anyLong());
            verify(outboxEventPublisher, never()).publishEvent(any(), any(), any(), any());
        }

        @Test
        @DisplayName("ATS-30: Scheduler runs twice - second run is no-op (idempotent)")
        void schedulerRunsTwice_secondRunIsNoOp() {
            when(orderRepository.findByOrderIdAndStatusForUpdate(ORDER_ID, OrderStatus.PENDING))
                    .thenReturn(Optional.of(pendingOrder))
                    .thenReturn(Optional.empty());
            when(paymentRepository.findByOrder_OrderIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(processingPayment));
            when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(ORDER_ID))
                    .thenReturn(List.of(orderItem));
            when(inventoryRepository.findByProductIdWithLock(101L))
                    .thenReturn(Optional.of(inventory));

            service.cancelExpiredOrder(ORDER_ID, PAYMENT_ID);
            service.cancelExpiredOrder(ORDER_ID, PAYMENT_ID);

            verify(orderRepository).save(any()); // saved only once
            assertThat(inventory.getReservedQuantity()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("ATS-30: Inventory not found for a product - continues with other items")
        void inventoryNotFound_continuesOtherItems() {
            final OrderItem item2 = OrderItem.builder()
                    .orderItemId(2L).order(pendingOrder).productId(202L).productName("P2")
                    .quantity(3).unitPrice(new BigDecimal("10000")).subtotal(new BigDecimal("30000"))
                    .vatRate(new BigDecimal("5")).vatAmount(new BigDecimal("1500")).build();
            final Inventory inv2 = Inventory.builder().inventoryId(2L).quantity(20).reservedQuantity(7).version(1L).build();

            when(orderRepository.findByOrderIdAndStatusForUpdate(ORDER_ID, OrderStatus.PENDING))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(processingPayment));
            when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(ORDER_ID))
                    .thenReturn(List.of(orderItem, item2));
            when(inventoryRepository.findByProductIdWithLock(101L)).thenReturn(Optional.empty());
            when(inventoryRepository.findByProductIdWithLock(202L)).thenReturn(Optional.of(inv2));

            service.cancelExpiredOrder(ORDER_ID, PAYMENT_ID);

            assertThat(inv2.getReservedQuantity()).isEqualTo(4); // 7 - 3
        }

        @Test
        @DisplayName("ATS-30: Reserve never goes below 0 - Math.max protection")
        void releaseMoreThanReserved_doesNotGoBelowZero() {
            final Inventory nearZeroInventory = Inventory.builder()
                    .inventoryId(1L).quantity(50).reservedQuantity(1).version(1L).build();
            when(orderRepository.findByOrderIdAndStatusForUpdate(ORDER_ID, OrderStatus.PENDING))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(processingPayment));
            when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(ORDER_ID))
                    .thenReturn(List.of(orderItem));  // qty=2 but reserved=1
            when(inventoryRepository.findByProductIdWithLock(101L))
                    .thenReturn(Optional.of(nearZeroInventory));

            service.cancelExpiredOrder(ORDER_ID, PAYMENT_ID);

            assertThat(nearZeroInventory.getReservedQuantity()).isEqualTo(0); // Math.max(0, 1-2)=0
            assertThat(nearZeroInventory.getQuantity()).isEqualTo(50);        // unchanged
        }
    }
}
