package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.order.PaymentCallbackRequest;
import com.example.banhangtructuyen.application.service.OutboxEventPublisher;
import com.example.banhangtructuyen.application.service.PaymentService;
import com.example.banhangtructuyen.application.service.ProductService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ATS-29: Handles Banking Gateway payment callback with full idempotency and deduction guard.
 *
 * <p>Idempotency layers:
 * <ol>
 *   <li>If Payment already SUCCESS/FAILED with the same idempotencyKey → return immediately.</li>
 *   <li>ATS-29: If Order is already PAID → return immediately (prevents double-deduction when
 *       demoConfirm ran first and Banking Gateway callback arrives later with a different key).</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductService productService;
    private final OutboxEventPublisher outboxEventPublisher;

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processCallback(final PaymentCallbackRequest request) {
        final Order order = orderRepository.findByOrderNumberForUpdate(request.orderNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.orderNumber()));

        // ATS-29: Primary idempotency guard — if Order already confirmed/paid, do NOT deduct again.
        // This covers the cross-flow case: demoConfirm ran first (Order → PAID, inventory deducted),
        // then Banking Gateway callback arrives with a different idempotencyKey.
        if (order.getStatus() == OrderStatus.PAID) {
            log.warn("ATS-29: Callback received for already-PAID order={}. Returning without re-processing.",
                    request.orderNumber());
            return;
        }

        // ATS-29: Secondary idempotency guard — same idempotencyKey, already SUCCESS or FAILED
        final Optional<Payment> existingPaymentOpt = paymentRepository.findByOrderNumberForUpdate(request.orderNumber());
        if (existingPaymentOpt.isPresent()) {
            final Payment existingPayment = existingPaymentOpt.get();
            if (existingPayment.getIdempotencyKey().equals(request.idempotencyKey()) &&
                    (existingPayment.getStatus() == Payment.PaymentStatus.SUCCESS || existingPayment.getStatus() == Payment.PaymentStatus.FAILED)) {
                log.debug("ATS-29: Duplicate callback with same idempotencyKey={} for order={}. Skipping.",
                        request.idempotencyKey(), request.orderNumber());
                return;
            }
        }

        if (request.amount().compareTo(order.getTotalAmount()) != 0) {
            throw new IllegalArgumentException("Payment amount does not match order amount");
        }

        final Payment payment = existingPaymentOpt.orElseGet(() -> Payment.builder()
                .order(order)
                .amount(request.amount())
                .build());

        payment.setIdempotencyKey(request.idempotencyKey());
        payment.setTransactionRef(request.transactionRef());
        
        final boolean isSuccess = "SUCCESS".equalsIgnoreCase(request.status());
        payment.setStatus(isSuccess ? Payment.PaymentStatus.SUCCESS : Payment.PaymentStatus.FAILED);
        
        if (isSuccess) {
            payment.setPaidAt(Instant.now());
            order.setStatus(OrderStatus.PAID);
            
            // Deduct inventory
            final List<OrderItem> orderItems = orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(order.getOrderId());
            for (final OrderItem item : orderItems) {
                final Inventory inventory = inventoryRepository.findByProductIdWithLock(item.getProductId())
                        .orElseThrow(() -> new IllegalStateException("Inventory not found for product " + item.getProductId()));
                
                inventory.deduct(item.getQuantity());
                
                // Evict cache (ATS-17)
                productService.clearProductCaches(item.getProductId());
            }

            // Publish Outbox Event
            outboxEventPublisher.publishEvent("PAYMENT", order.getOrderNumber(), "banhang.payment.success",
                    Map.of("orderId", order.getOrderId(), "orderNumber", order.getOrderNumber(), "amount", payment.getAmount()));
        } else {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            // We could also release reserved inventory here, or wait for CancelOrder flow
            outboxEventPublisher.publishEvent("PAYMENT", order.getOrderNumber(), "banhang.payment.failed",
                    Map.of("orderId", order.getOrderId(), "orderNumber", order.getOrderNumber()));
        }

        paymentRepository.save(payment);
        orderRepository.save(order);
    }
}
