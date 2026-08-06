package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.category.CategoryRequest;
import com.example.banhangtructuyen.application.dto.category.CategoryResponse;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> findAll();

    CategoryResponse findById(Long categoryId);

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(Long categoryId, CategoryRequest request);

    void delete(Long categoryId);
}
