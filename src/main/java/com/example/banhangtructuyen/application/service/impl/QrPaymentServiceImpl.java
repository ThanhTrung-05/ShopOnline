package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.order.DemoConfirmRequest;
import com.example.banhangtructuyen.application.dto.order.PaymentStatusResponse;
import com.example.banhangtructuyen.application.dto.order.QrPaymentResponse;
import com.example.banhangtructuyen.application.service.OutboxEventPublisher;
import com.example.banhangtructuyen.application.service.ProductService;
import com.example.banhangtructuyen.application.service.QrPaymentService;
import com.example.banhangtructuyen.config.AppProperties;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * ATS-27 + ATS-28: QR payment service implementation.
 *
 * <h3>ATS-27 — QR generation flow</h3>
 * <ol>
 *   <li>Validate Order exists and belongs to {@code customerId}.</li>
 *   <li>Validate Order.status == PENDING (only PENDING orders can initiate payment).</li>
 *   <li>Check existing Payment: if PROCESSING and QR not expired → return existing QR (idempotent).</li>
 *   <li>Create or refresh Payment: idempotencyKey, qrCode (mock), qrExpiresAt = now + 15 min, status = PROCESSING.</li>
 *   <li>Cache payment status in Redis (TTL = statusCacheTtlSeconds).</li>
 *   <li>Return QrPaymentResponse.</li>
 * </ol>
 *
 * <h3>ATS-28 — Demo confirm flow (ADMIN-only, demo-mode guard)</h3>
 * <ol>
 *   <li>Check app.payment.demo-mode == true; throw UnsupportedOperationException otherwise.</li>
 *   <li>Lock Order + Payment (PESSIMISTIC_WRITE, SERIALIZABLE).</li>
 *   <li>Validate Payment.status == PROCESSING (idempotency: SUCCESS/FAILED → return).</li>
 *   <li>Validate request.amount == order.totalAmount.</li>
 *   <li>Set Payment: SUCCESS, paidAt.</li>
 *   <li>Set Order: PAID.</li>
 *   <li>ATS-16: deduct inventory (PESSIMISTIC_WRITE per product).</li>
 *   <li>ATS-17: clearProductCaches.</li>
 *   <li>Publish Outbox event.</li>
 *   <li>Update Redis cache to SUCCESS.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrPaymentServiceImpl implements QrPaymentService {

    private static final String REDIS_PAYMENT_STATUS_KEY = "payment:status:";

    private final AppProperties appProperties;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductService productService;
    private final OutboxEventPublisher outboxEventPublisher;
    private final StringRedisTemplate redisTemplate;

    // -------------------------------------------------------------------------
    // ATS-27: Generate QR
    // -------------------------------------------------------------------------

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public QrPaymentResponse generateQr(final Long orderId, final Long customerId) {
        // Step 1: Validate ownership
        final Order order = orderRepository.findByOrderIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId.toString()));

        // Step 2: Validate Order is in PENDING status
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Đơn hàng không ở trạng thái chờ thanh toán (status=" + order.getStatus() + ")");
        }

        // Step 3: Check existing Payment — idempotency (reuse if PROCESSING and QR not expired)
        final Optional<Payment> existingOpt = paymentRepository.findByOrder_OrderId(orderId);
        if (existingOpt.isPresent()) {
            final Payment existing = existingOpt.get();
            final boolean isProcessing = existing.getStatus() == Payment.PaymentStatus.PROCESSING;
            final boolean qrValid = existing.getQrExpiresAt() != null
                    && existing.getQrExpiresAt().isAfter(Instant.now());
            if (isProcessing && qrValid) {
                log.debug("ATS-27: Reusing existing QR for orderId={}", orderId);
                return toQrResponse(existing, order);
            }
        }

        // Step 4: Create or refresh Payment
        final AppProperties.Payment cfg = appProperties.getPayment();
        final Instant qrExpiresAt = Instant.now().plus(cfg.getQrExpiryMinutes(), ChronoUnit.MINUTES);
        final String idempotencyKey = "PAY-" + orderId + "-" + UUID.randomUUID();
        final String qrCode = buildMockQrCode(order);

        final Payment payment = existingOpt.orElseGet(() -> Payment.builder()
                .order(order)
                .amount(order.getTotalAmount())
                .build());

        payment.setIdempotencyKey(idempotencyKey);
        payment.setQrCode(qrCode);
        payment.setQrExpiresAt(qrExpiresAt);
        payment.setStatus(Payment.PaymentStatus.PROCESSING);
        final Payment saved = paymentRepository.save(payment);

        // Step 5: Cache status in Redis
        cachePaymentStatus(orderId, Payment.PaymentStatus.PROCESSING, cfg.getStatusCacheTtlSeconds());

        log.info("ATS-27: QR generated for orderId={} orderNumber={}", orderId, order.getOrderNumber());
        return toQrResponse(saved, order);
    }

    // -------------------------------------------------------------------------
    // ATS-27: Polling — status check (cache-first)
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(final Long orderId, final Long customerId) {
        // Ownership check — lightweight, no lock needed
        orderRepository.findByOrderIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId.toString()));

        // Cache-first
        final String cached = redisTemplate.opsForValue().get(REDIS_PAYMENT_STATUS_KEY + orderId);
        if (cached != null) {
            log.debug("ATS-27: Cache HIT for payment status orderId={}", orderId);
            return new PaymentStatusResponse(orderId, Payment.PaymentStatus.valueOf(cached));
        }

        // DB fallback
        log.debug("ATS-27: Cache MISS for payment status orderId={}, querying DB", orderId);
        final Payment.PaymentStatus status = paymentRepository.findByOrder_OrderId(orderId)
                .map(Payment::getStatus)
                .orElse(Payment.PaymentStatus.PENDING);

        return new PaymentStatusResponse(orderId, status);
    }

    // -------------------------------------------------------------------------
    // ATS-28: Demo confirmation (ADMIN-only, demo-mode flag guard)
    // -------------------------------------------------------------------------

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void demoConfirm(final DemoConfirmRequest request) {
        // Guard: demo-mode must be explicitly enabled
        if (!appProperties.getPayment().isDemoMode()) {
            throw new UnsupportedOperationException(
                    "Simulated payment confirmation is disabled in this environment");
        }

        // Lock Order
        final Order order = orderRepository.findByOrderNumberForUpdate(request.orderNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.orderNumber()));

        // Lock Payment
        final Payment payment = paymentRepository.findByOrder_OrderIdForUpdate(order.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order", request.orderNumber()));

        // ATS-28: Idempotency — already processed
        if (payment.getStatus() == Payment.PaymentStatus.SUCCESS
                || payment.getStatus() == Payment.PaymentStatus.FAILED) {
            log.warn("ATS-28: Demo confirm called on already-processed payment id={} status={}",
                    payment.getPaymentId(), payment.getStatus());
            return;
        }

        // ATS-28: Must be in PROCESSING state
        if (payment.getStatus() != Payment.PaymentStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Payment không ở trạng thái PROCESSING (status=" + payment.getStatus() + ")");
        }

        // ATS-28: Amount validation — backend is the source of truth
        if (request.amount().compareTo(order.getTotalAmount()) != 0) {
            log.warn("ATS-28: Amount mismatch for order={}: expected={} got={}",
                    request.orderNumber(), order.getTotalAmount(), request.amount());
            throw new IllegalArgumentException(
                    "Số tiền không khớp với tổng đơn hàng (expected=" + order.getTotalAmount() + ")");
        }

        // Update Payment → SUCCESS
        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        payment.setPaidAt(Instant.now());
        payment.setTransactionRef("DEMO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        paymentRepository.save(payment);

        // Update Order → PAID
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        // ATS-16: Deduct inventory (same as PaymentServiceImpl.processCallback)
        final List<OrderItem> orderItems = orderItemRepository
                .findAllByOrder_OrderIdOrderByOrderItemIdAsc(order.getOrderId());
        for (final OrderItem item : orderItems) {
            final Inventory inventory = inventoryRepository.findByProductIdWithLock(item.getProductId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Inventory not found for product " + item.getProductId()));
            inventory.deduct(item.getQuantity());
            // ATS-17: Evict product cache
            productService.clearProductCaches(item.getProductId());
        }

        // Publish outbox event
        outboxEventPublisher.publishEvent(
                "PAYMENT",
                order.getOrderNumber(),
                "banhang.payment.success",
                Map.of(
                        "orderId", order.getOrderId(),
                        "orderNumber", order.getOrderNumber(),
                        "amount", payment.getAmount(),
                        "demo", true
                ));

        // Update Redis cache → SUCCESS
        cachePaymentStatus(order.getOrderId(), Payment.PaymentStatus.SUCCESS,
                appProperties.getPayment().getStatusCacheTtlSeconds());

        log.info("ATS-28: Demo confirm SUCCESS for orderNumber={}", request.orderNumber());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * ATS-27: Builds a mock QR string for demo purposes.
     * Format: orderId|orderNumber|amount
     * In production this would call the Banking Gateway API.
     */
    private static String buildMockQrCode(final Order order) {
        return order.getOrderId() + "|" + order.getOrderNumber() + "|" + order.getTotalAmount().toPlainString();
    }

    private static QrPaymentResponse toQrResponse(final Payment payment, final Order order) {
        return new QrPaymentResponse(
                payment.getPaymentId(),
                order.getOrderId(),
                order.getOrderNumber(),
                payment.getAmount(),
                payment.getQrCode(),
                payment.getStatus(),
                payment.getQrExpiresAt()
        );
    }

    private void cachePaymentStatus(final Long orderId, final Payment.PaymentStatus status, final int ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(
                    REDIS_PAYMENT_STATUS_KEY + orderId,
                    status.name(),
                    ttlSeconds,
                    TimeUnit.SECONDS);
        } catch (final Exception e) {
            // Redis failure must NOT break the payment flow — log and continue
            log.warn("ATS-27: Failed to cache payment status for orderId={}: {}", orderId, e.getMessage());
        }
    }
}
