package com.example.banhangtructuyen.application.dto.order;

import com.example.banhangtructuyen.domain.model.Payment;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ATS-27: Lightweight response for GET /api/v1/payments/{orderId}/status.
 *
 * <p>Used by the frontend polling loop (every 3 seconds) to check whether the
 * payment has been confirmed or failed. Served cache-first from Redis.
 */
@Schema(description = "Payment status polling response (ATS-27)")
public record PaymentStatusResponse(

        @Schema(description = "Internal order ID", example = "1001")
        Long orderId,

        @Schema(description = "Current payment status: PENDING | PROCESSING | SUCCESS | FAILED | EXPIRED",
                example = "PROCESSING")
        Payment.PaymentStatus status
) {}
