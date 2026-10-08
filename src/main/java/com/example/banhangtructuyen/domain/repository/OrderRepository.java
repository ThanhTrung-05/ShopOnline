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
}
