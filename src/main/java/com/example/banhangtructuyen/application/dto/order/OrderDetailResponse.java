package com.example.banhangtructuyen.application.dto.order;

import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.model.Payment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * ATS-39/ATS-41: Extended with VAT breakdown, shipping address and payment status.
 * The success confirmation page uses this DTO to display full order information without
 * relying on transient frontend state (URL params or React state passed from ShippingPage).
 */
@Schema(description = "Full order detail for display in order status and success confirmation (ATS-39, ATS-41)")
public record OrderDetailResponse(
        @Schema(description = "Internal order ID", example = "1001")
        Long orderId,

        String orderNumber,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt,

        @Schema(description = "Merchandise subtotal before VAT", example = "36000")
        BigDecimal totalBeforeVat,

        @Schema(description = "Total VAT amount", example = "3600")
        BigDecimal totalVatAmount,

        @Schema(description = "Shipping fee", example = "20000")
        BigDecimal shippingFee,

        @Schema(description = "Grand total = totalBeforeVat + totalVatAmount + shippingFee", example = "59600")
        BigDecimal totalAmount,

        @Schema(description = "Shipping address snapshot captured at order creation time")
        String shippingAddress,

        @Schema(description = "Current payment status; null if no payment record exists yet")
        Payment.PaymentStatus paymentStatus,

        List<OrderItemResponse> items
) {}

