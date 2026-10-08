package com.example.banhangtructuyen.application.dto.order;

import com.example.banhangtructuyen.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateOrderResponse(
        String orderNumber,
        OrderStatus status,
        BigDecimal totalAmount,
        BigDecimal shippingFee,
        Instant createdAt
) {}
