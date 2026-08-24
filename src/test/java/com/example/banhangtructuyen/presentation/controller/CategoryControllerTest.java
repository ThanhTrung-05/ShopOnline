package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.category.CategoryRequest;
import com.example.banhangtructuyen.application.dto.category.CategoryResponse;
import com.example.banhangtructuyen.application.service.CategoryService;
import com.example.banhangtructuyen.domain.exception.DuplicateResourceException;
import com.example.banhangtructuyen.domain.exception.ResourceInUseException;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc integration tests for {@link CategoryController}.
 * {@link CategoryService} is mocked via {@code @MockBean} — no DB or Redis required.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("CategoryController MockMvc Tests")
class CategoryControllerTest {

    private static final String CLIENT_ID = "shoponline-backend";
    private static final String ADMIN_TOKEN = "admin-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @org.junit.jupiter.api.BeforeEach
    void setUpSecurity() {
        when(jwtDecoder.decode(ADMIN_TOKEN))
                .thenReturn(jwtWithRoles(ADMIN_TOKEN, "admin-subject", List.of("ADMIN")));
    }

    private static CategoryResponse sampleCategory() {
        return new CategoryResponse(1L, "Thuc pham", "THUC_PHAM", "Thuc pham tuoi song",
                new BigDecimal("5"), "ACTIVE");
    }

    private static CategoryRequest sampleRequest() {
        return new CategoryRequest("Thuc pham", "THUC_PHAM", "Thuc pham tuoi song",
                new BigDecimal("5"), "ACTIVE");
    }

    @Nested
    @DisplayName("GET /api/categories")
    class ListCategories {

        @Test
        @DisplayName("200 — returns category list")
        void listCategories_returns200() throws Exception {
            when(categoryService.findAll()).thenReturn(List.of(sampleCategory()));

            mockMvc.perform(get("/api/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].categoryName").value("Thuc pham"))
                    .andExpect(jsonPath("$.data[0].vatRate").value(5));
        }
    }

    @Nested
    @DisplayName("GET /api/categories/{id}")
    class GetCategory {

        @Test
        @DisplayName("200 — found")
        void getCategory_found_returns200() throws Exception {
            when(categoryService.findById(1L)).thenReturn(sampleCategory());

            mockMvc.perform(get("/api/categories/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.categoryId").value(1))
                    .andExpect(jsonPath("$.data.categoryCode").value("THUC_PHAM"));
        }

        @Test
        @DisplayName("404 — not found")
        void getCategory_notFound_returns404() throws Exception {
            when(categoryService.findById(99L))
                    .thenThrow(new ResourceNotFoundException("Category", 99L));

            mockMvc.perform(get("/api/categories/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("400 — invalid id (< 1)")
        void getCategory_invalidId_returns400() throws Exception {
            mockMvc.perform(get("/api/categories/0"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/categories")
    class CreateCategory {

        @Test
        @DisplayName("200 — created successfully")
        void createCategory_valid_returns200() throws Exception {
            when(categoryService.create(org.mockito.ArgumentMatchers.any(CategoryRequest.class)))
                    .thenReturn(sampleCategory());

            mockMvc.perform(post("/api/categories")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(sampleRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.categoryName").value("Thuc pham"));
        }

        @Test
        @DisplayName("409 — duplicate category code")
        void createCategory_duplicateCode_returns409() throws Exception {
            when(categoryService.create(org.mockito.ArgumentMatchers.any(CategoryRequest.class)))
                    .thenThrow(new DuplicateResourceException("Category code already exists: THUC_PHAM"));

            mockMvc.perform(post("/api/categories")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(sampleRequest())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("400 — blank category name rejected")
        void createCategory_blankName_returns400() throws Exception {
            final String body = objectMapper.writeValueAsString(
                    new CategoryRequest("", "THUC_PHAM", null, new BigDecimal("5"), "ACTIVE"));

            mockMvc.perform(post("/api/categories")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 — invalid VAT rate (not 5 or 10) rejected")
        void createCategory_invalidVatRate_returns400() throws Exception {
            when(categoryService.create(org.mockito.ArgumentMatchers.any(CategoryRequest.class)))
                    .thenThrow(new IllegalArgumentException("VAT rate must be either 5 or 10"));

            final String body = objectMapper.writeValueAsString(
                    new CategoryRequest("Thuc pham", "THUC_PHAM", null, new BigDecimal("8"), "ACTIVE"));

            mockMvc.perform(post("/api/categories")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("VAT rate must be either 5 or 10"));
        }
    }

    @Nested
    @DisplayName("PUT /api/categories/{id}")
    class UpdateCategory {

        @Test
        @DisplayName("200 — updated successfully")
        void updateCategory_valid_returns200() throws Exception {
            when(categoryService.update(eq(1L), org.mockito.ArgumentMatchers.any(CategoryRequest.class)))
                    .thenReturn(sampleCategory());

            mockMvc.perform(put("/api/categories/1")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(sampleRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.categoryId").value(1));
        }

        @Test
        @DisplayName("404 — category not found")
        void updateCategory_notFound_returns404() throws Exception {
            when(categoryService.update(eq(99L), org.mockito.ArgumentMatchers.any(CategoryRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Category", 99L));

            mockMvc.perform(put("/api/categories/99")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(sampleRequest())))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/categories/{id}")
    class DeleteCategory {

        @Test
        @DisplayName("200 — deleted successfully")
        void deleteCategory_noProducts_returns200() throws Exception {
            mockMvc.perform(delete("/api/categories/1")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("409 — category still referenced by products")
        void deleteCategory_hasProducts_returns409() throws Exception {
            org.mockito.Mockito.doThrow(new ResourceInUseException(
                            "Cannot delete category: it is still referenced by one or more products"))
                    .when(categoryService).delete(1L);

            mockMvc.perform(delete("/api/categories/1")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — category not found")
        void deleteCategory_notFound_returns404() throws Exception {
            org.mockito.Mockito.doThrow(new ResourceNotFoundException("Category", 99L))
                    .when(categoryService).delete(99L);

            mockMvc.perform(delete("/api/categories/99")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                    .andExpect(status().isNotFound());
        }
    }

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
    }
}
