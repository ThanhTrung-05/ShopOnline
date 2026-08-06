package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.product.ProductDetailResponse;
import com.example.banhangtructuyen.application.dto.product.ProductRequest;
import com.example.banhangtructuyen.application.dto.product.ProductResponse;
import org.springframework.data.domain.Page;

public interface ProductService {

    Page<ProductResponse> findAll(int page, int size, Long categoryId, String search);

    /** Returns basic product info — used by ATS-2 product list. */
    ProductResponse findById(Long productId);

    /**
     * Returns full product detail including VAT breakdown — used by ATS-4.
     * Throws ResourceNotFoundException if product does not exist or is not ACTIVE.
     */
    ProductDetailResponse findDetailById(Long productId);

    /** Creates a new product together with its linked inventory row (ATS-6). */
    ProductDetailResponse create(ProductRequest request);

    /** Updates an existing product by ID (ATS-6). */
    ProductDetailResponse update(Long productId, ProductRequest request);

    /** Soft-deletes a product by flipping its status to DELETED (ATS-6). */
    void delete(Long productId);
}
