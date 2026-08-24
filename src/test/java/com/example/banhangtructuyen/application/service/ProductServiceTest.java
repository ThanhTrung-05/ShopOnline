package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.product.ProductDetailResponse;
import com.example.banhangtructuyen.application.dto.product.ProductRequest;
import com.example.banhangtructuyen.application.dto.product.ProductResponse;
import com.example.banhangtructuyen.application.service.impl.ProductServiceImpl;
import com.example.banhangtructuyen.config.AppProperties;
import com.example.banhangtructuyen.domain.exception.DuplicateResourceException;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Category;
import com.example.banhangtructuyen.domain.model.Inventory;
import com.example.banhangtructuyen.domain.model.Product;
import com.example.banhangtructuyen.domain.repository.CategoryRepository;
import com.example.banhangtructuyen.domain.repository.InventoryRepository;
import com.example.banhangtructuyen.domain.repository.ProductRepository;
import com.example.banhangtructuyen.infrastructure.cache.CacheKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProductServiceImpl}.
 * Redis and DB are mocked — no Spring context is loaded.
 *
 * <p>Coverage matrix:
 * <ul>
 *   <li>findAll — cache HIT</li>
 *   <li>findAll — cache MISS</li>
 *   <li>findAll — Redis error (graceful fallback)</li>
 *   <li>findById — cache HIT</li>
 *   <li>findById — cache MISS, product found</li>
 *   <li>findById — cache MISS, product NOT found → 404</li>
 *   <li>findById — Redis down, falls back to DB</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    // ── Mocks ──────────────────────────────────────────────────────────────

    @Mock private ProductRepository       productRepository;
    @Mock private CategoryRepository      categoryRepository;
    @Mock private InventoryRepository     inventoryRepository;
    @Mock private StringRedisTemplate     redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    /** Real ObjectMapper — validates actual serialisation/deserialisation. */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private AppProperties appProperties;

    @InjectMocks
    private ProductServiceImpl productService;

    // ── Test Fixtures ──────────────────────────────────────────────────────

    private Product activeProduct;
    private ProductResponse productResponse;

    @BeforeEach
    void setUp() throws Exception {
        // Real AppProperties with defaults
        appProperties = new AppProperties();

        // Inject via constructor manually (Mockito @InjectMocks cannot inject final field)
        productService = new ProductServiceImpl(productRepository, categoryRepository, inventoryRepository,
                redisTemplate, objectMapper, appProperties);

        // Build a minimal ACTIVE product fixture
        final Category category = Category.builder()
                .categoryId(1L)
                .categoryCode("THUC_PHAM")
                .categoryName("Thực phẩm")
                .vatRate(new BigDecimal("10.00"))
                .status(Category.CategoryStatus.ACTIVE)
                .build();

        final Inventory inventory = Inventory.builder()
                .inventoryId(1L)
                .quantity(100)
                .reservedQuantity(5)
                .build();

        activeProduct = Product.builder()
                .productId(1L)
                .productSlug("TP001")
                .productName("Gạo ST25 5kg")
                .price(new BigDecimal("180000"))
                .status(Product.ProductStatus.ACTIVE)
                .category(category)
                .inventory(inventory)
                .build();

        productResponse = new ProductResponse(
                1L, "Gạo ST25 5kg", "TP001",
                new BigDecimal("180000"), null, null,
                1L, "Thực phẩm", 95, "ACTIVE"
        );

        // Wire valueOps once for all tests that need it
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private ProductRequest validRequest() {
        return new ProductRequest("Gạo ST25 5kg", "TP001", 1L, "desc",
                new BigDecimal("180000"), null, "ACTIVE", 100);
    }

    // ══════════════════════════════════════════════════════════════════════
    // create
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("create — Product")
    class Create {

        @Test
        @DisplayName("Valid request — creates product and linked inventory")
        void create_valid_succeeds() {
            final Category category = activeProduct.getCategory();
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(productRepository.existsByProductSlug("TP001")).thenReturn(false);
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

            final ProductDetailResponse result = productService.create(validRequest());

            assertThat(result.name()).isEqualTo("Gạo ST25 5kg");
            assertThat(result.categoryId()).isEqualTo(1L);
            verify(productRepository).save(any(Product.class));
            verify(inventoryRepository).save(any(Inventory.class));
        }

        @Test
        @DisplayName("Category not found — throws ResourceNotFoundException")
        void create_categoryNotFound_throws404() {
            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.create(new ProductRequest(
                    "Gạo ST25 5kg", "TP001", 99L, "desc",
                    new BigDecimal("180000"), null, "ACTIVE", 100)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("Duplicate product slug — throws DuplicateResourceException")
        void create_duplicateSlug_throws409() {
            final Category category = activeProduct.getCategory();
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(productRepository.existsByProductSlug("TP001")).thenReturn(true);

            assertThatThrownBy(() -> productService.create(validRequest()))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("TP001");

            verify(productRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // update
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("update — Product")
    class Update {

        @Test
        @DisplayName("Valid update — succeeds")
        void update_valid_succeeds() {
            final Category category = activeProduct.getCategory();
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(productRepository.findByIdWithInventory(1L)).thenReturn(Optional.of(activeProduct));
            when(productRepository.existsByProductSlugAndProductIdNot("TP001", 1L)).thenReturn(false);
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            final ProductDetailResponse result = productService.update(1L, validRequest());

            assertThat(result.name()).isEqualTo("Gạo ST25 5kg");
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Product not found — throws ResourceNotFoundException")
        void update_notFound_throws404() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(activeProduct.getCategory()));
            when(productRepository.findByIdWithInventory(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.update(99L, validRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Category not found — throws ResourceNotFoundException")
        void update_categoryNotFound_throws404() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.update(1L, validRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("Duplicate slug against another product — throws DuplicateResourceException")
        void update_duplicateSlug_throws409() {
            final Category category = activeProduct.getCategory();
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(productRepository.findByIdWithInventory(1L)).thenReturn(Optional.of(activeProduct));
            when(productRepository.existsByProductSlugAndProductIdNot("TP001", 1L)).thenReturn(true);

            assertThatThrownBy(() -> productService.update(1L, validRequest()))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(productRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // delete
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("delete — Product")
    class Delete {

        @Test
        @DisplayName("Existing product — soft-deletes by flipping status to DELETED")
        void delete_existing_softDeletes() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(activeProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            productService.delete(1L);

            assertThat(activeProduct.getStatus()).isEqualTo(Product.ProductStatus.DELETED);
            verify(productRepository).save(activeProduct);
        }

        @Test
        @DisplayName("Product not found — throws ResourceNotFoundException")
        void delete_notFound_throws404() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.delete(99L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(productRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // findAll
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findAll — Product List")
    class FindAll {

        @Test
        @DisplayName("Cache HIT — returns cached page without touching DB")
        void findAll_cacheHit_returnsPageFromCache() throws Exception {
            // Arrange
            final String cacheKey = CacheKeys.productList(0, 20, null, null, null, null);
            final String cachedJson = objectMapper.writeValueAsString(
                    Map.of("content", List.of(productResponse), "total", 1L));

            when(valueOps.get(cacheKey)).thenReturn(cachedJson);

            // Act
            final Page<ProductResponse> result = productService.findAll(0, 20, null, null, null, null);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1L);

            // DB must NOT be touched
            verifyNoInteractions(productRepository);
            verify(valueOps, never()).set(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Cache MISS — queries DB, maps results, writes cache")
        void findAll_cacheMiss_queriesDBAndWritesCache() {
            // Arrange
            final String cacheKey = CacheKeys.productList(0, 20, 1L, null, null, null);
            final Page<Product> dbPage = new PageImpl<>(List.of(activeProduct),
                    PageRequest.of(0, 20), 1L);

            when(valueOps.get(cacheKey)).thenReturn(null);
            when(productRepository.findActiveProducts(eq(1L), isNull(), isNull(), isNull(), any()))
                    .thenReturn(dbPage);

            // Act
            final Page<ProductResponse> result = productService.findAll(0, 20, 1L, null, null, null);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);

            final ProductResponse dto = result.getContent().get(0);
            assertThat(dto.id()).isEqualTo(1L);
            assertThat(dto.name()).isEqualTo("Gạo ST25 5kg");
            assertThat(dto.inventoryCount()).isEqualTo(95); // 100 - 5 reserved

            // Cache must be populated
            verify(valueOps).set(eq(cacheKey), anyString(), any());
        }

        @Test
        @DisplayName("Redis error on read — falls back to DB gracefully")
        void findAll_redisReadError_fallsBackToDb() {
            // Arrange
            final Page<Product> dbPage = new PageImpl<>(
                    List.of(activeProduct), PageRequest.of(0, 20), 1L);

            when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis connection refused"));
            when(productRepository.findActiveProducts(isNull(), isNull(), isNull(), isNull(), any()))
                    .thenReturn(dbPage);

            // Act — must NOT throw
            final Page<ProductResponse> result = productService.findAll(0, 20, null, null, null, null);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("DB returns empty page — returns empty page (no exception)")
        void findAll_emptyResult_returnsEmptyPage() {
            // Arrange
            final Page<Product> emptyPage = Page.empty(PageRequest.of(0, 20));

            when(valueOps.get(anyString())).thenReturn(null);
            when(productRepository.findActiveProducts(isNull(), isNull(), isNull(), isNull(), any()))
                    .thenReturn(emptyPage);

            // Act
            final Page<ProductResponse> result = productService.findAll(0, 20, null, null, null, null);

            // Assert
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // findById
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findById — Product Detail")
    class FindById {

        @Test
        @DisplayName("Cache HIT — returns cached product without touching DB")
        void findById_cacheHit_returnsFromCache() throws Exception {
            // Arrange
            final String cacheKey = CacheKeys.productDetail(1L);
            final String cachedJson = objectMapper.writeValueAsString(productResponse);

            when(valueOps.get(cacheKey)).thenReturn(cachedJson);

            // Act
            final ProductResponse result = productService.findById(1L);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("Gạo ST25 5kg");

            verifyNoInteractions(productRepository);
        }

        @Test
        @DisplayName("Cache MISS + product found — queries DB, maps, writes cache")
        void findById_cacheMiss_productFound_mapsAndCaches() {
            // Arrange
            final String cacheKey = CacheKeys.productDetail(1L);

            when(valueOps.get(cacheKey)).thenReturn(null);
            when(productRepository.findActiveById(1L)).thenReturn(Optional.of(activeProduct));

            // Act
            final ProductResponse result = productService.findById(1L);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.categoryName()).isEqualTo("Thực phẩm");
            assertThat(result.inventoryCount()).isEqualTo(95);
            assertThat(result.status()).isEqualTo("ACTIVE");

            // Cache must be written
            verify(valueOps).set(eq(cacheKey), anyString(), any());
        }

        @Test
        @DisplayName("Cache MISS + product NOT found — throws ResourceNotFoundException (HTTP 404)")
        void findById_cacheMiss_productNotFound_throws404() {
            // Arrange
            when(valueOps.get(anyString())).thenReturn(null);
            when(productRepository.findActiveById(9999L)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> productService.findById(9999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("9999");

            // Cache must NOT be written
            verify(valueOps, never()).set(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Redis down on read — falls back to DB gracefully")
        void findById_redisReadError_fallsBackToDb() {
            // Arrange
            when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis timeout"));
            when(productRepository.findActiveById(1L)).thenReturn(Optional.of(activeProduct));

            // Act — must NOT throw
            final ProductResponse result = productService.findById(1L);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Inventory is null — inventoryCount defaults to 0")
        void findById_inventoryNull_inventoryCountIsZero() {
            // Arrange — product without linked inventory (edge case)
            final Product productWithoutInventory = Product.builder()
                    .productId(2L)
                    .productSlug("TEST-NULL-INV")
                    .productName("No Inventory Product")
                    .price(new BigDecimal("50000"))
                    .status(Product.ProductStatus.ACTIVE)
                    .category(activeProduct.getCategory())
                    .inventory(null)
                    .build();

            when(valueOps.get(anyString())).thenReturn(null);
            when(productRepository.findActiveById(2L)).thenReturn(Optional.of(productWithoutInventory));

            // Act
            final ProductResponse result = productService.findById(2L);

            // Assert
            assertThat(result.inventoryCount()).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ATS-6 VAT validation — create and update
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ATS-6 — VAT rate validation (5% and 10% only)")
    class VatValidation {

        private Category categoryWithVat(final BigDecimal vatRate) {
            return Category.builder()
                    .categoryId(1L)
                    .categoryCode("TEST-VAT")
                    .categoryName("VAT Test Category")
                    .vatRate(vatRate)
                    .status(Category.CategoryStatus.ACTIVE)
                    .build();
        }

        @Test
        @DisplayName("Create — VAT 5% accepted (category with vatRate=5)")
        void create_vat5Percent_succeeds() {
            final Category cat5 = categoryWithVat(new BigDecimal("5.00"));
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat5));
            when(productRepository.existsByProductSlug("TP001")).thenReturn(false);
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

            final ProductDetailResponse result = productService.create(validRequest());

            assertThat(result.vatRate()).isEqualByComparingTo("5.00");
        }

        @Test
        @DisplayName("Create — VAT 10% accepted (category with vatRate=10)")
        void create_vat10Percent_succeeds() {
            final Category cat10 = categoryWithVat(new BigDecimal("10.00"));
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat10));
            when(productRepository.existsByProductSlug("TP001")).thenReturn(false);
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

            final ProductDetailResponse result = productService.create(validRequest());

            assertThat(result.vatRate()).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("Create — VAT 7% rejected (only 5 and 10 are valid)")
        void create_vatInvalid_throws400() {
            final Category catBad = categoryWithVat(new BigDecimal("7.00"));
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(catBad));

            assertThatThrownBy(() -> productService.create(validRequest()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Only 5% and 10% are allowed");

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("Create — VAT 0% rejected")
        void create_vat0Percent_throws400() {
            final Category catZero = categoryWithVat(new BigDecimal("0.00"));
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(catZero));

            assertThatThrownBy(() -> productService.create(validRequest()))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("Update — invalid VAT rejected")
        void update_vatInvalid_throws400() {
            final Category catBad = categoryWithVat(new BigDecimal("3.00"));
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(catBad));

            assertThatThrownBy(() -> productService.update(1L, validRequest()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Only 5% and 10% are allowed");

            verify(productRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ATS-6 — findAllForAdmin
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findAllForAdmin — Admin product management list")
    class FindAllForAdmin {

        @Test
        @DisplayName("Returns page from DB without Redis caching")
        void findAllForAdmin_returnsPage_noCache() {
            final Page<Product> dbPage = new PageImpl<>(List.of(activeProduct),
                    PageRequest.of(0, 20), 1L);
            when(productRepository.findAllProducts(isNull(), isNull(), any()))
                    .thenReturn(dbPage);

            final Page<ProductResponse> result = productService.findAllForAdmin(0, 20, null, null);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).id()).isEqualTo(1L);

            // Redis must NOT be touched for admin list
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("Returns empty page when no products exist")
        void findAllForAdmin_emptyPage_returnsEmpty() {
            when(productRepository.findAllProducts(isNull(), isNull(), any()))
                    .thenReturn(Page.empty(PageRequest.of(0, 20)));

            final Page<ProductResponse> result = productService.findAllForAdmin(0, 20, null, null);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("Filters by category ID correctly")
        void findAllForAdmin_filterByCategoryId() {
            final Page<Product> dbPage = new PageImpl<>(List.of(activeProduct),
                    PageRequest.of(0, 20), 1L);
            when(productRepository.findAllProducts(eq(1L), isNull(), any()))
                    .thenReturn(dbPage);

            final Page<ProductResponse> result = productService.findAllForAdmin(0, 20, 1L, null);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).categoryId()).isEqualTo(1L);
        }
    }
}
