package com.example.banhangtructuyen.application.dto.customer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Partial profile update — omitted or null fields are left unchanged; "
        + "fullName, if present, must not be blank")
public record UpdateProfileRequest(
        @Schema(description = "New full name (omit or null to leave unchanged)", example = "Nguyễn Văn B")
        @Size(max = 200, message = "Full name must not exceed 200 characters")
        String fullName,

        @Schema(description = "New Vietnamese phone number (omit or null to leave unchanged)", example = "0987654321")
        @Pattern(regexp = "^(0|\\+84)\\d{9,10}$", message = "Phone must be a valid Vietnamese phone number")
        String phone
) {}
