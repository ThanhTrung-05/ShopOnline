package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.config.AuditingConfig;
import com.example.banhangtructuyen.domain.model.Category;
import com.example.banhangtructuyen.domain.model.Inventory;
import com.example.banhangtructuyen.domain.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-level tests for {@link InventoryRepository} backed by the H2 in-memory DB
 * (Oracle compatibility mode) configured for the "test" profile.
 *
 * <p>ATS-14: Verifies that {@link InventoryRepository#findByProduct_ProductId(Long)} returns
 * current inventory data, correctly reflects QUANTITY and RESERVED_QUANTITY, and
 * that {@link Inventory#getAvailableQuantity()} computes correctly.
 */
@DataJpaTest
@Import(AuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("InventoryRepository Tests (ATS-14)")
class InventoryRepositoryTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    // ── Test data helpers ──────────────────────────────────────────────────

    private Category persistCategory(final String code) {
        return entityManager.persistFlushFind(
                Category.builder()
                        .categoryCode(code)
                        .categoryName(code)
                        .vatRate(new BigDecimal("10.00"))
                        .sortOrder(0)
                        .status(Category.CategoryStatus.ACTIVE)
                        .build());
    }

    private Product persistProduct(final Category category, final String slug,
                                    final Product.ProductStatus status) {
        return entityManager.persistFlushFind(
                Product.builder()
                        .productSlug(slug)
                        .category(category)
                        .productName(slug)
                        .price(new BigDecimal("10000"))
                        .status(status)
                        .build());
    }

    private Inventory persistInventory(final Product product, final int quantity, final int reserved) {
        final Inventory inv = Inventory.builder()
                .product(product)
                .quantity(quantity)
                .reservedQuantity(reserved)
                .build();
        entityManager.persistAndFlush(inv);
        entityManager.clear(); // clear first-level cache → next read is a real DB hit
        return inv;
    }

    // ── findByProduct_ProductId ───────────────────────────────────────────

    @Nested
    @DisplayName("findByProduct_ProductId")
    class FindByProductProductId {

        @Test
        @DisplayName("ATS-14 RULE 5 — returns inventory for an existing product (real-time DB read)")
        void findByProductProductId_returnsInventory_whenExists() {
            final Category category = persistCategory("INV-CAT-1");
            final Product product = persistProduct(category, "sp-ton-kho-1", Product.ProductStatus.ACTIVE);
            persistInventory(product, 50, 5);

            final Optional<Inventory> result =
                    inventoryRepository.findByProduct_ProductId(product.getProductId());

            assertThat(result).isPresent();
            assertThat(result.get().getQuantity()).isEqualTo(50);
            assertThat(result.get().getReservedQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("ATS-14 RULE 2 — availableQuantity = quantity - reservedQuantity")
        void findByProductProductId_availableQuantityIsCorrect() {
            final Category category = persistCategory("INV-CAT-2");
            final Product product = persistProduct(category, "sp-ton-kho-2", Product.ProductStatus.ACTIVE);
            persistInventory(product, 100, 30);

            final Inventory inventory =
                    inventoryRepository.findByProduct_ProductId(product.getProductId()).orElseThrow();

            // Available = 100 - 30 = 70
            assertThat(inventory.getAvailableQuantity()).isEqualTo(70);
        }

        @Test
        @DisplayName("ATS-14 RULE 2 — availableQuantity is 0 when reserved >= quantity")
        void findByProductProductId_availableQuantityIsZero_whenReservedGtQuantity() {
            final Category category = persistCategory("INV-CAT-3");
            final Product product = persistProduct(category, "sp-ton-kho-3", Product.ProductStatus.ACTIVE);
            persistInventory(product, 5, 5);  // fully reserved

            final Inventory inventory =
                    inventoryRepository.findByProduct_ProductId(product.getProductId()).orElseThrow();

            assertThat(inventory.getAvailableQuantity()).isZero();
        }

        @Test
        @DisplayName("ATS-14 — returns empty when product has no inventory record")
        void findByProductProductId_empty_whenNoInventoryRecord() {
            final Category category = persistCategory("INV-CAT-4");
            final Product product = persistProduct(category, "sp-khong-ton-kho", Product.ProductStatus.ACTIVE);
            // intentionally no inventory record

            final Optional<Inventory> result =
                    inventoryRepository.findByProduct_ProductId(product.getProductId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("ATS-14 — returns empty for non-existent product ID")
        void findByProductProductId_empty_whenProductIdDoesNotExist() {
            final Optional<Inventory> result =
                    inventoryRepository.findByProduct_ProductId(999_999L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("ATS-14 RULE 4 — inventory is returned even when product is INACTIVE (check is on product status, not here)")
        void findByProductProductId_returnsInventory_whenProductIsInactive() {
            final Category category = persistCategory("INV-CAT-5");
            final Product product = persistProduct(category, "sp-inactive-inv", Product.ProductStatus.INACTIVE);
            persistInventory(product, 20, 0);

            // InventoryRepository does not filter by product status — that is ProductRepository's job
            final Optional<Inventory> result =
                    inventoryRepository.findByProduct_ProductId(product.getProductId());

            assertThat(result).isPresent();
            assertThat(result.get().getQuantity()).isEqualTo(20);
        }

        @Test
        @DisplayName("ATS-14 — inventory with zero quantity has 0 availableQuantity")
        void findByProductProductId_availableIsZero_whenQuantityIsZero() {
            final Category category = persistCategory("INV-CAT-6");
            final Product product = persistProduct(category, "sp-het-hang", Product.ProductStatus.ACTIVE);
            persistInventory(product, 0, 0);

            final Inventory inventory =
                    inventoryRepository.findByProduct_ProductId(product.getProductId()).orElseThrow();

            assertThat(inventory.getQuantity()).isZero();
            assertThat(inventory.getReservedQuantity()).isZero();
            assertThat(inventory.getAvailableQuantity()).isZero();
        }

        @Test
        @DisplayName("ATS-14 — correct inventory is returned when multiple products exist")
        void findByProductProductId_returnsCorrectInventory_whenMultipleProductsExist() {
            final Category category = persistCategory("INV-CAT-7");
            final Product productA = persistProduct(category, "sp-a-multi", Product.ProductStatus.ACTIVE);
            final Product productB = persistProduct(category, "sp-b-multi", Product.ProductStatus.ACTIVE);
            persistInventory(productA, 10, 2);
            persistInventory(productB, 50, 10);

            final Inventory inventoryA =
                    inventoryRepository.findByProduct_ProductId(productA.getProductId()).orElseThrow();
            final Inventory inventoryB =
                    inventoryRepository.findByProduct_ProductId(productB.getProductId()).orElseThrow();

            // Each product's inventory is isolated
            assertThat(inventoryA.getQuantity()).isEqualTo(10);
            assertThat(inventoryA.getAvailableQuantity()).isEqualTo(8);
            assertThat(inventoryB.getQuantity()).isEqualTo(50);
            assertThat(inventoryB.getAvailableQuantity()).isEqualTo(40);
        }
    }

    // ── getAvailableQuantity domain method ────────────────────────────────

    @Nested
    @DisplayName("Inventory.getAvailableQuantity()")
    class GetAvailableQuantity {

        @Test
        @DisplayName("ATS-14 RULE 2 — never returns negative (clamped to 0)")
        void getAvailableQuantity_neverNegative_whenReservedExceedsQuantity() {
            // This tests the domain method directly — the DB constraint prevents reserved > quantity
            // but the domain method should be defensive anyway
            final Inventory inventory = Inventory.builder()
                    .quantity(3)
                    .reservedQuantity(5)  // abnormal state — domain clamps to 0
                    .build();

            assertThat(inventory.getAvailableQuantity()).isZero();
        }

        @Test
        @DisplayName("ATS-14 — positive quantity with no reservation")
        void getAvailableQuantity_equalsQuantity_whenNoReservation() {
            final Inventory inventory = Inventory.builder()
                    .quantity(100)
                    .reservedQuantity(0)
                    .build();

            assertThat(inventory.getAvailableQuantity()).isEqualTo(100);
        }
    }
}
