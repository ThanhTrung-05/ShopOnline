package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.CreateOrderResponse;
import com.example.banhangtructuyen.application.dto.shipping.ShippingCheckoutInfo;
import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;
import com.example.banhangtructuyen.domain.model.Cart;
import com.example.banhangtructuyen.domain.model.CartItem;
import com.example.banhangtructuyen.domain.model.Category;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Order;
import com.example.banhangtructuyen.domain.model.OrderItem;
import com.example.banhangtructuyen.domain.model.OrderStatus;
import com.example.banhangtructuyen.domain.model.Product;
import com.example.banhangtructuyen.domain.model.ShippingMethod;
import com.example.banhangtructuyen.domain.model.ShippingRegion;
import com.example.banhangtructuyen.domain.repository.CartItemRepository;
import com.example.banhangtructuyen.domain.repository.CartRepository;
import com.example.banhangtructuyen.domain.repository.CategoryRepository;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import com.example.banhangtructuyen.domain.repository.OrderItemRepository;
import com.example.banhangtructuyen.domain.repository.OrderRepository;
import com.example.banhangtructuyen.domain.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:order-creation-transaction;MODE=Oracle;DB_CLOSE_DELAY=-1")
@DisplayName("Order creation transaction integration")
class OrderCreationTransactionIntegrationTest {

    private static final String SUBJECT = "order-transaction-subject";
    private static final ShippingSelectionRequest REQUEST =
            new ShippingSelectionRequest(42L, ShippingMethod.STANDARD);

    @Autowired private OrderCreationService orderCreationService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private CartRepository cartRepository;
    @SpyBean private CartItemRepository cartItemRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @MockBean private ShippingPreparationService shippingPreparationService;

    private Long customerId;

    @BeforeEach
    void setUp() {
        reset(cartItemRepository, shippingPreparationService);
        clearDatabase();
        customerId = createCartFixture();
        when(shippingPreparationService.prepareShipping(SUBJECT, REQUEST))
                .thenReturn(new ShippingCheckoutInfo(
                        customerId,
                        42L,
                        "Nguyen Van A",
                        "0987654321",
                        "123 Le Loi",
                        "Ben Nghe",
                        "District 1",
                        "Ha Noi",
                        ShippingMethod.STANDARD,
                        ShippingRegion.LOCAL,
                        new BigDecimal("10000.00")));
    }

    @Test
    @DisplayName("commits Order and OrderItems together and clears only CartItems")
    void createOrder_shouldCommitCompleteOrderAndClearCartItems() {
        final CreateOrderResponse response = orderCreationService.createOrder(SUBJECT, REQUEST);

        final Order order = orderRepository
                .findByOrderNumberAndCustomerId(response.orderNumber(), customerId)
                .orElseThrow();
        final List<OrderItem> orderItems = orderItemRepository
                .findAllByOrder_OrderIdOrderByOrderItemIdAsc(order.getOrderId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getCustomerId()).isEqualTo(customerId);
        assertThat(order.getShippingAddress()).isEqualTo(
                "Nguyen Van A, 0987654321, 123 Le Loi, Ben Nghe, District 1, Ha Noi");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("34000.00");
        assertThat(orderItems).singleElement().satisfies(item -> {
            assertThat(item.getProductName()).isEqualTo("Checkout product");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("12000.00");
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getSubtotal()).isEqualByComparingTo("24000.00");
        });
        assertThat(cartRepository.findByCustomerId(customerId)).isPresent();
        assertThat(cartItemRepository.findViewItemsByCustomerId(customerId)).isEmpty();
    }

    @Test
    @DisplayName("a final persistence failure rolls back Order, OrderItems, and cart clearing")
    void createOrder_shouldRollbackEverythingWhenCartClearFlushFails() {
        doThrow(new DataIntegrityViolationException("cart clear flush failed"))
                .when(cartItemRepository).flush();

        assertThatThrownBy(() -> orderCreationService.createOrder(SUBJECT, REQUEST))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("cart clear flush failed");

        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(cartItemRepository.findViewItemsByCustomerId(customerId))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getQuantity()).isEqualTo(2);
                    assertThat(item.getUnitPrice()).isEqualByComparingTo("12000.00");
                });
    }

    private Long createCartFixture() {
        final Category category = categoryRepository.saveAndFlush(Category.builder()
                .categoryCode("CHECKOUT_TX")
                .categoryName("Checkout Transaction")
                .vatRate(new BigDecimal("10.00"))
                .sortOrder(1)
                .status(Category.CategoryStatus.ACTIVE)
                .build());
        final Product product = productRepository.saveAndFlush(Product.builder()
                .productSlug("checkout-transaction-product")
                .category(category)
                .productName("Checkout product")
                .price(new BigDecimal("15000.00"))
                .status(Product.ProductStatus.ACTIVE)
                .build());
        final Customer customer = customerRepository.saveAndFlush(Customer.builder()
                .email("checkout-transaction@example.com")
                .fullName("Checkout Transaction Customer")
                .keycloakUserId(SUBJECT)
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.CustomerRole.USER)
                .build());
        final Cart cart = cartRepository.saveAndFlush(Cart.builder()
                .customerId(customer.getCustomerId())
                .build());
        cartItemRepository.saveAndFlush(CartItem.builder()
                .cart(cart)
                .product(product)
                .quantity(2)
                .unitPrice(new BigDecimal("12000.00"))
                .build());
        return customer.getCustomerId();
    }

    private void clearDatabase() {
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        cartItemRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }
}
