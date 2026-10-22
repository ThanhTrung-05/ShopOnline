package com.example.banhangtructuyen.application.dto.order;

import com.example.banhangtructuyen.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ATS-26: Updated response for POST /api/v1/orders.
 * Now includes the full VAT breakdown so the frontend displays
 * the correct pre-VAT, VAT and post-VAT totals from the backend.
 * Frontend must NOT calculate totals itself.
 *
 * <p>ATS-39: Added orderId — required by the frontend to call the QR generation API
 * (GET /api/v1/payments/qr/{orderId}) immediately after order creation.
 */
@Schema(description = "Order creation response with full VAT breakdown (ATS-26, ATS-39)")
public record CreateOrderResponse(

        @Schema(description = "Internal order ID (used by QR payment API)", example = "1001")
        Long orderId,

        @Schema(description = "Business-facing order number", example = "ORD-20261009-AB12CD34")
        String orderNumber,

        @Schema(description = "Order status at creation", example = "PENDING")
        OrderStatus status,

        @Schema(description = "Merchandise subtotal before VAT (sum of unitPrice * qty per line)", example = "36000")
        BigDecimal totalBeforeVat,

        @Schema(description = "Total VAT amount across all line items", example = "3600")
        BigDecimal totalVatAmount,

        @Schema(description = "Shipping fee", example = "20000")
        BigDecimal shippingFee,

        @Schema(description = "Grand total = totalBeforeVat + totalVatAmount + shippingFee", example = "59600")
        BigDecimal totalAmount,

        @Schema(description = "Order creation timestamp")
        Instant createdAt
) {}
