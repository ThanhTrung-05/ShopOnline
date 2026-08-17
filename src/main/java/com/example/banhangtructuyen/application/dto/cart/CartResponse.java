package com.example.banhangtructuyen.application.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Current customer's cart with calculated subtotal")
public record CartResponse(
        List<CartViewItemResponse> items,
        BigDecimal subtotal
) {}
