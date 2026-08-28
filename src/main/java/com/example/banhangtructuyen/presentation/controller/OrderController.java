package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.order.OrderStatusResponse;
import com.example.banhangtructuyen.application.service.AuthenticatedCustomerResolver;
import com.example.banhangtructuyen.application.service.OrderStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/me/orders")
@RequiredArgsConstructor
@Tag(name = "Customer Orders", description = "Authenticated customer order tracking")
public class OrderController {

    private final AuthenticatedCustomerResolver authenticatedCustomerResolver;
    private final OrderStatusService orderStatusService;

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
