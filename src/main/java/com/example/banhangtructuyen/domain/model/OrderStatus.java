package com.example.banhangtructuyen.domain.model;

/**
 * Order lifecycle values stored in {@code ORDERS.STATUS}.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PAID,
    PAYMENT_FAILED,
    SHIPPING,
    DELIVERED,
    CANCELLED,
    REFUNDED
}
