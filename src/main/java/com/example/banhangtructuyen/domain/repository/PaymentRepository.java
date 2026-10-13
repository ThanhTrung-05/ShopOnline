package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order.orderNumber = :orderNumber")
    Optional<Payment> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

    /**
     * ATS-27: Read-only lookup to check whether a Payment already exists for a given Order.
     * Used for idempotency in QR generation: if PROCESSING and not expired, reuse the existing QR.
     */
    @Query("SELECT p FROM Payment p WHERE p.order.orderId = :orderId")
    Optional<Payment> findByOrder_OrderId(@Param("orderId") Long orderId);

    /**
     * ATS-28: Pessimistic write lock variant, used in the demo confirm flow
     * to prevent concurrent double-confirmation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order.orderId = :orderId")
    Optional<Payment> findByOrder_OrderIdForUpdate(@Param("orderId") Long orderId);
}
