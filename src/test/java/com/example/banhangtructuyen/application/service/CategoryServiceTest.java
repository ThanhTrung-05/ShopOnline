package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.category.CategoryRequest;
import com.example.banhangtructuyen.application.dto.category.CategoryResponse;
import com.example.banhangtructuyen.application.service.impl.CategoryServiceImpl;
import com.example.banhangtructuyen.domain.exception.DuplicateResourceException;
import com.example.banhangtructuyen.domain.exception.ResourceInUseException;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Category;
import com.example.banhangtructuyen.domain.repository.CategoryRepository;
import com.example.banhangtructuyen.domain.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CategoryServiceImpl}. Repository layer is mocked — no DB required.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private Category existingCategory;

    @BeforeEach
    void setUp() {
        existingCategory = Category.builder()
                .categoryId(1L)
                .categoryCode("THUC_PHAM")
                .categoryName("Thuc pham")
                .description("Thuc pham tuoi song")
                .vatRate(new BigDecimal("5"))
                .sortOrder(0)
                .status(Category.CategoryStatus.ACTIVE)
                .build();
    }

    private CategoryRequest validRequest(final BigDecimal vatRate) {
        return new CategoryRequest("Thuc pham", "THUC_PHAM", "Thuc pham tuoi song", vatRate, "ACTIVE");
    }

    // ══════════════════════════════════════════════════════════════════════
    // findAll / findById
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findAll / findById")
    class Read {

        @Test
        @DisplayName("findAll — returns mapped list")
        void findAll_returnsMappedList() {
            when(categoryRepository.findAll()).thenReturn(List.of(existingCategory));

            final List<CategoryResponse> result = categoryService.findAll();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).categoryId()).isEqualTo(1L);
            assertThat(result.get(0).categoryName()).isEqualTo("Thuc pham");
        }

        @Test
        @DisplayName("findById — found, returns mapped response")
        void findById_found_returnsResponse() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(existingCategory));

            final CategoryResponse result = categoryService.findById(1L);

            assertThat(result.categoryId()).isEqualTo(1L);
            assertThat(result.vatRate()).isEqualByComparingTo("5");
        }

        @Test
        @DisplayName("findById — not found, throws ResourceNotFoundException")
        void findById_notFound_throws404() {
            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.findById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // create
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("Valid request, VAT 5% — creates and returns category")
        void create_validVat5_succeeds() {
            when(categoryRepository.existsByCategoryCode("THUC_PHAM")).thenReturn(false);
            when(categoryRepository.existsByCategoryName("Thuc pham")).thenReturn(false);
            when(categoryRepository.save(any(Category.class))).thenReturn(existingCategory);

            final CategoryResponse result = categoryService.create(validRequest(new BigDecimal("5")));

            assertThat(result.vatRate()).isEqualByComparingTo("5");
            verify(categoryRepository).save(any(Category.class));
        }

        @Test
        @DisplayName("Valid request, VAT 10% — creates and returns category")
        void create_validVat10_succeeds() {
            final Category category10 = Category.builder()
                    .categoryId(2L).categoryCode("DIEN_MAY").categoryName("Dien may")
                    .vatRate(new BigDecimal("10")).sortOrder(0)
                    .status(Category.CategoryStatus.ACTIVE).build();

            when(categoryRepository.existsByCategoryCode("DIEN_MAY")).thenReturn(false);
            when(categoryRepository.existsByCategoryName("Dien may")).thenReturn(false);
            when(categoryRepository.save(any(Category.class))).thenReturn(category10);

            final CategoryResponse result = categoryService.create(
                    new CategoryRequest("Dien may", "DIEN_MAY", null, new BigDecimal("10"), "ACTIVE"));

            assertThat(result.vatRate()).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("VAT rate not 5 or 10 — rejected")
        void create_invalidVatRate_throws() {
            assertThatThrownBy(() -> categoryService.create(validRequest(new BigDecimal("8"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("5 or 10");

            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("Duplicate category code — throws DuplicateResourceException")
        void create_duplicateCode_throws409() {
            when(categoryRepository.existsByCategoryCode("THUC_PHAM")).thenReturn(true);

            assertThatThrownBy(() -> categoryService.create(validRequest(new BigDecimal("5"))))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("THUC_PHAM");

            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Duplicate category name — throws DuplicateResourceException")
        void create_duplicateName_throws409() {
            when(categoryRepository.existsByCategoryCode("THUC_PHAM")).thenReturn(false);
            when(categoryRepository.existsByCategoryName("Thuc pham")).thenReturn(true);

            assertThatThrownBy(() -> categoryService.create(validRequest(new BigDecimal("5"))))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Thuc pham");

            verify(categoryRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // update
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("Valid update — succeeds")
        void update_valid_succeeds() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(existingCategory));
            when(categoryRepository.existsByCategoryCodeAndCategoryIdNot("THUC_PHAM", 1L)).thenReturn(false);
            when(categoryRepository.existsByCategoryNameAndCategoryIdNot("Thuc pham moi", 1L)).thenReturn(false);
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            final CategoryResponse result = categoryService.update(1L,
                    new CategoryRequest("Thuc pham moi", "THUC_PHAM", "desc", new BigDecimal("10"), "ACTIVE"));

            assertThat(result.categoryName()).isEqualTo("Thuc pham moi");
            assertThat(result.vatRate()).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("Category not found — throws ResourceNotFoundException")
        void update_notFound_throws404() {
            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.update(99L, validRequest(new BigDecimal("5"))))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Duplicate code against another category — throws DuplicateResourceException")
        void update_duplicateCode_throws409() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(existingCategory));
            when(categoryRepository.existsByCategoryCodeAndCategoryIdNot("DIEN_MAY", 1L)).thenReturn(true);

            assertThatThrownBy(() -> categoryService.update(1L,
                    new CategoryRequest("Thuc pham", "DIEN_MAY", null, new BigDecimal("5"), "ACTIVE")))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Invalid VAT rate on update — rejected before touching repository")
        void update_invalidVatRate_throws() {
            assertThatThrownBy(() -> categoryService.update(1L, validRequest(new BigDecimal("7"))))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(categoryRepository);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // delete
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("No products reference category — deletes successfully")
        void delete_noProducts_succeeds() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(existingCategory));
            when(productRepository.existsByCategory_CategoryId(1L)).thenReturn(false);

            categoryService.delete(1L);

            verify(categoryRepository).delete(existingCategory);
        }

        @Test
        @DisplayName("Products reference category — throws ResourceInUseException")
        void delete_hasProducts_throws409() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(existingCategory));
            when(productRepository.existsByCategory_CategoryId(1L)).thenReturn(true);

            assertThatThrownBy(() -> categoryService.delete(1L))
                    .isInstanceOf(ResourceInUseException.class);

            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Category not found — throws ResourceNotFoundException")
        void delete_notFound_throws404() {
            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.delete(99L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(productRepository);
        }
    }
}
