package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.ApiResponse;
import com.example.banhangtructuyen.application.dto.auth.RegisterRequest;
import com.example.banhangtructuyen.application.dto.auth.RegisterResponse;
import com.example.banhangtructuyen.application.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Customer authentication — registration")
public class AuthController {

    private final AuthService authService;

    @Operation(
        summary = "Register a new customer account",
        description = "Creates a Keycloak identity, an ACTIVE Oracle customer with ROLE=USER, "
                    + "and the customer's lifetime cart. The password is managed only by Keycloak "
                    + "and is never persisted in Oracle or returned in the response."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account created successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid email, password, full name, or phone",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already registered or database conflict",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Keycloak provisioning unavailable",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal registration provisioning failure",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody final RegisterRequest request) {
        final RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

}
