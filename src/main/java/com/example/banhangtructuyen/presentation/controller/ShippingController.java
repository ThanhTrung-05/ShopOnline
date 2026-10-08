package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.shipping.ShippingCheckoutInfo;
import com.example.banhangtructuyen.application.dto.shipping.ShippingSelectionRequest;
import com.example.banhangtructuyen.application.service.ShippingPreparationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/me/shipping")
@RequiredArgsConstructor
@Tag(name = "Customer Shipping", description = "Authenticated customer shipping preparation")
public class ShippingController {

    private final ShippingPreparationService shippingPreparationService;

    @Operation(
            summary = "Prepare shipping",
            description = "Validates an owned delivery address and calculates the shipping fee.")
    @PostMapping("/prepare")
    public ResponseEntity<ApiResponse<ShippingCheckoutInfo>> prepareShipping(
            @AuthenticationPrincipal final Jwt jwt,
            @Valid @RequestBody final ShippingSelectionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                shippingPreparationService.prepareShipping(jwt.getSubject(), request)));
    }
}
