package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.notification.NotificationResponse;
import com.example.banhangtructuyen.application.dto.notification.UnreadNotificationCountResponse;
import com.example.banhangtructuyen.application.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers/me/notifications")
@RequiredArgsConstructor
@Tag(name = "Customer Notifications", description = "Authenticated customer order-status notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "List notifications for the authenticated customer")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getNotifications(jwt.getSubject())));
    }

    @Operation(summary = "Get the authenticated customer's unread notification count")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadNotificationCountResponse>> getUnreadCount(
            @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getUnreadCount(jwt.getSubject())));
    }

    @Operation(summary = "Mark an owned notification as read")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @AuthenticationPrincipal final Jwt jwt,
            @PathVariable final Long notificationId) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.markAsRead(jwt.getSubject(), notificationId)));
    }
}
