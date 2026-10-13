package com.example.banhangtructuyen.application.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * ATS-28: Request body for POST /api/v1/payments/demo/confirm.
 *
 * <p>Used exclusively for demo/testing purposes to simulate a bank QR scan.
 * The {@code orderNumber} and {@code amount} are cross-checked against the
 * backend's stored order data — the backend does NOT trust these values blindly.
 *
 * <p>This endpoint is protected by:
 * <ul>
 *   <li>ADMIN role (Spring Security)</li>
 *   <li>{@code app.payment.demo-mode=true} flag (must be explicitly enabled)</li>
 * </ul>
 */
@Schema(description = "Simulated payment confirmation request — demo/testing only (ATS-28)")
public record DemoConfirmRequest(

        @Schema(description = "Business order number to confirm payment for",
                example = "ORD-20261013-AB12CD34")
        @NotBlank(message = "Order number is required")
        String orderNumber,

        @Schema(description = "Amount claimed to have been paid (validated against backend order total)",
                example = "350000.00")
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount
) {}
