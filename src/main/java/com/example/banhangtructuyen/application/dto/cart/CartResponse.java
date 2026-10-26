package com.example.banhangtructuyen.application.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Current customer's cart with calculated subtotal")
public record CartResponse(
        List<CartViewItemResponse> items,
        BigDecimal subtotal,
        @Schema(description = "true when at least one item has an INACTIVE or DELETED product; "
                + "checkout will be rejected until those items are removed")
        boolean hasUnavailableItems
) {}
