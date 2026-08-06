package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.config.AuditingConfig;
import com.example.banhangtructuyen.domain.model.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Repository-level tests for {@link CategoryRepository}, backed by the H2
 * in-memory DB (Oracle compatibility mode) configured for the "test" profile.
 * Verifies the ATS-5 {@code UK_CATEGORIES_NAME} constraint and lookup queries.
 */
@DataJpaTest
@Import(AuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("CategoryRepository Tests")
class CategoryRepositoryTest {

    @org.springframework.beans.factory.annotation.Autowired
    private CategoryRepository categoryRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private TestEntityManager entityManager;

    private Category persistCategory(final String code, final String name) {
        final Category category = Category.builder()
                .categoryCode(code)
                .categoryName(name)
                .vatRate(new BigDecimal("5"))
                .sortOrder(0)
                .status(Category.CategoryStatus.ACTIVE)
                .build();
        return entityManager.persistFlushFind(category);
    }

    private Category saveAndFlushCategory(final String code, final String name) {
        final Category category = Category.builder()
                .categoryCode(code)
                .categoryName(name)
                .vatRate(new BigDecimal("5"))
                .sortOrder(0)
                .status(Category.CategoryStatus.ACTIVE)
                .build();
        return categoryRepository.saveAndFlush(category);
    }

    @Test
    @DisplayName("existsByCategoryCode — true when code exists")
    void existsByCategoryCode_found_returnsTrue() {
        persistCategory("THUC_PHAM", "Thuc pham");

        assertThat(categoryRepository.existsByCategoryCode("THUC_PHAM")).isTrue();
        assertThat(categoryRepository.existsByCategoryCode("KHONG_TON_TAI")).isFalse();
    }

    @Test
    @DisplayName("existsByCategoryName — true when name exists")
    void existsByCategoryName_found_returnsTrue() {
        persistCategory("THUC_PHAM", "Thuc pham");

        assertThat(categoryRepository.existsByCategoryName("Thuc pham")).isTrue();
        assertThat(categoryRepository.existsByCategoryName("Khong ton tai")).isFalse();
    }

    @Test
    @DisplayName("existsByCategoryCodeAndCategoryIdNot — excludes self")
    void existsByCategoryCodeAndCategoryIdNot_excludesSelf() {
        final Category category = persistCategory("THUC_PHAM", "Thuc pham");

        assertThat(categoryRepository
                .existsByCategoryCodeAndCategoryIdNot("THUC_PHAM", category.getCategoryId()))
                .isFalse();

        final Category other = persistCategory("DIEN_MAY", "Dien may");
        assertThat(categoryRepository
                .existsByCategoryCodeAndCategoryIdNot("THUC_PHAM", other.getCategoryId()))
                .isTrue();
    }

    @Test
    @DisplayName("existsByCategoryNameAndCategoryIdNot — excludes self")
    void existsByCategoryNameAndCategoryIdNot_excludesSelf() {
        final Category category = persistCategory("THUC_PHAM", "Thuc pham");

        assertThat(categoryRepository
                .existsByCategoryNameAndCategoryIdNot("Thuc pham", category.getCategoryId()))
                .isFalse();

        final Category other = persistCategory("DIEN_MAY", "Dien may");
        assertThat(categoryRepository
                .existsByCategoryNameAndCategoryIdNot("Thuc pham", other.getCategoryId()))
                .isTrue();
    }

    @Test
    @DisplayName("UK_CATEGORIES_NAME — duplicate category name is rejected at DB level")
    void duplicateCategoryName_violatesUniqueConstraint() {
        persistCategory("THUC_PHAM", "Thuc pham");

        assertThatThrownBy(() -> saveAndFlushCategory("DIEN_MAY", "Thuc pham"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("UK_CATEGORIES_CODE — duplicate category code is rejected at DB level (pre-existing constraint)")
    void duplicateCategoryCode_violatesUniqueConstraint() {
        persistCategory("THUC_PHAM", "Thuc pham");

        assertThatThrownBy(() -> saveAndFlushCategory("THUC_PHAM", "Thuc pham khac"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
