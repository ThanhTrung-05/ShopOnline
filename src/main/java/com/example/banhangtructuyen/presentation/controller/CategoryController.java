package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.category.CategoryRequest;
import com.example.banhangtructuyen.application.dto.category.CategoryResponse;
import com.example.banhangtructuyen.application.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin REST controller for product category management (ATS-5).
 *
 * <p>Controller stays thin: all business rules (duplicate checks, VAT rate
 * validation, delete-in-use guard) live in {@link CategoryService}.
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Validated
@Tag(name = "Category", description = "Admin category management — CRUD endpoints")
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "List categories", description = "Returns all categories.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listCategories() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.findAll()));
    }

    @Operation(summary = "Get category detail", description = "Returns a single category by ID.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategory(
            @Parameter(description = "Category ID", example = "1")
            @PathVariable @Min(value = 1, message = "Category ID must be at least 1") Long id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.findById(id)));
    }

    @Operation(summary = "Create category", description = "Creates a new category. Category code and name must be unique.")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody final CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.create(request)));
    }

    @Operation(summary = "Update category", description = "Updates an existing category by ID.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @Parameter(description = "Category ID", example = "1")
            @PathVariable @Min(value = 1, message = "Category ID must be at least 1") Long id,
            @Valid @RequestBody final CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.update(id, request)));
    }

    @Operation(summary = "Delete category",
            description = "Deletes a category by ID. Fails if the category is still referenced by products.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @Parameter(description = "Category ID", example = "1")
            @PathVariable @Min(value = 1, message = "Category ID must be at least 1") Long id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
