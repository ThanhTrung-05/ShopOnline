package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumberAndCustomerId(String orderNumber, Long customerId);
}
