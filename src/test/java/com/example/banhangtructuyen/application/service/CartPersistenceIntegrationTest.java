package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.cart.AddCartItemRequest;
import com.example.banhangtructuyen.application.dto.cart.CartResponse;
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
import org.junit.jupiter.api.Nested;
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

/**
 * ATS-66 — Backend Cart Persistence Integration Tests.
 *
 * <p>Verifies that cart data persists correctly across simulated session boundaries
 * (logout/login = new JWT, same keycloakUserId), that the ATS-66 single-query
 * optimisation works against a real database, and that ownership/access-control rules hold.
 *
 * <p>Uses H2 in-memory (MODE=Oracle) via the test profile. Each test runs in its
 * own transaction via TransactionTemplate to simulate multiple HTTP requests.
 */
@DataJpaTest
@Import(AuditingConfig.class)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("ATS-66 — Cart Persistence Integration Tests")
class CartPersistenceIntegrationTest {

    private static final String SUBJECT_A = "ats66-customer-a";
    private static final String SUBJECT_B = "ats66-customer-b";

    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private CartServiceImpl service;

    private Long productId;
    private Long productBId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        service = new CartServiceImpl(
                cartRepository, cartItemRepository, customerRepository,
                productRepository, new AuthenticatedCustomerResolver(customerRepository));
        clearDatabase();
        seedFixtures();
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    // -------------------------------------------------------------------------
    // ATS-66.1: Persistence across simulated session boundary (logout/login)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ATS-66.1: Persistence across session boundaries")
    class SessionBoundaryPersistence {

        @Test
        @DisplayName("cart survives logout and login — keyed to customer, not session")
        void cartSurvivesLogoutAndLogin() {
            // Session 1: customer adds item
            tx.executeWithoutResult(status ->
                    service.addItem(SUBJECT_A, new AddCartItemRequest(productId, 3)));

            // Simulate logout → login (same subject, new JWT token)
            final CartResponse cart = tx.execute(status ->
                    service.getCurrentCart(SUBJECT_A));

            assertThat(cart).isNotNull();
            assertThat(cart.items()).hasSize(1);
            assertThat(cart.items().get(0).quantity()).isEqualTo(3);
            assertThat(cart.items().get(0).productId()).isEqualTo(productId);
        }

        @Test
        @DisplayName("cart persists after page reload — repeated GET /cart returns same data")
        void cartPersistsAfterPageReload() {
            tx.executeWithoutResult(status ->
                    service.addItem(SUBJECT_A, new AddCartItemRequest(productId, 2)));

            final CartResponse firstLoad = tx.execute(status ->
                    service.getCurrentCart(SUBJECT_A));
            final CartResponse secondLoad = tx.execute(status ->
                    service.getCurrentCart(SUBJECT_A));

            assertThat(firstLoad).isNotNull();
            assertThat(secondLoad).isNotNull();
            assertThat(secondLoad.items()).hasSize(firstLoad.items().size());
            assertThat(secondLoad.subtotal()).isEqualByComparingTo(firstLoad.subtotal());
        }

        @Test
        @DisplayName("cart update and remove persist across requests")
        void cartPersistsThroughUpdateAndRemove() {
            tx.executeWithoutResult(status ->
                    service.addItem(SUBJECT_A, new AddCartItemRequest(productId, 2)));
            tx.executeWithoutResult(status ->
                    service.addItem(SUBJECT_A, new AddCartItemRequest(productBId, 1)));

            final Long cartItemId = tx.execute(status ->
                    cartItemRepository.findViewItemsByCustomerId(getCustomerId(SUBJECT_A))
                            .stream()
                            .filter(ci -> ci.getProduct().getProductId().equals(productId))
                            .findFirst()
                            .map(CartItem::getCartItemId)
                            .orElseThrow());

            // Update
            tx.executeWithoutResult(status ->
                    service.updateItemQuantity(SUBJECT_A, cartItemId,
                            new UpdateCartItemQuantityRequest(5)));

            final CartResponse afterUpdate = tx.execute(status ->
                    service.getCurrentCart(SUBJECT_A));
            assertThat(afterUpdate).isNotNull();
            final var updatedItem = afterUpdate.items().stream()
                    .filter(i -> i.productId().equals(productId))
                    .findFirst().orElseThrow();
            assertThat(updatedItem.quantity()).isEqualTo(5);

            // Remove
            tx.executeWithoutResult(status ->
                    service.removeItem(SUBJECT_A, cartItemId));

            final CartResponse afterRemove = tx.execute(status ->
                    service.getCurrentCart(SUBJECT_A));
            assertThat(afterRemove).isNotNull();
            assertThat(afterRemove.items()).hasSize(1);
            assertThat(afterRemove.items().get(0).productId()).isEqualTo(productBId);
        }
    }

