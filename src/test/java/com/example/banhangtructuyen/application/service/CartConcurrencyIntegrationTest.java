package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.cart.AddCartItemRequest;
import com.example.banhangtructuyen.application.dto.cart.CartItemResponse;
import com.example.banhangtructuyen.application.dto.cart.UpdateCartItemQuantityRequest;
import com.example.banhangtructuyen.application.service.impl.CartServiceImpl;
import com.example.banhangtructuyen.config.AuditingConfig;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Cart;
import com.example.banhangtructuyen.domain.model.CartItem;
import com.example.banhangtructuyen.domain.model.Category;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Inventory;
import com.example.banhangtructuyen.domain.model.Product;
import com.example.banhangtructuyen.domain.repository.CartItemRepository;
import com.example.banhangtructuyen.domain.repository.CartRepository;
import com.example.banhangtructuyen.domain.repository.CategoryRepository;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import com.example.banhangtructuyen.domain.repository.InventoryRepository;
import com.example.banhangtructuyen.domain.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(AuditingConfig.class)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Cart concurrency integration tests")
class CartConcurrencyIntegrationTest {

    private static final String SUBJECT = "cart-concurrency-subject";

    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private CartServiceImpl service;
    private Fixture fixture;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        service = new CartServiceImpl(
                cartRepository,
                cartItemRepository,
                customerRepository,
                productRepository,
                new AuthenticatedCustomerResolver(customerRepository));
        clearDatabase();
        fixture = createFixture(true);
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    @Test
    @DisplayName("two concurrent adds keep one CartItem and preserve both increments")
    void concurrentAdd_shouldNotLoseIncrementOrCreateDuplicate() throws Exception {
        final List<CartItemResponse> responses = runConcurrently(
                () -> service.addItem(SUBJECT, new AddCartItemRequest(fixture.productId(), 1)),
                () -> service.addItem(SUBJECT, new AddCartItemRequest(fixture.productId(), 1))
        );

        assertThat(responses)
                .extracting(CartItemResponse::quantity)
                .containsExactlyInAnyOrder(1, 2);
        assertSingleItemWithQuantity(2);
        assertInventoryUnchanged();
    }

    @Test
    @DisplayName("two concurrent first adds create one lifetime Cart and one CartItem")
    void concurrentFirstAdd_shouldCreateOneCartAndOneItem() throws Exception {
        transactionTemplate.executeWithoutResult(status -> cartRepository.deleteAllInBatch());

        runConcurrently(
                () -> service.addItem(SUBJECT, new AddCartItemRequest(fixture.productId(), 1)),
                () -> service.addItem(SUBJECT, new AddCartItemRequest(fixture.productId(), 1))
        );

        final long cartCount = transactionTemplate.execute(status -> cartRepository.count());
        assertThat(cartCount).isOne();
        assertSingleItemWithQuantity(2);
    }

    @Test
    @DisplayName("concurrent add and replacement update produce a serializable quantity")
    void concurrentAddAndUpdate_shouldNotUseStaleQuantity() throws Exception {
        final Long cartItemId = createCartItem(1);

        runConcurrently(
                () -> service.addItem(SUBJECT, new AddCartItemRequest(fixture.productId(), 2)),
                () -> service.updateItemQuantity(
                        SUBJECT, cartItemId, new UpdateCartItemQuantityRequest(5))
        );

        final int finalQuantity = currentItems().get(0).getQuantity();
        assertThat(finalQuantity)
                .as("valid serial orders are add-then-update=5 or update-then-add=7")
                .isIn(5, 7);
        assertThat(finalQuantity).isNotEqualTo(3);
        assertInventoryUnchanged();
    }

    @Test
    @DisplayName("two concurrent replacement updates both complete under the Cart lock")
    void concurrentUpdates_shouldBeSerialized() throws Exception {
        final Long cartItemId = createCartItem(1);

        final List<CartItemResponse> responses = runConcurrently(
                () -> service.updateItemQuantity(
                        SUBJECT, cartItemId, new UpdateCartItemQuantityRequest(4)),
                () -> service.updateItemQuantity(
                        SUBJECT, cartItemId, new UpdateCartItemQuantityRequest(7))
        );

        assertThat(responses)
                .extracting(CartItemResponse::quantity)
                .containsExactlyInAnyOrder(4, 7);
        assertThat(currentItems().get(0).getQuantity()).isIn(4, 7);
    }

    @Test
    @DisplayName("concurrent update and remove never resurrect a removed CartItem")
    void concurrentUpdateAndRemove_shouldNotResurrectItem() throws Exception {
        final Long cartItemId = createCartItem(1);

        final List<String> outcomes = runConcurrently(
                () -> {
                    try {
                        service.updateItemQuantity(
                                SUBJECT, cartItemId, new UpdateCartItemQuantityRequest(5));
                        return "updated";
                    } catch (final ResourceNotFoundException ex) {
                        return "missing";
                    }
                },
                () -> {
                    service.removeItem(SUBJECT, cartItemId);
                    return "removed";
                }
        );

        assertThat(outcomes).contains("removed");
        assertThat(outcomes).allMatch(outcome ->
                outcome.equals("updated") || outcome.equals("missing") || outcome.equals("removed"));
        assertThat(currentItems()).isEmpty();
        assertInventoryUnchanged();
    }

