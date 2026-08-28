package com.example.banhangtructuyen.application.dto.order;

import com.example.banhangtructuyen.domain.model.OrderStatus;

import java.time.Instant;

public record OrderStatusResponse(
        String orderNumber,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
