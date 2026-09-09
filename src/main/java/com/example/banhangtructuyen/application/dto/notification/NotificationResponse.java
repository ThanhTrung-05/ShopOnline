package com.example.banhangtructuyen.application.dto.notification;

import com.example.banhangtructuyen.domain.model.OrderStatus;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String orderNumber,
        OrderStatus oldStatus,
        OrderStatus newStatus,
        String message,
        Instant createdAt,
        Instant readAt,
        boolean read
) {}
