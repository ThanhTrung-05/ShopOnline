package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.customer.CustomerResponse;
import com.example.banhangtructuyen.application.dto.customer.UpdateProfileRequest;
import com.example.banhangtructuyen.application.service.CustomerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service profile endpoints for ATS-23. The customer is always resolved from the
 * validated JWT subject ({@code sub}) via {@code CUSTOMERS.KEYCLOAK_USER_ID} — never from a
 * client-supplied customer id.
 */
@RestController
@RequestMapping("/api/v1/customers/me")
@RequiredArgsConstructor
@Tag(name = "Customer Profile", description = "Authenticated customer self-service profile")
public class CustomerController {

    private final CustomerProfileService customerProfileService;

    @Operation(
        summary = "Get current customer profile",
        description = "Returns the profile of the authenticated customer, resolved from the JWT subject."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile returned",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid, or expired token",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No customer record for this identity",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> getProfile(@AuthenticationPrincipal final Jwt jwt) {
        final CustomerResponse response = customerProfileService.getProfile(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Update current customer profile",
        description = "Partially updates fullName and/or phone. Omitted or null fields are left unchanged. "
                    + "Does not touch Keycloak — email, password, and roles are unaffected."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Blank full name or invalid phone format",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid, or expired token",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No customer record for this identity",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> updateProfile(
            @AuthenticationPrincipal final Jwt jwt,
            @Valid @RequestBody final UpdateProfileRequest request) {
        final CustomerResponse response = customerProfileService.updateProfile(jwt.getSubject(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
