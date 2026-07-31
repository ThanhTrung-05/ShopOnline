package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.product.ProductDetailResponse;
import com.example.banhangtructuyen.application.dto.product.ProductResponse;
import com.example.banhangtructuyen.application.service.ProductService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

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
            when(productService.findAll(0, 20, null, null))
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
        @DisplayName("200 — filtered by category code")
        void listProducts_shouldReturn200_withCategoryFilter() throws Exception {
            // Arrange
            when(productService.findAll(0, 20, "THUC_PHAM", null))
                    .thenReturn(singleProductPage(sampleProduct()));

            // Act + Assert
            mockMvc.perform(get("/api/v1/products")
                            .param("category", "THUC_PHAM"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].categoryName").value("Thực phẩm"));
        }

        @Test
        @DisplayName("200 — filtered by search keyword")
        void listProducts_shouldReturn200_withSearchKeyword() throws Exception {
            // Arrange
            when(productService.findAll(0, 20, null, "gạo"))
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
            when(productService.findAll(0, 20, "DIEN_MAY", "nonexistent"))
                    .thenReturn(emptyPage);

            // Act + Assert
            mockMvc.perform(get("/api/v1/products")
                            .param("category", "DIEN_MAY")
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
}
