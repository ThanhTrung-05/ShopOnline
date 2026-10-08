package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.OperationsOrderResponse;
import com.example.banhangtructuyen.application.dto.order.OrderStatusResponse;
import com.example.banhangtructuyen.application.service.impl.OrderOperationsServiceImpl;
import com.example.banhangtructuyen.application.service.impl.OrderStatusServiceImpl;
import com.example.banhangtructuyen.application.service.impl.NotificationServiceImpl;
import com.example.banhangtructuyen.config.AuditingConfig;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import com.example.banhangtructuyen.domain.repository.NotificationRepository;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        AuditingConfig.class,
        AuthenticatedCustomerResolver.class,
        NotificationServiceImpl.class,
        OrderOperationsServiceImpl.class,
        OrderStatusServiceImpl.class
})
@ActiveProfiles("test")
@DisplayName("ATS-35 persistence and ATS-34 integration")
class OrderOperationsIntegrationTest {

    private static final String ORDER_NUMBER = "ORD-20260907-ATS35";
    private static final Instant OLD_UPDATED_AT = Instant.parse("2025-01-01T00:00:00Z");

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private OrderOperationsService orderOperationsService;

    @Autowired
    private OrderStatusService orderStatusService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("a transactional update persists status and ATS-34 sees the new status and timestamp")
    void updateStatus_shouldBeVisibleToCustomerTrackingAfterRefetch() {
        final Customer customer = customerRepository.saveAndFlush(Customer.builder()
                .email("ats35-customer@example.com")
                .fullName("ATS-35 Customer")
                .keycloakUserId("ats35-customer-subject")
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.CustomerRole.USER)
                .build());
        final Order order = orderRepository.saveAndFlush(Order.builder()
                .customerId(customer.getCustomerId())
                .orderNumber(ORDER_NUMBER)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("125000.00"))
                .shippingAddress("Shipping address snapshot")
                .build());
        entityManager.createNativeQuery(
                        "UPDATE ORDERS SET UPDATED_AT = :updatedAt WHERE ORDER_ID = :orderId")
                .setParameter("updatedAt", Timestamp.from(OLD_UPDATED_AT))
                .setParameter("orderId", order.getOrderId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        final OperationsOrderResponse updated = orderOperationsService
                .updateStatus(ORDER_NUMBER, OrderStatus.CONFIRMED);
        entityManager.clear();
        final OrderStatusResponse customerView = orderStatusService
                .getOrderStatus(ORDER_NUMBER, customer.getCustomerId());

        assertThat(updated.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(updated.updatedAt()).isAfter(OLD_UPDATED_AT);
        assertThat(customerView.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(Duration.between(updated.updatedAt(), customerView.updatedAt()).abs())
                .isLessThanOrEqualTo(Duration.ofNanos(1_000));
        assertThat(notificationRepository
                .findAllByCustomerIdOrderByCreatedAtDescNotificationIdDesc(customer.getCustomerId()))
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getOrderNumber()).isEqualTo(ORDER_NUMBER);
                    assertThat(notification.getOldStatus()).isEqualTo(OrderStatus.PENDING);
                    assertThat(notification.getNewStatus()).isEqualTo(OrderStatus.CONFIRMED);
                    assertThat(notification.getReadAt()).isNull();
                });
    }
}
