package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByEmail(String email);

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByKeycloakUserId(String keycloakUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT customer FROM Customer customer WHERE customer.customerId = :customerId")
    Optional<Customer> findByIdForUpdate(@Param("customerId") Long customerId);
}
