package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.product.ProductDetailResponse;
import com.example.banhangtructuyen.application.dto.product.ProductResponse;
import com.example.banhangtructuyen.application.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
                    + "Supports optional filtering by category code and keyword search. "
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

            @Parameter(description = "Filter by category code (e.g. THUC_PHAM, DIEN_MAY, SANH_SU)", example = "THUC_PHAM")
            @RequestParam(required = false) String category,

            @Parameter(description = "Keyword to search in product name (case-insensitive)", example = "gạo")
            @RequestParam(required = false) @Size(max = 100, message = "Search keyword must not exceed 100 characters") String search) {

        final Page<ProductResponse> result = productService.findAll(page, size, category, search);
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
}