    @Test
    @DisplayName("database constraint rejects duplicate product rows in one Cart")
    void database_shouldRejectDuplicateProductInCart() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            final Cart cart = cartRepository.findById(fixture.cartId()).orElseThrow();
            final Product product = productRepository.findByIdWithInventory(fixture.productId()).orElseThrow();

            cartItemRepository.saveAndFlush(newCartItem(cart, product, 1));
            cartItemRepository.saveAndFlush(newCartItem(cart, product, 2));
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture createFixture(final boolean createCart) {
        return transactionTemplate.execute(status -> {
            final Category category = categoryRepository.save(Category.builder()
                    .categoryCode("CART_TEST")
                    .categoryName("Cart Test")
                    .vatRate(new BigDecimal("10.00"))
                    .sortOrder(1)
                    .status(Category.CategoryStatus.ACTIVE)
                    .build());
            final Product product = productRepository.save(Product.builder()
                    .productSlug("cart-concurrency-product")
                    .category(category)
                    .productName("Cart concurrency product")
                    .price(new BigDecimal("10000.00"))
                    .status(Product.ProductStatus.ACTIVE)
                    .build());
            inventoryRepository.save(Inventory.builder()
                    .product(product)
                    .quantity(100)
                    .reservedQuantity(0)
                    .build());
            final Customer customer = customerRepository.save(Customer.builder()
                    .email("cart-concurrency@example.com")
                    .fullName("Cart Concurrency Customer")
                    .passwordHash("$2a$12$hashedvalue")
                    .keycloakUserId(SUBJECT)
                    .status(Customer.CustomerStatus.ACTIVE)
                    .role(Customer.CustomerRole.USER)
                    .build());
            final Cart cart = createCart
                    ? cartRepository.save(Cart.builder().customerId(customer.getCustomerId()).build())
                    : null;

            cartRepository.flush();
            inventoryRepository.flush();
            return new Fixture(
                    customer.getCustomerId(),
                    cart == null ? null : cart.getCartId(),
                    product.getProductId()
            );
        });
    }

    private Long createCartItem(final int quantity) {
        return transactionTemplate.execute(status -> {
            final Cart cart = cartRepository.findById(fixture.cartId()).orElseThrow();
            final Product product = productRepository.findByIdWithInventory(fixture.productId()).orElseThrow();
            return cartItemRepository.saveAndFlush(newCartItem(cart, product, quantity)).getCartItemId();
        });
    }

    private static CartItem newCartItem(final Cart cart, final Product product, final int quantity) {
        return CartItem.builder()
                .cart(cart)
                .product(product)
                .quantity(quantity)
                .unitPrice(product.getPrice())
                .build();
    }

    private List<CartItem> currentItems() {
        return transactionTemplate.execute(status ->
                cartItemRepository.findViewItemsByCustomerId(fixture.customerId()));
    }

    private void assertSingleItemWithQuantity(final int quantity) {
        final List<CartItem> items = currentItems();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getQuantity()).isEqualTo(quantity);
    }

    private void assertInventoryUnchanged() {
        final int[] quantities = transactionTemplate.execute(status -> {
            final Inventory inventory = productRepository.findActiveById(fixture.productId())
                    .orElseThrow()
                    .getInventory();
            return new int[] {inventory.getQuantity(), inventory.getReservedQuantity()};
        });
        assertThat(quantities).containsExactly(100, 0);
    }

    private <T> List<T> runConcurrently(
            final Supplier<T> firstOperation,
            final Supplier<T> secondOperation) throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final CyclicBarrier startBarrier = new CyclicBarrier(2);
        try {
            final Future<T> first = executor.submit(() -> inTransactionAfterBarrier(startBarrier, firstOperation));
            final Future<T> second = executor.submit(() -> inTransactionAfterBarrier(startBarrier, secondOperation));
            return List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private <T> T inTransactionAfterBarrier(
            final CyclicBarrier startBarrier,
            final Supplier<T> operation) {
        return transactionTemplate.execute(status -> {
            awaitBarrier(startBarrier);
            return operation.get();
        });
    }

    private static void awaitBarrier(final CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (final Exception ex) {
            throw new IllegalStateException("Concurrent test barrier failed", ex);
        }
    }

    private void clearDatabase() {
        transactionTemplate.executeWithoutResult(status -> {
            cartItemRepository.deleteAllInBatch();
            cartRepository.deleteAllInBatch();
            inventoryRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
            categoryRepository.deleteAllInBatch();
            customerRepository.deleteAllInBatch();
        });
    }

    private record Fixture(Long customerId, Long cartId, Long productId) {
    }
}
