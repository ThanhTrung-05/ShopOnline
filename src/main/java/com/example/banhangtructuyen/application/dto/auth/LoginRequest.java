package com.example.banhangtructuyen.application.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Login request — email and password")
public record LoginRequest(
        @Schema(description = "Registered email", example = "admin@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @Schema(description = "Account password", example = "admin123")
        @NotBlank(message = "Password is required")
        String password
) {}
