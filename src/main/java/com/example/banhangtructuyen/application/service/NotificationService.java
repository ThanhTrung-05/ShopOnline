package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.notification.NotificationResponse;
import com.example.banhangtructuyen.application.dto.notification.UnreadNotificationCountResponse;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderStatus;

import java.util.List;

public interface NotificationService {

    void createOrderStatusChangedNotification(
            Order order,
            OrderStatus oldStatus,
            OrderStatus newStatus);

    List<NotificationResponse> getNotifications(String keycloakSubject);

    UnreadNotificationCountResponse getUnreadCount(String keycloakSubject);

    NotificationResponse markAsRead(String keycloakSubject, Long notificationId);
}
