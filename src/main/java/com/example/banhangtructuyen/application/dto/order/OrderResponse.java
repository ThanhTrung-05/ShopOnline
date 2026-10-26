package com.example.banhangtructuyen.application.dto.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long orderId,
        String orderNumber,
        String status,
        BigDecimal totalAmount,
        String shippingAddress,
        String note,
        Instant createdAt,
        List<OrderItemResponse> items
) {}
