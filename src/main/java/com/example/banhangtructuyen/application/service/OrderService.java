package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.order.OrderResponse;
import com.example.banhangtructuyen.application.dto.order.PlaceOrderRequest;

import java.util.List;

public interface OrderService {

    /**
     * Places an order from the customer's current cart.
     * Acquires PESSIMISTIC_WRITE locks on each product's inventory row,
     * validates stock, deducts quantities atomically, then creates the order
     * and clears the cart. (ATS-14)
     */
    OrderResponse placeOrder(String keycloakSubject, PlaceOrderRequest request);

    /** Returns all orders for the authenticated customer, newest first. */
    List<OrderResponse> getMyOrders(String keycloakSubject);

    /** Returns a single order by ID, scoped to the authenticated customer. */
    OrderResponse getMyOrder(String keycloakSubject, Long orderId);
}
