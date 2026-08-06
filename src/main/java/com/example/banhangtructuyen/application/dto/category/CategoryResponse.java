package com.example.banhangtructuyen.application.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Category information returned by the admin category API")
public record CategoryResponse(
        @Schema(description = "Unique category database ID", example = "1")
        Long categoryId,

        @Schema(description = "Display name of the category", example = "Thuc pham")
        String categoryName,

        @Schema(description = "Unique business code for the category", example = "THUC_PHAM")
        String categoryCode,

        @Schema(description = "Category description (nullable)")
        String description,

        @Schema(description = "VAT rate (%) applied to products in this category", example = "5")
        BigDecimal vatRate,

        @Schema(description = "Category status", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE"})
        String status
) {}
