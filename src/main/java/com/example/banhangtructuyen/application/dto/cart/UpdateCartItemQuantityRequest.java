package com.example.banhangtructuyen.application.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Update cart item quantity for the authenticated customer's cart")
public record UpdateCartItemQuantityRequest(
        @Schema(description = "Replacement quantity", example = "2")
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 1000, message = "Quantity must not exceed 1000")
        Integer quantity
) {}
