package com.example.banhangtructuyen.application.dto.inventory;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * ATS-18: Inventory alert item returned to Admin.
 * Represents a single product that is either OUT_OF_STOCK or LOW_STOCK.
 */
@Schema(description = "Inventory alert item for Admin dashboard")
public record InventoryAlertResponse(

        @Schema(description = "Product ID", example = "4")
        Long productId,

        @Schema(description = "Product name", example = "Bia Tiger lon 330ml")
        String productName,

        @Schema(description = "Product slug", example = "bia-tiger-lon-330ml")
        String productSlug,

        @Schema(description = "Category name", example = "Đồ uống")
        String categoryName,

        @Schema(description = "Unit price before VAT", example = "18000")
        BigDecimal price,

        @Schema(description = "Total physical stock in warehouse", example = "10")
        int quantity,

        @Schema(description = "Quantity reserved in pending orders", example = "10")
        int reservedQuantity,

        @Schema(description = "Available quantity = quantity - reservedQuantity", example = "0")
        int availableQuantity,

        @Schema(description = "Alert level: OUT_OF_STOCK or LOW_STOCK", example = "OUT_OF_STOCK")
        AlertLevel alertLevel
) {
    public enum AlertLevel { OUT_OF_STOCK, LOW_STOCK }
}
