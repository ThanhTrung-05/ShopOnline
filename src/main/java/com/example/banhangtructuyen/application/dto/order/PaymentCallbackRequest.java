package com.example.banhangtructuyen.application.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentCallbackRequest(
        @NotBlank(message = "Order number is required")
        String orderNumber,
        
        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey,
        
        @NotNull(message = "Amount is required")
        BigDecimal amount,
        
        @NotBlank(message = "Status is required")
        String status,
        
        String transactionRef
) {}
