package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.config.AuditingConfig;
import com.example.banhangtructuyen.domain.model.Category;
import com.example.banhangtructuyen.domain.model.Inventory;
import com.example.banhangtructuyen.domain.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Repository-level tests for {@link ProductRepository}, backed by the H2
 * in-memory DB (Oracle compatibility mode) configured for the "test" profile.
 * Verifies the ATS-4 "only ACTIVE products are visible" rule at the query level.
 */
@DataJpaTest
@Import(AuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("ProductRepository Tests")
class ProductRepositoryTest {

    @org.springframework.beans.factory.annotation.Autowired
    private ProductRepository productRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private TestEntityManager entityManager;

    private Category persistCategory(final String code, final BigDecimal vatRate) {
        final Category category = Category.builder()
                .categoryCode(code)
                .categoryName(code)
                .vatRate(vatRate)
                .sortOrder(0)
                .status(Category.CategoryStatus.ACTIVE)
                .build();
        return entityManager.persistFlushFind(category);
    }

    private Product persistProduct(final Category category, final String slug,
                                    final BigDecimal price, final Product.ProductStatus status) {
        final Product product = Product.builder()
                .productSlug(slug)
                .category(category)
                .productName(slug)
                .price(price)
                .status(status)
                .build();
        return entityManager.persistFlushFind(product);
    }

    private void persistInventory(final Product product, final int quantity, final int reserved) {
        final Inventory inventory = Inventory.builder()
                .product(product)
                .quantity(quantity)
                .reservedQuantity(reserved)
                .build();
        entityManager.persistAndFlush(inventory);
        entityManager.clear();
    }

    @Test
    @DisplayName("findActiveById — returns product when ACTIVE, with category and inventory fetched")
    void findActiveById_returnsProduct_whenActive() {
        final Category category = persistCategory("FRESH", new BigDecimal("5.00"));
        final Product product = persistProduct(category, "rau-cai-1kg",
                new BigDecimal("20000"), Product.ProductStatus.ACTIVE);
        persistInventory(product, 100, 10);

        final Optional<Product> found = productRepository.findActiveById(product.getProductId());

        assertThat(found).isPresent();
        assertThat(found.get().getCategory().getVatRate()).isEqualByComparingTo("5.00");
        assertThat(found.get().getInventory().getAvailableQuantity()).isEqualTo(90);
    }

    @Test
    @DisplayName("findActiveById — empty when product is INACTIVE (ATS-4 404 rule)")
    void findActiveById_empty_whenInactive() {
        final Category category = persistCategory("BEVERAGES", new BigDecimal("10.00"));
        final Product product = persistProduct(category, "nuoc-ngot",
                new BigDecimal("12000"), Product.ProductStatus.INACTIVE);
        persistInventory(product, 50, 0);

        final Optional<Product> found = productRepository.findActiveById(product.getProductId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findActiveById — empty when product does not exist")
    void findActiveById_empty_whenNotFound() {
        final Optional<Product> found = productRepository.findActiveById(999_999L);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findActiveById — empty when product is DELETED")
    void findActiveById_empty_whenDeleted() {
        final Category category = persistCategory("SNACKS", new BigDecimal("10.00"));
        final Product product = persistProduct(category, "banh-keo",
                new BigDecimal("25000"), Product.ProductStatus.DELETED);
        persistInventory(product, 10, 0);

        final Optional<Product> found = productRepository.findActiveById(product.getProductId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByProductSlug — true when slug is already taken")
    void existsByProductSlug_true_whenTaken() {
        final Category category = persistCategory("DAIRY", new BigDecimal("5.00"));
        persistProduct(category, "sua-tuoi-1l", new BigDecimal("30000"), Product.ProductStatus.ACTIVE);

        assertThat(productRepository.existsByProductSlug("sua-tuoi-1l")).isTrue();
    }

    @Test
    @DisplayName("existsByProductSlug — false when slug is free")
    void existsByProductSlug_false_whenFree() {
        assertThat(productRepository.existsByProductSlug("khong-ton-tai")).isFalse();
    }

    @Test
    @DisplayName("existsByProductSlugAndProductIdNot — false when slug belongs to the same product being updated")
    void existsByProductSlugAndProductIdNot_false_whenSameProduct() {
        final Category category = persistCategory("MEAT", new BigDecimal("10.00"));
        final Product product = persistProduct(category, "thit-bo-1kg",
                new BigDecimal("250000"), Product.ProductStatus.ACTIVE);

        final boolean exists = productRepository.existsByProductSlugAndProductIdNot(
                "thit-bo-1kg", product.getProductId());

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("existsByProductSlugAndProductIdNot — true when slug belongs to a different product")
    void existsByProductSlugAndProductIdNot_true_whenDifferentProduct() {
        final Category category = persistCategory("SEAFOOD", new BigDecimal("5.00"));
        final Product product1 = persistProduct(category, "ca-hoi-1kg",
                new BigDecimal("300000"), Product.ProductStatus.ACTIVE);
        final Product product2 = persistProduct(category, "ca-hoi-2kg",
                new BigDecimal("580000"), Product.ProductStatus.ACTIVE);

        final boolean exists = productRepository.existsByProductSlugAndProductIdNot(
                "ca-hoi-1kg", product2.getProductId());

        assertThat(exists).isTrue();
    }
}
