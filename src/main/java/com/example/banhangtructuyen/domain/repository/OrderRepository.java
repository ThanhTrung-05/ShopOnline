package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Order> findAllByOrderByCreatedAtDesc();

    Optional<Order> findByOrderNumberAndCustomerId(String orderNumber, Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

    /**
     * ATS-27: Lookup Order by primary key + customerId for ownership validation in QR generation.
     * No lock needed — read-only check before entering the transactional QR creation flow.
     */
    Optional<Order> findByOrderIdAndCustomerId(Long orderId, Long customerId);

    /**
     * ATS-30: Pessimistic lock on an Order in a specific status.
     * Used by the expiry scheduler to atomically check-and-lock a PENDING order before cancelling.
     * Returns empty Optional if order is NOT in the given status — safe concurrent race condition guard.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderId = :orderId AND o.status = :status")
    Optional<Order> findByOrderIdAndStatusForUpdate(
            @Param("orderId") Long orderId,
            @Param("status") com.example.banhangtructuyen.domain.model.OrderStatus status);
}
