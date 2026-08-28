package com.example.banhangtructuyen.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Order entity — maps to the partitioned ORDERS table.
 * Partitioning by CREATED_AT is transparent to JPA (Oracle handles routing).
 * Inventory is deducted atomically under PESSIMISTIC_WRITE lock before this record is saved.
 */
@Entity
@Table(name = "ORDERS")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_ID", nullable = false, updatable = false)
    private Long orderId;

    /** FK to CUSTOMERS — stored as plain Long to avoid lazy-load overhead. */
    @Column(name = "CUSTOMER_ID", nullable = false, updatable = false)
    private Long customerId;

    /** Human-readable order number: ORD-YYYYMMDD-{UUID8} */
    @Column(name = "ORDER_NUMBER", nullable = false, unique = true, length = 50, updatable = false)
    private String orderNumber;

    @Column(name = "STATUS", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "TOTAL_AMOUNT", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "SHIPPING_ADDRESS", nullable = false, length = 500)
    private String shippingAddress;

    @Column(name = "NOTE", length = 1000)
    private String note;

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "UPDATED_BY", length = 100)
    private String updatedBy;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    public enum OrderStatus {
        PENDING, CONFIRMED, PAID, PAYMENT_FAILED, SHIPPING, DELIVERED, CANCELLED, REFUNDED
    }
}
