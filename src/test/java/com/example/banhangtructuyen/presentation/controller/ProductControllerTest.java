package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.product.ProductDetailResponse;
import com.example.banhangtructuyen.application.dto.product.ProductRequest;
import com.example.banhangtructuyen.application.dto.product.ProductResponse;
import com.example.banhangtructuyen.application.service.ProductService;
import com.example.banhangtructuyen.domain.exception.DuplicateResourceException;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc integration tests for {@link ProductController}.
 *
 * <p>Uses {@code @SpringBootTest + @AutoConfigureMockMvc} (full Spring context)
 * consistent with the existing test convention in this project.
 * The {@link ProductService} is mocked via {@code @MockBean} — no DB or Redis required.
 *
 * <p>Both endpoints are public; no JWT token injection is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ProductController MockMvc Tests")
class ProductControllerTest {

    private static final String CLIENT_ID = "shoponline-backend";
    private static final String ADMIN_TOKEN = "admin-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @org.junit.jupiter.api.BeforeEach
    void setUpSecurity() {
        when(jwtDecoder.decode(ADMIN_TOKEN))
                .thenReturn(jwtWithRoles(ADMIN_TOKEN, "admin-subject", List.of("ADMIN")));
    }

    // ── Shared fixture ─────────────────────────────────────────────────────

    private static ProductResponse sampleProduct() {
        return new ProductResponse(
                1L, "Gạo ST25 5kg", "TP001",
                new BigDecimal("180000"), null, "<ul><li>VAT: 5%</li></ul>",
                1L, "Thực phẩm", 95, "ACTIVE"
        );
    }

    private static Page<ProductResponse> singleProductPage(final ProductResponse product) {
        return new PageImpl<>(List.of(product), PageRequest.of(0, 20), 1L);
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET /api/v1/products
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/products — Product List")
    class ListProducts {

        @Test
        @DisplayName("200 — default pagination, returns product list")
        void listProducts_shouldReturn200_withDefaultPagination() throws Exception {
            // Arrange
            when(productService.findAll(0, 20, null, null, null, null))
                    .thenReturn(singleProductPage(sampleProduct()));

            // Act + Assert
            mockMvc.perform(get("/api/v1/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].id").value(1))
                    .andExpect(jsonPath("$.data.content[0].name").value("Gạo ST25 5kg"))
                    .andExpect(jsonPath("$.data.content[0].price").value(180000))
                    .andExpect(jsonPath("$.data.content[0].categoryName").value("Thực phẩm"))
                    .andExpect(jsonPath("$.data.content[0].inventoryCount").value(95))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.totalPages").value(1))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.traceId").exists());
        }

        @Test
        @DisplayName("200 — filtered by category ID")
        void listProducts_shouldReturn200_withCategoryFilter() throws Exception {
            // Arrange
            when(productService.findAll(0, 20, 1L, null, null, null))
                    .thenReturn(singleProductPage(sampleProduct()));

            // Act + Assert
            mockMvc.perform(get("/api/v1/products")
                            .param("categoryId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].categoryName").value("Thực phẩm"));
        }

