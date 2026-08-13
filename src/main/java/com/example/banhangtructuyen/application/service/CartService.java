package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.cart.AddCartItemRequest;
import com.example.banhangtructuyen.application.dto.cart.CartItemResponse;

public interface CartService {

    CartItemResponse addItem(String keycloakSubject, AddCartItemRequest request);
}
