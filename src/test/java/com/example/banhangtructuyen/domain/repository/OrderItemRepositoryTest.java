package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.config.AuditingConfig;
import com.example.banhangtructuyen.domain.model.Category;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderItem;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(AuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("OrderItemRepository Tests")
class OrderItemRepositoryTest {

    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    @DisplayName("maps the existing ORDER_ITEMS snapshot columns and Order relation")
    void saveAndReload_shouldPersistOrderItemSnapshot() {
        final Customer customer = customerRepository.saveAndFlush(Customer.builder()
                .email("order-item@example.com")
                .fullName("Order Item Customer")
                .keycloakUserId("kc-order-item")
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.CustomerRole.USER)
                .build());
        final Category category = categoryRepository.saveAndFlush(Category.builder()
                .categoryCode("ORDER_ITEM")
                .categoryName("Order Item")
                .vatRate(new BigDecimal("10.00"))
                .sortOrder(1)
                .status(Category.CategoryStatus.ACTIVE)
                .build());
        final Product product = productRepository.saveAndFlush(Product.builder()
                .productSlug("order-item-product")
                .category(category)
                .productName("Current product name")
                .price(new BigDecimal("15000.00"))
                .status(Product.ProductStatus.ACTIVE)
                .build());
        final Order order = orderRepository.saveAndFlush(Order.builder()
                .customerId(customer.getCustomerId())
                .orderNumber("ORD-20260907-ITEMMAPPING")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("40000.00"))
                .shippingAddress("Snapshot address")
                .build());

        final OrderItem saved = orderItemRepository.saveAndFlush(OrderItem.builder()
                .order(order)
                .productId(product.getProductId())
                .productName("Product name at checkout")
                .unitPrice(new BigDecimal("12000.00"))
                .quantity(2)
                .subtotal(new BigDecimal("24000.00"))
                .build());
        entityManager.clear();

        assertThat(orderItemRepository.findById(saved.getOrderItemId()))
                .isPresent()
                .get()
                .satisfies(item -> {
                    assertThat(item.getOrder().getOrderId()).isEqualTo(order.getOrderId());
                    assertThat(item.getProductId()).isEqualTo(product.getProductId());
                    assertThat(item.getProductName()).isEqualTo("Product name at checkout");
                    assertThat(item.getUnitPrice()).isEqualByComparingTo("12000.00");
                    assertThat(item.getQuantity()).isEqualTo(2);
                    assertThat(item.getSubtotal()).isEqualByComparingTo("24000.00");
                });
    }
}
