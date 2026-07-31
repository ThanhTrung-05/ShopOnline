package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.product.ProductDetailResponse;
import com.example.banhangtructuyen.application.dto.product.ProductResponse;
import org.springframework.data.domain.Page;

public interface ProductService {

    Page<ProductResponse> findAll(int page, int size, String categoryCode, String search);

    /** Returns basic product info — used by ATS-2 product list. */
    ProductResponse findById(Long productId);

    /**
     * Returns full product detail including VAT breakdown — used by ATS-4.
     * Throws ResourceNotFoundException if product does not exist or is not ACTIVE.
     */
    ProductDetailResponse findDetailById(Long productId);
}
