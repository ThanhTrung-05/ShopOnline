package com.example.banhangtructuyen.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Minimal read-side mapping of the existing Oracle {@code ORDERS} table.
 */
@Entity
@Table(name = "ORDERS")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_ID", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "CUSTOMER_ID", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "ORDER_NUMBER", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "TOTAL_AMOUNT", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "SHIPPING_ADDRESS", nullable = false, length = 500)
    private String shippingAddress;

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;
}
