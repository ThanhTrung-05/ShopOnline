package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByCustomerIdOrderByCreatedAtDescNotificationIdDesc(Long customerId);

    long countByCustomerIdAndReadAtIsNull(Long customerId);

    Optional<Notification> findByNotificationIdAndCustomerId(Long notificationId, Long customerId);
}
