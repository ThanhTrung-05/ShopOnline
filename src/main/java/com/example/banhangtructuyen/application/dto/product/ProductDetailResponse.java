package com.example.banhangtructuyen.application.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Detailed product response DTO used exclusively by GET /api/v1/products/{id}.
 * Extends the basic product information with VAT breakdown.
 * All monetary values are in VND (Vietnamese Dong).
 *
 * VAT calculation:
 *   vatAmount         = price * vatRate / 100
 *   priceIncludingVat = price + vatAmount
 */
@Schema(description = "Full product detail including VAT breakdown, returned by GET /api/v1/products/{id}")
public record ProductDetailResponse(

        @Schema(description = "Unique product database ID", example = "1")
        Long id,

        @Schema(description = "Display name of the product", example = "Sua tuoi Vinamilk 1L")
        String name,

        @Schema(description = "URL-friendly unique slug identifier", example = "sua-tuoi-vinamilk-1l")
        String slug,

        @Schema(description = "Unit price BEFORE VAT in VND", example = "35000")
        BigDecimal price,

        @Schema(description = "VAT rate (%) applicable to this product's category. E.g. 5 or 10", example = "5")
        BigDecimal vatRate,

        @Schema(description = "VAT amount = price * vatRate / 100 (rounded half-up to 0 decimal places)", example = "1750")
        BigDecimal vatAmount,

        @Schema(description = "Total price including VAT = price + vatAmount", example = "36750")
        BigDecimal priceIncludingVat,

        @Schema(description = "Product image URL (nullable)", example = "https://cdn.example.com/images/vinamilk-1l.jpg")
        String imageUrl,

        @Schema(description = "Full product description (nullable, HTML allowed)")
        String description,

        @Schema(description = "ID of the parent category", example = "2")
        Long categoryId,

        @Schema(description = "Display name of the parent category", example = "Sua & San pham tu sua")
        String categoryName,

        @Schema(description = "Available stock (QUANTITY - RESERVED_QUANTITY). Returns 0 when out of stock.", example = "150")
        int inventoryCount,

        @Schema(description = "Product lifecycle status", example = "ACTIVE",
                allowableValues = {"ACTIVE", "INACTIVE", "DELETED"})
        String status
) {}
