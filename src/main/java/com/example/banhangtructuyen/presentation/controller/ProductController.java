package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.product.ProductDetailResponse;
import com.example.banhangtructuyen.application.dto.product.ProductRequest;
import com.example.banhangtructuyen.application.dto.product.ProductResponse;
import com.example.banhangtructuyen.application.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Public REST controller for the product catalog.
 *
 * <p>Both endpoints are publicly accessible (no authentication required)
 * as configured in {@link com.example.banhangtructuyen.config.SecurityConfig}.
 *
 * <p>Cache-Aside pattern is fully handled in the Service layer:
 * <ul>
 *   <li>Product list: TTL 5 minutes</li>
 *   <li>Product detail: TTL 10 minutes</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Validated
@Tag(name = "Product", description = "Public product catalog — list and detail endpoints")
public class ProductController {

    private final ProductService productService;

    /**
     * Returns a paginated, filterable list of ACTIVE products.
     * Validates that page >= 0 and 1 <= size <= 100 per sequence diagram spec.
     */
    @Operation(
        summary = "List products",
        description = "Returns a paginated list of active products. "
                    + "Supports optional filtering by category ID and keyword search. "
                    + "Results are cached in Redis with a 5-minute TTL (Cache-Aside pattern)."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product list returned successfully",
            content = @Content(schema = @Schema(implementation = com.example.banhangtructuyen.application.dto.ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid pagination parameters (page < 0 or size out of range 1–100)",
            content = @Content(schema = @Schema(implementation = com.example.banhangtructuyen.application.dto.ApiResponse.class)))
    })
    @GetMapping
    public ResponseEntity<com.example.banhangtructuyen.application.dto.ApiResponse<Page<ProductResponse>>> listProducts(
            @Parameter(description = "Zero-indexed page number (must be >= 0)", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page number must be 0 or greater") int page,

            @Parameter(description = "Number of items per page (1–100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Page size must be at least 1")
                                               @Max(value = 100, message = "Page size must not exceed 100") int size,

            @Parameter(description = "Filter by category ID", example = "1")
            @RequestParam(required = false) @Min(value = 1, message = "Category ID must be at least 1") Long categoryId,

            @Parameter(description = "Keyword to search in product name (case-insensitive)", example = "gạo")
            @RequestParam(required = false) @Size(max = 100, message = "Search keyword must not exceed 100 characters") String search,

            @Parameter(description = "Minimum price filter (inclusive)", example = "10000")
            @RequestParam(required = false) @DecimalMin(value = "0", message = "Minimum price must be 0 or greater") BigDecimal minPrice,

            @Parameter(description = "Maximum price filter (inclusive)", example = "500000")
            @RequestParam(required = false) @DecimalMin(value = "0", message = "Maximum price must be 0 or greater") BigDecimal maxPrice) {

        final Page<ProductResponse> result = productService.findAll(page, size, categoryId, search, minPrice, maxPrice);
        return ResponseEntity.ok(com.example.banhangtructuyen.application.dto.ApiResponse.success(result));
    }

    /**
     * Returns the full detail of a single ACTIVE product including VAT breakdown.
     * Cache-Aside: TTL 10 minutes via Redis.
     * Returns 404 if product does not exist or is INACTIVE/DELETED.
     */
    @Operation(
        summary = "Get product detail with VAT",
        description = "Returns full detail of a single active product by ID, including VAT rate, VAT amount, "
                    + "and total price including VAT. Result is cached in Redis with a 10-minute TTL. "
                    + "Returns 404 if the product does not exist or has been deactivated."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product detail returned successfully",
            content = @Content(schema = @Schema(implementation = com.example.banhangtructuyen.application.dto.ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found or inactive",
            content = @Content(schema = @Schema(implementation = com.example.banhangtructuyen.application.dto.ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid product ID (must be >= 1)",
            content = @Content(schema = @Schema(implementation = com.example.banhangtructuyen.application.dto.ApiResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<com.example.banhangtructuyen.application.dto.ApiResponse<ProductDetailResponse>> getProduct(
            @Parameter(description = "Product ID (must be a positive integer)", example = "1", required = true)
            @PathVariable @Min(value = 1, message = "Product ID must be at least 1") Long id) {
        return ResponseEntity.ok(com.example.banhangtructuyen.application.dto.ApiResponse.success(
                productService.findDetailById(id)));
    }

    @Operation(summary = "Create product",
            description = "Creates a new product together with its linked inventory row. "
                        + "Product slug must be unique and category must exist. (ATS-6, admin)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed (invalid VAT rate, price, inventory, etc.)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Category not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Product slug already exists")
    })
    @PostMapping
    public ResponseEntity<com.example.banhangtructuyen.application.dto.ApiResponse<ProductDetailResponse>> createProduct(
            @Valid @RequestBody final ProductRequest request) {
        return ResponseEntity.ok(com.example.banhangtructuyen.application.dto.ApiResponse.success(
                productService.create(request)));
    }

    @Operation(summary = "Update product",
            description = "Updates an existing product by ID, including category, VAT-affecting fields, and inventory quantity. (ATS-6, admin)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product or category not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Product slug already exists")
    })
    @PutMapping("/{id}")
    public ResponseEntity<com.example.banhangtructuyen.application.dto.ApiResponse<ProductDetailResponse>> updateProduct(
            @Parameter(description = "Product ID", example = "1")
            @PathVariable @Min(value = 1, message = "Product ID must be at least 1") Long id,
            @Valid @RequestBody final ProductRequest request) {
        return ResponseEntity.ok(com.example.banhangtructuyen.application.dto.ApiResponse.success(
                productService.update(id, request)));
    }

    @Operation(summary = "Delete product",
            description = "Soft-deletes a product by setting its status to DELETED. (ATS-6, admin)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<com.example.banhangtructuyen.application.dto.ApiResponse<Void>> deleteProduct(
            @Parameter(description = "Product ID", example = "1")
            @PathVariable @Min(value = 1, message = "Product ID must be at least 1") Long id) {
        productService.delete(id);
        return ResponseEntity.ok(com.example.banhangtructuyen.application.dto.ApiResponse.success(null));
    }

    /**
     * Admin product management list — returns all non-DELETED products (ACTIVE + INACTIVE).
     * Does NOT use Redis cache so admin always sees up-to-date data.
     * Requires ROLE_ADMIN (enforced by SecurityConfig).
     */
    @Operation(
        summary = "[Admin] List all products for management",
        description = "Returns all non-DELETED products (ACTIVE and INACTIVE statuses) for admin management. "
                    + "No caching — always returns live data. Requires ROLE_ADMIN. (ATS-6)"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Admin product list returned successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid pagination parameters"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized — JWT token required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — ROLE_ADMIN required")
    })
    @GetMapping("/admin/products")
    public ResponseEntity<com.example.banhangtructuyen.application.dto.ApiResponse<Page<ProductResponse>>> adminListProducts(
            @Parameter(description = "Zero-indexed page number", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must be 0 or greater") int page,

            @Parameter(description = "Items per page (1–100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
                                               @Max(value = 100, message = "Size must not exceed 100") int size,

            @Parameter(description = "Filter by category ID", example = "1")
            @RequestParam(required = false) @Min(value = 1, message = "Category ID must be at least 1") Long categoryId,

            @Parameter(description = "Keyword search in product name", example = "gạo")
            @RequestParam(required = false) @Size(max = 100, message = "Search keyword must not exceed 100 characters") String search) {

        final Page<ProductResponse> result = productService.findAllForAdmin(page, size, categoryId, search);
        return ResponseEntity.ok(com.example.banhangtructuyen.application.dto.ApiResponse.success(result));
    }
}
