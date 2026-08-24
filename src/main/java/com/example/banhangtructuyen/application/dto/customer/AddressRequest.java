package com.example.banhangtructuyen.application.dto.customer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Delivery address create/update request. Free-text fields only — "
        + "no admin ward/district/province codes. Default-address status is managed "
        + "via the separate set-default endpoint, not this DTO.")
public record AddressRequest(
        @Schema(description = "Recipient name", example = "Nguyễn Văn A")
        @NotBlank(message = "Recipient name is required")
        @Size(max = 200, message = "Recipient name must not exceed 200 characters")
        String recipientName,

        @Schema(description = "Vietnamese phone number", example = "0987654321")
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^(0|\\+84)\\d{9,10}$", message = "Phone must be a valid Vietnamese phone number")
        String phone,

        @Schema(description = "Street address line", example = "123 Đường Lê Lợi")
        @NotBlank(message = "Address line is required")
        @Size(max = 255, message = "Address line must not exceed 255 characters")
        String line1,

        @Schema(description = "Ward (optional, free text)", example = "Phường Bến Nghé")
        @Size(max = 100, message = "Ward must not exceed 100 characters")
        String ward,

        @Schema(description = "District (optional, free text)", example = "Quận 1")
        @Size(max = 100, message = "District must not exceed 100 characters")
        String district,

        @Schema(description = "Province/city (free text)", example = "TP. Hồ Chí Minh")
        @NotBlank(message = "Province is required")
        @Size(max = 100, message = "Province must not exceed 100 characters")
        String province
) {}
