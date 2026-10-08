package com.example.banhangtructuyen.application.dto.order;

import com.example.banhangtructuyen.domain.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull(message = "Order status is required")
        OrderStatus status
) {}
