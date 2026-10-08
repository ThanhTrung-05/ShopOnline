package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.notification.NotificationResponse;
import com.example.banhangtructuyen.application.dto.notification.UnreadNotificationCountResponse;
import com.example.banhangtructuyen.application.service.AuthenticatedCustomerResolver;
import com.example.banhangtructuyen.application.service.NotificationService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Notification;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final AuthenticatedCustomerResolver authenticatedCustomerResolver;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void createOrderStatusChangedNotification(
            final Order order,
            final OrderStatus oldStatus,
            final OrderStatus newStatus) {
        final String message = "Đơn hàng " + order.getOrderNumber()
                + " đã chuyển trạng thái từ " + oldStatus + " sang " + newStatus + ".";

        notificationRepository.save(Notification.builder()
                .customerId(order.getCustomerId())
                .orderNumber(order.getOrderNumber())
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .message(message)
                .build());
    }

    @Override
    public List<NotificationResponse> getNotifications(final String keycloakSubject) {
        final Long customerId = resolveCustomer(keycloakSubject).getCustomerId();
        return notificationRepository
                .findAllByCustomerIdOrderByCreatedAtDescNotificationIdDesc(customerId)
                .stream()
                .map(NotificationServiceImpl::toResponse)
                .toList();
    }

    @Override
    public UnreadNotificationCountResponse getUnreadCount(final String keycloakSubject) {
        final Long customerId = resolveCustomer(keycloakSubject).getCustomerId();
        return new UnreadNotificationCountResponse(
                notificationRepository.countByCustomerIdAndReadAtIsNull(customerId));
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(
            final String keycloakSubject,
            final Long notificationId) {
        final Long customerId = resolveCustomer(keycloakSubject).getCustomerId();
        final Notification notification = notificationRepository
                .findByNotificationIdAndCustomerId(notificationId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));

        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }

        return toResponse(notification);
    }

    private Customer resolveCustomer(final String keycloakSubject) {
        return authenticatedCustomerResolver.resolveActiveCustomer(keycloakSubject);
    }

    private static NotificationResponse toResponse(final Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getOrderNumber(),
                notification.getOldStatus(),
                notification.getNewStatus(),
                notification.getMessage(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.getReadAt() != null
        );
    }
}
