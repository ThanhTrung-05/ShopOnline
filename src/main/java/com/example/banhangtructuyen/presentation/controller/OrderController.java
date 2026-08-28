package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.order.OrderResponse;
import com.example.banhangtructuyen.application.dto.order.PlaceOrderRequest;
import com.example.banhangtructuyen.application.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for customer order management (ATS-14).
 * All endpoints require ROLE_CUSTOMER (enforced in SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Validated
@Tag(name = "Order", description = "Customer order placement and history (ATS-14)")
public class OrderController {

    private final OrderService orderService;

    @Operation(
        summary = "Place order from cart",
        description = "Acquires PESSIMISTIC_WRITE locks on inventory for every cart item, "
                    + "validates stock, deducts quantities atomically, then creates the order "
                    + "and clears the cart. Returns 409 if any product has insufficient stock. (ATS-14)"
    )
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @AuthenticationPrincipal final Jwt jwt,
            @Valid @RequestBody final PlaceOrderRequest request) {
        final OrderResponse response = orderService.placeOrder(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "List my orders", description = "Returns all orders for the authenticated customer, newest first.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getMyOrders(jwt.getSubject())));
    }

    @Operation(summary = "Get order detail", description = "Returns details of a single order owned by the authenticated customer.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getMyOrder(
            @AuthenticationPrincipal final Jwt jwt,
            @PathVariable @Min(value = 1, message = "Order ID must be at least 1") Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getMyOrder(jwt.getSubject(), id)));
    }
}
