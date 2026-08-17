package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.cart.AddCartItemRequest;
import com.example.banhangtructuyen.application.dto.cart.CartItemResponse;
import com.example.banhangtructuyen.application.dto.cart.UpdateCartItemQuantityRequest;
import com.example.banhangtructuyen.application.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Authenticated customer cart")
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Add product to current customer's cart")
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartItemResponse>> addItem(
            @AuthenticationPrincipal final Jwt jwt,
            @Valid @RequestBody final AddCartItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(cartService.addItem(jwt.getSubject(), request)));
    }

    @Operation(summary = "Update item quantity in current customer's cart")
    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartItemResponse>> updateItemQuantity(
            @AuthenticationPrincipal final Jwt jwt,
            @PathVariable final Long cartItemId,
            @Valid @RequestBody final UpdateCartItemQuantityRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                cartService.updateItemQuantity(jwt.getSubject(), cartItemId, request)));
    }

    @Operation(summary = "Remove item from current customer's cart")
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> removeItem(
            @AuthenticationPrincipal final Jwt jwt,
            @PathVariable final Long cartItemId) {
        cartService.removeItem(jwt.getSubject(), cartItemId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
