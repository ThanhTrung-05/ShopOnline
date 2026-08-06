package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByCategoryCode(String categoryCode);

    boolean existsByCategoryName(String categoryName);

    boolean existsByCategoryCodeAndCategoryIdNot(String categoryCode, Long categoryId);

    boolean existsByCategoryNameAndCategoryIdNot(String categoryName, Long categoryId);
}
