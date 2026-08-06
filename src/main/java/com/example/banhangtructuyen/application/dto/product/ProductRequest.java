package com.example.banhangtructuyen.application.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Request payload to create or update a product")
public record ProductRequest(
        @Schema(description = "Display name of the product", example = "Gạo ST25 5kg")
        @NotBlank(message = "Product name is required")
        @Size(max = 300, message = "Product name must not exceed 300 characters")
        String productName,

        @Schema(description = "Unique URL-friendly slug", example = "gao-st25-5kg")
        @NotBlank(message = "Product slug is required")
        @Size(max = 200, message = "Product slug must not exceed 200 characters")
        String productSlug,

        @Schema(description = "ID of the parent category", example = "1")
        @NotNull(message = "Category is required")
        @Min(value = 1, message = "Category ID must be at least 1")
        Long categoryId,

        @Schema(description = "Product description (optional)")
        String description,

        @Schema(description = "Unit price in VND", example = "180000")
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        BigDecimal price,

        @Schema(description = "Product image URL (optional)", example = "https://cdn.example.com/images/gao-st25.jpg")
        @Size(max = 500, message = "Image URL must not exceed 500 characters")
        String imageUrl,

        @Schema(description = "Product status", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE", "DELETED"})
        @NotBlank(message = "Status is required")
        @Pattern(regexp = "ACTIVE|INACTIVE|DELETED", message = "Status must be ACTIVE, INACTIVE or DELETED")
        String status,

        @Schema(description = "Initial stock quantity (used only on creation)", example = "100")
        @NotNull(message = "Initial quantity is required")
        @Min(value = 0, message = "Initial quantity must not be negative")
        Integer initialQuantity
) {}
