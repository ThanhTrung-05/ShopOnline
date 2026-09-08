package com.example.banhangtructuyen.application.dto.order;

import com.example.banhangtructuyen.domain.model.OrderStatus;

import java.time.Instant;

public record OperationsOrderResponse(
        String orderNumber,
        OrderStatus status,
        Instant updatedAt
) {}
