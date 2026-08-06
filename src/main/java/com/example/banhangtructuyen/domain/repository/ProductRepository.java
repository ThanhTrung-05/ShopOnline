package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.category c
            JOIN FETCH p.inventory i
            WHERE p.status = 'ACTIVE'
              AND (:categoryId IS NULL OR c.categoryId = :categoryId)
              AND (:search IS NULL OR UPPER(p.productName) LIKE UPPER(CONCAT('%', :search, '%')))
            """)
    Page<Product> findActiveProducts(@Param("categoryId") Long categoryId,
                                     @Param("search") String search,
                                     Pageable pageable);

    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.category
            JOIN FETCH p.inventory
            WHERE p.productId = :id AND p.status = 'ACTIVE'
            """)
    Optional<Product> findActiveById(@Param("id") Long id);

    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.category c
            LEFT JOIN FETCH p.inventory i
            WHERE (:categoryId IS NULL OR c.categoryId = :categoryId)
              AND (:search IS NULL OR UPPER(p.productName) LIKE UPPER(CONCAT('%', :search, '%')))
              AND p.status <> 'DELETED'
            """)
    Page<Product> findAllProducts(@Param("categoryId") Long categoryId,
                                  @Param("search") String search,
                                  Pageable pageable);

    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.category
            LEFT JOIN FETCH p.inventory
            WHERE p.productId = :id
            """)
    Optional<Product> findByIdWithInventory(@Param("id") Long id);

    boolean existsByCategory_CategoryId(Long categoryId);

    boolean existsByProductSlug(String productSlug);

    boolean existsByProductSlugAndProductIdNot(String productSlug, Long productId);
}