    // -------------------------------------------------------------------------
    // ATS-66.2: Single-query optimisation correctness (DB level)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ATS-66.2: Single-query optimisation — no cart row returns empty (no exception)")
    class SingleQueryOptimisation {

        @Test
        @DisplayName("ATS-66: GET cart when customer has never added items returns empty")
        void getCartWithNoCartRow_returnsEmpty() {
            final CartResponse response = tx.execute(status ->
                    service.getCurrentCart(SUBJECT_A));

            assertThat(response).isNotNull();
            assertThat(response.items()).isEmpty();
            assertThat(response.subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("ATS-66: GET cart after all items removed returns empty (CARTS row stays)")
        void getCartAfterAllItemsRemoved_returnsEmpty() {
            tx.executeWithoutResult(status ->
                    service.addItem(SUBJECT_A, new AddCartItemRequest(productId, 1)));

            final Long cartItemId = tx.execute(status ->
                    cartItemRepository.findViewItemsByCustomerId(getCustomerId(SUBJECT_A))
                            .get(0).getCartItemId());

            tx.executeWithoutResult(status -> service.removeItem(SUBJECT_A, cartItemId));

            final CartResponse response = tx.execute(status ->
                    service.getCurrentCart(SUBJECT_A));

            assertThat(response).isNotNull();
            assertThat(response.items()).isEmpty();
            assertThat(response.subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
            // CARTS row stays (lifetime cart) — only items are gone
            final long cartRows = tx.execute(status -> cartRepository.count());
            assertThat(cartRows).isEqualTo(1L);
        }
    }

    // -------------------------------------------------------------------------
    // ATS-66.3: Ownership and access control
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ATS-66.3: Cart ownership — customer can only access own cart items")
    class CartOwnership {

        @Test
        @DisplayName("customer A cannot update customer B's cart item")
        void customerCannotUpdateOtherCustomersCartItem() {
            tx.executeWithoutResult(status ->
                    service.addItem(SUBJECT_B, new AddCartItemRequest(productId, 1)));

            final Long cartItemIdB = tx.execute(status ->
                    cartItemRepository.findViewItemsByCustomerId(getCustomerId(SUBJECT_B))
                            .get(0).getCartItemId());

            assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                    service.updateItemQuantity(SUBJECT_A, cartItemIdB,
                            new UpdateCartItemQuantityRequest(5))))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("CartItem");

            // B's item unchanged
            final CartResponse cartB = tx.execute(status -> service.getCurrentCart(SUBJECT_B));
            assertThat(cartB).isNotNull();
            assertThat(cartB.items().get(0).quantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("customer A cannot remove customer B's cart item")
        void customerCannotRemoveOtherCustomersCartItem() {
            tx.executeWithoutResult(status ->
                    service.addItem(SUBJECT_B, new AddCartItemRequest(productId, 2)));

            final Long cartItemIdB = tx.execute(status ->
                    cartItemRepository.findViewItemsByCustomerId(getCustomerId(SUBJECT_B))
                            .get(0).getCartItemId());

            assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                    service.removeItem(SUBJECT_A, cartItemIdB)))
                    .isInstanceOf(ResourceNotFoundException.class);

            final CartResponse cartB = tx.execute(status -> service.getCurrentCart(SUBJECT_B));
            assertThat(cartB).isNotNull();
            assertThat(cartB.items()).hasSize(1);
        }

        @Test
        @DisplayName("two customers have fully independent carts")
        void twoCustomersHaveIndependentCarts() {
            tx.executeWithoutResult(status ->
                    service.addItem(SUBJECT_A, new AddCartItemRequest(productId, 3)));
            tx.executeWithoutResult(status ->
                    service.addItem(SUBJECT_B, new AddCartItemRequest(productId, 7)));

            final CartResponse cartA = tx.execute(status -> service.getCurrentCart(SUBJECT_A));
            final CartResponse cartB = tx.execute(status -> service.getCurrentCart(SUBJECT_B));

            assertThat(cartA).isNotNull();
            assertThat(cartB).isNotNull();
            assertThat(cartA.items().get(0).quantity()).isEqualTo(3);
            assertThat(cartB.items().get(0).quantity()).isEqualTo(7);
        }
    }

