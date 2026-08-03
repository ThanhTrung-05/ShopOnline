package com.example.banhangtructuyen.application.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Customer registration request")
public record RegisterRequest(
        @Schema(description = "Customer email — used as login identifier", example = "customer@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 200, message = "Email must not exceed 200 characters")
        String email,

        @Schema(description = "Password — 8 to 64 characters", example = "SecurePass123")
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
        String password,

        @Schema(description = "Customer full name", example = "Nguyễn Văn A")
        @NotBlank(message = "Full name is required")
        @Size(max = 200, message = "Full name must not exceed 200 characters")
        String fullName,

        @Schema(description = "Vietnamese phone number (optional)", example = "0987654321")
        @Pattern(regexp = "^(0|\\+84)\\d{9,10}$", message = "Phone must be a valid Vietnamese phone number")
        String phone
) {}
