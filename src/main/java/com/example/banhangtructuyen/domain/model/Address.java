package com.example.banhangtructuyen.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Customer delivery address entity mapped to ADDRESSES table.
 * Free-text fields only — no admin codes or external address API lookups.
 * At most one address per customer may have {@code defaultAddress = true},
 * enforced by a function-based unique index at the DB level (see V9 migration)
 * and by {@code AddressServiceImpl} at the application level.
 */
@Entity
@Table(name = "ADDRESSES")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ADDRESS_ID", nullable = false, updatable = false)
    private Long addressId;

    @Column(name = "CUSTOMER_ID", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "RECIPIENT_NAME", nullable = false, length = 200)
    private String recipientName;

    @Column(name = "PHONE", nullable = false, length = 20)
    private String phone;

    @Column(name = "LINE1", nullable = false, length = 255)
    private String line1;

    @Column(name = "WARD", length = 100)
    private String ward;

    @Column(name = "DISTRICT", length = 100)
    private String district;

    @Column(name = "PROVINCE", nullable = false, length = 100)
    private String province;

    @Column(name = "IS_DEFAULT", nullable = false)
    private boolean defaultAddress;

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
}
