package com.example.banhangtructuyen.application.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Request payload to create or update a category")
public record CategoryRequest(
        @Schema(description = "Display name of the category", example = "Thuc pham")
        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Category name must not exceed 100 characters")
        String categoryName,

        @Schema(description = "Unique business code for the category", example = "THUC_PHAM")
        @NotBlank(message = "Category code is required")
        @Size(max = 50, message = "Category code must not exceed 50 characters")
        String categoryCode,

        @Schema(description = "Category description (optional)")
        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @Schema(description = "VAT rate (%) applied to products in this category. Allowed values: 5 or 10", example = "5")
        @NotNull(message = "VAT rate is required")
        BigDecimal vatRate,

        @Schema(description = "Category status", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE"})
        @NotBlank(message = "Status is required")
        @Pattern(regexp = "ACTIVE|INACTIVE", message = "Status must be ACTIVE or INACTIVE")
        String status
) {}
