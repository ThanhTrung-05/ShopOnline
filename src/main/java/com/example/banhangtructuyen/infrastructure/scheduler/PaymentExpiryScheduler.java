package com.example.banhangtructuyen.infrastructure.scheduler;

import com.example.banhangtructuyen.application.service.OrderCancellationService;
import com.example.banhangtructuyen.domain.model.Payment;
import com.example.banhangtructuyen.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * ATS-30: Scheduler that periodically checks for expired QR payments and auto-cancels
 * the associated PENDING orders, releasing their reserved inventory.
 *
 * <h3>Design decisions</h3>
 * <ul>
 *   <li>Uses Spring {@code @Scheduled} no additional infrastructure (no message broker,
 *       no separate worker process). Fits the Spring Boot Monolith architecture.</li>
 *   <li>Each order cancellation runs in its own transaction (via {@code OrderCancellationService}),
 *       so a failure on one order does not roll back the others.</li>
 *   <li>Cron expression is configurable via {@code app.scheduler.payment-expiry-cron} in yml.
 *       Default: every 1 minute.</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * <p>The scheduler can run multiple times safely:
 * <ul>
 *   <li>{@code findExpiredProcessingPayments} only returns PROCESSING payments with an expired QR
 *       AND an associated PENDING order so already-cancelled or paid orders are excluded
 *       at the query level.</li>
 *   <li>{@code OrderCancellationService.cancelExpiredOrder} additionally acquires a
 *       PESSIMISTIC_WRITE lock inside its own transaction, so even if two scheduler instances
 *       race, only one will cancel the order.</li>
 * </ul>
 *
 * <h3>Race condition Payment vs Timeout</h3>
 * <p>If a customer confirms payment at the exact moment the scheduler fires:
 * <ul>
 *   <li>Payment confirm locks Order first sees PAID scheduler skips.</li>
 *   <li>Scheduler locks Order first sets CANCELLED payment confirm sees non-PENDING rejects.</li>
 *   <li>Only one outcome persists. SERIALIZABLE isolation ensures atomicity.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryScheduler {

    private final PaymentRepository paymentRepository;
    private final OrderCancellationService orderCancellationService;

    /**
     * ATS-30: Main scheduled job. Runs according to {@code app.scheduler.payment-expiry-cron}.
     *
     * <p>Does NOT run inside a transaction itself each individual cancellation has its own
     * transaction so a single failure does not affect the rest of the batch.
     */
    @Scheduled(cron = "${app.scheduler.payment-expiry-cron:0 */1 * * * *}")
    public void cancelExpiredOrders() {
        final Instant now = Instant.now();
        log.debug("ATS-30: PaymentExpiryScheduler triggered at {}", now);

        final List<Payment> expiredPayments = paymentRepository.findExpiredProcessingPayments(now);

        if (expiredPayments.isEmpty()) {
            log.debug("ATS-30: No expired pending orders found.");
            return;
        }

        log.info("ATS-30: Found {} expired payment(s) to process.", expiredPayments.size());

        int successCount = 0;
        int errorCount = 0;

        for (final Payment payment : expiredPayments) {
            final Long orderId = payment.getOrder().getOrderId();
            final Long paymentId = payment.getPaymentId();
            try {
                orderCancellationService.cancelExpiredOrder(orderId, paymentId);
                successCount++;
            } catch (final Exception e) {
                // ATS-30: Do NOT let one order failure break the entire batch.
                // The scheduler will retry on the next cron tick.
                errorCount++;
                log.error("ATS-30: Failed to cancel expired order {} (paymentId={}): {}",
                        orderId, paymentId, e.getMessage(), e);
            }
        }

        log.info("ATS-30: Expiry batch complete cancelled={}, errors={}", successCount, errorCount);
    }
}