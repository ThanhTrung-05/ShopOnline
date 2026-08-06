

package com.example.banhangtructuyen.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Product category with self-referencing parent hierarchy.
 * VAT_RATE: tax rate (%) applied to all products in this category.
 */
@Entity
@Table(name = "CATEGORIES")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CATEGORY_ID", nullable = false, updatable = false)
    private Long categoryId;

    @Column(name = "CATEGORY_CODE", nullable = false, unique = true, length = 50)
    private String categoryCode;

    @Column(name = "CATEGORY_NAME", nullable = false, unique = true, length = 100)
    private String categoryName;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** VAT rate (%) for all products in this category. E.g. 5.00 or 10.00 */
    @Column(name = "VAT_RATE", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARENT_ID")
    private Category parent;

    @Column(name = "SORT_ORDER", nullable = false)
    private int sortOrder;

    @Column(name = "STATUS", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CategoryStatus status;

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private List<Product> products;

    public enum CategoryStatus { ACTIVE, INACTIVE }
}
