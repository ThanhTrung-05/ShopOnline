package com.example.banhangtructuyen.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Order line item — maps to ORDER_ITEMS.
 * Product name is captured at order time (denormalized) so history is preserved
 * even if the product is later renamed or deleted.
 */
@Entity
@Table(name = "ORDER_ITEMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_ITEM_ID", nullable = false, updatable = false)
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ORDER_ID", nullable = false, updatable = false)
    private Order order;

    /** Snapshot of PRODUCTS.PRODUCT_ID at order time. */
    @Column(name = "PRODUCT_ID", nullable = false, updatable = false)
    private Long productId;

    /** Denormalized product name captured at order time. */
    @Column(name = "PRODUCT_NAME", nullable = false, length = 300, updatable = false)
    private String productName;

    @Column(name = "QUANTITY", nullable = false, updatable = false)
    private int quantity;

    /** Unit price at the time of ordering. */
    @Column(name = "UNIT_PRICE", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    /** = quantity × unitPrice, pre-computed for reporting performance. */
    @Column(name = "SUBTOTAL", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal subtotal;
}
