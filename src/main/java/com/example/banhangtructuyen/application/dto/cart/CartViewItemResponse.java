package com.example.banhangtructuyen.application.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Cart item in the current cart view")
public record CartViewItemResponse(
        Long cartItemId,
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal itemSubtotal
) {}
