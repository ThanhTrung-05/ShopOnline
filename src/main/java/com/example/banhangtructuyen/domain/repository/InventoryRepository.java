package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.Inventory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * Acquires a PESSIMISTIC_WRITE (SELECT FOR UPDATE) lock on the inventory row
     * for the given product. Used exclusively during order placement to prevent
     * overselling (ATS-14). 5-second timeout to avoid indefinite blocking.
     *
     * <p>Must be called inside an active {@code @Transactional} context.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT i FROM Inventory i WHERE i.product.productId = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);
}

