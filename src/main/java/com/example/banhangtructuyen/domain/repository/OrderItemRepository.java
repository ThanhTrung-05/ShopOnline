package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrder_OrderIdOrderByOrderItemIdAsc(Long orderId);
}
