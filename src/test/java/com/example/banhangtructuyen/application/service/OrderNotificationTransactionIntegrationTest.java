package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.service.impl.NotificationServiceImpl;
import com.example.banhangtructuyen.application.service.impl.OrderOperationsServiceImpl;
import com.example.banhangtructuyen.config.AuditingConfig;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import com.example.banhangtructuyen.domain.repository.NotificationRepository;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
        AuditingConfig.class,
        AuthenticatedCustomerResolver.class,
        NotificationServiceImpl.class,
        OrderOperationsServiceImpl.class
})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("ATS-36 order notification transaction")
class OrderNotificationTransactionIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private OrderOperationsService orderOperationsService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("valid status update commits one notification with the order")
    void validUpdate_shouldCommitNotification() {
        final Fixture fixture = createFixture("valid");

        orderOperationsService.updateStatus(fixture.orderNumber(), OrderStatus.CONFIRMED);

        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
        assertThat(notificationRepository
                .findAllByCustomerIdOrderByCreatedAtDescNotificationIdDesc(fixture.customerId()))
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getOldStatus()).isEqualTo(OrderStatus.PENDING);
                    assertThat(notification.getNewStatus()).isEqualTo(OrderStatus.CONFIRMED);
                });
    }

    @Test
    @DisplayName("invalid status update commits no notification")
    void invalidUpdate_shouldCreateNoNotification() {
        final Fixture fixture = createFixture("invalid");

        assertThatThrownBy(() -> orderOperationsService
                .updateStatus(fixture.orderNumber(), OrderStatus.DELIVERED))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(notificationRepository
                .findAllByCustomerIdOrderByCreatedAtDescNotificationIdDesc(fixture.customerId()))
                .isEmpty();
    }

    @Test
    @DisplayName("outer rollback reverts both order status and notification")
    void rollback_shouldRevertOrderAndNotificationTogether() {
        final Fixture fixture = createFixture("rollback");
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            orderOperationsService.updateStatus(fixture.orderNumber(), OrderStatus.CONFIRMED);
            status.setRollbackOnly();
        });

        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(notificationRepository
                .findAllByCustomerIdOrderByCreatedAtDescNotificationIdDesc(fixture.customerId()))
                .isEmpty();
    }

    private Fixture createFixture(final String suffix) {
        final Customer customer = customerRepository.save(Customer.builder()
                .email("ats36-" + suffix + "@example.com")
                .fullName("ATS-36 Customer")
                .keycloakUserId("ats36-" + suffix + "-subject")
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.CustomerRole.USER)
                .build());
        final Order order = orderRepository.save(Order.builder()
                .customerId(customer.getCustomerId())
                .orderNumber("ORD-ATS36-" + suffix.toUpperCase())
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("125000.00"))
                .shippingAddress("Shipping address snapshot")
                .build());
        return new Fixture(customer.getCustomerId(), order.getOrderId(), order.getOrderNumber());
    }

    private record Fixture(Long customerId, Long orderId, String orderNumber) {}
}