        @Test
        @DisplayName("200 — filtered by search keyword")
        void listProducts_shouldReturn200_withSearchKeyword() throws Exception {
            // Arrange
            when(productService.findAll(0, 20, null, "gạo", null, null))
                    .thenReturn(singleProductPage(sampleProduct()));

            // Act + Assert
            mockMvc.perform(get("/api/v1/products")
                            .param("search", "gạo"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].name").value("Gạo ST25 5kg"));
        }

        @Test
        @DisplayName("200 — empty result set")
        void listProducts_shouldReturn200_withEmptyPage() throws Exception {
            // Arrange
            final Page<ProductResponse> emptyPage = new PageImpl<>(
                    Collections.emptyList(), PageRequest.of(0, 20), 0L);
            when(productService.findAll(0, 20, 2L, "nonexistent", null, null))
                    .thenReturn(emptyPage);

            // Act + Assert
            mockMvc.perform(get("/api/v1/products")
                            .param("categoryId", "2")
                            .param("search", "nonexistent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }

        @Test
        @DisplayName("400 — page is negative")
        void listProducts_shouldReturn400_whenPageNegative() throws Exception {
            mockMvc.perform(get("/api/v1/products")
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("400 — size exceeds maximum (100)")
        void listProducts_shouldReturn400_whenSizeTooLarge() throws Exception {
            mockMvc.perform(get("/api/v1/products")
                            .param("size", "200"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("400 — size is zero")
        void listProducts_shouldReturn400_whenSizeIsZero() throws Exception {
            mockMvc.perform(get("/api/v1/products")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET /api/v1/products/{id}
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/products/{id} — Product Detail")
    class GetProduct {

        private ProductDetailResponse sampleProductDetail() {
            return new ProductDetailResponse(
                    1L, "Gạo ST25 5kg", "TP001",
                    new BigDecimal("180000"), new BigDecimal("5.00"), new BigDecimal("9000"), new BigDecimal("189000"),
                    null, "<ul><li>VAT: 5%</li></ul>",
                    1L, "Thực phẩm", 95, "ACTIVE"
            );
        }

        @Test
        @DisplayName("200 — product exists and is ACTIVE")
        void getProduct_shouldReturn200_whenProductExists() throws Exception {
            // Arrange
            when(productService.findDetailById(1L)).thenReturn(sampleProductDetail());

            // Act + Assert
            mockMvc.perform(get("/api/v1/products/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Gạo ST25 5kg"))
                    .andExpect(jsonPath("$.data.price").value(180000))
                    .andExpect(jsonPath("$.data.vatRate").value(5.0))
                    .andExpect(jsonPath("$.data.vatAmount").value(9000))
                    .andExpect(jsonPath("$.data.priceIncludingVat").value(189000))
                    .andExpect(jsonPath("$.data.categoryId").value(1))
                    .andExpect(jsonPath("$.data.inventoryCount").value(95))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.traceId").exists());
        }

        @Test
        @DisplayName("400 — invalid product id")
        void getProduct_shouldReturn400_whenIdInvalid() throws Exception {
            mockMvc.perform(get("/api/v1/products/0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — product does not exist or is INACTIVE/DELETED")
        void getProduct_shouldReturn404_whenProductNotFound() throws Exception {
            // Arrange
            when(productService.findDetailById(9999L))
                    .thenThrow(new ResourceNotFoundException("Product", 9999L));

            // Act + Assert
            mockMvc.perform(get("/api/v1/products/9999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(containsString("9999")));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // POST /api/v1/products
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/products — Create Product")
    class CreateProduct {

        @Autowired
        private ObjectMapper objectMapper;

        private ProductDetailResponse sampleProductDetail() {
            return new ProductDetailResponse(
                    1L, "Gạo ST25 5kg", "TP001",
                    new BigDecimal("180000"), new BigDecimal("5.00"), new BigDecimal("9000"), new BigDecimal("189000"),
                    null, "desc", 1L, "Thực phẩm", 100, "ACTIVE"
            );
        }

        private ProductRequest sampleRequest() {
            return new ProductRequest("Gạo ST25 5kg", "TP001", 1L, "desc",
                    new BigDecimal("180000"), null, "ACTIVE", 100);
        }

        @Test
        @DisplayName("200 — created successfully")
        void createProduct_valid_returns200() throws Exception {
            when(productService.create(any(ProductRequest.class))).thenReturn(sampleProductDetail());

            mockMvc.perform(post("/api/v1/products")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(sampleRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Gạo ST25 5kg"))
                    .andExpect(jsonPath("$.data.categoryId").value(1));
        }

        @Test
        @DisplayName("409 — duplicate product slug")
        void createProduct_duplicateSlug_returns409() throws Exception {
            when(productService.create(any(ProductRequest.class)))
                    .thenThrow(new DuplicateResourceException("Product slug already exists: TP001"));

            mockMvc.perform(post("/api/v1/products")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(sampleRequest())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — category does not exist")
        void createProduct_categoryNotFound_returns404() throws Exception {
            when(productService.create(any(ProductRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Category", 99L));

            final String body = objectMapper.writeValueAsString(
                    new ProductRequest("Gạo ST25 5kg", "TP001", 99L, "desc",
                            new BigDecimal("180000"), null, "ACTIVE", 100));

            mockMvc.perform(post("/api/v1/products")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("400 — blank product name rejected")
        void createProduct_blankName_returns400() throws Exception {
            final String body = objectMapper.writeValueAsString(
                    new ProductRequest("", "TP001", 1L, "desc",
                            new BigDecimal("180000"), null, "ACTIVE", 100));

            mockMvc.perform(post("/api/v1/products")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 — negative price rejected")
        void createProduct_negativePrice_returns400() throws Exception {
            final String body = objectMapper.writeValueAsString(
                    new ProductRequest("Gạo ST25 5kg", "TP001", 1L, "desc",
                            new BigDecimal("-1"), null, "ACTIVE", 100));

            mockMvc.perform(post("/api/v1/products")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 — negative inventory quantity rejected")
        void createProduct_negativeInventory_returns400() throws Exception {
            final String body = objectMapper.writeValueAsString(
                    new ProductRequest("Gạo ST25 5kg", "TP001", 1L, "desc",
                            new BigDecimal("180000"), null, "ACTIVE", -5));

            mockMvc.perform(post("/api/v1/products")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PUT /api/v1/products/{id}
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PUT /api/v1/products/{id} — Update Product")
    class UpdateProduct {

        @Autowired
        private ObjectMapper objectMapper;

        private ProductDetailResponse sampleProductDetail() {
            return new ProductDetailResponse(
                    1L, "Gạo ST25 5kg mới", "TP001",
                    new BigDecimal("190000"), new BigDecimal("5.00"), new BigDecimal("9500"), new BigDecimal("199500"),
                    null, "desc", 1L, "Thực phẩm", 80, "ACTIVE"
            );
        }

        private ProductRequest sampleRequest() {
            return new ProductRequest("Gạo ST25 5kg mới", "TP001", 1L, "desc",
                    new BigDecimal("190000"), null, "ACTIVE", 80);
        }

        @Test
        @DisplayName("200 — updated successfully")
        void updateProduct_valid_returns200() throws Exception {
            when(productService.update(eq(1L), any(ProductRequest.class))).thenReturn(sampleProductDetail());

            mockMvc.perform(put("/api/v1/products/1")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(sampleRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Gạo ST25 5kg mới"));
        }

        @Test
        @DisplayName("404 — product not found")
        void updateProduct_notFound_returns404() throws Exception {
            when(productService.update(eq(99L), any(ProductRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Product", 99L));

            mockMvc.perform(put("/api/v1/products/99")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(sampleRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("409 — duplicate slug against another product")
        void updateProduct_duplicateSlug_returns409() throws Exception {
            when(productService.update(eq(1L), any(ProductRequest.class)))
                    .thenThrow(new DuplicateResourceException("Product slug already exists: TP001"));

            mockMvc.perform(put("/api/v1/products/1")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(sampleRequest())))
                    .andExpect(status().isConflict());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DELETE /api/v1/products/{id}
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DELETE /api/v1/products/{id} — Delete Product")
    class DeleteProduct {

        @Test
        @DisplayName("200 — soft-deleted successfully")
        void deleteProduct_existing_returns200() throws Exception {
            mockMvc.perform(delete("/api/v1/products/1")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("404 — product not found")
        void deleteProduct_notFound_returns404() throws Exception {
            org.mockito.Mockito.doThrow(new ResourceNotFoundException("Product", 99L))
                    .when(productService).delete(99L);

            mockMvc.perform(delete("/api/v1/products/99")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                    .andExpect(status().isNotFound());
        }
    }

<<<<<<< HEAD
    // ══════════════════════════════════════════════════════════════════════
    // GET /api/v1/products/admin/products — Admin product management list
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/products/admin/products — Admin Product List (ATS-6)")
    class AdminListProducts {

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        @DisplayName("200 — returns all non-DELETED products for admin")
        void adminList_shouldReturn200_withProducts() throws Exception {
            final java.util.List<ProductResponse> products = java.util.List.of(
                    new ProductResponse(1L, "Gạo ST25 5kg", "TP001",
                            new java.math.BigDecimal("180000"), null, "desc",
                            1L, "Thực phẩm", 100, "ACTIVE"),
                    new ProductResponse(2L, "Sản phẩm tạm dừng", "TP002",
                            new java.math.BigDecimal("50000"), null, null,
                            1L, "Thực phẩm", 0, "INACTIVE")
            );
            final org.springframework.data.domain.Page<ProductResponse> page =
                    new org.springframework.data.domain.PageImpl<>(products,
                            org.springframework.data.domain.PageRequest.of(0, 20), 2L);

            when(productService.findAllForAdmin(0, 20, null, null)).thenReturn(page);

            mockMvc.perform(get("/api/v1/products/admin/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.content[1].status").value("INACTIVE"))
                    .andExpect(jsonPath("$.data.totalElements").value(2));
        }

        @Test
        @DisplayName("400 — page is negative")
        void adminList_shouldReturn400_whenPageNegative() throws Exception {
            mockMvc.perform(get("/api/v1/products/admin/products")
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("400 — size exceeds 100")
        void adminList_shouldReturn400_whenSizeTooLarge() throws Exception {
            mockMvc.perform(get("/api/v1/products/admin/products")
                            .param("size", "999"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ATS-6 VAT validation via controller
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ATS-6 VAT — Controller level VAT validation")
    class VatControllerValidation {

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        @DisplayName("400 — create rejected when category has invalid VAT rate")
        void createProduct_invalidVat_returns400() throws Exception {
            when(productService.create(any(ProductRequest.class)))
                    .thenThrow(new IllegalArgumentException(
                            "Invalid VAT rate 7% on category 'TestCat'. Only 5% and 10% are allowed."));

            final String body = objectMapper.writeValueAsString(
                    new ProductRequest("TestProd", "TP-VAT", 5L, null,
                            new java.math.BigDecimal("100000"), null, "ACTIVE", 10));

            mockMvc.perform(post("/api/v1/products")
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(containsString("Only 5% and 10% are allowed")));
        }
=======
    private static String bearer(final String token) {
        return "Bearer " + token;
    }

    private static Jwt jwtWithRoles(final String tokenValue, final String subject, final List<String> roles) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.parse("2026-08-12T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-12T01:00:00Z"))
                .claim("iss", "http://localhost:8081/realms/shoponline")
                .audience(List.of(CLIENT_ID))
                .claim("resource_access", Map.of(CLIENT_ID, Map.of("roles", roles)))
                .build();
>>>>>>> 2e744a0ec2370b770aff69565021da8d47088517
    }
}
