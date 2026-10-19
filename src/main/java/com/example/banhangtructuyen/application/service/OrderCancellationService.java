package com.example.banhangtructuyen.application.service;

/**
 * ATS-30: Service for cancelling an expired pending order and releasing its reserved inventory.
 *
 * <p>Called by {@code PaymentExpiryScheduler} when a Payment QR code has expired
 * and the associated Order is still PENDING (customer did not pay in time).
 *
 * <p>Concurrency contract: implementations MUST acquire a PESSIMISTIC_WRITE lock on the Order
 * before mutating state, so that a concurrent payment confirmation will block and then see
 * the CANCELLED status, preventing double-processing.
 */
public interface OrderCancellationService {

    /**
     * ATS-30: Cancel the order identified by {@code orderId} and release all reserved inventory.
     *
     * <p>Business rules:
     * <ul>
     *   <li>Only cancels if Order is still PENDING — idempotent if already PAID or CANCELLED.</li>
     *   <li>Releases exactly the reserved quantity from each OrderItem.</li>
     *   <li>Updates Payment status to EXPIRED.</li>
     *   <li>Clears product caches (ATS-17).</li>
     *   <li>Publishes {@code banhang.order.cancelled} outbox event.</li>
     * </ul>
     *
     * @param orderId   the ID of the Order to cancel
     * @param paymentId the ID of the associated Payment to mark as EXPIRED
     */
    void cancelExpiredOrder(Long orderId, Long paymentId);
}