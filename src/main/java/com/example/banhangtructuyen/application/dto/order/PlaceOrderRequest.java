package com.example.banhangtructuyen.application.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to place a new order from the customer's current cart")
public record PlaceOrderRequest(

        @Schema(description = "Full delivery address", example = "123 Nguyễn Huệ, Q1, TP.HCM")
        @NotBlank(message = "Địa chỉ giao hàng không được để trống")
        @Size(max = 500, message = "Địa chỉ giao hàng không được vượt quá 500 ký tự")
        String shippingAddress,

        @Schema(description = "Optional delivery note", example = "Gọi trước khi giao")
        @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
        String note
) {}
