package com.example.banhangtructuyen.application.dto.customer;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Delivery address — does not expose customerId")
public record AddressResponse(
        @Schema(description = "Address ID", example = "1")
        Long addressId,

        @Schema(description = "Recipient name", example = "Nguyễn Văn A")
        String recipientName,

        @Schema(description = "Vietnamese phone number", example = "0987654321")
        String phone,

        @Schema(description = "Street address line", example = "123 Đường Lê Lợi")
        String line1,

        @Schema(description = "Ward (nullable)", example = "Phường Bến Nghé")
        String ward,

        @Schema(description = "District (nullable)", example = "Quận 1")
        String district,

        @Schema(description = "Province/city", example = "TP. Hồ Chí Minh")
        String province,

        @Schema(description = "Whether this is the customer's default delivery address", example = "true")
        boolean isDefault
) {}
