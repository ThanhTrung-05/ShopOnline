package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.DemoConfirmRequest;
import com.example.banhangtructuyen.application.dto.order.PaymentStatusResponse;
import com.example.banhangtructuyen.application.dto.order.QrPaymentResponse;
import com.example.banhangtructuyen.application.service.impl.QrPaymentServiceImpl;
import com.example.banhangtructuyen.config.AppProperties;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import org.mockito.quality.Strictness;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ATS-27 + ATS-28: Unit tests for QrPaymentServiceImpl.
 *
 * Covers:
 *  - QR generation: valid, reuse, invalid status, order not found
 *  - Status polling: cache hit, cache miss
 *  - Demo confirm: success, idempotency, wrong amount, demo-mode disabled, invalid state
 *
 * Uses LENIENT strictness because shared @BeforeEach stubs (appProperties, redisTemplate)
 * are not consumed by every individual test (e.g. tests that throw before reaching those calls).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QrPaymentServiceImplTest {

    @Mock private AppProperties appProperties;
    @Mock private AppProperties.Payment paymentConfig;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductService productService;
    @Mock private OutboxEventPublisher outboxEventPublisher;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private QrPaymentServiceImpl service;

    private static final Long ORDER_ID = 1001L;
    private static final Long CUSTOMER_ID = 42L;
    private static final BigDecimal TOTAL = new BigDecimal("350000");

    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        pendingOrder = Order.builder()
                .orderId(ORDER_ID)
                .customerId(CUSTOMER_ID)
                .orderNumber("ORD-20261013-ABCD1234")
                .status(OrderStatus.PENDING)
                .totalBeforeVat(new BigDecimal("318182"))
                .totalVatAmount(new BigDecimal("31818"))
                .totalAmount(TOTAL)
                .shippingAddress("123 Test St")
                .build();

        // Lenient: these stubs are shared defaults; not every test reaches all of them
        // (e.g. tests that throw early before payment config or Redis are accessed).
        org.mockito.Mockito.lenient().when(appProperties.getPayment()).thenReturn(paymentConfig);
        org.mockito.Mockito.lenient().when(paymentConfig.getQrExpiryMinutes()).thenReturn(15);
        org.mockito.Mockito.lenient().when(paymentConfig.getStatusCacheTtlSeconds()).thenReturn(960);
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // =========================================================================
    // ATS-27: generateQr
    // =========================================================================

    @Nested
    @DisplayName("ATS-27: generateQr")
    class GenerateQrTests {

        @Test
        @DisplayName("Should create new Payment and QR for valid PENDING order")
        void generateQr_validPendingOrder_createsPaymentAndQr() {
            when(orderRepository.findByOrderIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.empty());

            final Payment saved = Payment.builder()
                    .paymentId(555L)
                    .order(pendingOrder)
                    .amount(TOTAL)
                    .status(Payment.PaymentStatus.PROCESSING)
                    .qrCode(ORDER_ID + "|ORD-20261013-ABCD1234|350000")
                    .qrExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                    .build();
            when(paymentRepository.save(any())).thenReturn(saved);

            final QrPaymentResponse response = service.generateQr(ORDER_ID, CUSTOMER_ID);

            assertThat(response.orderId()).isEqualTo(ORDER_ID);
            assertThat(response.orderNumber()).isEqualTo("ORD-20261013-ABCD1234");
            assertThat(response.amount()).isEqualByComparingTo(TOTAL);
            assertThat(response.status()).isEqualTo(Payment.PaymentStatus.PROCESSING);
            assertThat(response.qrCode()).contains(ORDER_ID.toString());
            assertThat(response.qrCode()).contains(TOTAL.toPlainString());
            assertThat(response.qrExpiresAt()).isAfter(Instant.now());

            verify(paymentRepository).save(any(Payment.class));
            verify(valueOps).set(anyString(), anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("Should reuse existing QR when PROCESSING and not expired")
        void generateQr_existingProcessingValidQr_reusesWithoutSave() {
            final Instant futureExpiry = Instant.now().plus(10, ChronoUnit.MINUTES);
            final Payment existingPayment = Payment.builder()
                    .paymentId(555L)
                    .order(pendingOrder)
                    .amount(TOTAL)
                    .status(Payment.PaymentStatus.PROCESSING)
                    .qrCode("1001|ORD-20261013-ABCD1234|350000")
                    .qrExpiresAt(futureExpiry)
                    .idempotencyKey("PAY-1001-existing")
                    .build();

            when(orderRepository.findByOrderIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.of(existingPayment));

            final QrPaymentResponse response = service.generateQr(ORDER_ID, CUSTOMER_ID);

            assertThat(response.paymentId()).isEqualTo(555L);
            assertThat(response.qrCode()).isEqualTo("1001|ORD-20261013-ABCD1234|350000");
            // Must NOT save a new payment — idempotent
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("ATS-27: QR amount must be taken from backend Order.totalAmount, not frontend")
        void generateQr_amountSourceIsBackendOrder() {
            when(orderRepository.findByOrderIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.empty());

            final ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            when(paymentRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            service.generateQr(ORDER_ID, CUSTOMER_ID);

            assertThat(captor.getValue().getAmount()).isEqualByComparingTo(TOTAL);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when order does not exist")
        void generateQr_orderNotFound_throwsResourceNotFoundException() {
            when(orderRepository.findByOrderIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateQr(ORDER_ID, CUSTOMER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when order is not PENDING")
        void generateQr_orderNotPending_throwsIllegalArgumentException() {
            pendingOrder.setStatus(OrderStatus.PAID);
            when(orderRepository.findByOrderIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                    .thenReturn(Optional.of(pendingOrder));

            assertThatThrownBy(() -> service.generateQr(ORDER_ID, CUSTOMER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("trạng thái chờ thanh toán");
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when order is PAYMENT_FAILED")
        void generateQr_paymentFailedOrder_throwsIllegalArgumentException() {
            pendingOrder.setStatus(OrderStatus.PAYMENT_FAILED);
            when(orderRepository.findByOrderIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                    .thenReturn(Optional.of(pendingOrder));

            assertThatThrownBy(() -> service.generateQr(ORDER_ID, CUSTOMER_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("ATS-27: Should NOT deduct inventory when QR is created")
        void generateQr_doesNotDeductInventory() {
            when(orderRepository.findByOrderIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.empty());
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.generateQr(ORDER_ID, CUSTOMER_ID);

            verify(inventoryRepository, never()).findByProductIdWithLock(anyLong());
        }

        @Test
        @DisplayName("Should refresh QR when existing Payment is PROCESSING but QR is expired")
        void generateQr_expiredQr_refreshesPayment() {
            final Instant pastExpiry = Instant.now().minus(5, ChronoUnit.MINUTES);
            final Payment expiredPayment = Payment.builder()
                    .paymentId(555L)
                    .order(pendingOrder)
                    .amount(TOTAL)
                    .status(Payment.PaymentStatus.PROCESSING)
                    .qrCode("old-qr")
                    .qrExpiresAt(pastExpiry)
                    .idempotencyKey("PAY-1001-old")
                    .build();

            when(orderRepository.findByOrderIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.of(expiredPayment));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            final QrPaymentResponse response = service.generateQr(ORDER_ID, CUSTOMER_ID);

            assertThat(response.qrCode()).isNotEqualTo("old-qr");
            verify(paymentRepository).save(any(Payment.class));
        }
    }

    // =========================================================================
    // ATS-27: getPaymentStatus
    // =========================================================================

    @Nested
    @DisplayName("ATS-27: getPaymentStatus")
    class GetPaymentStatusTests {

        @Test
        @DisplayName("Should return cached status on cache hit")
        void getPaymentStatus_cacheHit_returnsCachedStatus() {
            when(orderRepository.findByOrderIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                    .thenReturn(Optional.of(pendingOrder));
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("payment:status:" + ORDER_ID)).thenReturn("PROCESSING");

            final PaymentStatusResponse response = service.getPaymentStatus(ORDER_ID, CUSTOMER_ID);

            assertThat(response.status()).isEqualTo(Payment.PaymentStatus.PROCESSING);
            assertThat(response.orderId()).isEqualTo(ORDER_ID);
            verify(paymentRepository, never()).findByOrder_OrderId(anyLong());
        }

        @Test
        @DisplayName("Should query DB on cache miss")
        void getPaymentStatus_cacheMiss_queriesDb() {
            when(orderRepository.findByOrderIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                    .thenReturn(Optional.of(pendingOrder));
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("payment:status:" + ORDER_ID)).thenReturn(null);
            when(paymentRepository.findByOrder_OrderId(ORDER_ID))
                    .thenReturn(Optional.of(Payment.builder()
                            .status(Payment.PaymentStatus.SUCCESS).build()));

            final PaymentStatusResponse response = service.getPaymentStatus(ORDER_ID, CUSTOMER_ID);

            assertThat(response.status()).isEqualTo(Payment.PaymentStatus.SUCCESS);
            verify(paymentRepository).findByOrder_OrderId(ORDER_ID);
        }

        @Test
        @DisplayName("Should return PENDING when no payment exists (cache miss + no DB record)")
        void getPaymentStatus_noPayment_returnsPending() {
            when(orderRepository.findByOrderIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                    .thenReturn(Optional.of(pendingOrder));
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(anyString())).thenReturn(null);
            when(paymentRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.empty());

            final PaymentStatusResponse response = service.getPaymentStatus(ORDER_ID, CUSTOMER_ID);

            assertThat(response.status()).isEqualTo(Payment.PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException for non-owned order")
        void getPaymentStatus_orderNotOwnedByCustomer_throwsNotFoundException() {
            when(orderRepository.findByOrderIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPaymentStatus(ORDER_ID, CUSTOMER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // ATS-28: demoConfirm
    // =========================================================================

    @Nested
    @DisplayName("ATS-28: demoConfirm")
    class DemoConfirmTests {

        private Payment processingPayment;
        private OrderItem orderItem;
        private Inventory inventory;

        @BeforeEach
        void setUpDemo() {
            // Lenient: isDemoMode is only consumed in tests that don't throw before reaching it
            org.mockito.Mockito.lenient().when(paymentConfig.isDemoMode()).thenReturn(true);

            processingPayment = Payment.builder()
                    .paymentId(555L)
                    .order(pendingOrder)
                    .amount(TOTAL)
                    .status(Payment.PaymentStatus.PROCESSING)
                    .idempotencyKey("PAY-1001-abc")
                    .build();

            orderItem = OrderItem.builder()
                    .orderItemId(1L)
                    .order(pendingOrder)
                    .productId(10L)
                    .quantity(2)
                    .unitPrice(new BigDecimal("175000"))
                    .subtotal(new BigDecimal("350000"))
                    .vatRate(BigDecimal.TEN)
                    .vatAmount(new BigDecimal("35000"))
                    .build();

            inventory = Inventory.builder()
                    .inventoryId(1L)
                    .quantity(10)
                    .reservedQuantity(2)
                    .version(1L)
                    .build();
        }

        @Test
        @DisplayName("ATS-28: Should confirm payment successfully and deduct inventory exactly once")
        void demoConfirm_success_updatesPaymentOrderAndDeductsInventory() {
            final DemoConfirmRequest request = new DemoConfirmRequest("ORD-20261013-ABCD1234", TOTAL);

            when(orderRepository.findByOrderNumberForUpdate("ORD-20261013-ABCD1234"))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(processingPayment));
            when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(ORDER_ID))
                    .thenReturn(List.of(orderItem));
            when(inventoryRepository.findByProductIdWithLock(10L))
                    .thenReturn(Optional.of(inventory));

            service.demoConfirm(request);

            // Payment → SUCCESS
            assertThat(processingPayment.getStatus()).isEqualTo(Payment.PaymentStatus.SUCCESS);
            assertThat(processingPayment.getPaidAt()).isNotNull();
            assertThat(processingPayment.getTransactionRef()).startsWith("DEMO-");
            verify(paymentRepository).save(processingPayment);

            // Order → PAID
            assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(orderRepository).save(pendingOrder);

            // ATS-16: inventory deducted exactly once
            assertThat(inventory.getQuantity()).isEqualTo(8);
            verify(inventoryRepository, times(1)).findByProductIdWithLock(10L);

            // ATS-17: cache cleared
            verify(productService, times(1)).clearProductCaches(10L);

            // Outbox published
            verify(outboxEventPublisher).publishEvent(
                    anyString(), anyString(), anyString(), any());

            // Redis updated
            verify(valueOps).set(anyString(), anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("ATS-28: Should be idempotent — duplicate confirm on SUCCESS returns silently")
        void demoConfirm_alreadySuccess_idempotentReturn() {
            processingPayment.setStatus(Payment.PaymentStatus.SUCCESS);
            final DemoConfirmRequest request = new DemoConfirmRequest("ORD-20261013-ABCD1234", TOTAL);

            when(orderRepository.findByOrderNumberForUpdate("ORD-20261013-ABCD1234"))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(processingPayment));

            service.demoConfirm(request);

            // ATS-16: must NOT deduct inventory again
            verify(inventoryRepository, never()).findByProductIdWithLock(anyLong());
            verify(orderRepository, never()).save(any());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("ATS-28: Should be idempotent — duplicate confirm on FAILED returns silently")
        void demoConfirm_alreadyFailed_idempotentReturn() {
            processingPayment.setStatus(Payment.PaymentStatus.FAILED);
            final DemoConfirmRequest request = new DemoConfirmRequest("ORD-20261013-ABCD1234", TOTAL);

            when(orderRepository.findByOrderNumberForUpdate("ORD-20261013-ABCD1234"))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(processingPayment));

            service.demoConfirm(request);

            verify(inventoryRepository, never()).findByProductIdWithLock(anyLong());
        }

        @Test
        @DisplayName("ATS-28: Should throw IllegalArgumentException on amount mismatch")
        void demoConfirm_wrongAmount_throwsIllegalArgumentException() {
            final DemoConfirmRequest request = new DemoConfirmRequest(
                    "ORD-20261013-ABCD1234", new BigDecimal("999999"));

            when(orderRepository.findByOrderNumberForUpdate("ORD-20261013-ABCD1234"))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(processingPayment));

            assertThatThrownBy(() -> service.demoConfirm(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Số tiền không khớp");

            verify(inventoryRepository, never()).findByProductIdWithLock(anyLong());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("ATS-28: Should throw IllegalStateException when Payment is not PROCESSING")
        void demoConfirm_paymentNotProcessing_throwsIllegalStateException() {
            processingPayment.setStatus(Payment.PaymentStatus.EXPIRED);
            final DemoConfirmRequest request = new DemoConfirmRequest("ORD-20261013-ABCD1234", TOTAL);

            when(orderRepository.findByOrderNumberForUpdate("ORD-20261013-ABCD1234"))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(processingPayment));

            assertThatThrownBy(() -> service.demoConfirm(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PROCESSING");
        }

        @Test
        @DisplayName("ATS-28: Should throw UnsupportedOperationException when demo-mode is disabled")
        void demoConfirm_demoModeDisabled_throwsUnsupportedOperationException() {
            when(paymentConfig.isDemoMode()).thenReturn(false);
            final DemoConfirmRequest request = new DemoConfirmRequest("ORD-20261013-ABCD1234", TOTAL);

            assertThatThrownBy(() -> service.demoConfirm(request))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("disabled");

            verify(orderRepository, never()).findByOrderNumberForUpdate(anyString());
        }

        @Test
        @DisplayName("ATS-28: Should throw ResourceNotFoundException when order does not exist")
        void demoConfirm_orderNotFound_throwsResourceNotFoundException() {
            final DemoConfirmRequest request = new DemoConfirmRequest("NONEXISTENT", TOTAL);

            when(orderRepository.findByOrderNumberForUpdate("NONEXISTENT"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.demoConfirm(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("ATS-28: Should throw ResourceNotFoundException when Payment does not exist")
        void demoConfirm_paymentNotFound_throwsResourceNotFoundException() {
            final DemoConfirmRequest request = new DemoConfirmRequest("ORD-20261013-ABCD1234", TOTAL);

            when(orderRepository.findByOrderNumberForUpdate("ORD-20261013-ABCD1234"))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.demoConfirm(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("ATS-28: Failed payment must NOT update Order to PAID")
        void demoConfirm_wrongAmount_orderStatusUnchanged() {
            final DemoConfirmRequest request = new DemoConfirmRequest(
                    "ORD-20261013-ABCD1234", new BigDecimal("1"));

            when(orderRepository.findByOrderNumberForUpdate("ORD-20261013-ABCD1234"))
                    .thenReturn(Optional.of(pendingOrder));
            when(paymentRepository.findByOrder_OrderIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(processingPayment));

            assertThatThrownBy(() -> service.demoConfirm(request))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        }
    }
}
