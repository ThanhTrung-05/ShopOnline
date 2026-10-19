package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.service.OrderCancellationService;
import com.example.banhangtructuyen.application.service.OutboxEventPublisher;
import com.example.banhangtructuyen.application.service.ProductService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * ATS-30: Implementation of order cancellation and reserve release for expired orders.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Acquire PESSIMISTIC_WRITE lock on Order WHERE status = PENDING (atomic check-and-lock).</li>
 *   <li>If Order is not PENDING (already PAID or CANCELLED) return immediately (idempotent).</li>
 *   <li>Set Order CANCELLED.</li>
 *   <li>Set Payment EXPIRED.</li>
 *   <li>For each OrderItem: release(qty) on Inventory (PESSIMISTIC_WRITE lock per product).</li>
 *   <li>Clear product caches (ATS-17).</li>
 *   <li>Publish outbox event banhang.order.cancelled.</li>
 * </ol>
 *
 * <h3>Concurrency</h3>
 * <p>PESSIMISTIC_WRITE lock on Order ensures that:
 * <ul>
 *   <li>If payment confirm runs concurrently, it will block on the lock and then see CANCELLED
 *       status and reject the payment (Order not PENDING).</li>
 *   <li>If two scheduler workers race, only one acquires the lock; the second sees CANCELLED
 *       and skips no double-release.</li>
 * </ul>
 *
 * <h3>Inventory safety</h3>
 * <p>{@code Inventory.release()} uses {@code Math.max(0, reservedQuantity - amount)},
 * so it is safe to call even if reservedQuantity is already 0.
 * QUANTITY (physical stock) is NOT modified only RESERVED_QUANTITY decreases.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancellationServiceImpl implements OrderCancellationService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductService productService;
    private final OutboxEventPublisher outboxEventPublisher;

    /**
     * ATS-30: Cancel an expired pending order and release its reserved inventory.
     *
     * <p>Each call runs in its own SERIALIZABLE transaction so that the PESSIMISTIC_WRITE lock
     * on the Order races correctly against concurrent payment confirms.
     * If the transaction rolls back, the scheduler can safely retry on the next cron tick.
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void cancelExpiredOrder(final Long orderId, final Long paymentId) {

        // Step 1: Acquire PESSIMISTIC_WRITE lock only if Order is still PENDING.
        // If it was already PAID (payment arrived just before scheduler) or CANCELLED skip.
        final Order order = orderRepository
                .findByOrderIdAndStatusForUpdate(orderId, OrderStatus.PENDING)
                .orElse(null);

        if (order == null) {
            log.debug("ATS-30: Order {} is no longer PENDING skipping cancellation (already paid or cancelled).",
                    orderId);
            return;
        }

        // Step 2: Lock Payment and check it is still PROCESSING (guard against race condition).
        final Payment payment = paymentRepository.findByOrder_OrderIdForUpdate(orderId)
                .orElse(null);

        if (payment == null) {
            log.warn("ATS-30: Payment {} not found for order {}. Cancelling order only.", paymentId, orderId);
        } else if (payment.getStatus() != Payment.PaymentStatus.PROCESSING) {
            // Payment was already finalised (SUCCESS/FAILED/EXPIRED) skip inventory changes.
            log.debug("ATS-30: Payment {} for order {} is already in status {}. Skipping.",
                    paymentId, orderId, payment.getStatus());
            return;
        }

        // Step 3: Cancel Order and mark Payment EXPIRED.
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("ATS-30: Order {} cancelled (was PENDING, QR expired).", order.getOrderNumber());

        if (payment != null) {
            payment.setStatus(Payment.PaymentStatus.EXPIRED);
            paymentRepository.save(payment);
            log.info("ATS-30: Payment {} marked EXPIRED for order {}.", paymentId, order.getOrderNumber());
        }

        // Step 4: Release reserved inventory one lock per product.
        final List<OrderItem> orderItems =
                orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId);

        for (final OrderItem item : orderItems) {
            final Inventory inventory = inventoryRepository
                    .findByProductIdWithLock(item.getProductId())
                    .orElse(null);

            if (inventory == null) {
                log.warn("ATS-30: Inventory not found for productId={}. Cannot release reserved quantity.",
                        item.getProductId());
                continue;
            }

            final int before = inventory.getReservedQuantity();
            inventory.release(item.getQuantity());
            log.debug("ATS-30: Released {} units for productId={}. reservedQty: {} -> {}",
                    item.getQuantity(), item.getProductId(), before, inventory.getReservedQuantity());

            // ATS-17: Evict product cache so available quantity is reflected immediately.
            productService.clearProductCaches(item.getProductId());
        }

        // Step 5: Publish outbox event for downstream consumers.
        outboxEventPublisher.publishEvent(
                "ORDER",
                order.getOrderNumber(),
                "banhang.order.cancelled",
                Map.of(
                        "orderId", order.getOrderId(),
                        "orderNumber", order.getOrderNumber(),
                        "reason", "PAYMENT_EXPIRED"
                ));

        log.info("ATS-30: Completed cancellation of order {} {} item(s) reserve released.",
                order.getOrderNumber(), orderItems.size());
    }
}