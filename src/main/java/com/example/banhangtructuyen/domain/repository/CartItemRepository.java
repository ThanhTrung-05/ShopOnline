package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCart_CartIdAndProduct_ProductId(Long cartId, Long productId);

    Optional<CartItem> findByCart_CustomerIdAndCartItemId(Long customerId, Long cartItemId);

    @Query("""
            select ci
            from CartItem ci
            join fetch ci.product
            where ci.cart.customerId = :customerId
            order by ci.cartItemId asc
            """)
    List<CartItem> findViewItemsByCustomerId(@Param("customerId") Long customerId);
}
