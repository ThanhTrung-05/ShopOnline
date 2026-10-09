package com.example.banhangtructuyen.application.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * ATS-26: OrderItem response with full VAT breakdown per line.
 * vatRate and vatAmount are captured at order creation time (immutable).
 */
@Schema(description = "Order line item with VAT breakdown (ATS-26)")
public record OrderItemResponse(
        @Schema(description = "Product ID", example = "4")
        Long productId,

        @Schema(description = "Product name at time of order", example = "Bia Tiger lon 330ml")
        String productName,

        @Schema(description = "Unit price before VAT", example = "18000")
        BigDecimal unitPrice,

        @Schema(description = "Quantity", example = "2")
        Integer quantity,

        @Schema(description = "Subtotal before VAT = unitPrice * quantity", example = "36000")
        BigDecimal subtotal,

        @Schema(description = "VAT rate (%) captured from category at order time", example = "10.00")
        BigDecimal vatRate,

        @Schema(description = "VAT amount = subtotal * vatRate / 100, rounded HALF_UP", example = "3600")
        BigDecimal vatAmount,

        @Schema(description = "Line total including VAT = subtotal + vatAmount", example = "39600")
        BigDecimal subtotalIncludingVat
) {
    /**
     * Convenience factory from domain model.
     */
    public static OrderItemResponse from(final com.example.banhangtructuyen.domain.model.OrderItem item) {
        final BigDecimal subtotalWithVat = item.getSubtotal().add(item.getVatAmount());
        return new OrderItemResponse(
                item.getProductId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getSubtotal(),
                item.getVatRate(),
                item.getVatAmount(),
                subtotalWithVat
        );
    }
}
