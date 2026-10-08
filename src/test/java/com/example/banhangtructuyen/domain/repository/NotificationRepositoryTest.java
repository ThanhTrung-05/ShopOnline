package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.config.AuditingConfig;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Notification;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(AuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("NotificationRepository")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    @DisplayName("list, unread count, and id lookup remain customer scoped")
    void queries_shouldRemainCustomerScoped() {
        final Long customerId = createCustomer("notification-owner@example.com", "notification-owner-sub");
        final Long otherCustomerId = createCustomer("notification-other@example.com", "notification-other-sub");
        final Notification unread = notificationRepository.save(notification(customerId, "ORD-OWNER-1", null));
        notificationRepository.save(notification(
                customerId, "ORD-OWNER-2", Instant.parse("2026-09-08T04:00:00Z")));
        notificationRepository.save(notification(otherCustomerId, "ORD-OTHER-1", null));
        notificationRepository.flush();

        assertThat(notificationRepository
                .findAllByCustomerIdOrderByCreatedAtDescNotificationIdDesc(customerId))
                .hasSize(2)
                .allMatch(notification -> notification.getCustomerId().equals(customerId));
        assertThat(notificationRepository.countByCustomerIdAndReadAtIsNull(customerId)).isEqualTo(1L);
        assertThat(notificationRepository
                .findByNotificationIdAndCustomerId(unread.getNotificationId(), customerId)).isPresent();
        assertThat(notificationRepository
                .findByNotificationIdAndCustomerId(unread.getNotificationId(), otherCustomerId)).isEmpty();
    }

    private Long createCustomer(final String email, final String keycloakSubject) {
        return customerRepository.save(Customer.builder()
                .email(email)
                .fullName("Notification Customer")
                .keycloakUserId(keycloakSubject)
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.CustomerRole.USER)
                .build()).getCustomerId();
    }

    private static Notification notification(
            final Long customerId,
            final String orderNumber,
            final Instant readAt) {
        return Notification.builder()
                .customerId(customerId)
                .orderNumber(orderNumber)
                .oldStatus(OrderStatus.PENDING)
                .newStatus(OrderStatus.CONFIRMED)
                .message("Order status changed")
                .readAt(readAt)
                .build();
    }
}
