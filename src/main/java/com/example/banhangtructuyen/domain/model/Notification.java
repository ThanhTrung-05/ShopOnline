package com.example.banhangtructuyen.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "NOTIFICATIONS")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NOTIFICATION_ID", nullable = false, updatable = false)
    private Long notificationId;

    @Column(name = "CUSTOMER_ID", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "ORDER_NUMBER", nullable = false, updatable = false, length = 50)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "OLD_STATUS", nullable = false, updatable = false, length = 30)
    private OrderStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "NEW_STATUS", nullable = false, updatable = false, length = 30)
    private OrderStatus newStatus;

    @Column(name = "MESSAGE", nullable = false, updatable = false, length = 500)
    private String message;

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "READ_AT")
    private Instant readAt;
}