    // -------------------------------------------------------------------------
    // ATS-66.4: Add same product multiple times — quantity accumulation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ATS-66.4: Duplicate product handling — quantity accumulates, single row")
    class DuplicateProductHandling {

        @Test
        @DisplayName("adding same product twice accumulates quantity, creates single CartItem row")
        void addSameProductTwice_accumulatesQuantity() {
            tx.executeWithoutResult(status ->
                    service.addItem(SUBJECT_A, new AddCartItemRequest(productId, 2)));
            tx.executeWithoutResult(status ->
                    service.addItem(SUBJECT_A, new AddCartItemRequest(productId, 3)));

            final CartResponse cart = tx.execute(status -> service.getCurrentCart(SUBJECT_A));

            assertThat(cart).isNotNull();
            assertThat(cart.items()).hasSize(1);
            assertThat(cart.items().get(0).quantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("one CARTS row per customer — lifetime cart (V11 UK constraint)")
        void oneCartPerCustomer_lifetimeCart() {
            tx.executeWithoutResult(status ->
                    service.addItem(SUBJECT_A, new AddCartItemRequest(productId, 1)));
            tx.executeWithoutResult(status ->
                    service.addItem(SUBJECT_A, new AddCartItemRequest(productBId, 1)));

            final long cartCount = tx.execute(status -> cartRepository.count());
            assertThat(cartCount).isEqualTo(1L);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Long getCustomerId(final String subject) {
        return customerRepository.findByKeycloakUserId(subject)
                .map(Customer::getCustomerId)
                .orElseThrow(() -> new IllegalStateException("Customer not found: " + subject));
    }

    private void seedFixtures() {
        tx.executeWithoutResult(status -> {
            final Category category = categoryRepository.save(Category.builder()
                    .categoryCode("ATS66_CAT")
                    .categoryName("ATS-66 Category")
                    .vatRate(new BigDecimal("10.00"))
                    .sortOrder(1)
                    .status(Category.CategoryStatus.ACTIVE)
                    .build());

            final Product pA = productRepository.save(Product.builder()
                    .productSlug("ats66-product-a")
                    .category(category)
                    .productName("ATS-66 Product A")
                    .price(new BigDecimal("50000.00"))
                    .status(Product.ProductStatus.ACTIVE)
                    .build());
            inventoryRepository.save(Inventory.builder()
                    .product(pA).quantity(100).reservedQuantity(0).build());
            productId = pA.getProductId();

            final Product pB = productRepository.save(Product.builder()
                    .productSlug("ats66-product-b")
                    .category(category)
                    .productName("ATS-66 Product B")
                    .price(new BigDecimal("30000.00"))
                    .status(Product.ProductStatus.ACTIVE)
                    .build());
            inventoryRepository.save(Inventory.builder()
                    .product(pB).quantity(50).reservedQuantity(0).build());
            productBId = pB.getProductId();

            customerRepository.save(Customer.builder()
                    .email("ats66-customer-a@example.com")
                    .fullName("ATS-66 Customer A")
                    .passwordHash("$2a$12$hashedvalue")
                    .keycloakUserId(SUBJECT_A)
                    .status(Customer.CustomerStatus.ACTIVE)
                    .role(Customer.CustomerRole.USER)
                    .build());

            customerRepository.save(Customer.builder()
                    .email("ats66-customer-b@example.com")
                    .fullName("ATS-66 Customer B")
                    .passwordHash("$2a$12$hashedvalue")
                    .keycloakUserId(SUBJECT_B)
                    .status(Customer.CustomerStatus.ACTIVE)
                    .role(Customer.CustomerRole.USER)
                    .build());
        });
    }

    private void clearDatabase() {
        tx.executeWithoutResult(status -> {
            cartItemRepository.deleteAllInBatch();
            cartRepository.deleteAllInBatch();
            inventoryRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
            categoryRepository.deleteAllInBatch();
            customerRepository.deleteAllInBatch();
        });
    }
}
