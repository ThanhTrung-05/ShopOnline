package com.example.banhangtructuyen.application.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Login response — JWT access token and account info")
public record LoginResponse(
        @Schema(description = "Signed JWT access token — send as 'Authorization: Bearer <token>'")
        String token,

        @Schema(description = "Customer email", example = "admin@example.com")
        String email,

        @Schema(description = "Customer full name", example = "Quản trị viên")
        String fullName,

        @Schema(description = "Account role", example = "ADMIN", allowableValues = {"USER", "ADMIN"})
        String role
) {}
