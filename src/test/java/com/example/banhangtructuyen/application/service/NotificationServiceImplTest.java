package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.notification.NotificationResponse;
import com.example.banhangtructuyen.application.dto.notification.UnreadNotificationCountResponse;
import com.example.banhangtructuyen.application.service.impl.NotificationServiceImpl;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Notification;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceImplTest {

    private static final String SUBJECT = "customer-subject";
    private static final Long CUSTOMER_ID = 10L;
    private static final Long NOTIFICATION_ID = 20L;
    private static final String ORDER_NUMBER = "ORD-20260908-NOTIFY";
    private static final Instant CREATED_AT = Instant.parse("2026-09-08T02:00:00Z");

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AuthenticatedCustomerResolver authenticatedCustomerResolver;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, authenticatedCustomerResolver);
    }

    @Test
    @DisplayName("creates an unread notification for the order owner")
    void createOrderStatusChangedNotification_shouldPersistUnreadNotification() {
        final Order order = Order.builder()
                .customerId(CUSTOMER_ID)
                .orderNumber(ORDER_NUMBER)
                .build();

        service.createOrderStatusChangedNotification(
                order, OrderStatus.PENDING, OrderStatus.CONFIRMED);

        final ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        final Notification notification = captor.getValue();
        assertThat(notification.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(notification.getOrderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(notification.getOldStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(notification.getNewStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(notification.getMessage()).contains(ORDER_NUMBER, "PENDING", "CONFIRMED");
        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    @DisplayName("lists only notifications scoped to the resolved JWT subject")
    void getNotifications_shouldUseResolvedCustomerId() {
        stubCustomer();
        when(notificationRepository.findAllByCustomerIdOrderByCreatedAtDescNotificationIdDesc(CUSTOMER_ID))
                .thenReturn(List.of(sampleNotification(null)));

        final List<NotificationResponse> result = service.getNotifications(SUBJECT);

        assertThat(result).singleElement().satisfies(notification -> {
            assertThat(notification.id()).isEqualTo(NOTIFICATION_ID);
            assertThat(notification.orderNumber()).isEqualTo(ORDER_NUMBER);
            assertThat(notification.read()).isFalse();
        });
        verify(authenticatedCustomerResolver).resolveActiveCustomer(SUBJECT);
        verify(notificationRepository)
                .findAllByCustomerIdOrderByCreatedAtDescNotificationIdDesc(CUSTOMER_ID);
    }

    @Test
    @DisplayName("counts unread notifications only for the resolved customer")
    void getUnreadCount_shouldUseResolvedCustomerId() {
        stubCustomer();
        when(notificationRepository.countByCustomerIdAndReadAtIsNull(CUSTOMER_ID)).thenReturn(3L);

        final UnreadNotificationCountResponse result = service.getUnreadCount(SUBJECT);

        assertThat(result.unreadCount()).isEqualTo(3L);
        verify(notificationRepository).countByCustomerIdAndReadAtIsNull(CUSTOMER_ID);
    }

    @Test
    @DisplayName("marks an owned notification read")
    void markAsRead_shouldUpdateOwnedNotification() {
        stubCustomer();
        final Notification notification = sampleNotification(null);
        when(notificationRepository.findByNotificationIdAndCustomerId(NOTIFICATION_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(notification));

        final NotificationResponse result = service.markAsRead(SUBJECT, NOTIFICATION_ID);

        assertThat(result.read()).isTrue();
        assertThat(result.readAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    @DisplayName("does not load or mutate another customer's notification")
    void markAsRead_shouldReturnNotFoundForAnotherCustomer() {
        stubCustomer();
        when(notificationRepository.findByNotificationIdAndCustomerId(NOTIFICATION_ID, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(SUBJECT, NOTIFICATION_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Notification not found with id: " + NOTIFICATION_ID);

        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any(Notification.class));
    }

    @Test
    @DisplayName("marking an already-read notification is idempotent")
    void markAsRead_shouldNotOverwriteExistingReadTimestamp() {
        stubCustomer();
        final Instant existingReadAt = Instant.parse("2026-09-08T03:00:00Z");
        final Notification notification = sampleNotification(existingReadAt);
        when(notificationRepository.findByNotificationIdAndCustomerId(NOTIFICATION_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(notification));

        final NotificationResponse result = service.markAsRead(SUBJECT, NOTIFICATION_ID);

        assertThat(result.readAt()).isEqualTo(existingReadAt);
        verify(notificationRepository, never()).save(notification);
    }

    private void stubCustomer() {
        when(authenticatedCustomerResolver.resolveActiveCustomer(SUBJECT))
                .thenReturn(Customer.builder().customerId(CUSTOMER_ID).build());
    }

    private static Notification sampleNotification(final Instant readAt) {
        return Notification.builder()
                .notificationId(NOTIFICATION_ID)
                .customerId(CUSTOMER_ID)
                .orderNumber(ORDER_NUMBER)
                .oldStatus(OrderStatus.PENDING)
                .newStatus(OrderStatus.CONFIRMED)
                .message("Order status changed")
                .createdAt(CREATED_AT)
                .readAt(readAt)
                .build();
    }
}
