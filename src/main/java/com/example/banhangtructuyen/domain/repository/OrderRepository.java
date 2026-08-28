package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
            SELECT o FROM Order o
            WHERE o.customerId = :customerId
            ORDER BY o.createdAt DESC
            """)
    List<Order> findByCustomerIdOrderByCreatedAtDesc(@Param("customerId") Long customerId);

    @Query("""
            SELECT o FROM Order o
            WHERE o.orderId = :orderId
              AND o.customerId = :customerId
            """)
    Optional<Order> findByOrderIdAndCustomerId(@Param("orderId") Long orderId,
                                               @Param("customerId") Long customerId);
}
