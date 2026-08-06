package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.category.CategoryRequest;
import com.example.banhangtructuyen.application.dto.category.CategoryResponse;
import com.example.banhangtructuyen.application.service.CategoryService;
import com.example.banhangtructuyen.domain.exception.DuplicateResourceException;
import com.example.banhangtructuyen.domain.exception.ResourceInUseException;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Category;
import com.example.banhangtructuyen.domain.repository.CategoryRepository;
import com.example.banhangtructuyen.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private static final Set<BigDecimal> ALLOWED_VAT_RATES = Set.of(
            BigDecimal.valueOf(5), BigDecimal.valueOf(10));

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public CategoryResponse findById(final Long categoryId) {
        return toResponse(getOrThrow(categoryId));
    }

    @Override
    @Transactional
    public CategoryResponse create(final CategoryRequest request) {
        validateVatRate(request.vatRate());

        if (categoryRepository.existsByCategoryCode(request.categoryCode())) {
            throw new DuplicateResourceException(
                    "Category code already exists: " + request.categoryCode());
        }
        if (categoryRepository.existsByCategoryName(request.categoryName())) {
            throw new DuplicateResourceException(
                    "Category name already exists: " + request.categoryName());
        }

        final Category category = Category.builder()
                .categoryCode(request.categoryCode())
                .categoryName(request.categoryName())
                .description(request.description())
                .vatRate(request.vatRate())
                .sortOrder(0)
                .status(Category.CategoryStatus.valueOf(request.status()))
                .build();

        return toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse update(final Long categoryId, final CategoryRequest request) {
        validateVatRate(request.vatRate());

        final Category category = getOrThrow(categoryId);

        if (categoryRepository.existsByCategoryCodeAndCategoryIdNot(request.categoryCode(), categoryId)) {
            throw new DuplicateResourceException(
                    "Category code already exists: " + request.categoryCode());
        }
        if (categoryRepository.existsByCategoryNameAndCategoryIdNot(request.categoryName(), categoryId)) {
            throw new DuplicateResourceException(
                    "Category name already exists: " + request.categoryName());
        }

        category.setCategoryCode(request.categoryCode());
        category.setCategoryName(request.categoryName());
        category.setDescription(request.description());
        category.setVatRate(request.vatRate());
        category.setStatus(Category.CategoryStatus.valueOf(request.status()));

        return toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void delete(final Long categoryId) {
        final Category category = getOrThrow(categoryId);

        if (productRepository.existsByCategory_CategoryId(categoryId)) {
            throw new ResourceInUseException(
                    "Cannot delete category: it is still referenced by one or more products");
        }

        categoryRepository.delete(category);
    }

    private Category getOrThrow(final Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }

    private void validateVatRate(final BigDecimal vatRate) {
        final boolean allowed = ALLOWED_VAT_RATES.stream()
                .anyMatch(allowedRate -> allowedRate.compareTo(vatRate) == 0);
        if (!allowed) {
            throw new IllegalArgumentException("VAT rate must be either 5 or 10");
        }
    }

    private CategoryResponse toResponse(final Category category) {
        return new CategoryResponse(
                category.getCategoryId(),
                category.getCategoryName(),
                category.getCategoryCode(),
                category.getDescription(),
                category.getVatRate(),
                category.getStatus().name()
        );
    }
}
