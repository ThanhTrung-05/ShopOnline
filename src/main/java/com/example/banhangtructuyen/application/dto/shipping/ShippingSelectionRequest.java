package com.example.banhangtructuyen.application.dto.shipping;

import com.example.banhangtructuyen.domain.model.ShippingMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Select an owned delivery address and an MVP shipping method. "
        + "Customer identity and address details are always resolved by the backend.")
public record ShippingSelectionRequest(
        @Schema(description = "Delivery address id", example = "42")
        @NotNull(message = "Address id is required")
        @Positive(message = "Address id must be greater than 0")
        Long addressId,

        @Schema(
                description = "Shipping service level",
                example = "STANDARD",
                allowableValues = {"STANDARD", "EXPRESS"})
        @NotNull(message = "Shipping method is required")
        ShippingMethod shippingMethod
) {}
