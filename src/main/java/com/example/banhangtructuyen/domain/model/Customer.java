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
 * Customer account entity mapped to CUSTOMERS table.
 * PASSWORD_HASH stores a BCrypt hash (60 chars) — never plaintext.
 */
@Entity
@Table(name = "CUSTOMERS")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CUSTOMER_ID", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "EMAIL", nullable = false, unique = true, length = 200)
    private String email;

    @Column(name = "FULL_NAME", nullable = false, length = 200)
    private String fullName;

    @Column(name = "PHONE", length = 20)
    private String phone;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "KEYCLOAK_USER_ID", length = 36, unique = true)
    private String keycloakUserId;

    @Column(name = "STATUS", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CustomerStatus status;

    @Column(name = "ROLE", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CustomerRole role;

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

    public enum CustomerStatus { ACTIVE, INACTIVE, BANNED }

    public enum CustomerRole { USER, ADMIN }
}
