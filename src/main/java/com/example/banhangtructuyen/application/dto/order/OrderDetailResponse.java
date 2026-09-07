package com.example.banhangtructuyen.application.dto.order;

import com.example.banhangtructuyen.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDetailResponse(
        String orderNumber,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt,
        BigDecimal totalAmount,
        List<OrderItemResponse> items
) {}
