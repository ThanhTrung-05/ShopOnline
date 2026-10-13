package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.order.DemoConfirmRequest;
import com.example.banhangtructuyen.application.dto.order.PaymentCallbackRequest;
import com.example.banhangtructuyen.application.dto.order.PaymentStatusResponse;
import com.example.banhangtructuyen.application.dto.order.QrPaymentResponse;
import com.example.banhangtructuyen.application.service.AuthenticatedCustomerResolver;
import com.example.banhangtructuyen.application.service.PaymentService;
import com.example.banhangtructuyen.application.service.QrPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment API controller.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/v1/payments/callback}       — Banking Gateway webhook (no JWT)</li>
 *   <li>{@code GET  /api/v1/payments/qr/{orderId}}   — ATS-27: Generate/reuse QR (CUSTOMER)</li>
 *   <li>{@code GET  /api/v1/payments/{orderId}/status} — ATS-27: Poll payment status (CUSTOMER)</li>
 *   <li>{@code POST /api/v1/payments/demo/confirm}   — ATS-28: Simulate confirmation (ADMIN, demo only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment webhook, QR generation, status polling, and demo confirmation")
public class PaymentController {

    private final PaymentService paymentService;
    private final QrPaymentService qrPaymentService;
    private final AuthenticatedCustomerResolver authenticatedCustomerResolver;

    // -------------------------------------------------------------------------
    // Existing endpoint — DO NOT MODIFY
    // -------------------------------------------------------------------------

    @Operation(summary = "Process payment callback webhook (Banking Gateway → Backend)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Callback processed")
    @PostMapping("/callback")
    public ResponseEntity<ApiResponse<Void>> handleCallback(
            @Valid @RequestBody final PaymentCallbackRequest request) {
        paymentService.processCallback(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // -------------------------------------------------------------------------
    // ATS-27: QR generation
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Generate (or reuse) a QR code for payment — ATS-27",
            description = "Creates a PROCESSING Payment record and returns a QR code payload. "
                    + "If a valid QR already exists for this order (PROCESSING + not expired), "
                    + "the existing QR is returned without creating a duplicate. "
                    + "Total amount is sourced from the backend (ATS-26) — frontend must not compute it."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "QR generated or reused"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Order is not in PENDING status"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Order not found or does not belong to authenticated customer")
    })
    @GetMapping("/qr/{orderId}")
    public ResponseEntity<ApiResponse<QrPaymentResponse>> generateQr(
            @AuthenticationPrincipal final Jwt jwt,
            @Parameter(description = "Internal order ID", example = "1001")
            @PathVariable final Long orderId) {

        final Long customerId = authenticatedCustomerResolver
                .resolveActiveCustomer(jwt.getSubject())
                .getCustomerId();
        final QrPaymentResponse response = qrPaymentService.generateQr(orderId, customerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // -------------------------------------------------------------------------
    // ATS-27: Status polling
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get payment status for an order — ATS-27 (polling)",
            description = "Returns the current payment status. Served from Redis cache when available "
                    + "(TTL ~15 min). Frontend polls this every 3 seconds until SUCCESS or FAILED."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Status returned (PENDING | PROCESSING | SUCCESS | FAILED | EXPIRED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Order not found or not owned by authenticated customer")
    })
    @GetMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getPaymentStatus(
            @AuthenticationPrincipal final Jwt jwt,
            @Parameter(description = "Internal order ID", example = "1001")
            @PathVariable final Long orderId) {

        final Long customerId = authenticatedCustomerResolver
                .resolveActiveCustomer(jwt.getSubject())
                .getCustomerId();
        final PaymentStatusResponse response = qrPaymentService.getPaymentStatus(orderId, customerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // -------------------------------------------------------------------------
    // ATS-28: Demo confirmation (ADMIN-only, demo-mode flag guard)
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Simulate QR payment confirmation — ATS-28 (demo/testing only)",
            description = "Simulates a bank QR scan and marks the payment as SUCCESS. "
                    + "Requires ADMIN role AND app.payment.demo-mode=true (environment flag). "
                    + "This endpoint MUST NOT be enabled on production. "
                    + "On success: Payment → SUCCESS, Order → PAID, inventory deducted (ATS-16), "
                    + "caches cleared (ATS-17), outbox event published."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Payment confirmed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Order/Payment not in expected state, or amount mismatch"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Insufficient role or demo-mode disabled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Order or Payment not found")
    })
    @PostMapping("/demo/confirm")
    public ResponseEntity<ApiResponse<Void>> demoConfirm(
            @Valid @RequestBody final DemoConfirmRequest request) {
        qrPaymentService.demoConfirm(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
