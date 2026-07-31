package com.example.banhangtructuyen.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Inventory entity — 1:1 with Product.
 * Uses PESSIMISTIC_WRITE lock (SELECT FOR UPDATE) during checkout.
 * VERSION field provides backup optimistic lock.
 *
 * CONSTRAINT: QUANTITY >= 0 enforced at both DB and application level.
 */
@Entity
@Table(name = "INVENTORY")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INVENTORY_ID", nullable = false, updatable = false)
    private Long inventoryId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PRODUCT_ID", nullable = false, unique = true)
    private Product product;

    /** Physical stock in warehouse. Must never go below 0. */
    @Column(name = "QUANTITY", nullable = false)
    private int quantity;

    /** Quantity reserved (in pending orders, not yet shipped). */
    @Column(name = "RESERVED_QUANTITY", nullable = false)
    private int reservedQuantity;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

    @LastModifiedDate
    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "UPDATED_BY", length = 100)
    private String updatedBy;

    /** Available stock = quantity - reservedQuantity */
    public int getAvailableQuantity() {
        return Math.max(0, quantity - reservedQuantity);
    }

    /**
     * Deduct stock. Throws IllegalStateException if result < 0.
     * Called inside @Transactional + PESSIMISTIC_WRITE lock.
     */
    public void deduct(final int amount) {
        if (quantity - amount < 0) {
            throw new IllegalStateException(
                "Cannot deduct " + amount + " from inventory " + inventoryId
                + ". Current quantity: " + quantity);
        }
        this.quantity -= amount;
        this.reservedQuantity = Math.max(0, this.reservedQuantity - amount);
    }

    public void reserve(final int amount) {
        if (getAvailableQuantity() < amount) {
            throw new IllegalStateException("Not enough available quantity to reserve");
        }
        this.reservedQuantity += amount;
    }

    public void release(final int amount) {
        this.reservedQuantity = Math.max(0, this.reservedQuantity - amount);
    }
}
