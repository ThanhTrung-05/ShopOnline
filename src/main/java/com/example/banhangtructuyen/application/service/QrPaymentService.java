package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.DemoConfirmRequest;
import com.example.banhangtructuyen.application.dto.order.PaymentStatusResponse;
import com.example.banhangtructuyen.application.dto.order.QrPaymentResponse;

/**
 * ATS-27 + ATS-28: QR payment lifecycle service.
 *
 * <p>Covers:
 * <ul>
 *   <li>Generating (or reusing) a QR code for a PENDING order (ATS-27).</li>
 *   <li>Polling the current payment status, served cache-first (ATS-27).</li>
 *   <li>Simulating a successful payment confirmation for demo/testing (ATS-28).</li>
 * </ul>
 *
 * <p>All total-amount values are sourced exclusively from the backend
 * ({@code Order.totalAmount}) to prevent frontend amount tampering.
 */
public interface QrPaymentService {

    /**
     * ATS-27: Generate or reuse a QR code for the given order.
     *
     * <p>Validates:
     * <ul>
     *   <li>Order exists and belongs to the authenticated customer.</li>
     *   <li>Order status is PENDING (payment not yet initiated or expired).</li>
     * </ul>
     * If a PROCESSING Payment with a non-expired QR already exists, returns it
     * without creating a duplicate (idempotent).
     *
     * @param orderId    internal order PK
     * @param customerId authenticated customer's PK
     * @return QR code and payment metadata
     */
    QrPaymentResponse generateQr(Long orderId, Long customerId);

    /**
     * ATS-27: Return the current payment status for the given order (cache-first).
     *
     * @param orderId    internal order PK
     * @param customerId authenticated customer's PK (ownership check)
     * @return status response
     */
    PaymentStatusResponse getPaymentStatus(Long orderId, Long customerId);

    /**
     * ATS-28: Simulate a successful QR scan and payment confirmation.
     *
     * <p>Only available when {@code app.payment.demo-mode=true} and caller
     * has ADMIN role. Throws {@link UnsupportedOperationException} otherwise.
     *
     * @param request order number + claimed amount (validated against backend)
     */
    void demoConfirm(DemoConfirmRequest request);
}
