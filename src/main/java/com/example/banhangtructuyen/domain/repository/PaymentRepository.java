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
}
