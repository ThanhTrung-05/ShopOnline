package com.example.banhangtructuyen.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "ORDER_ITEMS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_ITEM_ID", nullable = false, updatable = false)
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ORDER_ID", nullable = false, updatable = false)
    private Order order;

    @Column(name = "PRODUCT_ID", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "PRODUCT_NAME", nullable = false, updatable = false, length = 300)
    private String productName;

    @Column(name = "QUANTITY", nullable = false, updatable = false)
    private Integer quantity;

    @Column(name = "UNIT_PRICE", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "SUBTOTAL", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;
}
