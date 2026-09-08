package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.order.OperationsOrderResponse;
import com.example.banhangtructuyen.application.dto.order.UpdateOrderStatusRequest;
import com.example.banhangtructuyen.application.service.OrderOperationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/operations/orders")
@RequiredArgsConstructor
@Tag(name = "Order Operations", description = "Administrative and warehouse order status operations")
public class OrderOperationsController {

    private final OrderOperationsService orderOperationsService;

    @Operation(summary = "List orders for operational processing")
    @GetMapping
    public ResponseEntity<ApiResponse<List<OperationsOrderResponse>>> getOrders() {
        return ResponseEntity.ok(ApiResponse.success(orderOperationsService.getOrders()));
    }

    @Operation(summary = "Update an order using the ATS-35 transition rules")
    @PatchMapping("/{orderNumber}/status")
    public ResponseEntity<ApiResponse<OperationsOrderResponse>> updateStatus(
            @PathVariable final String orderNumber,
            @Valid @RequestBody final UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                orderOperationsService.updateStatus(orderNumber, request.status())));
    }
}
