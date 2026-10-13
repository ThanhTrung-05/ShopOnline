package com.example.banhangtructuyen.application.dto.order;

import com.example.banhangtructuyen.domain.model.Payment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ATS-27: Response returned by GET /api/v1/payments/qr/{orderId}.
 *
 * <p>Contains all information the frontend needs to display the QR code and
 * payment details. The {@code qrCode} field holds a mock VietQR-style string
 * (orderId|orderNumber|amount) — in production this would be replaced by the
 * actual QR payload from the Banking Gateway.
 *
 * <p>Frontend must NOT derive the total amount itself; it must use the {@code amount}
 * returned here, which originates from {@code Order.totalAmount} (ATS-26 calculation).
 */
@Schema(description = "QR code payment response (ATS-27)")
public record QrPaymentResponse(

        @Schema(description = "Internal payment ID", example = "555")
        Long paymentId,

        @Schema(description = "Internal order ID used for polling", example = "1001")
        Long orderId,

        @Schema(description = "Business-facing order number", example = "ORD-20261013-AB12CD34")
        String orderNumber,

        @Schema(description = "Grand total amount to be paid (VAT included, from backend — ATS-26)",
                example = "350000.00")
        BigDecimal amount,

        @Schema(description = "QR code content (mock VietQR format: orderId|orderNumber|amount)",
                example = "1001|ORD-20261013-AB12CD34|350000.00")
        String qrCode,

        @Schema(description = "Current payment status", example = "PROCESSING")
        Payment.PaymentStatus status,

        @Schema(description = "QR code expiry timestamp (15 minutes from generation)")
        Instant qrExpiresAt
) {}
