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

/**
 * Product entity mapped to PRODUCTS table.
 * Slug is the URL-friendly unique identifier.
 * @Version for optimistic locking on product updates.
 */
@Entity
@Table(name = "PRODUCTS")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRODUCT_ID", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "PRODUCT_SLUG", nullable = false, unique = true, length = 200)
    private String productSlug;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CATEGORY_ID", nullable = false)
    private Category category;

    @Column(name = "PRODUCT_NAME", nullable = false, length = 300)
    private String productName;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    /** Monetary amount — NUMBER(19,2) in Oracle. Never use float/double. */
    @Column(name = "PRICE", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "IMAGE_URL", length = 500)
    private String imageUrl;

    @Column(name = "STATUS", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ProductStatus status;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

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

    /** Lazy-loaded inventory — use JOIN FETCH in repository when needed. */
    @OneToOne(mappedBy = "product", fetch = FetchType.LAZY)
    private Inventory inventory;

    public enum ProductStatus { ACTIVE, INACTIVE, DELETED }
}
