package com.example.banhangtructuyen.application.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Response DTO for a single product — used in both list and detail views.
 * All monetary values are in VND (Vietnamese Dong).
 */
@Schema(description = "Product information returned by the catalog API")
public record ProductResponse(
        @Schema(description = "Unique product database ID", example = "1")
        Long id,

        @Schema(description = "Display name of the product", example = "Gạo ST25 5kg")
        String name,

        @Schema(description = "URL-friendly unique slug identifier", example = "TP001")
        String slug,

        @Schema(description = "Unit price in VND (Vietnamese Dong)", example = "180000")
        BigDecimal price,

        @Schema(description = "Product image URL (nullable)", example = "https://cdn.example.com/images/tp001.jpg")
        String imageUrl,

        @Schema(description = "HTML product description (nullable)")
        String description,

        @Schema(description = "ID of the parent category", example = "1")
        Long categoryId,

        @Schema(description = "Display name of the parent category", example = "Thực phẩm")
        String categoryName,

        @Schema(description = "Available stock quantity (QUANTITY - RESERVED_QUANTITY). Returns 0 when out of stock.", example = "100")
        int inventoryCount,

        @Schema(description = "Product lifecycle status", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE", "DELETED"})
        String status
) {}

