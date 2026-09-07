package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.order.CreateOrderResponse;
import com.example.banhangtructuyen.application.dto.order.OrderDetailResponse;
import com.example.banhangtructuyen.application.dto.order.OrderStatusResponse;
import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;
import com.example.banhangtructuyen.application.service.AuthenticatedCustomerResolver;
import com.example.banhangtructuyen.application.service.OrderCreationService;
import com.example.banhangtructuyen.application.service.OrderStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers/me/orders")
@RequiredArgsConstructor
@Tag(name = "Customer Orders", description = "Authenticated customer order tracking")
public class OrderController {

    private final AuthenticatedCustomerResolver authenticatedCustomerResolver;
    private final OrderStatusService orderStatusService;
    private final OrderCreationService orderCreationService;

    @Operation(summary = "Create an order from the authenticated customer's current cart")
    @PostMapping
    public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(
            @AuthenticationPrincipal final Jwt jwt,
            @Valid @RequestBody final ShippingSelectionRequest request) {
        final CreateOrderResponse response = orderCreationService.createOrder(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "List the authenticated customer's orders")
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderStatusResponse>>> getOrders(
            @AuthenticationPrincipal final Jwt jwt) {
        final Long customerId = authenticatedCustomerResolver
                .resolveActiveCustomer(jwt.getSubject())
                .getCustomerId();
        final List<OrderStatusResponse> response = orderStatusService.getOrderStatuses(customerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get an owned order with its item snapshots")
    @GetMapping("/{orderNumber}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrderDetails(
            @AuthenticationPrincipal final Jwt jwt,
            @PathVariable final String orderNumber) {
        final Long customerId = authenticatedCustomerResolver
                .resolveActiveCustomer(jwt.getSubject())
                .getCustomerId();
        final OrderDetailResponse response = orderStatusService.getOrderDetails(orderNumber, customerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get the status of an owned order")
    @GetMapping("/{orderNumber}/status")
    public ResponseEntity<ApiResponse<OrderStatusResponse>> getOrderStatus(
            @AuthenticationPrincipal final Jwt jwt,
            @PathVariable final String orderNumber) {
        final Long customerId = authenticatedCustomerResolver
                .resolveActiveCustomer(jwt.getSubject())
                .getCustomerId();
        final OrderStatusResponse response = orderStatusService.getOrderStatus(orderNumber, customerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
