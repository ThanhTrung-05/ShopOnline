package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.customer.AddressRequest;
import com.example.banhangtructuyen.application.dto.customer.AddressResponse;
import com.example.banhangtructuyen.application.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Self-service delivery address endpoints for ATS-23. The customer is always resolved from the
 * validated JWT subject ({@code sub}) via {@code CUSTOMERS.KEYCLOAK_USER_ID} — never from a
 * client-supplied customer id. Every address lookup binds addressId + customerId together;
 * an address belonging to another customer resolves as 404, not 403.
 */
@RestController
@RequestMapping("/api/v1/customers/me/addresses")
@RequiredArgsConstructor
@Tag(name = "Customer Addresses", description = "Authenticated customer self-service delivery address management")
public class AddressController {

    private final AddressService addressService;

    @Operation(summary = "List delivery addresses", description = "Returns all delivery addresses for the authenticated customer, ordered by addressId.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> listAddresses(@AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(addressService.listAddresses(jwt.getSubject())));
    }

    @Operation(summary = "Get a delivery address", description = "Returns a single address owned by the authenticated customer. 404 if it does not exist or belongs to another customer.")
    @GetMapping("/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> getAddress(
            @AuthenticationPrincipal final Jwt jwt,
            @PathVariable final Long addressId) {
        return ResponseEntity.ok(ApiResponse.success(addressService.getAddress(jwt.getSubject(), addressId)));
    }

    @Operation(summary = "Create a delivery address", description = "The first address created for a customer automatically becomes the default; subsequent ones are non-default.")
    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> createAddress(
            @AuthenticationPrincipal final Jwt jwt,
            @Valid @RequestBody final AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(addressService.createAddress(jwt.getSubject(), request)));
    }

    @Operation(summary = "Update a delivery address", description = "Full update of address fields. Default-address status is unaffected — use the set-default endpoint for that.")
    @PutMapping("/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @AuthenticationPrincipal final Jwt jwt,
            @PathVariable final Long addressId,
            @Valid @RequestBody final AddressRequest request) {
        return ResponseEntity.ok(ApiResponse.success(addressService.updateAddress(jwt.getSubject(), addressId, request)));
    }

    @Operation(summary = "Delete a delivery address", description = "If the deleted address was the default and other addresses remain, the address with the smallest addressId becomes the new default.")
    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @AuthenticationPrincipal final Jwt jwt,
            @PathVariable final Long addressId) {
        addressService.deleteAddress(jwt.getSubject(), addressId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set an address as default", description = "Clears any existing default for this customer and sets the target address as default, in the same transaction.")
    @PatchMapping("/{addressId}/default")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(
            @AuthenticationPrincipal final Jwt jwt,
            @PathVariable final Long addressId) {
        return ResponseEntity.ok(ApiResponse.success(addressService.setDefaultAddress(jwt.getSubject(), addressId)));
    }
}
