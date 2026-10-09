package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.productId = :productId")
    Optional<Inventory> findByProductIdWithLock(@Param("productId") Long productId);

    /**
     * ATS-18: Returns inventory rows where available quantity (quantity - reservedQuantity) = 0.
     * JOIN FETCH avoids N+1 on product and category.
     */
    @Query("""
            SELECT i FROM Inventory i
            JOIN FETCH i.product p
            JOIN FETCH p.category
            WHERE (i.quantity - i.reservedQuantity) <= 0
              AND p.status = 'ACTIVE'
            ORDER BY p.productName ASC
            """)
    List<Inventory> findOutOfStock();

    /**
     * ATS-18: Returns inventory rows where 0 < availableQty <= threshold.
     * JOIN FETCH avoids N+1 on product and category.
     */
    @Query("""
            SELECT i FROM Inventory i
            JOIN FETCH i.product p
            JOIN FETCH p.category
            WHERE (i.quantity - i.reservedQuantity) > 0
              AND (i.quantity - i.reservedQuantity) <= :threshold
              AND p.status = 'ACTIVE'
            ORDER BY (i.quantity - i.reservedQuantity) ASC
            """)
    List<Inventory> findLowStock(@Param("threshold") int threshold);
}
