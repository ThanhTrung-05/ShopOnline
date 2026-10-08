package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.notification.NotificationResponse;
import com.example.banhangtructuyen.application.dto.notification.UnreadNotificationCountResponse;
import com.example.banhangtructuyen.application.service.NotificationService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ATS-36 customer notification endpoints")
class NotificationControllerTest {

    private static final String SUBJECT = "customer-subject";
    private static final Long NOTIFICATION_ID = 42L;
    private static final String BASE_PATH = "/api/v1/customers/me/notifications";
    private static final Instant CREATED_AT = Instant.parse("2026-09-08T02:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    @DisplayName("CUSTOMER lists only notifications resolved from JWT subject")
    void customer_shouldListNotifications() throws Exception {
        when(notificationService.getNotifications(SUBJECT)).thenReturn(List.of(sampleResponse(null)));

        mockMvc.perform(get(BASE_PATH).with(roleJwt("CUSTOMER", SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(NOTIFICATION_ID))
                .andExpect(jsonPath("$.data[0].orderNumber").value("ORD-20260908-NOTIFY"))
                .andExpect(jsonPath("$.data[0].oldStatus").value("PENDING"))
                .andExpect(jsonPath("$.data[0].newStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data[0].read").value(false))
                .andExpect(jsonPath("$.data[0].readAt").doesNotExist());

        verify(notificationService).getNotifications(SUBJECT);
    }

    @Test
    @DisplayName("CUSTOMER reads its unread count")
    void customer_shouldReadUnreadCount() throws Exception {
        when(notificationService.getUnreadCount(SUBJECT))
                .thenReturn(new UnreadNotificationCountResponse(4L));

        mockMvc.perform(get(BASE_PATH + "/unread-count").with(roleJwt("CUSTOMER", SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(4));

        verify(notificationService).getUnreadCount(SUBJECT);
    }

    @Test
    @DisplayName("CUSTOMER marks an owned notification read")
    void customer_shouldMarkOwnedNotificationRead() throws Exception {
        final Instant readAt = Instant.parse("2026-09-08T03:00:00Z");
        when(notificationService.markAsRead(SUBJECT, NOTIFICATION_ID))
                .thenReturn(sampleResponse(readAt));

        mockMvc.perform(patch(BASE_PATH + "/" + NOTIFICATION_ID + "/read")
                        .with(roleJwt("CUSTOMER", SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(NOTIFICATION_ID))
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.readAt").value("2026-09-08T03:00:00Z"));

        verify(notificationService).markAsRead(SUBJECT, NOTIFICATION_ID);
    }

    @Test
    @DisplayName("another customer's notification remains indistinguishable from missing")
    void customer_shouldReceive404ForAnotherCustomersNotification() throws Exception {
        when(notificationService.markAsRead(SUBJECT, NOTIFICATION_ID))
                .thenThrow(new ResourceNotFoundException("Notification", NOTIFICATION_ID));

        mockMvc.perform(patch(BASE_PATH + "/" + NOTIFICATION_ID + "/read")
                        .with(roleJwt("CUSTOMER", SUBJECT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Notification not found with id: " + NOTIFICATION_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "WAREHOUSE_STAFF"})
    @DisplayName("non-CUSTOMER roles cannot access notifications")
    void unrelatedRole_shouldReceive403(final String role) throws Exception {
        mockMvc.perform(get(BASE_PATH).with(roleJwt(role, role.toLowerCase() + "-subject")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("anonymous notification request receives 401")
    void anonymous_shouldReceive401() throws Exception {
        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }

    private static NotificationResponse sampleResponse(final Instant readAt) {
        return new NotificationResponse(
                NOTIFICATION_ID,
                "ORD-20260908-NOTIFY",
                OrderStatus.PENDING,
                OrderStatus.CONFIRMED,
                "Order status changed",
                CREATED_AT,
                readAt,
                readAt != null
        );
    }

    private static RequestPostProcessor roleJwt(final String role, final String subject) {
        return jwt()
                .jwt(builder -> builder.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
