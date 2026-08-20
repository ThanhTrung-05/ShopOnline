package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByCustomerId(Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cart FROM Cart cart WHERE cart.customerId = :customerId")
    Optional<Cart> findByCustomerIdForUpdate(@Param("customerId") Long customerId);
}
