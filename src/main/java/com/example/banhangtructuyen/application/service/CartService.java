package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.cart.AddCartItemRequest;
import com.example.banhangtructuyen.application.dto.cart.CartItemResponse;
import com.example.banhangtructuyen.application.dto.cart.CartResponse;
import com.example.banhangtructuyen.application.dto.cart.UpdateCartItemQuantityRequest;

public interface CartService {

    CartResponse getCurrentCart(String keycloakSubject);

    CartItemResponse addItem(String keycloakSubject, AddCartItemRequest request);

    CartItemResponse updateItemQuantity(String keycloakSubject, Long cartItemId, UpdateCartItemQuantityRequest request);

    void removeItem(String keycloakSubject, Long cartItemId);
}
