package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCart_CartIdAndProduct_ProductId(Long cartId, Long productId);
}
