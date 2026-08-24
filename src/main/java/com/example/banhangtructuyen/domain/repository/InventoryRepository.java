package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository for {@link Inventory} entities (1:1 with PRODUCTS).
 *
 * <p>ATS-14: {@link #findByProduct_ProductId(Long)} provides a real-time,
 * database-backed inventory lookup used during cart mutations to verify
 * that the requested quantity does not exceed available stock.
 * Available stock = QUANTITY - RESERVED_QUANTITY.
 *
 * <p>Note: No inventory deduction, reservation, or order logic belongs here.
 * This repository is read-only for ATS-14 purposes.
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * ATS-14 — Real-time inventory lookup by product ID.
     *
     * <p>Used by {@code CartServiceImpl.updateItemQuantity()} to read the current
     * inventory state directly from the database, bypassing any entity cache,
     * as required by the UpdateCart sequence diagram (step 16: findByProductId).
     *
     * @param productId the ID of the product whose inventory to look up
     * @return an {@link Optional} containing the inventory, or empty if none exists
     */
    @Query("SELECT i FROM Inventory i WHERE i.product.productId = :productId")
    Optional<Inventory> findByProduct_ProductId(@Param("productId") Long productId);
}
