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

/**
 * ATS-26: ORDER_ITEMS entity updated to persist VAT breakdown at order time.
 * vatRate and vatAmount are captured from Category.vatRate at the moment of order creation.
 * This ensures historical invoices remain accurate even if VAT rates change later.
 *
 * <p>Calculation (ATS-26):
 *   subtotal    = unitPrice * quantity                 (merchandise subtotal before VAT)
 *   vatAmount   = subtotal * vatRate / 100             (rounded HALF_UP, 0 decimal places)
 *   totalWithVat = subtotal + vatAmount
 */
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

    /** Unit price BEFORE VAT, captured from Product.price at order time. */
    @Column(name = "UNIT_PRICE", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    /** Subtotal before VAT = unitPrice * quantity. */
    @Column(name = "SUBTOTAL", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    /** VAT rate (%) captured from Category.vatRate at order time. E.g. 5.00 or 10.00. */
    @Column(name = "VAT_RATE", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    /** VAT amount = subtotal * vatRate / 100, rounded HALF_UP to 0 decimal places. */
    @Column(name = "VAT_AMOUNT", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal vatAmount;
}
