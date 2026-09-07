package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.config.AuditingConfig;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(AuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("OrderRepository Tests")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("status is persisted as the Oracle-compatible enum name")
    void saveAndReload_shouldPersistStatusAsString() {
        final Long customerId = createCustomer("order-status@example.com");
        final Order saved = orderRepository.saveAndFlush(sampleOrder(
                "ORD-20260827-STATUS01", customerId, OrderStatus.SHIPPING));
        entityManager.clear();

        final Object storedStatus = entityManager.getEntityManager()
                .createNativeQuery("SELECT STATUS FROM ORDERS WHERE ORDER_ID = :orderId")
                .setParameter("orderId", saved.getOrderId())
                .getSingleResult();

        assertThat(storedStatus).isEqualTo(OrderStatus.SHIPPING.name());
        assertThat(orderRepository.findById(saved.getOrderId()))
                .isPresent()
                .get()
                .extracting(Order::getStatus)
                .isEqualTo(OrderStatus.SHIPPING);
    }

    @Test
    @DisplayName("findByOrderNumberAndCustomerId returns the owning customer's order")
    void findByOrderNumberAndCustomerId_shouldReturnOwnedOrder() {
        final Long customerId = createCustomer("order-owner@example.com");
        orderRepository.saveAndFlush(sampleOrder(
                "ORD-20260827-OWNER001", customerId, OrderStatus.CONFIRMED));
        entityManager.clear();

        assertThat(orderRepository.findByOrderNumberAndCustomerId(
                "ORD-20260827-OWNER001", customerId))
                .isPresent()
                .get()
                .satisfies(order -> {
                    assertThat(order.getCustomerId()).isEqualTo(customerId);
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
                });
    }

    @Test
    @DisplayName("findByOrderNumberAndCustomerId hides an order from another customer")
    void findByOrderNumberAndCustomerId_shouldReturnEmptyForOtherCustomer() {
        final Long ownerId = createCustomer("order-owner-two@example.com");
        final Long otherCustomerId = createCustomer("order-other@example.com");
        orderRepository.saveAndFlush(sampleOrder(
                "ORD-20260827-OWNER002", ownerId, OrderStatus.PAID));
        entityManager.clear();

        assertThat(orderRepository.findByOrderNumberAndCustomerId(
                "ORD-20260827-OWNER002", otherCustomerId)).isEmpty();
    }

    @Test
    @DisplayName("findAllByCustomerId returns only the customer's orders newest first")
    void findAllByCustomerId_shouldReturnOnlyOwnedOrdersNewestFirst() {
        final Long ownerId = createCustomer("order-list-owner@example.com");
        final Long otherCustomerId = createCustomer("order-list-other@example.com");
        final Order olderOwnedOrder = orderRepository.saveAndFlush(sampleOrder(
                "ORD-20260826-OWNED001", ownerId, OrderStatus.CONFIRMED));
        final Order newerOwnedOrder = orderRepository.saveAndFlush(sampleOrder(
                "ORD-20260828-OWNED002", ownerId, OrderStatus.SHIPPING));
        final Order otherCustomerOrder = orderRepository.saveAndFlush(sampleOrder(
                "ORD-20260829-OTHER001", otherCustomerId, OrderStatus.PAID));

        setCreatedAt(olderOwnedOrder.getOrderId(), "2026-08-26T03:00:00Z");
        setCreatedAt(newerOwnedOrder.getOrderId(), "2026-08-28T03:00:00Z");
        setCreatedAt(otherCustomerOrder.getOrderId(), "2026-08-29T03:00:00Z");
        entityManager.clear();

        final List<Order> orders = orderRepository
                .findAllByCustomerIdOrderByCreatedAtDesc(ownerId);

        assertThat(orders)
                .extracting(Order::getOrderNumber)
                .containsExactly("ORD-20260828-OWNED002", "ORD-20260826-OWNED001");
        assertThat(orders)
                .extracting(Order::getCustomerId)
                .containsOnly(ownerId);
    }

    @Test
    @DisplayName("findAllByCustomerId returns an empty list when the customer has no orders")
    void findAllByCustomerId_shouldReturnEmptyList() {
        final Long customerId = createCustomer("order-list-empty@example.com");

        assertThat(orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId)).isEmpty();
    }

    private void setCreatedAt(final Long orderId, final String createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE ORDERS SET CREATED_AT = :createdAt WHERE ORDER_ID = :orderId")
                .setParameter("createdAt", Timestamp.from(Instant.parse(createdAt)))
                .setParameter("orderId", orderId)
                .executeUpdate();
        entityManager.flush();
    }

    private Long createCustomer(final String email) {
        final Customer customer = Customer.builder()
                .email(email)
                .fullName("Nguyen Van A")
                .phone("0987654321")
                .passwordHash("$2a$12$hashedvalue")
                .keycloakUserId("kc-" + email)
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.CustomerRole.USER)
                .build();
        return customerRepository.saveAndFlush(customer).getCustomerId();
    }

    private static Order sampleOrder(
            final String orderNumber,
            final Long customerId,
            final OrderStatus status) {
        return Order.builder()
                .orderNumber(orderNumber)
                .customerId(customerId)
                .status(status)
                .totalAmount(new BigDecimal("125000.00"))
                .shippingAddress("123 Le Loi, District 1, Ho Chi Minh City")
                .build();
    }
}
