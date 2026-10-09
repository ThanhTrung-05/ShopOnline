package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.order.PaymentCallbackRequest;
import com.example.banhangtructuyen.application.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment webhook and callback handling")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Process payment callback webhook")
    @PostMapping("/callback")
    public ResponseEntity<ApiResponse<Void>> handleCallback(
            @Valid @RequestBody final PaymentCallbackRequest request) {
        paymentService.processCallback(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
