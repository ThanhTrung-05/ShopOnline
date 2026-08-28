package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("SELECT oi FROM OrderItem oi WHERE oi.order.orderId = :orderId ORDER BY oi.orderItemId ASC")
    List<OrderItem> findByOrderId(@Param("orderId") Long orderId);
}
