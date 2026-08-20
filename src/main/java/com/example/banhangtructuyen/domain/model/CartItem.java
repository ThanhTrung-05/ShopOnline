package com.example.banhangtructuyen.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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

@Entity
@Table(
        name = "CART_ITEMS",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_CART_ITEMS_PRODUCT",
                columnNames = {"CART_ID", "PRODUCT_ID"}
        )
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CART_ITEM_ID", nullable = false, updatable = false)
    private Long cartItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CART_ID", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private Product product;

    @Column(name = "QUANTITY", nullable = false)
    private Integer quantity;

    @Column(name = "UNIT_PRICE", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @CreatedDate
    @Column(name = "ADDED_AT", nullable = false, updatable = false)
    private Instant addedAt;

    @LastModifiedDate
    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;
}
