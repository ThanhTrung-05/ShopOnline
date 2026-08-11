package com.example.banhangtructuyen.application.dto.customer;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Customer profile information — never includes password or Keycloak internals")
public record CustomerResponse(
        @Schema(description = "Unique customer database ID", example = "1")
        Long customerId,

        @Schema(description = "Customer email", example = "customer@example.com")
        String email,

        @Schema(description = "Customer full name", example = "Nguyễn Văn A")
        String fullName,

        @Schema(description = "Vietnamese phone number (nullable)", example = "0987654321")
        String phone,

        @Schema(description = "Account role", example = "USER", allowableValues = {"USER", "ADMIN"})
        String role,

        @Schema(description = "Account status", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE", "BANNED"})
        String status,

        @Schema(description = "Account creation timestamp")
        Instant createdAt,

        @Schema(description = "Last profile update timestamp")
        Instant updatedAt
) {}
